#!/usr/bin/env bash
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_DIR="$(mktemp -d /tmp/nexora-update.XXXXXX)"
trap 'find "$TEST_DIR" -mindepth 1 -delete; rmdir "$TEST_DIR"' EXIT

REPO="$TEST_DIR/repo"
ORIGIN="$TEST_DIR/origin.git"
mkdir -p "$REPO/deploy/scripts" "$TEST_DIR/state" "$TEST_DIR/backups" "$TEST_DIR/bin"
cp "$SOURCE_ROOT/deploy/scripts/nexora-vps.sh" "$REPO/deploy/scripts/"
cp "$SOURCE_ROOT/deploy/scripts/verify-vps.sh" "$REPO/deploy/scripts/"
cp "$SOURCE_ROOT/docker-compose.vps.yml" "$REPO/"
printf 'ALLOW_CODE_EXECUTION=false\n' > "$REPO/.env.production"
printf 'stable\n' > "$REPO/deployment-marker"

git -C "$REPO" init --initial-branch=main --quiet
git -C "$REPO" config user.name 'Nexora CI'
git -C "$REPO" config user.email 'nexora-ci@example.invalid'
git -C "$REPO" add .
git -C "$REPO" commit --quiet -m 'Stable deployment'
STABLE_COMMIT="$(git -C "$REPO" rev-parse HEAD)"
git clone --quiet --bare "$REPO" "$ORIGIN"
git -C "$REPO" remote add origin "$ORIGIN"

cat > "$TEST_DIR/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${NEXORA_TEST_DOCKER_LOG:?}"
if [[ "${1:-}" == "compose" && "${2:-}" == "version" ]]; then
  exit 0
fi
if [[ " $* " == *" ps --status running --services "* ]]; then
  exit 0
fi
if [[ " $* " == *" up --help "* ]]; then
  printf 'Usage: docker compose up\n'
  exit 0
fi
if [[ " $* " == *" build "* ]] && grep -Fxq 'broken' deployment-marker; then
  printf 'simulated image build failure\n' >&2
  exit 42
fi
exit 0
EOF

cat > "$TEST_DIR/bin/curl" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$TEST_DIR/bin/docker" "$TEST_DIR/bin/curl"

run_update() {
  local output_file="$1"
  shift
  {
    cd "$REPO"
    PATH="$TEST_DIR/bin:$PATH" \
    NEXORA_TEST_DOCKER_LOG="$TEST_DIR/docker.log" \
    NEXORA_STATE_DIR="$TEST_DIR/state" \
    NEXORA_BACKUP_DIR="$TEST_DIR/backups" \
    NEXORA_REFRESH_CLI=false \
    NEXORA_VERIFY_INTERVAL_SECONDS=0 \
      bash deploy/scripts/nexora-vps.sh update "$@"
  } > "$output_file" 2>&1
}

: > "$TEST_DIR/docker.log"
if ! run_update "$TEST_DIR/noop.out"; then
  cat "$TEST_DIR/noop.out" >&2
  exit 1
fi
grep -Fq 'no se reinició ningún contenedor' "$TEST_DIR/noop.out"
if grep -Eq ' build | up -d ' "$TEST_DIR/docker.log"; then
  printf 'A no-op update unexpectedly changed Docker state.\n' >&2
  exit 1
fi

printf 'broken\n' > "$REPO/deployment-marker"
git -C "$REPO" add deployment-marker
git -C "$REPO" commit --quiet -m 'Broken deployment'
BROKEN_COMMIT="$(git -C "$REPO" rev-parse HEAD)"
git -C "$REPO" push --quiet origin main
git -C "$REPO" reset --quiet --hard "$STABLE_COMMIT"

: > "$TEST_DIR/docker.log"
set +e
run_update "$TEST_DIR/rollback.out"
update_status=$?
set -e

[[ "$update_status" == "1" ]]
[[ "$(git -C "$REPO" rev-parse HEAD)" == "$STABLE_COMMIT" ]]
[[ "$(< "$REPO/deployment-marker")" == "stable" ]]
grep -Fq "iniciando rollback automático a $STABLE_COMMIT" "$TEST_DIR/rollback.out"
grep -Fq 'Rollback automático completado.' "$TEST_DIR/rollback.out"
grep -Fq "$BROKEN_COMMIT" "$REPO/.git/FETCH_HEAD"

printf 'VPS update contract test passed.\n'
