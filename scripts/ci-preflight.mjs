import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
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
  'apps/android/GhostNexoraAndroid/settings.gradle.kts',
  'apps/android/GhostNexoraAndroid/build.gradle.kts',
  'apps/android/GhostNexoraAndroid/app/build.gradle.kts',
  'apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/MainActivity.kt',
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
    if (!content.includes(token)) {
      errors.push(`${label} should reference: ${token}`);
    }
  }
}

for (const relativePath of requiredFiles) {
  const absolutePath = path.join(root, relativePath);
  if (!fs.existsSync(absolutePath)) {
    errors.push(`Missing required file: ${relativePath}`);
  }
}

const packagePath = path.join(root, 'package.json');
if (fs.existsSync(packagePath)) {
  const pkg = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
  for (const scriptName of requiredPackageScripts) {
    if (!pkg.scripts?.[scriptName]) {
      errors.push(`Missing package.json script: ${scriptName}`);
    }
  }
  if (pkg.dependencies?.next !== '15.5.7') {
    errors.push('package.json should use patched Next.js 15.5.7');
  }
  if (pkg.dependencies?.react !== '19.1.2' || pkg.dependencies?.['react-dom'] !== '19.1.2') {
    errors.push('package.json should use patched React/React DOM 19.1.2');
  }
}

assertIncludes('.env.vps.example', ['APP_ENV=', 'NEXT_PUBLIC_SITE_URL=', 'NEXT_PUBLIC_API_URL=', 'MOBILE_PRODUCTION_API_URL=', 'AI_PROVIDER=']);
assertIncludes('docker-compose.vps.yml', ['services:', 'app:', 'postgres:', 'ollama:', '.env.production', '127.0.0.1:3000:3000', '127.0.0.1:11434:11434', 'pgvector/pgvector:pg16']);
assertIncludes('src/app/page.tsx', ['/chat', '/docs', '/openapi.json', 'api.nexoraia.com']);
assertIncludes('src/app/chat/page.tsx', ['use client', 'fetch("/api/chat"', 'mode-chip', 'textarea']);
assertIncludes('src/app/api/chat/route.ts', ['requestId', 'z.ZodError', 'runAgent']);
assertIncludes('src/app/api/mobile/chat/route.ts', ['requestId', 'client', 'projectId']);
assertIncludes('apps/android/GhostNexoraAndroid/app/build.gradle.kts', ['debug', 'release', 'ANDROID_KEYSTORE_PATH', 'ANDROID_KEYSTORE_PASSWORD', 'https://api.nexoraia.com/', '10.0.2.2']);
assertIncludes('apps/android/GhostNexoraAndroid/app/src/main/java/com/ghostnexora/ai/MainActivity.kt', ['LazyColumn', 'postChat', 'JSONObject', 'X-Nexora-Client']);
assertIncludes('.github/workflows/android-ci.yml', ['assembleDebug', 'ANDROID_KEYSTORE_BASE64', 'assembleRelease', 'nexora-ai-debug-apk', 'nexora-ai-release-apk']);

if (errors.length > 0) {
  console.error('NexoraAI CI preflight failed:');
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log('NexoraAI CI preflight passed.');
