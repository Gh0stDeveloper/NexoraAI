#!/usr/bin/env node
import fs from "node:fs";

const requiredDirectories = [
  "apps/android/GhostNexoraAndroid",
  "src/app/api/mobile/chat/stream",
  "sandbox",
  "deploy/scripts",
  "docs",
  ".github/workflows",
];
const missing = requiredDirectories.filter((directory) => !fs.statSync(directory, { throwIfNoEntry: false })?.isDirectory());

if (missing.length) {
  console.error("Directorios requeridos ausentes:", missing);
  process.exit(1);
}

const packageJson = JSON.parse(fs.readFileSync("package.json", "utf8"));
const androidBuild = fs.readFileSync("apps/android/GhostNexoraAndroid/app/build.gradle.kts", "utf8");
const versionName = androidBuild.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
if (packageJson.version !== versionName) {
  console.error(`Versiones desincronizadas: web=${packageJson.version}, Android=${versionName}`);
  process.exit(1);
}

console.log(`Repositorio validado · versión ${packageJson.version}`);
