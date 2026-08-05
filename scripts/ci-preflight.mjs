import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const androidBase = 'apps/android/GhostNexoraAndroid';
const androidPackage = `${androidBase}/app/src/main/java/com/ghostnexora/ai`;
const androidRes = `${androidBase}/app/src/main/res`;
const nativeConfig = `${androidBase}/app/src/main/cpp/native_config.c`;
const requiredFiles = [
  'package.json',
  'Dockerfile',
  'docker-compose.vps.yml',
  '.env.vps.example',
  'public/nexora.svg',
  'public/manifest.json',
  '.github/workflows/web-api-ci.yml',
  '.github/workflows/docker-vps-ci.yml',
  '.github/workflows/android-ci.yml',
  '.github/workflows/training-ci.yml',
  'src/app/page.tsx',
  'src/app/chat/page.tsx',
  'src/app/api/chat/route.ts',
  'src/app/api/mobile/chat/route.ts',
  'src/app/api/mobile/status/route.ts',
  'src/lib/agent.ts',
  `${androidBase}/settings.gradle.kts`,
  `${androidBase}/build.gradle.kts`,
  `${androidBase}/app/build.gradle.kts`,
  `${androidBase}/app/src/main/AndroidManifest.xml`,
  `${androidPackage}/NexoraActivity.kt`,
  `${androidPackage}/NexoraApp.kt`,
  `${androidPackage}/NexoraTheme.kt`,
  `${androidPackage}/ChatComponents.kt`,
  `${androidPackage}/MessageComposer.kt`,
  `${androidPackage}/AttachmentReader.kt`,
  `${androidPackage}/ApiClient.kt`,
  `${androidPackage}/ChatStore.kt`,
  `${androidPackage}/Models.kt`,
  `${androidPackage}/NativeConfig.kt`,
  `${androidBase}/app/src/main/cpp/CMakeLists.txt`,
  nativeConfig,
  `${androidRes}/values/colors.xml`,
  `${androidRes}/values/styles.xml`,
  `${androidRes}/drawable/nexora_splash_icon.xml`,
  `${androidRes}/drawable/ic_nexora_foreground.xml`,
  `${androidRes}/mipmap-anydpi-v26/ic_launcher.xml`,
  `${androidRes}/mipmap-anydpi-v26/ic_launcher_round.xml`,
  'docs/android-multiagent-v04.md',
  'docs/duckdns-vps.md',
  'deploy/nginx/nexoraia-vps.conf',
  'deploy/scripts/bootstrap-vps.sh',
  'deploy/scripts/verify-vps.sh',
  'training/datasets/nexora-devsec-sample.jsonl',
  'training/scripts/validate_dataset.py',
];

const requiredPackageScripts = [
  'build',
  'typecheck',
  'ci:preflight',
  'validate:repo',
  'security:check',
  'dataset:validate',
  'docker:config:vps',
];

const errors = [];

function assertIncludes(filePath, tokens, label = filePath) {
  const absolutePath = path.join(root, filePath);
  if (!fs.existsSync(absolutePath)) return;
  const content = fs.readFileSync(absolutePath, 'utf8');
  for (const token of tokens) {
    if (!content.includes(token)) errors.push(`${label} should reference: ${token}`);
  }
}

function assertExcludes(filePath, tokens, label = filePath) {
  const absolutePath = path.join(root, filePath);
  if (!fs.existsSync(absolutePath)) return;
  const content = fs.readFileSync(absolutePath, 'utf8');
  for (const token of tokens) {
    if (content.includes(token)) errors.push(`${label} should not expose: ${token}`);
  }
}

for (const relativePath of requiredFiles) {
  if (!fs.existsSync(path.join(root, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
  }
}

const packagePath = path.join(root, 'package.json');
if (fs.existsSync(packagePath)) {
  const pkg = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
  for (const scriptName of requiredPackageScripts) {
    if (!pkg.scripts?.[scriptName]) errors.push(`Missing package.json script: ${scriptName}`);
  }
  if (pkg.dependencies?.next !== '15.5.7') {
    errors.push('package.json should use patched Next.js 15.5.7');
  }
  if (pkg.dependencies?.react !== '19.1.2' || pkg.dependencies?.['react-dom'] !== '19.1.2') {
    errors.push('package.json should use patched React/React DOM 19.1.2');
  }
}

assertIncludes('.env.vps.example', [
  'APP_ENV=',
  'PUBLIC_DOMAIN=ghostnexoraai.duckdns.org',
  'API_DOMAIN=apighostnexoraai.duckdns.org',
  'NEXT_PUBLIC_SITE_URL=https://ghostnexoraai.duckdns.org',
  'NEXT_PUBLIC_API_URL=https://apighostnexoraai.duckdns.org',
  'MOBILE_PRODUCTION_API_URL=https://apighostnexoraai.duckdns.org/',
  'AI_PROVIDER=',
  'OLLAMA_MODEL_PLANNER=',
  'OLLAMA_MODEL_SYNTHESIZER=',
  'OLLAMA_MULTI_AGENT_PARALLEL=',
  'OLLAMA_KEEP_ALIVE=',
]);
assertIncludes('docker-compose.vps.yml', [
  'services:',
  'app:',
  'postgres:',
  'ollama:',
  '.env.production',
  '127.0.0.1:3000:3000',
  '127.0.0.1:11434:11434',
  'pgvector/pgvector:pg16',
]);
assertIncludes('src/app/page.tsx', [
  '/chat',
  '/docs',
  '/openapi.json',
  'ghostnexoraai.duckdns.org',
  'apighostnexoraai.duckdns.org',
]);
assertIncludes('src/app/chat/page.tsx', ['use client', 'fetch("/api/chat"', 'mode-chip', 'textarea']);
assertIncludes('src/app/api/chat/route.ts', ['requestId', 'z.ZodError', 'runAgent']);
assertIncludes('src/app/api/mobile/chat/route.ts', [
  'attachments',
  'intelligence',
  'conversationId',
  'runAgent',
]);
assertIncludes('src/app/api/mobile/status/route.ts', [
  'ghostnexoraai.duckdns.org',
  'apighostnexoraai.duckdns.org',
  '0.4.1-duckdns-production',
]);
assertIncludes('src/lib/agent.ts', [
  'OLLAMA_VISION_MODEL',
  'OLLAMA_MULTI_AGENT_PARALLEL',
  'OLLAMA_MODEL_SYNTHESIZER',
  'keep_alive',
  'agentsUsed',
  'runCollaborativeOrchestration',
]);
assertIncludes(`${androidBase}/app/build.gradle.kts`, [
  'externalNativeBuild',
  'ndkVersion',
  'pdfbox-android',
  'core-splashscreen',
  'ANDROID_KEYSTORE_PATH',
  'isMinifyEnabled = true',
  'versionCode = 5',
  'versionName = "0.4.1-duckdns-production"',
]);
assertExcludes(`${androidBase}/app/build.gradle.kts`, [
  'DEFAULT_API_BASE_URL',
  'https://api.nexoraia.com/',
  'http://10.0.2.2:3000/',
]);
assertIncludes(`${androidBase}/app/src/main/AndroidManifest.xml`, [
  '.NexoraActivity',
  'Theme.NexoraAI.Starting',
  '@mipmap/ic_launcher',
  'adjustResize',
]);
assertIncludes(`${androidPackage}/NexoraActivity.kt`, [
  'installSplashScreen()',
  'setDecorFitsSystemWindows(window, false)',
  'setOnExitAnimationListener',
]);
assertIncludes(`${androidPackage}/NexoraApp.kt`, [
  'contentWindowInsets = WindowInsets(0, 0, 0, 0)',
  'MessageComposer',
  'Coordinando ${intelligence.agentCount} agentes',
]);
assertIncludes(`${androidPackage}/MessageComposer.kt`, [
  'WindowInsets.navigationBars',
  'imePadding()',
  'FocusRequester',
  'LocalSoftwareKeyboardController',
  'ComposerPanel.MODELS',
  'ComposerPanel.INTELLIGENCE',
]);
assertIncludes(`${androidPackage}/Models.kt`, [
  'agentCount',
  'INSTANT("instant"',
  'MAXIMUM("maximum"',
  '6)',
]);
assertIncludes(`${androidPackage}/AttachmentReader.kt`, [
  'PDFTextStripper',
  'extractDocx',
  'MAX_BYTES',
]);
assertIncludes(`${androidPackage}/ApiClient.kt`, [
  'NativeConfig.apiBaseUrl()',
  'attachments',
  'X-Nexora-Client',
  '720_000',
]);
assertIncludes(nativeConfig, [
  'encoded_url',
  'XOR_KEY',
  'NativeConfig_apiBaseUrl',
]);
assertExcludes(nativeConfig, [
  'https://apighostnexoraai.duckdns.org/',
  'https://api.nexoraia.com/',
]);
assertIncludes(`${androidRes}/values/styles.xml`, [
  'Theme.SplashScreen.IconBackground',
  'windowSplashScreenAnimatedIcon',
  'postSplashScreenTheme',
]);
assertIncludes(`${androidRes}/mipmap-anydpi-v26/ic_launcher.xml`, [
  '<adaptive-icon',
  '@drawable/ic_nexora_foreground',
  '<monochrome',
]);
assertIncludes('deploy/nginx/nexoraia-vps.conf', [
  'server_name ghostnexoraai.duckdns.org;',
  'server_name apighostnexoraai.duckdns.org;',
  'Access-Control-Allow-Origin "https://ghostnexoraai.duckdns.org"',
  'proxy_read_timeout 900s',
]);
assertIncludes('deploy/scripts/bootstrap-vps.sh', [
  'certbot',
  'python3-certbot-nginx',
  'certbot.timer',
]);
assertIncludes('deploy/scripts/verify-vps.sh', [
  'VERIFY_PUBLIC_DOMAINS',
  'ghostnexoraai.duckdns.org',
  'apighostnexoraai.duckdns.org',
]);
assertIncludes('docs/duckdns-vps.md', [
  'ghostnexoraai.duckdns.org',
  'apighostnexoraai.duckdns.org',
  'ANDROID_KEYSTORE_BASE64',
  'certbot --nginx',
]);
assertIncludes('.github/workflows/web-api-ci.yml', [
  'NEXT_PUBLIC_SITE_URL: https://ghostnexoraai.duckdns.org',
  'NEXT_PUBLIC_API_URL: https://apighostnexoraai.duckdns.org',
  'MOBILE_PRODUCTION_API_URL: https://apighostnexoraai.duckdns.org/',
]);
assertIncludes('.github/workflows/android-ci.yml', [
  'assembleDebug',
  'ANDROID_KEYSTORE_BASE64',
  'assembleRelease',
  'nexora-ai-debug-apk',
  'nexora-ai-release-apk',
  'ndk;27.0.12077973',
  'cmake;3.22.1',
]);

const domainSensitiveFiles = [
  '.env.vps.example',
  'README.md',
  'docs/ci-actions.md',
  'docs/duckdns-vps.md',
  'src/lib/env.ts',
  'src/app/page.tsx',
  'src/app/api/mobile/status/route.ts',
  'deploy/nginx/nexoraia-vps.conf',
  '.github/workflows/web-api-ci.yml',
];
for (const filePath of domainSensitiveFiles) {
  assertExcludes(filePath, ['https://nexoraia.com', 'https://api.nexoraia.com']);
}

if (errors.length > 0) {
  console.error('NexoraAI CI preflight failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('NexoraAI CI preflight passed.');
