#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_temp=""

cleanup_compose_temp() {
  if [[ -d "$compose_temp" && "$compose_temp" == /tmp/nexora-compose.* ]]; then
    find "$compose_temp" -mindepth 1 -delete
    rmdir "$compose_temp"
  fi
}
trap cleanup_compose_temp EXIT

bash "$ROOT/deploy/scripts/platform-check.sh"

sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates curl git gnupg jq openssl unzip \
  nginx certbot python3-certbot-nginx ufw fail2ban util-linux

if ! command -v docker >/dev/null 2>&1; then
  # shellcheck disable=SC1091
  source /etc/os-release
  docker_suite="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"
  if [[ -z "$docker_suite" ]]; then
    printf 'ERROR: no se pudo determinar el codename para el repositorio Docker.\n' >&2
    exit 3
  fi
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl --fail --location \
    "https://download.docker.com/linux/${ID}/gpg" \
    --output /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  printf '%s\n' \
    'Types: deb' \
    "URIs: https://download.docker.com/linux/${ID}" \
    "Suites: ${docker_suite}" \
    'Components: stable' \
    "Architectures: $(dpkg --print-architecture)" \
    'Signed-By: /etc/apt/keyrings/docker.asc' \
    | sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

if ! docker compose version >/dev/null 2>&1; then
  compose_version="${NEXORA_DOCKER_COMPOSE_VERSION:-v5.4.0}"
  case "$(uname -m)" in
    x86_64|amd64) compose_arch="x86_64" ;;
    aarch64|arm64) compose_arch="aarch64" ;;
    *)
      printf 'ERROR: arquitectura no compatible con Docker Compose.\n' >&2
      exit 4
      ;;
  esac
  compose_asset="docker-compose-linux-${compose_arch}"
  compose_temp="$(mktemp -d /tmp/nexora-compose.XXXXXX)"
  curl --fail --location --retry 3 \
    "https://github.com/docker/compose/releases/download/${compose_version}/${compose_asset}" \
    --output "$compose_temp/$compose_asset"
  curl --fail --location --retry 3 \
    "https://github.com/docker/compose/releases/download/${compose_version}/${compose_asset}.sha256" \
    --output "$compose_temp/${compose_asset}.sha256"
  (cd "$compose_temp" && sha256sum --check "${compose_asset}.sha256")
  sudo install -m 0755 -D \
    "$compose_temp/$compose_asset" \
    /usr/local/lib/docker/cli-plugins/docker-compose
  find "$compose_temp" -mindepth 1 -delete
  rmdir "$compose_temp"
  compose_temp=""
fi

if ! docker compose version >/dev/null 2>&1; then
  printf 'ERROR: Docker Compose v2 no está disponible después de la instalación.\n' >&2
  exit 5
fi

sudo systemctl enable --now docker nginx fail2ban
sudo systemctl enable --now certbot.timer
sudo usermod -aG docker "${SUDO_USER:-$USER}" || true

sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

sudo mkdir -p \
  /etc/nexora-ai \
  /opt/nexora-ai/backups \
  /opt/nexora-ai/cache/gradle \
  /opt/nexora-ai/releases \
  /opt/nexora-ai/secrets \
  /opt/nexora-ai/state \
  /var/lib/nexora-ai/sandbox-jobs
printf '%s\n' "$ROOT" | sudo tee /etc/nexora-ai/root >/dev/null
sudo chmod 700 /opt/nexora-ai/secrets /var/lib/nexora-ai/sandbox-jobs
operator_user="${SUDO_USER:-$USER}"
operator_group="$(id -gn "$operator_user")"
sudo chown "$operator_user:$operator_group" /opt/nexora-ai/backups /opt/nexora-ai/state
sudo install -m 0755 "$ROOT/deploy/scripts/nexora-vps.sh" /usr/local/bin/nexora

if [[ ! -f "$ROOT/.env.production" ]]; then
  cp "$ROOT/.env.vps.example" "$ROOT/.env.production"
  postgres_password="$(openssl rand -hex 24)"
  sandbox_token="$(openssl rand -hex 32)"
  sed -i \
    -e "s/CHANGE_THIS_PASSWORD/$postgres_password/g" \
    -e "s/CHANGE_WITH_AT_LEAST_24_RANDOM_CHARACTERS/$sandbox_token/g" \
    "$ROOT/.env.production"
  chmod 600 "$ROOT/.env.production"
fi

printf '%s\n' \
  'VPS preparada. Cierra sesión y vuelve a entrar para aplicar el grupo docker.' \
  'Después ejecuta: nexora install' \
  'Configura Nginx y TLS siguiendo docs/README-INSTALL.md.' \
  'El laboratorio permanece desactivado hasta cambiar ALLOW_CODE_EXECUTION=true.'
