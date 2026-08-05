#!/usr/bin/env bash
set -euo pipefail

PUBLIC_DOMAIN="${PUBLIC_DOMAIN:-ghostnexoraai.duckdns.org}"
API_DOMAIN="${API_DOMAIN:-apighostnexoraai.duckdns.org}"
VERIFY_PUBLIC_DOMAINS="${VERIFY_PUBLIC_DOMAINS:-false}"

docker compose -f docker-compose.vps.yml config >/dev/null

printf 'Comprobando aplicación local...\n'
curl --fail --silent --show-error --max-time 20 \
  http://127.0.0.1:3000/api/health >/dev/null
printf 'OK: servicio local.\n'

if [[ "$VERIFY_PUBLIC_DOMAINS" == "true" ]]; then
  printf 'Comprobando HTTPS público...\n'
  curl --fail --silent --show-error --max-time 30 \
    "https://${PUBLIC_DOMAIN}/" >/dev/null
  curl --fail --silent --show-error --max-time 30 \
    "https://${API_DOMAIN}/" >/dev/null
  curl --fail --silent --show-error --max-time 30 \
    "https://${API_DOMAIN}/api/health" >/dev/null
  printf 'OK: web y API públicas.\n'
else
  printf 'Verificación pública omitida. Usa VERIFY_PUBLIC_DOMAINS=true para probar HTTPS.\n'
fi

printf 'Verificación VPS completada.\n'
