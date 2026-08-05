#!/usr/bin/env bash
set -euo pipefail

PUBLIC_DOMAIN="${PUBLIC_DOMAIN:-ghostnexoraai.duckdns.org}"
API_DOMAIN="${API_DOMAIN:-apighostnexoraai.duckdns.org}"
VERIFY_PUBLIC_DOMAINS="${VERIFY_PUBLIC_DOMAINS:-false}"
ENV_FILE="${ENV_FILE:-.env.production}"

if ! docker compose version >/dev/null 2>&1; then
  printf 'ERROR: Docker Compose v2 no está instalado.\n' >&2
  exit 2
fi
COMPOSE=(docker compose --env-file "$ENV_FILE" -f docker-compose.vps.yml)
if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
  COMPOSE+=(--profile sandbox)
fi

"${COMPOSE[@]}" config >/dev/null

printf 'Comprobando aplicación local...\n'
curl --fail --silent --show-error --max-time 20 \
  http://127.0.0.1:3000/api/health >/dev/null
curl --fail --silent --show-error --max-time 20 \
  http://127.0.0.1:3000/api/mobile/status >/dev/null
printf 'OK: servicio local.\n'

if grep -Eq '^ALLOW_CODE_EXECUTION=true$' "$ENV_FILE"; then
  "${COMPOSE[@]}" exec -T sandbox node -e \
    "fetch('http://127.0.0.1:8787/health').then(r=>{if(!r.ok)process.exit(1)})"
  printf 'OK: laboratorio aislado activo.\n'
fi

if [[ "$VERIFY_PUBLIC_DOMAINS" == "true" ]]; then
  printf 'Comprobando HTTPS público...\n'
  curl --fail --silent --show-error --max-time 30 \
    "https://${PUBLIC_DOMAIN}/" >/dev/null
  curl --fail --silent --show-error --max-time 30 \
    "https://${API_DOMAIN}/" >/dev/null
  curl --fail --silent --show-error --max-time 30 \
    "https://${API_DOMAIN}/api/health" >/dev/null
  curl --fail --silent --show-error --max-time 30 \
    "https://${API_DOMAIN}/api/mobile/status" >/dev/null
  printf 'OK: web y API públicas.\n'
else
  printf 'Verificación pública omitida. Usa VERIFY_PUBLIC_DOMAINS=true para probar HTTPS.\n'
fi

printf 'Verificación VPS completada.\n'
