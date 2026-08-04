#!/usr/bin/env bash
set -euo pipefail
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg nginx ufw fail2ban
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
mkdir -p /opt/nexora-ai/backups
printf 'VPS base preparada. Instala Docker Engine oficial si aún no está instalado.\n'
