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
  local -a command=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
  if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
    command+=(--profile sandbox)
  fi
  if grep -Eq '^ENABLE_USER_ANDROID_BUILDS=true$' "$ENV_FILE"; then
    command+=(--profile user-builds)
  fi
  "${command[@]}" "$@"
}

compose_has_service() {
  compose config --services 2>/dev/null | grep -Fxq "$1"
}

read_env_value() {
  local key="$1"
  awk -F= -v target="$key" '
    $1 == target { value = substr($0, index($0, "=") + 1) }
    END {
      gsub(/^"|"$/, "", value)
      print value
    }
  ' "$ENV_FILE"
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

stop_disabled_optional_services() {
  if ! grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
      --profile sandbox stop sandbox >/dev/null 2>&1 || true
  fi
  if ! grep -Eq '^ENABLE_USER_ANDROID_BUILDS=true$' "$ENV_FILE"; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
      --profile user-builds stop android-build-worker >/dev/null 2>&1 || true
  fi
}

prepare_user_build_runtime() {
  if ! grep -Eq '^ENABLE_USER_ANDROID_BUILDS=true$' "$ENV_FILE"; then
    return 0
  fi
  if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
    printf 'ERROR: el compilador temporal de usuarios requiere una VPS Linux AMD64.\n' >&2
    return 1
  fi
  if [[ -x /opt/nexora-ai/gradle-8.10.2/bin/gradle ]] && \
     [[ -x /opt/nexora-ai/android-sdk/build-tools/35.0.0/apksigner ]] && \
     [[ -d /opt/nexora-ai/cache/user-gradle ]] && \
     [[ -d /var/lib/nexora-ai/android-build-jobs ]] && \
     [[ "$(stat -c %u /opt/nexora-ai/cache/user-gradle)" == "1001" ]] && \
     [[ "$(stat -c %u /var/lib/nexora-ai/android-build-jobs)" == "1001" ]]; then
    if [[ "$(id -u)" -ne 0 ]] || {
      [[ -f /opt/nexora-ai/secrets/user-builds/android-user-builds.keystore ]] &&
      [[ -f /opt/nexora-ai/secrets/user-builds/android-user-signing.env ]]
    }; then
      return 0
    fi
  fi
  if [[ "$(id -u)" -ne 0 ]]; then
    printf 'ERROR: falta preparar el compilador de usuarios; ejecuta `sudo nexora user-builds-enable`.\n' >&2
    return 1
  fi
  if [[ ! -x /opt/nexora-ai/gradle-8.10.2/bin/gradle ]] || \
     [[ ! -x /opt/nexora-ai/android-sdk/build-tools/35.0.0/apksigner ]]; then
    printf 'Preparando el SDK y Gradle compartidos mediante el compilador oficial...\n'
    bash "$ROOT/deploy/scripts/android-builder.sh" || return $?
  fi
  mkdir -p /opt/nexora-ai/cache/user-gradle /var/lib/nexora-ai/android-build-jobs
  chown 1001:1001 /opt/nexora-ai/cache/user-gradle /var/lib/nexora-ai/android-build-jobs
  bash "$ROOT/deploy/scripts/user-build-keystore.sh"
}

set_env_value() {
  local key="$1" value="$2" temporary="$ENV_FILE.tmp.$$"
  awk -v target="$key" -v replacement="$value" '
    BEGIN { replaced = 0 }
    index($0, target "=") == 1 {
      print target "=" replacement
      replaced = 1
      next
    }
    { print }
    END {
      if (!replaced) print target "=" replacement
    }
  ' "$ENV_FILE" > "$temporary"
  chmod --reference="$ENV_FILE" "$temporary"
  mv -f -- "$temporary" "$ENV_FILE"
}

enable_user_builds() {
  if [[ "$(id -u)" -ne 0 ]]; then
    printf 'ERROR: ejecuta `sudo nexora user-builds-enable`.\n' >&2
    exit 3
  fi
  set_env_value ENABLE_USER_ANDROID_BUILDS true
  if ! grep -Eq '^USER_BUILD_RATE_LIMIT_SALT=[a-f0-9]{64}$' "$ENV_FILE"; then
    set_env_value USER_BUILD_RATE_LIMIT_SALT "$(openssl rand -hex 32)"
  fi
  if ! prepare_user_build_runtime || ! deploy_revision false; then
    set_env_value ENABLE_USER_ANDROID_BUILDS false
    stop_disabled_optional_services
    compose up -d --no-deps --force-recreate app >/dev/null 2>&1 || true
    printf 'ERROR: no se pudo activar el compilador temporal; la función volvió a quedar desactivada.\n' >&2
    exit 1
  fi
  printf 'Compilaciones temporales de usuarios activadas y verificadas.\n'
}

disable_user_builds() {
  if [[ "$(id -u)" -ne 0 ]]; then
    printf 'ERROR: ejecuta `sudo nexora user-builds-disable`.\n' >&2
    exit 3
  fi
  local jobs_path
  jobs_path="$(read_env_value USER_BUILD_JOBS_PATH)"
  jobs_path="${jobs_path:-/var/lib/nexora-ai/android-build-jobs}"
  jobs_path="$(realpath -m -- "$jobs_path")"
  case "$jobs_path" in
    /var/lib/nexora-ai/*|/opt/nexora-ai/*) ;;
    *)
      printf 'ERROR: USER_BUILD_JOBS_PATH no está dentro de una ruta segura de Nexora.\n' >&2
      exit 4
      ;;
  esac
  set_env_value ENABLE_USER_ANDROID_BUILDS false
  stop_disabled_optional_services
  compose up -d --no-deps --force-recreate app
  if [[ -d "$jobs_path" ]]; then
    find "$jobs_path" -mindepth 1 -delete
  fi
  compose exec -T postgres psql -v ON_ERROR_STOP=1 -U nexora -d nexora_ai <<'SQL'
do $cleanup$
begin
  if to_regclass('public.android_build_jobs') is not null then
    update android_build_jobs
       set status = 'expired',
           progress_label = 'Enlace expirado',
           output_path = null,
           source_prompt = '',
           source_content = '',
           updated_at = now()
     where status <> 'expired';
  end if;
end
$cleanup$;
SQL
  bash "$VERIFY_SCRIPT"
  printf 'Compilaciones temporales desactivadas; artefactos y enlaces eliminados.\n'
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
  if compose_has_service mailer; then
    if [[ "$pull_build" == "true" ]]; then
      compose build --pull mailer || return $?
    else
      compose build mailer || return $?
    fi
  fi
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
  if grep -Eq '^ENABLE_USER_ANDROID_BUILDS=true$' "$ENV_FILE"; then
    prepare_user_build_runtime || return $?
    if [[ "$pull_build" == "true" ]]; then
      compose build --pull android-build-worker || return $?
    else
      compose build android-build-worker || return $?
    fi
  fi
  stop_disabled_optional_services
  compose_up_and_wait || return $?
  bash "$VERIFY_SCRIPT" || return $?
}

show_deployment_diagnostics() {
  printf '\nEstado de los servicios:\n' >&2
  compose ps >&2 || true
  printf '\nÚltimas 120 líneas de la aplicación:\n' >&2
  compose logs --no-color --tail=120 app >&2 || true
  if compose_has_service mailer; then
    printf '\nÚltimas 120 líneas de Nexora Mail:\n' >&2
    compose logs --no-color --tail=120 mailer >&2 || true
  fi
  if grep -Eq '^ENABLE_USER_ANDROID_BUILDS=true$' "$ENV_FILE"; then
    printf '\nÚltimas 80 líneas del compilador efímero:\n' >&2
    compose logs --no-color --tail=80 android-build-worker >&2 || true
  fi
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

mail_dns() {
  if ! compose_has_service mailer; then
    printf 'Nexora Mail no existe en esta versión. Actualiza primero con `nexora update`.\n' >&2
    exit 1
  fi
  local domain hostname selector
  domain="$(read_env_value MAIL_DOMAIN)"
  domain="${domain:-$(read_env_value PUBLIC_DOMAIN)}"
  hostname="$(read_env_value MAIL_HOSTNAME)"
  hostname="${hostname:-$domain}"
  selector="$(read_env_value MAIL_DKIM_SELECTOR)"
  selector="${selector:-nexora}"

  printf '\n=== DNS recomendado para Nexora Mail ===\n'
  printf 'A/AAAA: %s -> IP pública de tu VPS\n' "$hostname"
  printf 'PTR/rDNS: IP pública de tu VPS -> %s\n' "$hostname"
  printf 'SPF (%s): v=spf1 a:%s -all\n' "$domain" "$hostname"
  printf 'DMARC (_dmarc.%s): v=DMARC1; p=quarantine; adkim=s; aspf=s\n' "$domain"
  printf 'DKIM (%s._domainkey.%s):\n' "$selector" "$domain"
  if compose ps --status running --services | grep -Fxq mailer; then
    compose exec -T mailer sh -c \
      "cat /var/lib/nexora-mail/${selector}.txt 2>/dev/null || true"
  else
    printf '  Inicia Nexora Mail para generar la clave: nexora restart\n'
  fi
  printf '\nNota: el proveedor de la VPS debe permitir TCP/25 de salida.\n'
}

mail_test() {
  local recipient="${1:-}"
  if [[ ! "$recipient" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
    printf 'Uso: nexora mail-test correo@dominio.com\n' >&2
    exit 1
  fi
  if ! compose_has_service mailer; then
    printf 'ERROR: Nexora Mail no existe en esta versión.\n' >&2
    exit 1
  fi
  compose exec -T -e NEXORA_TEST_RECIPIENT="$recipient" mailer python3 - <<'PY'
import hashlib
import json
import os
import urllib.request

recipient = os.environ["NEXORA_TEST_RECIPIENT"]
secret = os.environ.get("AUTH_EMAIL_WEBHOOK_SECRET", "").strip()
if not secret:
    database_secret = os.environ.get("POSTGRES_PASSWORD", "").strip()
    if not database_secret:
        raise SystemExit("Falta POSTGRES_PASSWORD/AUTH_EMAIL_WEBHOOK_SECRET")
    secret = hashlib.sha256(f"nexora-mail:{database_secret}".encode()).hexdigest()
payload = json.dumps({
    "type": "nexora.test",
    "to": recipient,
    "subject": "Prueba de Nexora Mail",
    "text": "Nexora Mail está funcionando desde tu propia VPS.",
    "html": "<h2>Nexora Mail</h2><p>El servicio de correo de tu VPS está funcionando.</p>",
}).encode()
request = urllib.request.Request(
    "http://127.0.0.1:8025/send",
    data=payload,
    method="POST",
    headers={"Content-Type": "application/json", "Authorization": f"Bearer {secret}"},
)
with urllib.request.urlopen(request, timeout=20) as response:
    print(response.read().decode())
PY
  printf 'Nexora Mail aceptó el mensaje para %s. Revisa también spam y los logs si no llega.\n' "$recipient"
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
  if compose_has_service mailer; then
    printf 'Nexora Mail está activo. Usa `nexora mail-dns` para ver SPF/DKIM/DMARC.\n'
  fi
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
  refresh_cli
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
  mail-dns) mail_dns ;;
  mail-test) mail_test "${2:-}" ;;
  stop) acquire_operation_lock; compose stop ;;
  restart)
    acquire_operation_lock
    stop_disabled_optional_services
    compose restart
    ;;
  android-release) acquire_operation_lock; bash "$ROOT/deploy/scripts/android-builder.sh" ;;
  user-builds-enable) acquire_operation_lock; enable_user_builds ;;
  user-builds-disable) acquire_operation_lock; disable_user_builds ;;
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
      '  mail-dns         Muestra A/PTR/SPF/DKIM/DMARC recomendados' \
      '  mail-test EMAIL  Envía una prueba con Nexora Mail' \
      '  android-release  Compila APK release en VPS AMD64' \
      '  user-builds-enable  Activa el compilador temporal aislado' \
      '  user-builds-disable Desactiva el compilador temporal' \
      '  cleanup          Elimina contenedores e imágenes temporales antiguas'
    ;;
esac
