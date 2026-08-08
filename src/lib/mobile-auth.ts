import { createHash, randomBytes, randomUUID, scrypt } from "node:crypto";
import type { NextRequest } from "next/server";
import { databaseQuery } from "@/lib/db";

export type MobileAuthProvider = "google" | "facebook" | "discord";

export type MobileAuthUser = {
  id: string;
  name: string;
  email: string | null;
  image: string | null;
};

export type MobileAuthSession = {
  user: MobileAuthUser;
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
};

type UserRow = {
  id: string;
  name: string;
  email: string | null;
  image_url: string | null;
};

type OAuthStateRow = {
  provider: MobileAuthProvider;
  redirect_uri: string;
  client_state: string;
  code_challenge: string;
};

type MobileCodeRow = {
  user_id: string;
  code_challenge: string;
};

type PasswordRow = UserRow & {
  password_salt: string;
  password_hash: string;
};

type SessionRow = UserRow & {
  session_id: string;
};

type ProviderProfile = {
  id: string;
  name: string;
  email: string | null;
  image: string | null;
};

export class MobileAuthError extends Error {
  constructor(
    message: string,
    public readonly status = 400,
    public readonly code = "auth_error",
  ) {
    super(message);
    this.name = "MobileAuthError";
  }
}

const ACCESS_TTL_MS = 60 * 60 * 1_000;
const REFRESH_TTL_MS = 30 * 24 * 60 * 60 * 1_000;
const OAUTH_STATE_TTL_MS = 10 * 60 * 1_000;
const MOBILE_CODE_TTL_MS = 5 * 60 * 1_000;
const ALLOWED_MOBILE_REDIRECT = "nexoraai://auth/callback";

function randomToken(bytes: number): string {
  return randomBytes(bytes).toString("base64url");
}

function hashToken(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}

function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}

function safeUser(row: UserRow): MobileAuthUser {
  return {
    id: row.id,
    name: row.name,
    email: row.email,
    image: row.image_url,
  };
}

function apiOrigin(): string {
  const configured =
    process.env.NEXT_PUBLIC_API_URL?.trim() ||
    process.env.MOBILE_PRODUCTION_API_URL?.trim();
  if (!configured) {
    throw new MobileAuthError(
      "NEXT_PUBLIC_API_URL es obligatorio para autenticación social.",
      503,
      "auth_not_configured",
    );
  }
  return configured.replace(/\/$/, "");
}

function assertMobileRedirect(uri: string): string {
  if (uri !== ALLOWED_MOBILE_REDIRECT) {
    throw new MobileAuthError("La URL de retorno de la aplicación no es válida.", 400, "invalid_redirect");
  }
  return uri;
}

function assertCodeChallenge(value: string): string {
  if (!/^[A-Za-z0-9_-]{43}$/.test(value)) {
    throw new MobileAuthError("El desafío PKCE no es válido.", 400, "invalid_pkce");
  }
  return value;
}

function providerEnv(provider: MobileAuthProvider): {
  clientId: string;
  clientSecret: string;
} {
  const prefix = provider.toUpperCase();
  const clientId = process.env[`${prefix}_CLIENT_ID`]?.trim();
  const clientSecret = process.env[`${prefix}_CLIENT_SECRET`]?.trim();
  if (!clientId || !clientSecret) {
    throw new MobileAuthError(
      `${provider} no está configurado en el servidor.`,
      503,
      "provider_not_configured",
    );
  }
  return { clientId, clientSecret };
}

function providerRedirectUri(provider: MobileAuthProvider): string {
  return `${apiOrigin()}/api/auth/mobile/callback/${provider}`;
}

function providerAuthorizationUrl(
  provider: MobileAuthProvider,
  state: string,
): string {
  const { clientId } = providerEnv(provider);
  const redirectUri = providerRedirectUri(provider);

  if (provider === "google") {
    const url = new URL("https://accounts.google.com/o/oauth2/v2/auth");
    url.search = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: "code",
      scope: "openid email profile",
      state,
      prompt: "select_account",
    }).toString();
    return url.toString();
  }

  if (provider === "discord") {
    const url = new URL("https://discord.com/oauth2/authorize");
    url.search = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: "code",
      scope: "identify email",
      state,
      prompt: "consent",
    }).toString();
    return url.toString();
  }

  const url = new URL("https://www.facebook.com/dialog/oauth");
  url.search = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: "code",
    scope: "email public_profile",
    state,
  }).toString();
  return url.toString();
}

async function readJsonResponse(response: Response): Promise<Record<string, unknown>> {
  const text = await response.text();
  const payload = (() => {
    try {
      return JSON.parse(text) as Record<string, unknown>;
    } catch {
      return {};
    }
  })();
  if (!response.ok) {
    const message =
      typeof payload.error_description === "string"
        ? payload.error_description
        : typeof payload.message === "string"
          ? payload.message
          : "El proveedor de identidad rechazó la solicitud.";
    throw new MobileAuthError(message, 502, "provider_error");
  }
  return payload;
}

async function exchangeProviderCode(
  provider: MobileAuthProvider,
  code: string,
): Promise<string> {
  const { clientId, clientSecret } = providerEnv(provider);
  const form = new URLSearchParams({
    client_id: clientId,
    client_secret: clientSecret,
    redirect_uri: providerRedirectUri(provider),
    code,
  });
  if (provider !== "facebook") form.set("grant_type", "authorization_code");

  const tokenUrl =
    provider === "google"
      ? "https://oauth2.googleapis.com/token"
      : provider === "discord"
        ? "https://discord.com/api/oauth2/token"
        : "https://graph.facebook.com/oauth/access_token";

  const response = await fetch(tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form,
    cache: "no-store",
  });
  const payload = await readJsonResponse(response);
  const accessToken = payload.access_token;
  if (typeof accessToken !== "string" || !accessToken) {
    throw new MobileAuthError(
      "El proveedor no devolvió un token de acceso.",
      502,
      "provider_token_missing",
    );
  }
  return accessToken;
}

async function providerProfile(
  provider: MobileAuthProvider,
  accessToken: string,
): Promise<ProviderProfile> {
  if (provider === "facebook") {
    const url = new URL("https://graph.facebook.com/me");
    url.search = new URLSearchParams({
      fields: "id,name,email,picture.type(large)",
      access_token: accessToken,
    }).toString();
    const payload = await readJsonResponse(
      await fetch(url, { cache: "no-store" }),
    );
    const picture = payload.picture as
      | { data?: { url?: string } }
      | undefined;
    return {
      id: String(payload.id || ""),
      name: String(payload.name || "Usuario Nexora"),
      email: typeof payload.email === "string" ? normalizeEmail(payload.email) : null,
      image: picture?.data?.url || null,
    };
  }

  const endpoint =
    provider === "google"
      ? "https://openidconnect.googleapis.com/v1/userinfo"
      : "https://discord.com/api/users/@me";
  const payload = await readJsonResponse(
    await fetch(endpoint, {
      headers: { Authorization: `Bearer ${accessToken}` },
      cache: "no-store",
    }),
  );

  if (provider === "google") {
    return {
      id: String(payload.sub || ""),
      name: String(payload.name || "Usuario Nexora"),
      email: typeof payload.email === "string" ? normalizeEmail(payload.email) : null,
      image: typeof payload.picture === "string" ? payload.picture : null,
    };
  }

  const discordId = String(payload.id || "");
  const avatar = typeof payload.avatar === "string" ? payload.avatar : null;
  return {
    id: discordId,
    name:
      typeof payload.global_name === "string" && payload.global_name
        ? payload.global_name
        : String(payload.username || "Usuario Nexora"),
    email: typeof payload.email === "string" ? normalizeEmail(payload.email) : null,
    image:
      avatar && discordId
        ? `https://cdn.discordapp.com/avatars/${discordId}/${avatar}.png?size=256`
        : null,
  };
}

async function upsertSocialUser(
  provider: MobileAuthProvider,
  profile: ProviderProfile,
): Promise<MobileAuthUser> {
  if (!profile.id) {
    throw new MobileAuthError(
      "El proveedor no devolvió un identificador de usuario.",
      502,
      "provider_profile_invalid",
    );
  }

  const existingAccount = await databaseQuery<UserRow>(
    `select u.id, u.name, u.email, u.image_url
       from app_auth_accounts a
       join app_users u on u.id = a.user_id
      where a.provider = $1 and a.provider_account_id = $2
      limit 1`,
    [provider, profile.id],
  );
  const linked = existingAccount.rows[0];
  if (linked) {
    const refreshed = await databaseQuery<UserRow>(
      `update app_users
          set name = $2,
              image_url = coalesce($3, image_url),
              email = coalesce(email, $4),
              updated_at = now()
        where id = $1
        returning id, name, email, image_url`,
      [linked.id, profile.name, profile.image, profile.email],
    );
    return safeUser(refreshed.rows[0] || linked);
  }

  let user: UserRow | undefined;
  if (profile.email) {
    const byEmail = await databaseQuery<UserRow>(
      `select id, name, email, image_url
         from app_users
        where lower(email) = lower($1)
        limit 1`,
      [profile.email],
    );
    user = byEmail.rows[0];
  }

  if (!user) {
    const created = await databaseQuery<UserRow>(
      `insert into app_users (id, name, email, image_url)
       values ($1, $2, $3, $4)
       returning id, name, email, image_url`,
      [randomUUID(), profile.name, profile.email, profile.image],
    );
    user = created.rows[0];
  }

  await databaseQuery(
    `insert into app_auth_accounts (
       id, user_id, provider, provider_account_id
     ) values ($1, $2, $3, $4)
     on conflict (provider, provider_account_id)
     do update set user_id = excluded.user_id, updated_at = now()`,
    [randomUUID(), user.id, provider, profile.id],
  );

  return safeUser(user);
}

function derivePassword(password: string, salt: string): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    scrypt(
      password,
      salt,
      64,
      { N: 16_384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 },
      (error, derivedKey) => {
        if (error) reject(error);
        else resolve(derivedKey);
      },
    );
  });
}

async function issueSession(user: MobileAuthUser): Promise<MobileAuthSession> {
  const accessToken = randomToken(32);
  const refreshToken = randomToken(48);
  const accessExpiresAt = new Date(Date.now() + ACCESS_TTL_MS);
  const refreshExpiresAt = new Date(Date.now() + REFRESH_TTL_MS);

  await databaseQuery(
    `delete from app_auth_sessions
      where user_id = $1 and (refresh_expires_at <= now() or revoked_at is not null)`,
    [user.id],
  );
  await databaseQuery(
    `insert into app_auth_sessions (
       id, user_id, access_token_hash, refresh_token_hash,
       access_expires_at, refresh_expires_at
     ) values ($1, $2, $3, $4, $5, $6)`,
    [
      randomUUID(),
      user.id,
      hashToken(accessToken),
      hashToken(refreshToken),
      accessExpiresAt,
      refreshExpiresAt,
    ],
  );

  return {
    user,
    accessToken,
    refreshToken,
    accessExpiresAt: accessExpiresAt.toISOString(),
    refreshExpiresAt: refreshExpiresAt.toISOString(),
  };
}

export function isMobileProvider(value: string): value is MobileAuthProvider {
  return value === "google" || value === "facebook" || value === "discord";
}

export async function createOAuthStart(input: {
  provider: MobileAuthProvider;
  redirectUri: string;
  clientState: string;
  codeChallenge: string;
}): Promise<string> {
  const redirectUri = assertMobileRedirect(input.redirectUri);
  const clientState = input.clientState.trim();
  if (!/^[A-Za-z0-9_-]{24,128}$/.test(clientState)) {
    throw new MobileAuthError("El estado de autenticación no es válido.", 400, "invalid_state");
  }
  const codeChallenge = assertCodeChallenge(input.codeChallenge.trim());
  const state = randomToken(32);

  await databaseQuery(
    `insert into mobile_oauth_states (
       state_hash, provider, redirect_uri, client_state, code_challenge, expires_at
     ) values ($1, $2, $3, $4, $5, $6)`,
    [
      hashToken(state),
      input.provider,
      redirectUri,
      clientState,
      codeChallenge,
      new Date(Date.now() + OAUTH_STATE_TTL_MS),
    ],
  );

  return providerAuthorizationUrl(input.provider, state);
}

export async function completeOAuthCallback(input: {
  provider: MobileAuthProvider;
  state: string;
  code: string;
}): Promise<string> {
  const stateResult = await databaseQuery<OAuthStateRow>(
    `delete from mobile_oauth_states
      where state_hash = $1
        and provider = $2
        and expires_at > now()
      returning provider, redirect_uri, client_state, code_challenge`,
    [hashToken(input.state), input.provider],
  );
  const state = stateResult.rows[0];
  if (!state) {
    throw new MobileAuthError(
      "La solicitud de autenticación expiró o no es válida.",
      400,
      "oauth_state_invalid",
    );
  }

  const providerAccessToken = await exchangeProviderCode(input.provider, input.code);
  const profile = await providerProfile(input.provider, providerAccessToken);
  const user = await upsertSocialUser(input.provider, profile);
  const mobileCode = randomToken(32);

  await databaseQuery(
    `insert into mobile_auth_codes (
       code_hash, user_id, code_challenge, expires_at
     ) values ($1, $2, $3, $4)`,
    [
      hashToken(mobileCode),
      user.id,
      state.code_challenge,
      new Date(Date.now() + MOBILE_CODE_TTL_MS),
    ],
  );

  const callback = new URL(state.redirect_uri);
  callback.searchParams.set("code", mobileCode);
  callback.searchParams.set("state", state.client_state);
  callback.searchParams.set("provider", input.provider);
  return callback.toString();
}

export async function exchangeMobileCode(
  code: string,
  verifier: string,
): Promise<MobileAuthSession> {
  if (!/^[A-Za-z0-9_-]{43,128}$/.test(verifier)) {
    throw new MobileAuthError("El verificador PKCE no es válido.", 400, "invalid_pkce");
  }
  const result = await databaseQuery<MobileCodeRow & UserRow>(
    `delete from mobile_auth_codes c
      using app_users u
      where c.code_hash = $1
        and c.expires_at > now()
        and u.id = c.user_id
      returning c.user_id, c.code_challenge, u.id, u.name, u.email, u.image_url`,
    [hashToken(code)],
  );
  const row = result.rows[0];
  if (!row) {
    throw new MobileAuthError("El código de acceso expiró.", 400, "mobile_code_expired");
  }

  const verifierChallenge = createHash("sha256").update(verifier).digest("base64url");
  const expected = Buffer.from(row.code_challenge);
  const actual = Buffer.from(verifierChallenge);
  if (expected.length !== actual.length || !cryptoSafeEqual(expected, actual)) {
    throw new MobileAuthError("La comprobación PKCE falló.", 401, "invalid_pkce");
  }
  return issueSession(safeUser(row));
}

function cryptoSafeEqual(left: Buffer, right: Buffer): boolean {
  if (left.length !== right.length) return false;
  let different = 0;
  for (let index = 0; index < left.length; index += 1) {
    different |= left[index] ^ right[index];
  }
  return different === 0;
}

export async function registerWithEmail(input: {
  name: string;
  email: string;
  password: string;
}): Promise<MobileAuthSession> {
  const name = input.name.trim().replace(/\s+/g, " ").slice(0, 80);
  const email = normalizeEmail(input.email);
  if (name.length < 2) {
    throw new MobileAuthError("Escribe tu nombre.", 400, "invalid_name");
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new MobileAuthError("El correo electrónico no es válido.", 400, "invalid_email");
  }
  if (input.password.length < 8 || input.password.length > 128) {
    throw new MobileAuthError(
      "La contraseña debe tener entre 8 y 128 caracteres.",
      400,
      "invalid_password",
    );
  }

  const existing = await databaseQuery<UserRow & { has_password: boolean }>(
    `select u.id, u.name, u.email, u.image_url,
            exists(select 1 from app_password_credentials p where p.user_id = u.id) as has_password
       from app_users u
      where lower(u.email) = lower($1)
      limit 1`,
    [email],
  );
  if (existing.rows[0]) {
    throw new MobileAuthError(
      existing.rows[0].has_password
        ? "Ya existe una cuenta con ese correo."
        : "Ese correo ya está vinculado a un inicio de sesión social.",
      409,
      "email_exists",
    );
  }

  const salt = randomToken(24);
  const passwordHash = (await derivePassword(input.password, salt)).toString("base64");
  const created = await databaseQuery<UserRow>(
    `insert into app_users (id, name, email)
     values ($1, $2, $3)
     returning id, name, email, image_url`,
    [randomUUID(), name, email],
  );
  const user = created.rows[0];
  await databaseQuery(
    `insert into app_password_credentials (user_id, password_salt, password_hash)
     values ($1, $2, $3)`,
    [user.id, salt, passwordHash],
  );
  return issueSession(safeUser(user));
}

export async function signInWithEmail(
  emailValue: string,
  password: string,
): Promise<MobileAuthSession> {
  const email = normalizeEmail(emailValue);
  const result = await databaseQuery<PasswordRow>(
    `select u.id, u.name, u.email, u.image_url,
            p.password_salt, p.password_hash
       from app_users u
       join app_password_credentials p on p.user_id = u.id
      where lower(u.email) = lower($1)
      limit 1`,
    [email],
  );
  const row = result.rows[0];
  if (!row) {
    throw new MobileAuthError("Correo o contraseña incorrectos.", 401, "invalid_credentials");
  }

  const actual = await derivePassword(password, row.password_salt);
  const expected = Buffer.from(row.password_hash, "base64");
  if (actual.length !== expected.length || !cryptoSafeEqual(actual, expected)) {
    throw new MobileAuthError("Correo o contraseña incorrectos.", 401, "invalid_credentials");
  }
  return issueSession(safeUser(row));
}

export async function authenticateMobileRequest(
  request: NextRequest,
): Promise<MobileAuthUser> {
  const authorization = request.headers.get("authorization") || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    throw new MobileAuthError("Debes iniciar sesión.", 401, "authentication_required");
  }

  const result = await databaseQuery<SessionRow>(
    `select s.id as session_id, u.id, u.name, u.email, u.image_url
       from app_auth_sessions s
       join app_users u on u.id = s.user_id
      where s.access_token_hash = $1
        and s.access_expires_at > now()
        and s.revoked_at is null
      limit 1`,
    [hashToken(match[1])],
  );
  const row = result.rows[0];
  if (!row) {
    throw new MobileAuthError("La sesión expiró.", 401, "session_expired");
  }
  void databaseQuery(
    `update app_auth_sessions set last_used_at = now() where id = $1`,
    [row.session_id],
  ).catch(() => undefined);
  return safeUser(row);
}

export async function refreshMobileSession(
  refreshToken: string,
): Promise<MobileAuthSession> {
  const result = await databaseQuery<SessionRow>(
    `select s.id as session_id, u.id, u.name, u.email, u.image_url
       from app_auth_sessions s
       join app_users u on u.id = s.user_id
      where s.refresh_token_hash = $1
        and s.refresh_expires_at > now()
        and s.revoked_at is null
      limit 1`,
    [hashToken(refreshToken)],
  );
  const row = result.rows[0];
  if (!row) {
    throw new MobileAuthError("La sesión ya no es válida.", 401, "refresh_expired");
  }

  const accessToken = randomToken(32);
  const nextRefreshToken = randomToken(48);
  const accessExpiresAt = new Date(Date.now() + ACCESS_TTL_MS);
  const refreshExpiresAt = new Date(Date.now() + REFRESH_TTL_MS);
  await databaseQuery(
    `update app_auth_sessions
        set access_token_hash = $2,
            refresh_token_hash = $3,
            access_expires_at = $4,
            refresh_expires_at = $5,
            last_used_at = now()
      where id = $1`,
    [
      row.session_id,
      hashToken(accessToken),
      hashToken(nextRefreshToken),
      accessExpiresAt,
      refreshExpiresAt,
    ],
  );

  return {
    user: safeUser(row),
    accessToken,
    refreshToken: nextRefreshToken,
    accessExpiresAt: accessExpiresAt.toISOString(),
    refreshExpiresAt: refreshExpiresAt.toISOString(),
  };
}

export async function revokeMobileSession(input: {
  accessToken?: string | null;
  refreshToken?: string | null;
}): Promise<void> {
  const accessHash = input.accessToken ? hashToken(input.accessToken) : null;
  const refreshHash = input.refreshToken ? hashToken(input.refreshToken) : null;
  if (!accessHash && !refreshHash) return;
  await databaseQuery(
    `update app_auth_sessions
        set revoked_at = now(), last_used_at = now()
      where ($1::text is not null and access_token_hash = $1)
         or ($2::text is not null and refresh_token_hash = $2)`,
    [accessHash, refreshHash],
  );
}

export function mobileAuthErrorResponse(error: unknown): Response {
  if (error instanceof MobileAuthError) {
    return Response.json(
      { ok: false, error: error.message, code: error.code },
      { status: error.status },
    );
  }
  console.error("[NexoraAI] Mobile auth error", error);
  return Response.json(
    { ok: false, error: "No se pudo completar la autenticación." },
    { status: 500 },
  );
}
