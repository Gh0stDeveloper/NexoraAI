#!/usr/bin/env bash
set -euo pipefail

if [[ -r /etc/nexora-ai/root ]]; then
  ROOT="$(< /etc/nexora-ai/root)"
else
  ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

COMPOSE_FILE="$ROOT/docker-compose.vps.yml"
ENV_FILE="$ROOT/.env.production"
BACKUP_DIR="${NEXORA_BACKUP_DIR:-/opt/nexora-ai/backups}"
STATE_DIR="${NEXORA_STATE_DIR:-/opt/nexora-ai/state}"
VERIFY_SCRIPT="$ROOT/deploy/scripts/verify-vps.sh"

if ! docker compose version >/dev/null 2>&1; then
  printf 'ERROR: Docker Compose v2 no está instalado. Ejecuta bootstrap-vps.sh.\n' >&2
  exit 2
fi

cd "$ROOT"

if [[ ! -f "$ENV_FILE" ]]; then
  printf 'ERROR: falta %s. Ejecuta primero bootstrap-vps.sh.\n' "$ENV_FILE" >&2
  exit 1
fi

compose() {
  if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" --profile sandbox "$@"
  else
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  fi
}

stop_disabled_sandbox() {
  if ! grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
      --profile sandbox stop sandbox >/dev/null 2>&1 || true
  fi
}

backup() {
  mkdir -p "$BACKUP_DIR"
  local stamp target
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  target="$BACKUP_DIR/postgres-$stamp.sql.gz"
  if compose ps --status running postgres | grep -q postgres; then
    compose exec -T postgres pg_dump -U nexora nexora_ai | gzip -9 > "$target"
    printf 'Respaldo creado: %s\n' "$target"
  else
    printf 'PostgreSQL no está activo; respaldo omitido.\n'
  fi
}

install_or_start() {
  compose pull postgres ollama
  compose build app
  if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then compose build sandbox; fi
  stop_disabled_sandbox
  compose up -d --remove-orphans
  bash "$VERIFY_SCRIPT"
}

update() {
  if [[ -n "$(git status --porcelain)" ]]; then
    printf 'ERROR: hay cambios locales. Guárdalos antes de actualizar.\n' >&2
    exit 1
  fi
  backup
  local previous
  previous="$(git rev-parse HEAD)"
  mkdir -p "$STATE_DIR"
  printf '%s\n' "$previous" > "$STATE_DIR/previous-version"
  git fetch --prune origin main
  git merge --ff-only origin/main
  compose build --pull app
  if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then compose build --pull sandbox; fi
  stop_disabled_sandbox
  compose up -d --remove-orphans
  if ! bash "$VERIFY_SCRIPT"; then
    printf 'La actualización no pasó la verificación. Versión anterior: %s\n' "$previous" >&2
    printf 'Ejecuta: nexora rollback %s\n' "$previous" >&2
    exit 1
  fi
  printf 'Nexora AI actualizado correctamente a %s.\n' "$(git rev-parse --short HEAD)"
}

rollback() {
  local target="${1:-}"
  if [[ -z "$target" && -r "$STATE_DIR/previous-version" ]]; then
    target="$(< "$STATE_DIR/previous-version")"
  fi
  if [[ -z "$target" ]]; then
    printf 'Uso: nexora rollback <commit>\n' >&2
    exit 1
  fi
  if [[ -n "$(git status --porcelain)" ]]; then
    printf 'ERROR: hay cambios locales; rollback cancelado.\n' >&2
    exit 1
  fi
  git cat-file -e "$target^{commit}"
  git reset --hard "$target"
  compose build app
  stop_disabled_sandbox
  compose up -d --remove-orphans
  bash "$VERIFY_SCRIPT"
}

case "${1:-help}" in
  install|start) install_or_start ;;
  update) update ;;
  backup) backup ;;
  rollback) rollback "${2:-}" ;;
  status) compose ps ;;
  logs) compose logs -f --tail="${2:-200}" ;;
  verify) VERIFY_PUBLIC_DOMAINS="${VERIFY_PUBLIC_DOMAINS:-true}" bash "$VERIFY_SCRIPT" ;;
  stop) compose stop ;;
  restart)
    stop_disabled_sandbox
    compose restart
    ;;
  android-release) bash "$ROOT/deploy/scripts/android-builder.sh" ;;
  cleanup)
    docker container prune --force --filter "until=24h"
    docker image prune --force --filter "until=168h"
    ;;
  *)
    printf '%s\n' \
      'Uso: nexora <comando>' \
      '  install          Construye e inicia Nexora AI' \
      '  update           Respalda, actualiza y verifica' \
      '  rollback [sha]   Restaura una versión anterior' \
      '  backup           Respalda PostgreSQL' \
      '  status           Muestra servicios' \
      '  logs [líneas]    Sigue los logs' \
      '  verify           Comprueba servicio y dominios' \
      '  android-release  Compila APK release en VPS AMD64' \
      '  cleanup          Elimina contenedores e imágenes temporales antiguas'
    ;;
esac
