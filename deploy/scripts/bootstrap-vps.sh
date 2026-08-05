#!/usr/bin/env bash
set -euo pipefail

sudo apt-get update
sudo apt-get install -y \
  ca-certificates \
  curl \
  gnupg \
  nginx \
  certbot \
  python3-certbot-nginx \
  ufw \
  fail2ban

sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

sudo systemctl enable --now nginx
sudo systemctl enable --now fail2ban
sudo systemctl enable --now certbot.timer

mkdir -p /opt/nexora-ai/backups

printf '%s\n' \
  'VPS base preparada.' \
  'Dominios esperados:' \
  '  Web: https://ghostnexoraai.duckdns.org' \
  '  API: https://apighostnexoraai.duckdns.org' \
  'Instala Docker Engine oficial si aún no está instalado y continúa con docs/duckdns-vps.md.'
