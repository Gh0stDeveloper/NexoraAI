import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const androidBase = "apps/android/GhostNexoraAndroid";
const androidPackage = `${androidBase}/app/src/main/java/com/ghostnexora/ai`;
const nativeCore = `${androidBase}/app/src/main/cpp/core.c`;
const errors = [];

const requiredFiles = [
  "package.json",
  "Dockerfile",
  "docker-compose.vps.yml",
  ".env.vps.example",
  "README.md",
  "public/nexora.svg",
  "src/app/page.tsx",
  "src/app/layout.tsx",
  "src/app/robots.ts",
  "src/app/sitemap.ts",
  "src/app/terms/page.tsx",
  "src/app/privacy/page.tsx",
  "src/app/download/route.ts",
  "src/app/api/auth/mobile/start/route.ts",
  "src/app/api/auth/mobile/callback/[provider]/route.ts",
  "src/app/api/auth/mobile/exchange/route.ts",
  "src/app/api/auth/mobile/email/route.ts",
  "src/app/api/auth/mobile/refresh/route.ts",
  "src/app/api/auth/mobile/me/route.ts",
  "src/app/api/auth/mobile/logout/route.ts",
  "src/app/api/mobile/user/state/route.ts",
  "src/app/api/mobile/chat/route.ts",
  "src/app/api/mobile/chat/stream/route.ts",
  "src/app/api/mobile/chat/jobs/route.ts",
  "src/app/api/mobile/chat/jobs/[id]/route.ts",
  "src/app/api/mobile/builds/route.ts",
  "src/app/api/mobile/builds/[id]/route.ts",
  "src/app/api/mobile/builds/[id]/download/route.ts",
  "src/lib/agent.ts",
  "src/lib/android-builds.ts",
  "src/lib/android-build-request.ts",
  "src/lib/android-release.ts",
  "src/lib/chat-jobs.ts",
  "src/lib/db.ts",
  "src/lib/mobile-auth.ts",
  "src/lib/mobile-chat.ts",
  "scripts/mobile-chat-contract.test.mjs",
  "scripts/durable-jobs-contract.test.mjs",
  "scripts/durable-jobs-db.integration.mjs",
  "scripts/vps-healthcheck.test.sh",
  "scripts/vps-update-contract.test.sh",
  "src/lib/rate-limit.ts",
  "src/lib/sandbox.ts",
  "sandbox/Dockerfile",
  "sandbox/server.mjs",
  "build-worker/Dockerfile",
  "build-worker/worker.mjs",
  "build-worker/template/app/build.gradle.kts",
  `${androidBase}/app/build.gradle.kts`,
  `${androidBase}/app/src/main/AndroidManifest.xml`,
  `${androidBase}/app/src/debug/res/xml/network_security_config.xml`,
  `${androidBase}/app/src/main/res/xml/network_security_config.xml`,
  `${androidBase}/app/src/main/cpp/CMakeLists.txt`,
  nativeCore,
  `${androidPackage}/ApiClient.kt`,
  `${androidPackage}/AuthApi.kt`,
  `${androidPackage}/AuthModels.kt`,
  `${androidPackage}/AuthScreen.kt`,
  `${androidPackage}/AuthStore.kt`,
  `${androidPackage}/AuthenticatedRoot.kt`,
  `${androidPackage}/CloudChatSync.kt`,
  `${androidPackage}/ChatComponents.kt`,
  `${androidPackage}/ChatStore.kt`,
  `${androidPackage}/DurableWork.kt`,
  `${androidPackage}/LegalComponents.kt`,
  `${androidPackage}/MessageComposer.kt`,
  `${androidPackage}/Models.kt`,
  `${androidPackage}/NativeBridge.kt`,
  `${androidPackage}/NexoraActivity.kt`,
  `${androidPackage}/NexoraApp.kt`,
  "deploy/scripts/bootstrap-vps.sh",
  "deploy/scripts/nexora-vps.sh",
  "deploy/scripts/android-builder.sh",
  "deploy/scripts/user-build-keystore.sh",
  "deploy/scripts/platform-check.sh",
  "deploy/scripts/verify-vps.sh",
  "deploy/nginx/nexoraia-vps.conf",
  "docs/README-INSTALL.md",
  "docs/README-UPDATE.md",
  "docs/ANDROID-BUILD-VPS.md",
  "docs/ANDROID-AUTH.md",
  "docs/DURABLE-CHAT-AND-USER-BUILDS.md",
  "docs/SUPPORT-MATRIX.md",
  "docs/SANDBOX.md",
  "docs/TROUBLESHOOTING.md",
  ".github/workflows/android-ci.yml",
  ".github/workflows/docker-vps-ci.yml",
  ".github/workflows/platform-compatibility.yml",
  ".github/workflows/training-ci.yml",
  ".github/workflows/web-api-ci.yml",
];

for (const file of requiredFiles) {
  if (!fs.existsSync(path.join(root, file))) errors.push(`Missing required file: ${file}`);
}

function content(file) {
  const target = path.join(root, file);
  return fs.existsSync(target) ? fs.readFileSync(target, "utf8") : "";
}

function includes(file, tokens) {
  const text = content(file);
  for (const token of tokens) {
    if (!text.includes(token)) errors.push(`${file} should reference: ${token}`);
  }
}

function excludes(file, tokens) {
  const text = content(file);
  for (const token of tokens) {
    if (text.includes(token)) errors.push(`${file} should not expose/reference: ${token}`);
  }
}

const pkg = JSON.parse(content("package.json") || "{}");
if (pkg.version !== "0.6.0") errors.push("package.json version must remain aligned with package-lock at 0.6.0");
if (pkg.dependencies?.pg !== "^8.16.3") errors.push("pg must remain pinned to the reviewed 8.16 line");
if (pkg.dependencies?.next !== "16.3.0") errors.push("Next.js must remain on reviewed 16.3.0");
if (pkg.dependencies?.react !== "19.2.8") errors.push("React must remain on reviewed 19.2.8");

includes("src/app/page.tsx", [
  "/download",
  "/terms",
  "/privacy",
  "SoftwareApplication",
  "Android 8+",
  "Tu inteligencia.",
]);
excludes("src/app/page.tsx", ["href=\"/chat\"", "Abrir cliente web"]);
includes("src/app/layout.tsx", ["metadataBase", "openGraph", "twitter", "canonical"]);
includes("src/app/robots.ts", ["sitemap", 'disallow: ["/api/"]']);
includes("src/app/chat/page.tsx", ['redirect("/")']);

includes("src/app/api/mobile/chat/stream/route.ts", [
  "application/x-ndjson",
  'type: "progress"',
  'type: "result"',
  "onProgress",
  "X-Accel-Buffering",
]);
includes("src/app/api/mobile/chat/jobs/route.ts", ["after", "runChatJobInBackground", "status: 202"]);
includes("src/lib/chat-jobs.ts", ["chat_jobs", "access_token_hash", "make_interval", "runAgent"]);
includes("src/lib/db.ts", [
  "DATABASE_URL",
  "app_users",
  "app_auth_sessions",
  "mobile_user_chat_state",
  "android_build_jobs",
  "chat_jobs",
]);
includes("src/lib/mobile-auth.ts", [
  "scrypt",
  "code_challenge",
  "refresh_token_hash",
  "authenticateMobileRequest",
  "nexoraai://auth/callback",
]);
includes("src/app/api/mobile/user/state/route.ts", [
  "authenticateMobileRequest",
  "mobile_user_chat_state",
  "MAX_STATE_BYTES",
]);
includes("src/lib/android-builds.ts", [
  "ENABLE_USER_ANDROID_BUILDS",
  "USER_BUILD_MAX_PER_HOUR",
  "USER_BUILD_RATE_LIMIT_SALT",
  "expiresAt",
]);
includes("src/app/api/mobile/builds/[id]/download/route.ts", [
  "Cache-Control",
  "no-store",
  "buildRoot",
]);
includes("src/lib/agent.ts", [
  "AgentProgress",
  "modeModel || roleModel",
  "progressStageForRole",
  "validateGeneratedCode",
  "trace",
  "elapsedMs",
]);
includes("src/lib/sandbox.ts", [
  "ALLOW_CODE_EXECUTION",
  "SANDBOX_RUNNER_TOKEN",
  "AbortSignal.timeout",
]);
includes("src/lib/rate-limit.ts", ["RATE_LIMIT_PER_MINUTE", "checkMobileRateLimit"]);
includes("src/lib/mobile-chat.ts", [
  "mobileIdentifierSchema",
  ".nullish()",
  ".transform((identifier) => identifier ?? undefined)",
]);
includes("src/app/api/mobile/chat/stream/route.ts", ["checkMobileRateLimit", 'status: 429']);
includes("sandbox/server.mjs", [
  '"--network"',
  '"none"',
  '"--read-only"',
  '"--cap-drop"',
  '"no-new-privileges"',
  '"65534:65534"',
  "SANDBOX_MAX_CONCURRENT_JOBS",
  '"--pids-limit"',
  '"--memory"',
  '"--cpus"',
]);

includes(`${androidBase}/app/build.gradle.kts`, [
  'versionCode = 9',
  'versionName = "0.7.0"',
  '"armeabi-v7a", "arm64-v8a", "x86", "x86_64"',
  "externalNativeBuild",
  "isMinifyEnabled = true",
  "enableV1Signing = true",
  "enableV2Signing = true",
  "enableV3Signing = true",
]);
includes(".gitignore", [
  `${androidBase}/app/.cxx`,
  `${androidBase}/app/.externalNativeBuild`,
]);
includes(`${androidPackage}/ApiClient.kt`, [
  "NativeBridge.apiOrigin()",
  "NativeBridge.chatPath()",
  '"application/x-ndjson"',
  '"progress"',
  "elapsedMs",
  "projectId",
  'projectId?.let { put("projectId", it) }',
  "validateCode",
]);
excludes(`${androidPackage}/ApiClient.kt`, [
  '.put("projectId", projectId ?: JSONObject.NULL)',
]);
includes(`${androidPackage}/AuthApi.kt`, [
  "code_challenge",
  "Authorization",
  "ensureFreshSession",
  "api/auth/mobile/exchange",
]);
includes(`${androidPackage}/AuthStore.kt`, [
  "AndroidKeyStore",
  "AES/GCM/NoPadding",
  "AuthCallbackBus",
]);
includes(`${androidPackage}/AuthenticatedRoot.kt`, [
  "CloudChatSync",
  "clearLocalChats",
  "NexoraAuthenticatedRoot",
]);
includes(`${androidPackage}/CloudChatSync.kt`, [
  "mergePayload",
  "mobile",
  "nexora_chat_history",
]);
includes(`${androidBase}/app/src/main/AndroidManifest.xml`, [
  'android:scheme="nexoraai"',
  'android:host="auth"',
  'android:path="/callback"',
  'android:launchMode="singleTop"',
]);
includes(`${androidPackage}/Models.kt`, [
  "ChatProject",
  "isPinned",
  "AgentProgress",
  "elapsedMs",
  "CodeValidationSummary",
  "ASSISTANT",
  "AndroidBuildArtifact",
  "RequestStatus",
]);
includes(`${androidPackage}/NexoraApp.kt`, [
  "thinkingElapsedMs",
  "thinkingProgress",
  "createProject",
  "toggleSessionPin",
  "LegalDocumentDialog",
  "AssistantThinking",
]);
includes(`${androidPackage}/ChatStore.kt`, ["KEY_PROJECTS", "isPinned", "codeValidation"]);
includes(`${androidPackage}/DurableWork.kt`, [
  "WorkManager",
  "ExistingWorkPolicy.KEEP",
  "NetworkType.CONNECTED",
  "PendingWorkStore",
  "Result.retry()",
]);
includes(`${androidPackage}/LegalComponents.kt`, ["Términos y condiciones", "Aviso de privacidad"]);
includes(`${androidBase}/app/src/main/res/xml/network_security_config.xml`, [
  'cleartextTrafficPermitted="false"',
]);
excludes(`${androidBase}/app/src/main/res/xml/network_security_config.xml`, [
  'cleartextTrafficPermitted="true"',
]);
includes(`${androidBase}/app/src/debug/res/xml/network_security_config.xml`, [
  'cleartextTrafficPermitted="true"',
  "10.0.2.2",
  "localhost",
]);
includes(`${androidBase}/app/src/main/cpp/CMakeLists.txt`, [
  "add_library(nexora SHARED core.c)",
  "-fvisibility=hidden",
  "--gc-sections",
]);
includes(nativeCore, ["RegisterNatives", "JNI_OnLoad", "nx_chat_route", "nx_client_header"]);
excludes(nativeCore, [
  "https://apighostnexoraai.duckdns.org",
  "http://10.0.2.2:3000",
  "Java_com_ghostnexora",
]);
excludes(`${androidPackage}/NativeBridge.kt`, ["https://", "http://", "nexora_config"]);

includes("docker-compose.vps.yml", [
  'profiles: ["sandbox"]',
  "/var/run/docker.sock:/var/run/docker.sock",
  "pgvector/pgvector:pg16",
  "127.0.0.1:3000:3000",
  'profiles: ["user-builds"]',
  "android-build-worker",
  "/var/lib/nexora-ai/releases:ro",
  "cap_drop",
]);
includes(".env.vps.example", [
  "APP_VERSION=0.7.0",
  "ANDROID_APK_URL=",
  "GOOGLE_CLIENT_ID=",
  "FACEBOOK_CLIENT_ID=",
  "DISCORD_CLIENT_ID=",
  "ALLOW_CODE_EXECUTION=false",
  "SANDBOX_RUNNER_TOKEN=",
  "SANDBOX_MAX_CONCURRENT_JOBS=2",
  "ENABLE_USER_ANDROID_BUILDS=false",
  "USER_BUILD_MAX_PER_HOUR=3",
]);
includes("deploy/scripts/android-builder.sh", [
  "android-release.keystore",
  "android-signing.env",
  "GRADLE_USER_HOME",
  "TOOLS_SHA256",
  "GRADLE_SHA256",
  "assembleRelease",
  "--v1-signing-enabled true",
  "--min-sdk-version 23",
  "NexoraAI-latest.apk",
  "latest.json",
  "publish_file_atomically",
]);
includes("deploy/scripts/bootstrap-vps.sh", [
  "download.docker.com/linux",
  "docker-compose-plugin",
  "sha256sum --check",
  "/opt/nexora-ai/state",
  "user-build-keystore.sh",
]);
includes("deploy/scripts/nexora-vps.sh", [
  "update)",
  "rollback)",
  "backup)",
  "android-release)",
  "flock --nonblock",
  "no se reinició ningún contenedor",
  "rollback automático",
  "la función volvió a quedar desactivada",
  "artefactos y enlaces eliminados",
]);
includes("deploy/scripts/verify-vps.sh", [
  "wait_for_command",
  "NEXORA_VERIFY_TIMEOUT_SECONDS",
  "show_app_diagnostics",
]);
includes("deploy/scripts/platform-check.sh", [
  "ubuntu:22",
  "ubuntu:24",
  "ubuntu:26",
  "debian:11",
  "debian:12",
  "debian:13",
]);
includes("deploy/nginx/nexoraia-vps.conf", [
  "location /downloads/",
  "X-Nexora-Version",
  "limit_req_zone",
  "client_max_body_size 40M",
  "proxy_buffering off",
  "X-Nexora-Request-Token",
  "access_log off",
]);
includes("build-worker/worker.mjs", [
  "for update skip locked",
  "interval '1 hour'",
  "V1",
  "V2",
  "V3",
  "Unsafe Android build job path",
  "source_content = ''",
  "NEXORA_EXTERNAL_APK_SIGNING",
  "env:ANDROID_KEYSTORE_PASSWORD",
]);
includes("src/app/download/route.ts", ["release?.downloadUrl"]);

includes(".github/workflows/android-ci.yml", [
  "lib/$ABI/libnexora.so",
  "$ANDROID_HOME/build-tools/35.0.0/apksigner",
  "--v3-signing-enabled true",
  "--min-sdk-version 23",
  "assembleDebug",
  "assembleRelease",
]);
includes(".github/workflows/platform-compatibility.yml", [
  "Ubuntu 22.04",
  "Ubuntu 26.04",
  "Ubuntu 24.04 ARM64",
  "Debian 12",
  "Debian 13",
]);

const sensitiveFiles = [
  "README.md",
  ".env.vps.example",
  "src/app/page.tsx",
  "src/app/api/mobile/status/route.ts",
  "deploy/nginx/nexoraia-vps.conf",
];
for (const file of sensitiveFiles) {
  excludes(file, ["https://nexoraia.com", "https://api.nexoraia.com"]);
}

if (errors.length) {
  console.error("NexoraAI CI preflight failed:");
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log("NexoraAI CI preflight passed.");
