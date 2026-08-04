import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const requiredFiles = [
  'package.json',
  'Dockerfile',
  'docker-compose.vps.yml',
  '.env.vps.example',
  '.github/workflows/web-api-ci.yml',
  '.github/workflows/docker-vps-ci.yml',
  '.github/workflows/android-ci.yml',
  '.github/workflows/training-ci.yml',
  'apps/android/GhostNexoraAndroid/settings.gradle.kts',
  'apps/android/GhostNexoraAndroid/build.gradle.kts',
  'apps/android/GhostNexoraAndroid/app/build.gradle.kts',
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
}

const envExamplePath = path.join(root, '.env.vps.example');
if (fs.existsSync(envExamplePath)) {
  const envExample = fs.readFileSync(envExamplePath, 'utf8');
  for (const key of ['APP_ENV', 'NEXT_PUBLIC_SITE_URL', 'NEXT_PUBLIC_API_URL', 'MOBILE_PRODUCTION_API_URL', 'AI_PROVIDER']) {
    if (!envExample.includes(`${key}=`)) {
      errors.push(`Missing VPS env key in .env.vps.example: ${key}`);
    }
  }
}

const dockerComposePath = path.join(root, 'docker-compose.vps.yml');
if (fs.existsSync(dockerComposePath)) {
  const compose = fs.readFileSync(dockerComposePath, 'utf8');
  for (const token of ['nexora-ai', 'postgres', 'ollama', '.env.production']) {
    if (!compose.includes(token)) {
      errors.push(`docker-compose.vps.yml should reference: ${token}`);
    }
  }
}

const androidBuildPath = path.join(root, 'apps/android/GhostNexoraAndroid/app/build.gradle.kts');
if (fs.existsSync(androidBuildPath)) {
  const androidBuild = fs.readFileSync(androidBuildPath, 'utf8');
  for (const token of ['debug', 'release', 'https://api.nexoraia.com/', '10.0.2.2']) {
    if (!androidBuild.includes(token)) {
      errors.push(`Android build config should reference: ${token}`);
    }
  }
}

if (errors.length > 0) {
  console.error('NexoraAI CI preflight failed:');
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log('NexoraAI CI preflight passed.');
