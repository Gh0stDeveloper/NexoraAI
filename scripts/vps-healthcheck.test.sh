#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_DIR="$(mktemp -d /tmp/nexora-healthcheck.XXXXXX)"
trap 'find "$TEST_DIR" -mindepth 1 -delete; rmdir "$TEST_DIR"' EXIT

mkdir -p "$TEST_DIR/bin"
printf 'ALLOW_CODE_EXECUTION=false\n' > "$TEST_DIR/env"

cat > "$TEST_DIR/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "compose" && "${2:-}" == "version" ]]; then
  exit 0
fi
exit 0
EOF

cat > "$TEST_DIR/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
counter_file="${NEXORA_TEST_COUNTER:?}"
attempt=0
if [[ -r "$counter_file" ]]; then
  attempt="$(< "$counter_file")"
fi
attempt=$((attempt + 1))
printf '%s\n' "$attempt" > "$counter_file"
if (( attempt <= 2 )); then
  printf 'curl: (56) Recv failure: Connection reset by peer\n' >&2
  exit 56
fi
exit 0
EOF

chmod +x "$TEST_DIR/bin/docker" "$TEST_DIR/bin/curl"

output="$({
  cd "$ROOT"
  PATH="$TEST_DIR/bin:$PATH" \
  ENV_FILE="$TEST_DIR/env" \
  NEXORA_TEST_COUNTER="$TEST_DIR/curl-attempts" \
  NEXORA_VERIFY_TIMEOUT_SECONDS=5 \
  NEXORA_VERIFY_PUBLIC_TIMEOUT_SECONDS=5 \
  NEXORA_VERIFY_INTERVAL_SECONDS=0 \
    bash deploy/scripts/verify-vps.sh
} 2>&1)"

grep -Fq 'Esperando health local; intento 1' <<< "$output"
grep -Fq 'OK: health local disponible (intento 3).' <<< "$output"
grep -Fq 'Verificación VPS completada.' <<< "$output"
[[ "$(< "$TEST_DIR/curl-attempts")" == "4" ]]

printf 'VPS healthcheck retry test passed.\n'
