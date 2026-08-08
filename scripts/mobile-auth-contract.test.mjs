import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const read = (path) => fs.readFileSync(path, "utf8");

const mobileAuth = read("src/lib/mobile-auth.ts");
const database = read("src/lib/db.ts");
const manifest = read(
  "apps/android/GhostNexoraAndroid/app/src/main/AndroidManifest.xml",
);
const authStore = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AuthStore.kt",
);
const authApi = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/AuthApi.kt",
);
const cloudSync = read(
  "apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/CloudChatSync.kt",
);

test("mobile auth keeps provider secrets on the server and uses PKCE", () => {
  for (const provider of ["google", "facebook", "discord"]) {
    assert.match(mobileAuth, new RegExp(`provider === \\"${provider}\\"|value === \\"${provider}\\"`));
  }
  assert.match(mobileAuth, /code_challenge/);
  assert.match(mobileAuth, /nexoraai:\/\/auth\/callback/);
  assert.match(mobileAuth, /refresh_token_hash/);
  assert.match(mobileAuth, /scrypt/);
  assert.doesNotMatch(authApi, /CLIENT_SECRET|clientSecret|GOOGLE_CLIENT_SECRET|DISCORD_CLIENT_SECRET|FACEBOOK_CLIENT_SECRET/);
});

test("Android stores authentication state with Android Keystore AES-GCM", () => {
  assert.match(authStore, /AndroidKeyStore/);
  assert.match(authStore, /AES\/GCM\/NoPadding/);
  assert.match(authStore, /KeyGenParameterSpec/);
  assert.match(authStore, /AuthCallbackBus/);
});

test("OAuth callback is isolated to the Nexora deep link", () => {
  assert.match(manifest, /android:scheme="nexoraai"/);
  assert.match(manifest, /android:host="auth"/);
  assert.match(manifest, /android:path="\/callback"/);
  assert.match(manifest, /android:launchMode="singleTop"/);
});

test("authenticated chat state has a durable account boundary", () => {
  for (const table of [
    "app_users",
    "app_auth_accounts",
    "app_password_credentials",
    "app_auth_sessions",
    "mobile_user_chat_state",
  ]) {
    assert.match(database, new RegExp(table));
  }
  assert.match(database, /nexora_prevent_implicit_auth_link/);
  assert.match(database, /implicit auth account linking is not allowed/);
  assert.match(cloudSync, /mergePayload/);
  assert.match(cloudSync, /AuthApi\.putCloudState/);
  assert.match(cloudSync, /nexora_chat_history/);
});
