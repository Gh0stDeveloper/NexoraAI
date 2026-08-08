import assert from "node:assert/strict";
import fs from "node:fs";
import { test } from "node:test";

const read = (file) => fs.readFileSync(file, "utf8");

const account = read("src/lib/mobile-account.ts");
const db = read("src/lib/db.ts");
const authApi = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AuthApi.kt",
);
const authScreen = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AuthScreen.kt",
);
const accountCenter = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AccountCenter.kt",
);
const chatComponents = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/ChatComponents.kt",
);

test("account recovery stores only protected one-time codes", () => {
  assert.match(account, /createHmac\("sha256", codePepper\(\)\)/);
  assert.match(account, /randomInt\(0, 1_000_000\)/);
  assert.match(account, /MAX_CODE_ATTEMPTS = 5/);
  assert.match(account, /CODE_TTL_MS = 10 \* 60 \* 1_000/);
  assert.match(account, /timingSafeEqual/);
  assert.doesNotMatch(db, /\bcode\s+text\b/i);
  assert.match(db, /code_hash char\(64\) not null/);
});

test("password reset revokes existing sessions and resists email enumeration", () => {
  assert.match(account, /set revoked_at = now\(\), last_used_at = now\(\)/);
  assert.match(
    read("src/app/api/auth/mobile/password/reset/route.ts"),
    /cannot be used to[\s\S]*enumerate/i,
  );
});

test("email delivery stays server-side and requires a protected webhook", () => {
  assert.match(account, /AUTH_EMAIL_WEBHOOK_URL/);
  assert.match(account, /AUTH_EMAIL_WEBHOOK_SECRET/);
  assert.match(account, /url\.protocol !== "https:" && !loopback/);
  assert.doesNotMatch(authApi, /AUTH_EMAIL_WEBHOOK/);
  assert.doesNotMatch(authApi, /CLIENT_SECRET/);
});

test("Android exposes account, recovery and device-session controls", () => {
  assert.match(authApi, /getAccountOverview/);
  assert.match(authApi, /requestEmailVerification/);
  assert.match(authApi, /requestPasswordReset/);
  assert.match(authApi, /revokeOtherSessions/);
  assert.match(authApi, /X-Nexora-Device/);
  assert.match(authScreen, /Olvidé mi contraseña/);
  assert.match(authScreen, /Código de 6 dígitos/);
  assert.match(accountCenter, /Centro de cuenta/);
  assert.match(accountCenter, /Cerrar todas las demás sesiones/);
});

test("chat presentation supports selectable text, code blocks and sharing", () => {
  assert.match(chatComponents, /SelectionContainer/);
  assert.match(chatComponents, /content\.split\("```"\)/);
  assert.match(chatComponents, /ContentCopy/);
  assert.match(chatComponents, /Intent\.ACTION_SEND/);
  assert.match(chatComponents, /Buscar chats y mensajes/);
});
