#!/usr/bin/env bash
set -euo pipefail

SECRET_DIR="${USER_BUILD_SECRET_DIR:-/opt/nexora-ai/secrets/user-builds}"
KEYSTORE="$SECRET_DIR/android-user-builds.keystore"
SIGNING_ENV="$SECRET_DIR/android-user-signing.env"

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  printf 'ERROR: la keystore global de usuarios debe prepararse con sudo.\n' >&2
  exit 3
fi
if ! command -v keytool >/dev/null 2>&1; then
  printf 'ERROR: falta keytool. Instala openjdk-17-jdk-headless.\n' >&2
  exit 4
fi

umask 077
mkdir -p "$SECRET_DIR"

if [[ -f "$KEYSTORE" && ! -f "$SIGNING_ENV" ]] || \
   [[ ! -f "$KEYSTORE" && -f "$SIGNING_ENV" ]]; then
  printf 'ERROR: la firma global de usuarios está incompleta. Restaura su respaldo; no se generó otra identidad.\n' >&2
  exit 5
fi

if [[ ! -f "$KEYSTORE" ]]; then
  store_password="$(openssl rand -hex 24)"
  key_password="$(openssl rand -hex 24)"
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storetype JKS \
    -storepass "$store_password" \
    -keypass "$key_password" \
    -alias nexora-user-builds \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=Nexora User Builds, OU=Ephemeral Mobile, O=Ghost Developer, C=MX"
  {
    printf 'ANDROID_KEYSTORE_PASSWORD=%s\n' "$store_password"
    printf 'ANDROID_KEY_ALIAS=%s\n' 'nexora-user-builds'
    printf 'ANDROID_KEY_PASSWORD=%s\n' "$key_password"
  } > "$SIGNING_ENV"
  printf 'Keystore global para apps de usuarios creada en %s. Respalda este directorio cifrado.\n' \
    "$SECRET_DIR"
fi

chown root:1001 "$SECRET_DIR" "$KEYSTORE" "$SIGNING_ENV"
chmod 0750 "$SECRET_DIR"
chmod 0640 "$KEYSTORE" "$SIGNING_ENV"
