#!/usr/bin/env bash
set -euo pipefail

PUBLIC_DOMAIN="${PUBLIC_DOMAIN:-ghostnexoraai.duckdns.org}"
API_DOMAIN="${API_DOMAIN:-apighostnexoraai.duckdns.org}"
VERIFY_PUBLIC_DOMAINS="${VERIFY_PUBLIC_DOMAINS:-false}"
ENV_FILE="${ENV_FILE:-.env.production}"
VERIFY_STARTUP_TIMEOUT_SECONDS="${NEXORA_VERIFY_TIMEOUT_SECONDS:-180}"
VERIFY_PUBLIC_TIMEOUT_SECONDS="${NEXORA_VERIFY_PUBLIC_TIMEOUT_SECONDS:-60}"
VERIFY_INTERVAL_SECONDS="${NEXORA_VERIFY_INTERVAL_SECONDS:-3}"
VERIFY_REQUEST_TIMEOUT_SECONDS="${NEXORA_VERIFY_REQUEST_TIMEOUT_SECONDS:-10}"

require_positive_integer() {
  local name="$1" value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    printf 'ERROR: %s debe ser un entero mayor que cero.\n' "$name" >&2
    exit 2
  fi
}

require_non_negative_integer() {
  local name="$1" value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    printf 'ERROR: %s debe ser un entero igual o mayor que cero.\n' "$name" >&2
    exit 2
  fi
}

require_positive_integer NEXORA_VERIFY_TIMEOUT_SECONDS "$VERIFY_STARTUP_TIMEOUT_SECONDS"
require_positive_integer NEXORA_VERIFY_PUBLIC_TIMEOUT_SECONDS "$VERIFY_PUBLIC_TIMEOUT_SECONDS"
require_non_negative_integer NEXORA_VERIFY_INTERVAL_SECONDS "$VERIFY_INTERVAL_SECONDS"
require_positive_integer NEXORA_VERIFY_REQUEST_TIMEOUT_SECONDS "$VERIFY_REQUEST_TIMEOUT_SECONDS"

if ! docker compose version >/dev/null 2>&1; then
  printf 'ERROR: Docker Compose v2 no está instalado.\n' >&2
  exit 2
fi
COMPOSE=(docker compose --env-file "$ENV_FILE" -f docker-compose.vps.yml)
if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
  COMPOSE+=(--profile sandbox)
fi
if grep -Eq '^ENABLE_USER_ANDROID_BUILDS=true$' "$ENV_FILE"; then
  COMPOSE+=(--profile user-builds)
fi

"${COMPOSE[@]}" config >/dev/null

show_app_diagnostics() {
  printf '\nEstado del contenedor de aplicación:\n' >&2
  "${COMPOSE[@]}" ps app >&2 || true
  printf '\nÚltimas 120 líneas del registro de aplicación:\n' >&2
  "${COMPOSE[@]}" logs --no-color --tail=120 app >&2 || true
}

wait_for_command() {
  local label="$1" timeout_seconds="$2"
  shift 2

  local attempt=1 deadline=$((SECONDS + timeout_seconds)) last_error="" remaining sleep_seconds
  while (( SECONDS < deadline )); do
    if last_error="$("$@" 2>&1)"; then
      printf 'OK: %s disponible (intento %d).\n' "$label" "$attempt"
      return 0
    fi

    remaining=$((deadline - SECONDS))
    if (( remaining <= 0 )); then
      break
    fi
    if (( attempt == 1 || attempt % 5 == 0 )); then
      printf 'Esperando %s; intento %d, quedan hasta %ss...\n' \
        "$label" "$attempt" "$remaining"
    fi

    sleep_seconds="$VERIFY_INTERVAL_SECONDS"
    if (( sleep_seconds > remaining )); then
      sleep_seconds="$remaining"
    fi
    sleep "$sleep_seconds"
    attempt=$((attempt + 1))
  done

  printf 'ERROR: %s no estuvo disponible después de %ss.\n' \
    "$label" "$timeout_seconds" >&2
  if [[ -n "$last_error" ]]; then
    printf 'Último error: %s\n' "$last_error" >&2
  fi
  return 1
}

wait_for_http() {
  local label="$1" url="$2" timeout_seconds="$3"
  wait_for_command "$label" "$timeout_seconds" \
    curl --fail --silent --show-error \
      --connect-timeout "$VERIFY_REQUEST_TIMEOUT_SECONDS" \
      --max-time "$VERIFY_REQUEST_TIMEOUT_SECONDS" \
      --output /dev/null "$url"
}

android_build_worker_running() {
  "${COMPOSE[@]}" ps --status running --services android-build-worker |
    grep -Fxq android-build-worker
}

printf 'Comprobando aplicación local...\n'
if ! wait_for_http \
  'health local' \
  'http://127.0.0.1:3000/api/health' \
  "$VERIFY_STARTUP_TIMEOUT_SECONDS"; then
  show_app_diagnostics
  exit 1
fi
if ! wait_for_http \
  'API móvil local' \
  'http://127.0.0.1:3000/api/mobile/status' \
  "$VERIFY_PUBLIC_TIMEOUT_SECONDS"; then
  show_app_diagnostics
  exit 1
fi
printf 'OK: servicio local.\n'

if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
  if ! wait_for_command 'laboratorio aislado' "$VERIFY_STARTUP_TIMEOUT_SECONDS" \
    "${COMPOSE[@]}" exec -T sandbox node -e \
      "fetch('http://127.0.0.1:8787/health').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"; then
    "${COMPOSE[@]}" ps sandbox >&2 || true
    "${COMPOSE[@]}" logs --no-color --tail=120 sandbox >&2 || true
    exit 1
  fi
  printf 'OK: laboratorio aislado activo.\n'
fi

if grep -Eq '^ENABLE_USER_ANDROID_BUILDS=true$' "$ENV_FILE"; then
  if ! wait_for_command 'compilador Android efímero' "$VERIFY_STARTUP_TIMEOUT_SECONDS" \
    android_build_worker_running; then
    "${COMPOSE[@]}" ps android-build-worker >&2 || true
    "${COMPOSE[@]}" logs --no-color --tail=120 android-build-worker >&2 || true
    exit 1
  fi
  printf 'OK: compilador Android efímero activo.\n'
fi

if [[ "$VERIFY_PUBLIC_DOMAINS" == "true" ]]; then
  printf 'Comprobando HTTPS público...\n'
  wait_for_http 'web pública' "https://${PUBLIC_DOMAIN}/" "$VERIFY_PUBLIC_TIMEOUT_SECONDS"
  wait_for_http 'API pública' "https://${API_DOMAIN}/" "$VERIFY_PUBLIC_TIMEOUT_SECONDS"
  wait_for_http 'health público' \
    "https://${API_DOMAIN}/api/health" "$VERIFY_PUBLIC_TIMEOUT_SECONDS"
  wait_for_http 'API móvil pública' \
    "https://${API_DOMAIN}/api/mobile/status" "$VERIFY_PUBLIC_TIMEOUT_SECONDS"
  printf 'OK: web y API públicas.\n'
else
  printf 'Verificación pública omitida. Usa VERIFY_PUBLIC_DOMAINS=true para probar HTTPS.\n'
fi

printf 'Verificación VPS completada.\n'
