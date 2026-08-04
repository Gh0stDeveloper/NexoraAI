#!/usr/bin/env bash
set -euo pipefail
docker compose -f docker-compose.vps.yml config >/dev/null
curl -fsS http://127.0.0.1:3000/api/health || true
printf 'Verificación VPS ejecutada.\n'
