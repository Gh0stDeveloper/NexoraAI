#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-human}"
ALLOW_UNSUPPORTED="${NEXORA_ALLOW_UNSUPPORTED:-false}"

if [[ ! -r /etc/os-release ]]; then
  printf 'ERROR: no se pudo identificar el sistema operativo.\n' >&2
  exit 1
fi

# shellcheck disable=SC1091
source /etc/os-release
DISTRO="${ID:-unknown}"
VERSION="${VERSION_ID:-unknown}"
MAJOR="${VERSION%%.*}"
ARCH="$(uname -m)"

case "$ARCH" in
  x86_64|amd64) SERVER_ARCH="amd64"; ANDROID_BUILD="supported" ;;
  aarch64|arm64) SERVER_ARCH="arm64"; ANDROID_BUILD="github-actions-recommended" ;;
  *) SERVER_ARCH="$ARCH"; ANDROID_BUILD="unsupported" ;;
esac

SUPPORTED=false
case "$DISTRO:$MAJOR" in
  ubuntu:22|ubuntu:24|ubuntu:26|debian:11|debian:12|debian:13) SUPPORTED=true ;;
esac

if [[ "$MODE" == "--ci" ]]; then
  printf 'distro=%s\nversion=%s\narch=%s\nandroid_build=%s\nsupported=%s\n' \
    "$DISTRO" "$VERSION" "$SERVER_ARCH" "$ANDROID_BUILD" "$SUPPORTED"
else
  printf 'Sistema: %s %s\nArquitectura: %s\nServidor Nexora: %s\nCompilación Android local: %s\n' \
    "$DISTRO" "$VERSION" "$SERVER_ARCH" "$SUPPORTED" "$ANDROID_BUILD"
fi

if [[ "$SUPPORTED" != "true" && "$ALLOW_UNSUPPORTED" != "true" ]]; then
  printf 'ERROR: versión no soportada. Usa Ubuntu 22.04/24.04/26.04 o Debian 11/12/13.\n' >&2
  printf 'Ubuntu 23.x y Debian 8-10 están fuera de soporte de seguridad.\n' >&2
  exit 2
fi

if [[ "$SERVER_ARCH" != "amd64" && "$SERVER_ARCH" != "arm64" ]]; then
  printf 'ERROR: Nexora AI requiere AMD64 o ARM64.\n' >&2
  exit 3
fi
