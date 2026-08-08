import assert from "node:assert/strict";
import fs from "node:fs";
import { test } from "node:test";

const read = (file) => fs.readFileSync(file, "utf8");

const account = read("src/lib/mobile-account.ts");
const link = read("src/lib/mobile-link.ts");
const db = read("src/lib/db.ts");
const authApi = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AuthApi.kt",
);
const authScreen = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AuthScreen.kt",
);
const accountCenter = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AdvancedAccountCenter.kt",
);
const authenticatedRoot = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AuthenticatedRoot.kt",
);
const chatComponents = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/ChatComponents.kt",
);
const chatApp = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/NexoraApp.kt",
);
const chatStore = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/ChatStore.kt",
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

test("password reset and authenticated password changes protect sessions", () => {
  assert.match(account, /confirmPasswordReset/);
  assert.match(account, /changeAccountPassword/);
  assert.match(account, /actual\.length !== expected\.length \|\| !timingSafeEqual/);
  assert.match(account, /access_token_hash <> \$2/);
  assert.match(
    read("src/app/api/auth/mobile/password/reset/route.ts"),
    /cannot be used to[\s\S]*enumerate/i,
  );
  assert.match(
    read("src/app/api/auth/mobile/account/password/route.ts"),
    /changeAccountPassword/,
  );
});

test("email delivery stays server-side and supports the private Nexora Mail network", () => {
  assert.match(account, /AUTH_EMAIL_WEBHOOK_URL/);
  assert.match(account, /AUTH_EMAIL_WEBHOOK_SECRET/);
  assert.match(account, /derivedRuntimeSecret\("nexora-mail"\)/);
  assert.match(account, /url\.hostname === "mailer"/);
  assert.match(account, /url\.protocol !== "https:" && !loopback && !internalMailer/);
  assert.doesNotMatch(authApi, /AUTH_EMAIL_WEBHOOK/);
  assert.doesNotMatch(authApi, /CLIENT_SECRET/);
});

test("explicit social linking preserves the implicit-link guard", () => {
  assert.match(db, /app_auth_link_authorizations/);
  assert.match(db, /mobile_account_link_states/);
  assert.match(db, /implicit auth account linking is not allowed/);
  assert.match(link, /createAccountLinkStart/);
  assert.match(link, /completeAccountLinkCallback/);
  assert.match(link, /provider_account_has_data/);
  assert.match(link, /last_auth_method/);
  assert.match(
    read("src/app/api/auth/mobile/callback/[provider]/route.ts"),
    /completeAccountLinkCallback[\s\S]*completeOAuthCallback/,
  );
});

test("Android exposes recovery, linking, avatars and device-session controls", () => {
  assert.match(authApi, /getAccountOverview/);
  assert.match(authApi, /requestEmailVerification/);
  assert.match(authApi, /requestPasswordReset/);
  assert.match(authApi, /socialLinkStart/);
  assert.match(authApi, /unlinkProvider/);
  assert.match(authApi, /changePassword/);
  assert.match(authApi, /X-Nexora-Device/);
  assert.match(authScreen, /Olvidé mi contraseña/);
  assert.match(authScreen, /Código de 6 dígitos/);
  assert.match(accountCenter, /Centro de cuenta/);
  assert.match(accountCenter, /Vincular/);
  assert.match(accountCenter, /Cerrar todas las demás sesiones/);
  assert.match(authenticatedRoot, /pending\.linking/);
  assert.match(
    read("apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/UserAvatar.kt"),
    /AsyncImage/,
  );
});

test("chat presentation supports selectable code and non-destructive response branches", () => {
  assert.match(chatComponents, /SelectionContainer/);
  assert.match(chatComponents, /content\.split\("```"\)/);
  assert.match(chatComponents, /ContentCopy/);
  assert.match(chatComponents, /Intent\.ACTION_SEND/);
  assert.match(chatApp, /createBranchSession/);
  assert.match(chatApp, /regenerateMessage/);
  assert.match(chatApp, /editUserMessage/);
  assert.match(chatApp, /messages\.take\(userIndex \+ 1\)/);
  assert.match(chatApp, /ChatBranchActions/);
  assert.match(chatStore, /variantGroupId/);
  assert.match(chatStore, /parentSessionId/);
});
