import {
  createHash,
  createHmac,
  randomBytes,
  randomInt,
  randomUUID,
  scrypt,
  timingSafeEqual,
} from "node:crypto";
import type { NextRequest } from "next/server";
import { databaseQuery } from "@/lib/db";
import {
  authenticateMobileRequest,
  MobileAuthError,
  type MobileAuthProvider,
  type MobileAuthUser,
} from "@/lib/mobile-auth";

type AccountUserRow = {
  id: string;
  name: string;
  email: string | null;
  image_url: string | null;
  email_verified_at: Date | string | null;
  has_password: boolean;
};

type AccountCodePurpose = "verify_email" | "reset_password";

type AccountCodeRow = {
  id: string;
  attempts: number;
  code_hash: string;
};

type AccountSessionRow = {
  id: string;
  device_name: string;
  created_at: Date | string;
  last_used_at: Date | string;
  current: boolean;
};

type PasswordCredentialRow = {
  password_salt: string;
  password_hash: string;
};

export type MobileAccountOverview = {
  user: MobileAuthUser & { emailVerified: boolean };
  providers: MobileAuthProvider[];
  hasPassword: boolean;
  sessions: Array<{
    id: string;
    deviceName: string;
    createdAt: Date | string;
    lastUsedAt: Date | string;
    current: boolean;
  }>;
};

const CODE_TTL_MS = 10 * 60 * 1_000;
const MAX_CODE_ATTEMPTS = 5;

function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}

function bearerToken(request: NextRequest): string {
  const authorization = request.headers.get("authorization") || "";
  return authorization.match(/^Bearer\s+(.+)$/i)?.[1] || "";
}

function currentAccessHash(request: NextRequest): string {
  const token = bearerToken(request);
  return token ? createHash("sha256").update(token).digest("hex") : "";
}

function derivedRuntimeSecret(label: string): string {
  const databaseSecret = process.env.POSTGRES_PASSWORD?.trim();
  if (!databaseSecret || databaseSecret === "CHANGE_THIS_PASSWORD") {
    throw new MobileAuthError(
      "La configuración de seguridad de la VPS no está completa.",
      503,
      "email_auth_not_configured",
    );
  }
  return createHash("sha256")
    .update(`${label}:${databaseSecret}`)
    .digest("hex");
}

function codePepper(): string {
  const configured = process.env.AUTH_CODE_PEPPER?.trim();
  if (configured && configured.length >= 32) return configured;
  return derivedRuntimeSecret("nexora-account-code");
}

function mailWebhookSecret(): string {
  const configured = process.env.AUTH_EMAIL_WEBHOOK_SECRET?.trim();
  if (configured && configured.length >= 32) return configured;
  return derivedRuntimeSecret("nexora-mail");
}

function hashCode(userId: string, purpose: AccountCodePurpose, code: string): string {
  return createHmac("sha256", codePepper())
    .update(`${userId}:${purpose}:${code}`)
    .digest("hex");
}

function makeCode(): string {
  return randomInt(0, 1_000_000).toString().padStart(6, "0");
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

function safeDeviceName(value: string | null): string {
  const clean = (value || "").trim().replace(/\s+/g, " ").slice(0, 100);
  return clean || "Android";
}

async function deliverAuthEmail(input: {
  purpose: AccountCodePurpose;
  email: string;
  name: string;
  code: string;
}): Promise<void> {
  const endpoint = process.env.AUTH_EMAIL_WEBHOOK_URL?.trim();
  const secret = mailWebhookSecret();
  if (!endpoint) {
    throw new MobileAuthError(
      "El envío de correo todavía no está configurado en la VPS.",
      503,
      "email_delivery_not_configured",
    );
  }

  let url: URL;
  try {
    url = new URL(endpoint);
  } catch {
    throw new MobileAuthError(
      "AUTH_EMAIL_WEBHOOK_URL no es válida.",
      503,
      "email_delivery_not_configured",
    );
  }
  const loopback = url.hostname === "127.0.0.1" || url.hostname === "localhost";
  const internalMailer =
    url.protocol === "http:" &&
    url.hostname === "mailer" &&
    (url.port === "8025" || url.port === "");
  if (url.protocol !== "https:" && !loopback && !internalMailer) {
    throw new MobileAuthError(
      "El webhook de correo debe usar HTTPS o la red privada de Nexora Mail.",
      503,
      "email_delivery_not_configured",
    );
  }

  const verifying = input.purpose === "verify_email";
  const subject = verifying
    ? "Verifica tu correo en Nexora AI"
    : "Código para restablecer tu contraseña de Nexora AI";
  const action = verifying ? "verificar tu correo" : "restablecer tu contraseña";
  const text = `Hola ${input.name}. Tu código para ${action} es ${input.code}. Expira en 10 minutos.`;
  const html = `<div style="font-family:system-ui,sans-serif;max-width:520px;margin:auto;padding:28px;background:#0b0e13;color:#f5f7fa;border-radius:24px"><h2 style="margin:0 0 12px">Nexora AI</h2><p>${text}</p><div style="font-size:34px;font-weight:800;letter-spacing:8px;padding:18px 0;color:#22d3a6">${input.code}</div><p style="color:#98a2b3">Si no solicitaste este código, puedes ignorar este mensaje.</p></div>`;

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${secret}`,
    },
    body: JSON.stringify({
      type: verifying ? "nexora.auth.verify_email" : "nexora.auth.reset_password",
      to: input.email,
      name: input.name,
      subject,
      text,
      html,
      code: input.code,
      expiresInMinutes: 10,
    }),
    signal: AbortSignal.timeout(10_000),
    cache: "no-store",
  });
  if (!response.ok) {
    throw new MobileAuthError(
      "El servicio de correo rechazó el envío.",
      502,
      "email_delivery_failed",
    );
  }
}

async function issueCode(input: {
  userId: string;
  purpose: AccountCodePurpose;
  email: string;
  name: string;
}): Promise<void> {
  const code = makeCode();
  await databaseQuery(
    `update app_account_codes
        set consumed_at = now()
      where user_id = $1
        and purpose = $2
        and consumed_at is null`,
    [input.userId, input.purpose],
  );
  await databaseQuery(
    `insert into app_account_codes (
       id, user_id, purpose, email, code_hash, expires_at
     ) values ($1, $2, $3, $4, $5, $6)`,
    [
      randomUUID(),
      input.userId,
      input.purpose,
      input.email,
      hashCode(input.userId, input.purpose, code),
      new Date(Date.now() + CODE_TTL_MS),
    ],
  );
  try {
    await deliverAuthEmail({ ...input, code });
  } catch (error) {
    await databaseQuery(
      `update app_account_codes
          set consumed_at = now()
        where user_id = $1
          and purpose = $2
          and consumed_at is null`,
      [input.userId, input.purpose],
    ).catch(() => undefined);
    throw error;
  }
}

async function consumeCode(input: {
  userId: string;
  purpose: AccountCodePurpose;
  code: string;
}): Promise<void> {
  if (!/^\d{6}$/.test(input.code)) {
    throw new MobileAuthError("El código debe tener 6 dígitos.", 400, "invalid_code");
  }
  const result = await databaseQuery<AccountCodeRow>(
    `select id, attempts, code_hash
       from app_account_codes
      where user_id = $1
        and purpose = $2
        and consumed_at is null
        and expires_at > now()
      order by created_at desc
      limit 1`,
    [input.userId, input.purpose],
  );
  const row = result.rows[0];
  if (!row || row.attempts >= MAX_CODE_ATTEMPTS) {
    throw new MobileAuthError("El código expiró. Solicita uno nuevo.", 400, "code_expired");
  }

  const expected = Buffer.from(row.code_hash, "hex");
  const actual = Buffer.from(hashCode(input.userId, input.purpose, input.code), "hex");
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) {
    await databaseQuery(
      `update app_account_codes
          set attempts = attempts + 1,
              consumed_at = case when attempts + 1 >= $2 then now() else consumed_at end
        where id = $1`,
      [row.id, MAX_CODE_ATTEMPTS],
    );
    throw new MobileAuthError("El código no es correcto.", 400, "invalid_code");
  }

  await databaseQuery(
    `update app_account_codes set consumed_at = now() where id = $1`,
    [row.id],
  );
}

export async function getAccountOverview(request: NextRequest): Promise<MobileAccountOverview> {
  const authenticated = await authenticateMobileRequest(request);
  const currentHash = currentAccessHash(request);
  const deviceName = safeDeviceName(request.headers.get("x-nexora-device"));

  if (currentHash) {
    await databaseQuery(
      `update app_auth_sessions
          set device_name = $2,
              user_agent = coalesce($3, user_agent),
              last_used_at = now()
        where access_token_hash = $1`,
      [currentHash, deviceName, request.headers.get("user-agent")],
    );
  }

  const userResult = await databaseQuery<AccountUserRow>(
    `select u.id, u.name, u.email, u.image_url, u.email_verified_at,
            exists(select 1 from app_password_credentials p where p.user_id = u.id) as has_password
       from app_users u
      where u.id = $1
      limit 1`,
    [authenticated.id],
  );
  const row = userResult.rows[0];
  if (!row) throw new MobileAuthError("La cuenta ya no existe.", 404, "account_missing");

  const providersResult = await databaseQuery<{ provider: MobileAuthProvider }>(
    `select provider
       from app_auth_accounts
      where user_id = $1
      order by created_at asc`,
    [authenticated.id],
  );
  const sessionsResult = await databaseQuery<AccountSessionRow>(
    `select id, device_name, created_at, last_used_at,
            access_token_hash = $2 as current
       from app_auth_sessions
      where user_id = $1
        and revoked_at is null
        and refresh_expires_at > now()
      order by current desc, last_used_at desc`,
    [authenticated.id, currentHash || ""],
  );

  return {
    user: {
      id: row.id,
      name: row.name,
      email: row.email,
      image: row.image_url,
      emailVerified: Boolean(row.email_verified_at),
    },
    providers: providersResult.rows.map((provider) => provider.provider),
    hasPassword: row.has_password,
    sessions: sessionsResult.rows.map((session) => ({
      id: session.id,
      deviceName: session.device_name,
      createdAt: session.created_at,
      lastUsedAt: session.last_used_at,
      current: session.current,
    })),
  };
}

export async function updateAccountProfile(
  request: NextRequest,
  input: { name: string },
): Promise<MobileAuthUser> {
  const user = await authenticateMobileRequest(request);
  const name = input.name.trim().replace(/\s+/g, " ").slice(0, 80);
  if (name.length < 2) {
    throw new MobileAuthError("El nombre debe tener al menos 2 caracteres.", 400, "invalid_name");
  }
  const result = await databaseQuery<{
    id: string;
    name: string;
    email: string | null;
    image_url: string | null;
  }>(
    `update app_users
        set name = $2, updated_at = now()
      where id = $1
      returning id, name, email, image_url`,
    [user.id, name],
  );
  const row = result.rows[0];
  return { id: row.id, name: row.name, email: row.email, image: row.image_url };
}

export async function requestEmailVerification(request: NextRequest): Promise<void> {
  const user = await authenticateMobileRequest(request);
  const result = await databaseQuery<{
    name: string;
    email: string | null;
    email_verified_at: Date | string | null;
  }>(
    `select name, email, email_verified_at from app_users where id = $1 limit 1`,
    [user.id],
  );
  const row = result.rows[0];
  if (!row?.email) {
    throw new MobileAuthError("Tu cuenta no tiene un correo asociado.", 400, "email_missing");
  }
  if (row.email_verified_at) return;
  await issueCode({
    userId: user.id,
    purpose: "verify_email",
    email: row.email,
    name: row.name,
  });
}

export async function confirmEmailVerification(
  request: NextRequest,
  code: string,
): Promise<void> {
  const user = await authenticateMobileRequest(request);
  await consumeCode({ userId: user.id, purpose: "verify_email", code });
  await databaseQuery(
    `update app_users
        set email_verified_at = coalesce(email_verified_at, now()), updated_at = now()
      where id = $1`,
    [user.id],
  );
}

export async function requestPasswordReset(emailValue: string): Promise<void> {
  const email = normalizeEmail(emailValue);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return;
  const result = await databaseQuery<{
    id: string;
    name: string;
    email: string;
  }>(
    `select u.id, u.name, u.email
       from app_users u
       join app_password_credentials p on p.user_id = u.id
      where lower(u.email) = lower($1)
      limit 1`,
    [email],
  );
  const row = result.rows[0];
  if (!row) return;
  try {
    await issueCode({
      userId: row.id,
      purpose: "reset_password",
      email: row.email,
      name: row.name,
    });
  } catch (error) {
    console.error("[NexoraAI] Password reset email delivery failed", error);
  }
}

export async function confirmPasswordReset(input: {
  email: string;
  code: string;
  password: string;
}): Promise<void> {
  const email = normalizeEmail(input.email);
  if (input.password.length < 8 || input.password.length > 128) {
    throw new MobileAuthError(
      "La contraseña debe tener entre 8 y 128 caracteres.",
      400,
      "invalid_password",
    );
  }
  const result = await databaseQuery<{ id: string }>(
    `select u.id
       from app_users u
       join app_password_credentials p on p.user_id = u.id
      where lower(u.email) = lower($1)
      limit 1`,
    [email],
  );
  const user = result.rows[0];
  if (!user) {
    throw new MobileAuthError("El código no es correcto o expiró.", 400, "invalid_code");
  }
  await consumeCode({ userId: user.id, purpose: "reset_password", code: input.code });

  const salt = randomBytes(24).toString("base64url");
  const derived = (await derivePassword(input.password, salt)).toString("base64");
  await databaseQuery(
    `update app_password_credentials
        set password_salt = $2,
            password_hash = $3,
            updated_at = now()
      where user_id = $1`,
    [user.id, salt, derived],
  );
  await databaseQuery(
    `update app_auth_sessions
        set revoked_at = now(), last_used_at = now()
      where user_id = $1 and revoked_at is null`,
    [user.id],
  );
}

export async function changeAccountPassword(
  request: NextRequest,
  input: { currentPassword: string; newPassword: string },
): Promise<void> {
  const user = await authenticateMobileRequest(request);
  if (input.newPassword.length < 8 || input.newPassword.length > 128) {
    throw new MobileAuthError(
      "La nueva contraseña debe tener entre 8 y 128 caracteres.",
      400,
      "invalid_password",
    );
  }

  const account = await databaseQuery<{
    email_verified_at: Date | string | null;
  }>(`select email_verified_at from app_users where id = $1 limit 1`, [user.id]);
  const credentials = await databaseQuery<PasswordCredentialRow>(
    `select password_salt, password_hash
       from app_password_credentials
      where user_id = $1
      limit 1`,
    [user.id],
  );
  const current = credentials.rows[0];

  if (current) {
    const actual = await derivePassword(input.currentPassword, current.password_salt);
    const expected = Buffer.from(current.password_hash, "base64");
    if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
      throw new MobileAuthError("La contraseña actual no es correcta.", 401, "invalid_credentials");
    }
  } else if (!account.rows[0]?.email_verified_at) {
    throw new MobileAuthError(
      "Verifica tu correo antes de añadir una contraseña a una cuenta social.",
      403,
      "email_verification_required",
    );
  }

  const salt = randomBytes(24).toString("base64url");
  const derived = (await derivePassword(input.newPassword, salt)).toString("base64");
  await databaseQuery(
    `insert into app_password_credentials (user_id, password_salt, password_hash)
     values ($1, $2, $3)
     on conflict (user_id)
     do update set password_salt = excluded.password_salt,
                   password_hash = excluded.password_hash,
                   updated_at = now()`,
    [user.id, salt, derived],
  );

  const currentHash = currentAccessHash(request);
  await databaseQuery(
    `update app_auth_sessions
        set revoked_at = now(), last_used_at = now()
      where user_id = $1
        and revoked_at is null
        and access_token_hash <> $2`,
    [user.id, currentHash || ""],
  );
}

export async function revokeAccountSession(
  request: NextRequest,
  sessionId: string,
): Promise<void> {
  const user = await authenticateMobileRequest(request);
  await databaseQuery(
    `update app_auth_sessions
        set revoked_at = now(), last_used_at = now()
      where id = $1 and user_id = $2 and revoked_at is null`,
    [sessionId, user.id],
  );
}

export async function revokeOtherAccountSessions(request: NextRequest): Promise<void> {
  const user = await authenticateMobileRequest(request);
  const currentHash = currentAccessHash(request);
  await databaseQuery(
    `update app_auth_sessions
        set revoked_at = now(), last_used_at = now()
      where user_id = $1
        and revoked_at is null
        and access_token_hash <> $2`,
    [user.id, currentHash || ""],
  );
}