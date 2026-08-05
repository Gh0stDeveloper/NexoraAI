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
LOCK_FILE="${NEXORA_LOCK_FILE:-$STATE_DIR/operation.lock}"
BACKUP_RETENTION_DAYS="${NEXORA_BACKUP_RETENTION_DAYS:-30}"
COMPOSE_WAIT_TIMEOUT_SECONDS="${NEXORA_COMPOSE_WAIT_TIMEOUT_SECONDS:-240}"
REFRESH_CLI="${NEXORA_REFRESH_CLI:-true}"

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

acquire_operation_lock() {
  if ! command -v flock >/dev/null 2>&1; then
    printf 'ERROR: falta flock. Ejecuta bootstrap-vps.sh para instalar util-linux.\n' >&2
    exit 2
  fi
  mkdir -p "$STATE_DIR"
  exec 9>"$LOCK_FILE"
  if ! flock --nonblock 9; then
    printf 'ERROR: ya hay otra operación de Nexora AI en curso.\n' >&2
    exit 3
  fi
}

require_non_negative_integer() {
  local name="$1" value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    printf 'ERROR: %s debe ser un entero igual o mayor que cero.\n' "$name" >&2
    exit 2
  fi
}

require_positive_integer() {
  local name="$1" value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    printf 'ERROR: %s debe ser un entero mayor que cero.\n' "$name" >&2
    exit 2
  fi
}

require_non_negative_integer NEXORA_BACKUP_RETENTION_DAYS "$BACKUP_RETENTION_DAYS"
require_positive_integer NEXORA_COMPOSE_WAIT_TIMEOUT_SECONDS "$COMPOSE_WAIT_TIMEOUT_SECONDS"

stop_disabled_sandbox() {
  if ! grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
      --profile sandbox stop sandbox >/dev/null 2>&1 || true
  fi
}

cleanup_old_backups() {
  if (( BACKUP_RETENTION_DAYS == 0 )); then
    return 0
  fi
  find "$BACKUP_DIR" -maxdepth 1 -type f \
    -name 'postgres-*.sql.gz' -mtime "+$BACKUP_RETENTION_DAYS" \
    -print -delete
}

backup() {
  mkdir -p "$BACKUP_DIR"
  local stamp target partial
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  target="$BACKUP_DIR/postgres-$stamp.sql.gz"
  partial="$target.partial"
  if compose ps --status running --services | grep -Fxq postgres; then
    if ! compose exec -T postgres pg_dump -U nexora nexora_ai | gzip -9 > "$partial"; then
      rm -f -- "$partial"
      printf 'ERROR: no se pudo crear el respaldo PostgreSQL.\n' >&2
      return 1
    fi
    mv -- "$partial" "$target"
    printf 'Respaldo creado: %s\n' "$target"
    cleanup_old_backups
  else
    printf 'PostgreSQL no está activo; respaldo omitido.\n'
  fi
}

compose_up_and_wait() {
  local -a arguments=(up -d --remove-orphans)
  local up_help
  up_help="$(compose up --help 2>&1 || true)"
  if grep -q -- '--wait' <<< "$up_help"; then
    arguments+=(--wait --wait-timeout "$COMPOSE_WAIT_TIMEOUT_SECONDS")
  fi
  compose "${arguments[@]}"
}

deploy_revision() {
  local pull_build="$1"
  if [[ "$pull_build" == "true" ]]; then
    compose build --pull app || return $?
  else
    compose build app || return $?
  fi
  if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
    if [[ "$pull_build" == "true" ]]; then
      compose build --pull sandbox || return $?
    else
      compose build sandbox || return $?
    fi
  fi
  stop_disabled_sandbox
  compose_up_and_wait || return $?
  bash "$VERIFY_SCRIPT" || return $?
}

show_deployment_diagnostics() {
  printf '\nEstado de los servicios:\n' >&2
  compose ps >&2 || true
  printf '\nÚltimas 120 líneas de la aplicación:\n' >&2
  compose logs --no-color --tail=120 app >&2 || true
}

refresh_cli() {
  local source="$ROOT/deploy/scripts/nexora-vps.sh" target="/usr/local/bin/nexora"
  if [[ "$REFRESH_CLI" != "true" ]]; then
    return 0
  fi
  if [[ "$(id -u)" -eq 0 ]]; then
    install -m 0755 "$source" "$target"
  elif command -v sudo >/dev/null 2>&1 && sudo --non-interactive true 2>/dev/null; then
    sudo install -m 0755 "$source" "$target"
  else
    printf 'AVISO: ejecuta `sudo install -m 0755 %s %s` para actualizar el CLI.\n' \
      "$source" "$target" >&2
  fi
}

install_or_start() {
  compose pull postgres ollama
  deploy_revision false
  refresh_cli
}

update() {
  local target_ref="${1:-origin/main}"
  if [[ -n "$(git status --porcelain)" ]]; then
    printf 'ERROR: hay cambios locales. Guárdalos antes de actualizar.\n' >&2
    exit 1
  fi
  local previous target_commit
  previous="$(git rev-parse HEAD)"
  git fetch --prune --tags origin
  if ! target_commit="$(git rev-parse --verify "${target_ref}^{commit}")"; then
    printf 'ERROR: no se encontró la versión objetivo: %s\n' "$target_ref" >&2
    exit 1
  fi
  if [[ "$previous" == "$target_commit" ]]; then
    printf 'Nexora AI ya está actualizado en %s; no se reinició ningún contenedor.\n' \
      "$(git rev-parse --short HEAD)"
    refresh_cli
    return 0
  fi
  if ! git merge-base --is-ancestor "$previous" "$target_commit"; then
    printf 'ERROR: %s no es un avance rápido desde la versión instalada.\n' \
      "$target_ref" >&2
    printf 'Usa `nexora rollback <sha>` únicamente si deseas volver atrás.\n' >&2
    exit 1
  fi

  backup
  mkdir -p "$STATE_DIR"
  printf '%s\n' "$previous" > "$STATE_DIR/previous-version"
  git merge --ff-only "$target_commit"

  if ! deploy_revision true; then
    printf 'ERROR: el despliegue nuevo falló; iniciando rollback automático a %s.\n' \
      "$previous" >&2
    show_deployment_diagnostics
    git reset --hard "$previous"
    if deploy_revision false; then
      printf 'Rollback automático completado. Nexora AI volvió a %s.\n' \
        "$(git rev-parse --short HEAD)" >&2
      exit 1
    fi
    printf 'ERROR CRÍTICO: también falló el rollback automático.\n' >&2
    show_deployment_diagnostics
    exit 2
  fi

  refresh_cli
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
  local target_commit
  target_commit="$(git rev-parse --verify "${target}^{commit}")"
  git reset --hard "$target_commit"
  deploy_revision false
  printf 'Rollback completado a %s.\n' "$(git rev-parse --short HEAD)"
}

case "${1:-help}" in
  install|start) acquire_operation_lock; install_or_start ;;
  update) acquire_operation_lock; update "${2:-origin/main}" ;;
  backup) acquire_operation_lock; backup ;;
  rollback) acquire_operation_lock; rollback "${2:-}" ;;
  status) compose ps ;;
  logs) compose logs -f --tail="${2:-200}" ;;
  verify) VERIFY_PUBLIC_DOMAINS="${VERIFY_PUBLIC_DOMAINS:-true}" bash "$VERIFY_SCRIPT" ;;
  stop) acquire_operation_lock; compose stop ;;
  restart)
    acquire_operation_lock
    stop_disabled_sandbox
    compose restart
    ;;
  android-release) acquire_operation_lock; bash "$ROOT/deploy/scripts/android-builder.sh" ;;
  cleanup)
    acquire_operation_lock
    docker container prune --force --filter "until=24h"
    docker image prune --force --filter "until=168h"
    ;;
  *)
    printf '%s\n' \
      'Uso: nexora <comando>' \
      '  install          Construye e inicia Nexora AI' \
      '  update [ref]     Respalda, actualiza, verifica y revierte al fallar' \
      '  rollback [sha]   Restaura una versión anterior' \
      '  backup           Respalda PostgreSQL' \
      '  status           Muestra servicios' \
      '  logs [líneas]    Sigue los logs' \
      '  verify           Comprueba servicio y dominios' \
      '  android-release  Compila APK release en VPS AMD64' \
      '  cleanup          Elimina contenedores e imágenes temporales antiguas'
    ;;
esac
