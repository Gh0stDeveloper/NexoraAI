import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const androidBase = 'apps/android/GhostNexoraAndroid';
const androidPackage = `${androidBase}/app/src/main/java/com/ghostnexora/ai`;
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
  `${androidPackage}/MainActivity.kt`,
  `${androidPackage}/NexoraApp.kt`,
  `${androidPackage}/SplashScreen.kt`,
  `${androidPackage}/AttachmentReader.kt`,
  `${androidPackage}/ApiClient.kt`,
  `${androidPackage}/ChatStore.kt`,
  `${androidPackage}/Models.kt`,
  `${androidPackage}/NativeConfig.kt`,
  `${androidBase}/app/src/main/cpp/CMakeLists.txt`,
  `${androidBase}/app/src/main/cpp/native_config.c`,
  'deploy/nginx/nexoraia-vps.conf',
  'deploy/scripts/bootstrap-vps.sh',
  'deploy/scripts/verify-vps.sh',
  'training/datasets/nexora-devsec-sample.jsonl',
  'training/scripts/validate_dataset.py'
];

const requiredPackageScripts = [
  'build',
  'typecheck',
  'ci:preflight',
  'validate:repo',
  'security:check',
  'dataset:validate',
  'docker:config:vps'
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
  if (!fs.existsSync(path.join(root, relativePath))) errors.push(`Missing required file: ${relativePath}`);
}

const packagePath = path.join(root, 'package.json');
if (fs.existsSync(packagePath)) {
  const pkg = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
  for (const scriptName of requiredPackageScripts) {
    if (!pkg.scripts?.[scriptName]) errors.push(`Missing package.json script: ${scriptName}`);
  }
  if (pkg.dependencies?.next !== '15.5.7') errors.push('package.json should use patched Next.js 15.5.7');
  if (pkg.dependencies?.react !== '19.1.2' || pkg.dependencies?.['react-dom'] !== '19.1.2') {
    errors.push('package.json should use patched React/React DOM 19.1.2');
  }
}

assertIncludes('.env.vps.example', ['APP_ENV=', 'NEXT_PUBLIC_SITE_URL=', 'NEXT_PUBLIC_API_URL=', 'MOBILE_PRODUCTION_API_URL=', 'AI_PROVIDER=']);
assertIncludes('docker-compose.vps.yml', ['services:', 'app:', 'postgres:', 'ollama:', '.env.production', '127.0.0.1:3000:3000', '127.0.0.1:11434:11434', 'pgvector/pgvector:pg16']);
assertIncludes('src/app/page.tsx', ['/chat', '/docs', '/openapi.json', 'api.nexoraia.com']);
assertIncludes('src/app/chat/page.tsx', ['use client', 'fetch("/api/chat"', 'mode-chip', 'textarea']);
assertIncludes('src/app/api/chat/route.ts', ['requestId', 'z.ZodError', 'runAgent']);
assertIncludes('src/app/api/mobile/chat/route.ts', ['attachments', 'intelligence', 'conversationId', 'runAgent']);
assertIncludes('src/lib/agent.ts', ['OLLAMA_VISION_MODEL', 'images', 'IntelligenceLevel', 'AbortSignal.timeout']);
assertIncludes(`${androidBase}/app/build.gradle.kts`, ['externalNativeBuild', 'ndkVersion', 'pdfbox-android', 'ANDROID_KEYSTORE_PATH', 'isMinifyEnabled = true']);
assertExcludes(`${androidBase}/app/build.gradle.kts`, ['DEFAULT_API_BASE_URL', 'https://api.nexoraia.com/', 'http://10.0.2.2:3000/']);
assertIncludes(`${androidPackage}/NexoraApp.kt`, ['DropdownMenu', 'Imagen', 'Archivo', 'Modelo', 'Inteligencia', 'Nuevo chat', 'Historial']);
assertIncludes(`${androidPackage}/AttachmentReader.kt`, ['PDFTextStripper', 'extractDocx', 'MAX_BYTES']);
assertIncludes(`${androidPackage}/ApiClient.kt`, ['NativeConfig.apiBaseUrl()', 'attachments', 'X-Nexora-Client']);
assertIncludes(`${androidBase}/app/src/main/cpp/native_config.c`, ['encoded_url', 'XOR_KEY', 'NativeConfig_apiBaseUrl']);
assertIncludes('.github/workflows/android-ci.yml', ['assembleDebug', 'ANDROID_KEYSTORE_BASE64', 'assembleRelease', 'nexora-ai-debug-apk', 'nexora-ai-release-apk', 'ndk;27.0.12077973', 'cmake;3.22.1']);

if (errors.length > 0) {
  console.error('NexoraAI CI preflight failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('NexoraAI CI preflight passed.');
