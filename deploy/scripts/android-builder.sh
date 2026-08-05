#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_PROJECT="$ROOT/apps/android/GhostNexoraAndroid"
SDK_ROOT="${NEXORA_ANDROID_SDK_ROOT:-/opt/nexora-ai/android-sdk}"
GRADLE_ROOT="${NEXORA_GRADLE_ROOT:-/opt/nexora-ai/gradle-8.10.2}"
GRADLE_CACHE="${NEXORA_GRADLE_CACHE:-/opt/nexora-ai/cache/gradle}"
SECRET_DIR="${NEXORA_SECRET_DIR:-/opt/nexora-ai/secrets}"
RELEASE_DIR="${NEXORA_RELEASE_DIR:-/opt/nexora-ai/releases}"
KEYSTORE="$SECRET_DIR/android-release.keystore"
SIGNING_ENV="$SECRET_DIR/android-signing.env"
TOOLS_URL="${ANDROID_COMMANDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip}"
TOOLS_SHA256="${ANDROID_COMMANDLINE_TOOLS_SHA256:-4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583}"
GRADLE_URL="${GRADLE_DISTRIBUTION_URL:-https://services.gradle.org/distributions/gradle-8.10.2-bin.zip}"
GRADLE_SHA256="${GRADLE_DISTRIBUTION_SHA256:-31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26}"
TEMP_DIRS=()

cleanup_temp_dirs() {
  local temp_dir
  for temp_dir in "${TEMP_DIRS[@]}"; do
    if [[ -d "$temp_dir" && "$temp_dir" == /tmp/nexora-android-* ]]; then
      rm -rf -- "$temp_dir"
    fi
  done
}
trap cleanup_temp_dirs EXIT

if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
  printf 'ERROR: el compilador Android VPS se admite en Linux AMD64. Usa GitHub Actions en ARM64.\n' >&2
  exit 2
fi

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  printf 'Ejecuta este script con sudo para conservar SDK, caché y keystore en /opt.\n' >&2
  exit 3
fi

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates curl unzip zip openssl openjdk-17-jdk-headless

mkdir -p "$SDK_ROOT/cmdline-tools" "$GRADLE_CACHE" "$SECRET_DIR" "$RELEASE_DIR"
chmod 700 "$SECRET_DIR"

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  work="$(mktemp -d /tmp/nexora-android-tools.XXXXXX)"
  TEMP_DIRS+=("$work")
  curl --fail --location --retry 3 "$TOOLS_URL" -o "$work/tools.zip"
  printf '%s  %s\n' "$TOOLS_SHA256" "$work/tools.zip" | sha256sum --check -
  unzip -q "$work/tools.zip" -d "$work/tools"
  if [[ -e "$SDK_ROOT/cmdline-tools/latest" ]]; then
    mv "$SDK_ROOT/cmdline-tools/latest" \
      "$SDK_ROOT/cmdline-tools/incomplete-$(date -u +%s)-$$"
  fi
  mv "$work/tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

if [[ ! -x "$GRADLE_ROOT/bin/gradle" ]]; then
  work_gradle="$(mktemp -d /tmp/nexora-android-gradle.XXXXXX)"
  TEMP_DIRS+=("$work_gradle")
  curl --fail --location --retry 3 "$GRADLE_URL" -o "$work_gradle/gradle.zip"
  printf '%s  %s\n' "$GRADLE_SHA256" "$work_gradle/gradle.zip" | sha256sum --check -
  unzip -q "$work_gradle/gradle.zip" -d "$work_gradle/extracted"
  if [[ ! -x "$work_gradle/extracted/gradle-8.10.2/bin/gradle" ]]; then
    printf 'ERROR: el archivo Gradle no contiene la distribución esperada.\n' >&2
    exit 5
  fi
  mkdir -p "$(dirname "$GRADLE_ROOT")"
  if [[ -e "$GRADLE_ROOT" ]]; then
    mv "$GRADLE_ROOT" "$GRADLE_ROOT.incomplete-$(date -u +%s)-$$"
  fi
  mv "$work_gradle/extracted/gradle-8.10.2" "$GRADLE_ROOT"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export GRADLE_USER_HOME="$GRADLE_CACHE"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$GRADLE_ROOT/bin:$PATH"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "ndk;27.0.12077973" \
  "cmake;3.22.1"

if [[ ! -f "$KEYSTORE" || ! -f "$SIGNING_ENV" ]]; then
  store_password="$(openssl rand -hex 24)"
  key_password="$(openssl rand -hex 24)"
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storetype JKS \
    -storepass "$store_password" \
    -keypass "$key_password" \
    -alias nexora-release \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=Nexora AI, OU=Mobile, O=Ghost Developer, C=MX"
  {
    printf 'ANDROID_KEYSTORE_PATH=%q\n' "$KEYSTORE"
    printf 'ANDROID_KEYSTORE_PASSWORD=%q\n' "$store_password"
    printf 'ANDROID_KEY_ALIAS=%q\n' 'nexora-release'
    printf 'ANDROID_KEY_PASSWORD=%q\n' "$key_password"
  } > "$SIGNING_ENV"
  chmod 600 "$KEYSTORE" "$SIGNING_ENV"
  printf 'Keystore release creada. Haz una copia cifrada de %s y %s ahora.\n' "$KEYSTORE" "$SIGNING_ENV"
fi

# shellcheck disable=SC1090
source "$SIGNING_ENV"
export ANDROID_KEYSTORE_PATH ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD

cd "$ANDROID_PROJECT"
gradle assembleRelease --no-daemon --stacktrace

apk="$(find app/build/outputs/apk/release -type f -name '*.apk' | head -n 1)"
if [[ -z "$apk" ]]; then
  printf 'ERROR: Gradle no generó un APK release.\n' >&2
  exit 4
fi

version="$(grep -E 'versionName = ' app/build.gradle.kts | head -n 1 | cut -d '"' -f 2)"
target="$RELEASE_DIR/NexoraAI-${version}.apk"
install -m 0644 "$apk" "$target"
sha256sum "$target" > "$target.sha256"
printf 'APK firmado: %s\nChecksum: %s.sha256\n' "$target" "$target"
