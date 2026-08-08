import { createHash, randomBytes, randomUUID } from "node:crypto";
import type { NextRequest } from "next/server";
import { databaseQuery } from "@/lib/db";
import {
  authenticateMobileRequest,
  MobileAuthError,
  type MobileAuthProvider,
} from "@/lib/mobile-auth";

type LinkStateRow = {
  user_id: string;
  provider: MobileAuthProvider;
  redirect_uri: string;
  client_state: string;
  code_challenge: string;
};

type ProviderProfile = {
  id: string;
  name: string;
  email: string | null;
  image: string | null;
};

const LINK_STATE_TTL_MS = 10 * 60 * 1_000;
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

function apiOrigin(): string {
  const configured =
    process.env.NEXT_PUBLIC_API_URL?.trim() ||
    process.env.MOBILE_PRODUCTION_API_URL?.trim();
  if (!configured) {
    throw new MobileAuthError("La URL pública de la API no está configurada.", 503, "auth_not_configured");
  }
  return configured.replace(/\/$/, "");
}

function providerEnv(provider: MobileAuthProvider): { clientId: string; clientSecret: string } {
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

function providerAuthorizationUrl(provider: MobileAuthProvider, state: string): string {
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
          : "El proveedor rechazó la solicitud.";
    throw new MobileAuthError(message, 502, "provider_error");
  }
  return payload;
}

async function exchangeProviderCode(provider: MobileAuthProvider, code: string): Promise<string> {
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
  const payload = await readJsonResponse(
    await fetch(tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form,
      cache: "no-store",
    }),
  );
  const accessToken = payload.access_token;
  if (typeof accessToken !== "string" || !accessToken) {
    throw new MobileAuthError("El proveedor no devolvió un token de acceso.", 502, "provider_token_missing");
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
    const payload = await readJsonResponse(await fetch(url, { cache: "no-store" }));
    const picture = payload.picture as { data?: { url?: string } } | undefined;
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

export async function createAccountLinkStart(
  request: NextRequest,
  input: {
    provider: MobileAuthProvider;
    redirectUri: string;
    clientState: string;
    codeChallenge: string;
  },
): Promise<string> {
  const user = await authenticateMobileRequest(request);
  if (input.redirectUri !== ALLOWED_MOBILE_REDIRECT) {
    throw new MobileAuthError("La URL de retorno no es válida.", 400, "invalid_redirect");
  }
  if (!/^[A-Za-z0-9_-]{24,128}$/.test(input.clientState)) {
    throw new MobileAuthError("El estado de autenticación no es válido.", 400, "invalid_state");
  }
  if (!/^[A-Za-z0-9_-]{43}$/.test(input.codeChallenge)) {
    throw new MobileAuthError("El desafío PKCE no es válido.", 400, "invalid_pkce");
  }
  const existing = await databaseQuery(
    `select 1 from app_auth_accounts where user_id = $1 and provider = $2 limit 1`,
    [user.id, input.provider],
  );
  if (existing.rows[0]) {
    throw new MobileAuthError("Ese proveedor ya está vinculado.", 409, "provider_already_linked");
  }
  const providerState = randomToken(32);
  await databaseQuery(
    `insert into mobile_account_link_states (
       state_hash, user_id, provider, redirect_uri, client_state, code_challenge, expires_at
     ) values ($1, $2, $3, $4, $5, $6, $7)`,
    [
      hashToken(providerState),
      user.id,
      input.provider,
      input.redirectUri,
      input.clientState,
      input.codeChallenge,
      new Date(Date.now() + LINK_STATE_TTL_MS),
    ],
  );
  return providerAuthorizationUrl(input.provider, providerState);
}

async function assertSourceAccountCanMerge(sourceUserId: string): Promise<void> {
  const result = await databaseQuery<{
    provider_count: number;
    has_password: boolean;
    has_chat_state: boolean;
  }>(
    `select
       (select count(*)::int from app_auth_accounts a where a.user_id = $1) as provider_count,
       exists(select 1 from app_password_credentials p where p.user_id = $1) as has_password,
       exists(
         select 1 from mobile_user_chat_state s
          where s.user_id = $1
            and s.payload <> '{"sessions":[],"projects":[]}'::jsonb
       ) as has_chat_state`,
    [sourceUserId],
  );
  const row = result.rows[0];
  if (!row || row.provider_count > 1 || row.has_password || row.has_chat_state) {
    throw new MobileAuthError(
      "Ese proveedor pertenece a otra cuenta Nexora con datos propios. No se fusionó automáticamente.",
      409,
      "provider_account_has_data",
    );
  }
}

export async function completeAccountLinkCallback(input: {
  provider: MobileAuthProvider;
  state: string;
  code: string;
}): Promise<string | null> {
  const stateResult = await databaseQuery<LinkStateRow>(
    `delete from mobile_account_link_states
      where state_hash = $1
        and provider = $2
        and expires_at > now()
      returning user_id, provider, redirect_uri, client_state, code_challenge`,
    [hashToken(input.state), input.provider],
  );
  const linkState = stateResult.rows[0];
  if (!linkState) return null;

  const providerAccessToken = await exchangeProviderCode(input.provider, input.code);
  const profile = await providerProfile(input.provider, providerAccessToken);
  if (!profile.id) {
    throw new MobileAuthError("El proveedor no devolvió una identidad válida.", 502, "provider_profile_invalid");
  }

  const linkedResult = await databaseQuery<{
    account_id: string;
    user_id: string;
  }>(
    `select id as account_id, user_id
       from app_auth_accounts
      where provider = $1 and provider_account_id = $2
      limit 1`,
    [input.provider, profile.id],
  );
  const linked = linkedResult.rows[0];

  if (linked && linked.user_id !== linkState.user_id) {
    await assertSourceAccountCanMerge(linked.user_id);
  }

  await databaseQuery(
    `insert into app_auth_link_authorizations (
       id, user_id, provider, provider_account_id, expires_at
     ) values ($1, $2, $3, $4, $5)`,
    [
      randomUUID(),
      linkState.user_id,
      input.provider,
      profile.id,
      new Date(Date.now() + 60_000),
    ],
  );

  if (linked) {
    await databaseQuery(
      `update app_auth_accounts
          set user_id = $2, updated_at = now()
        where id = $1`,
      [linked.account_id, linkState.user_id],
    );
  } else {
    await databaseQuery(
      `insert into app_auth_accounts (
         id, user_id, provider, provider_account_id
       ) values ($1, $2, $3, $4)`,
      [randomUUID(), linkState.user_id, input.provider, profile.id],
    );
  }
  await databaseQuery(
    `delete from app_auth_link_authorizations
      where user_id = $1 and provider = $2 and provider_account_id = $3`,
    [linkState.user_id, input.provider, profile.id],
  );

  await databaseQuery(
    `update app_users
        set image_url = coalesce(image_url, $2),
            updated_at = now()
      where id = $1`,
    [linkState.user_id, profile.image],
  );

  if (linked && linked.user_id !== linkState.user_id) {
    await databaseQuery(
      `update app_auth_sessions set revoked_at = now(), last_used_at = now()
        where user_id = $1 and revoked_at is null`,
      [linked.user_id],
    );
    await databaseQuery(`delete from app_users where id = $1`, [linked.user_id]);
  }

  const mobileCode = randomToken(32);
  await databaseQuery(
    `insert into mobile_auth_codes (
       code_hash, user_id, code_challenge, expires_at
     ) values ($1, $2, $3, $4)`,
    [
      hashToken(mobileCode),
      linkState.user_id,
      linkState.code_challenge,
      new Date(Date.now() + MOBILE_CODE_TTL_MS),
    ],
  );

  const callback = new URL(linkState.redirect_uri);
  callback.searchParams.set("code", mobileCode);
  callback.searchParams.set("state", linkState.client_state);
  callback.searchParams.set("provider", input.provider);
  callback.searchParams.set("linked", "1");
  return callback.toString();
}

export async function unlinkAccountProvider(
  request: NextRequest,
  provider: MobileAuthProvider,
): Promise<void> {
  const user = await authenticateMobileRequest(request);
  const methods = await databaseQuery<{
    provider_count: number;
    has_password: boolean;
  }>(
    `select
       (select count(*)::int from app_auth_accounts where user_id = $1) as provider_count,
       exists(select 1 from app_password_credentials where user_id = $1) as has_password`,
    [user.id],
  );
  const row = methods.rows[0];
  const methodCount = (row?.provider_count || 0) + (row?.has_password ? 1 : 0);
  if (methodCount <= 1) {
    throw new MobileAuthError(
      "No puedes eliminar el único método de acceso de la cuenta.",
      409,
      "last_auth_method",
    );
  }
  const deleted = await databaseQuery(
    `delete from app_auth_accounts
      where user_id = $1 and provider = $2
      returning id`,
    [user.id, provider],
  );
  if (!deleted.rows[0]) {
    throw new MobileAuthError("Ese proveedor no está vinculado.", 404, "provider_not_linked");
  }
}