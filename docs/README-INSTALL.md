# 🚀 Instalación de Nexora AI desde cero

Esta guía instala web, API, Ollama, PostgreSQL, Nginx, TLS y el cliente Android en una VPS nueva. Los ejemplos usan los dominios actuales:

```text
Web: https://ghostnexoraai.duckdns.org
API: https://apighostnexoraai.duckdns.org
```

## 1. Requisitos previos

- Ubuntu Server 22.04/24.04/26.04 o Debian 11/12/13.
- Arquitectura AMD64 o ARM64.
- Acceso `sudo` y SSH.
- Puertos TCP 22, 80 y 443 permitidos en el firewall de la VPS y del proveedor.
- Registros DNS de ambos dominios apuntando a la IP pública.
- Recomendado para el modelo predeterminado: 8 vCPU, 16 GB RAM y 80 GB SSD.

> No cambies el puerto 22 ni detengas `sshd` durante la instalación. Nexora usa Nginx en 80/443 y la aplicación escucha únicamente en `127.0.0.1:3000`.

## 2. Preparar DNS

Configura estos registros con la misma IP pública:

| Host | Tipo | Valor |
|---|---|---|
| `ghostnexoraai.duckdns.org` | A/AAAA | IP de la VPS |
| `apighostnexoraai.duckdns.org` | A/AAAA | IP de la VPS |

Comprueba desde tu equipo:

```bash
nslookup ghostnexoraai.duckdns.org
nslookup apighostnexoraai.duckdns.org
```

## 3. Clonar el proyecto

```bash
sudo apt-get update
sudo apt-get install -y git
sudo mkdir -p /opt/nexora-ai
sudo git clone https://github.com/Gh0stDeveloper/NexoraAI.git /opt/nexora-ai/app
sudo chown -R "$USER":"$USER" /opt/nexora-ai/app
cd /opt/nexora-ai/app
```

## 4. Preparar la VPS

```bash
bash deploy/scripts/bootstrap-vps.sh
```

El script:

- valida distribución y arquitectura;
- instala Docker, Compose, Nginx, Certbot, UFW y Fail2ban;
- conserva el acceso SSH y abre 80/443;
- crea directorios de respaldos, caché, APK y secretos;
- genera `.env.production` con contraseñas aleatorias si no existe;
- instala el comando `/usr/local/bin/nexora`.

Cierra la sesión SSH y vuelve a entrar para aplicar la pertenencia al grupo `docker`.

## 5. Revisar la configuración

```bash
cd /opt/nexora-ai/app
nano .env.production
```

Revisa como mínimo:

```env
PUBLIC_DOMAIN=ghostnexoraai.duckdns.org
API_DOMAIN=apighostnexoraai.duckdns.org
NEXT_PUBLIC_SITE_URL=https://ghostnexoraai.duckdns.org
NEXT_PUBLIC_API_URL=https://apighostnexoraai.duckdns.org
MOBILE_PRODUCTION_API_URL=https://apighostnexoraai.duckdns.org/
ANDROID_APK_URL=https://ghostnexoraai.duckdns.org/downloads/NexoraAI-0.5.0.apk
ALLOW_CODE_EXECUTION=false
RATE_LIMIT_PER_MINUTE=80
```

No subas `.env.production` a GitHub.

## 6. Iniciar servicios

```bash
nexora install
nexora status
```

La primera construcción descarga imágenes y dependencias. Las actualizaciones posteriores reutilizan las capas y cachés.

## 7. Instalar modelos Ollama

Modelo de programación recomendado:

```bash
cd /opt/nexora-ai/app
docker compose -f docker-compose.vps.yml exec ollama \
  ollama pull qwen2.5-coder:7b
```

Modelo de visión opcional:

```bash
docker compose -f docker-compose.vps.yml exec ollama \
  ollama pull gemma3:4b
```

Comprueba los modelos:

```bash
docker compose -f docker-compose.vps.yml exec ollama ollama list
```

Si la VPS tiene 8 GB o menos, usa un modelo menor y mantén `OLLAMA_MULTI_AGENT_PARALLEL=false`.

## 8. Configurar Nginx

```bash
sudo cp deploy/nginx/nexoraia-vps.conf /etc/nginx/sites-available/nexora-ai.conf
sudo ln -sfn /etc/nginx/sites-available/nexora-ai.conf /etc/nginx/sites-enabled/nexora-ai.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

Prueba HTTP antes de solicitar TLS:

```bash
curl -I http://ghostnexoraai.duckdns.org
curl http://apighostnexoraai.duckdns.org/api/health
```

## 9. Activar HTTPS

```bash
sudo certbot --nginx \
  -d ghostnexoraai.duckdns.org \
  -d apighostnexoraai.duckdns.org
```

Elige redirección automática de HTTP a HTTPS. Verifica renovación:

```bash
sudo certbot renew --dry-run
systemctl status certbot.timer --no-pager
```

## 10. Compilar y publicar Android

En VPS Linux AMD64:

```bash
sudo nexora android-release
```

El APK quedará en:

```text
/opt/nexora-ai/releases/NexoraAI-0.5.0.apk
```

Nginx lo sirve en:

```text
https://ghostnexoraai.duckdns.org/downloads/NexoraAI-0.5.0.apk
```

Respalda inmediatamente:

```text
/opt/nexora-ai/secrets/android-release.keystore
/opt/nexora-ai/secrets/android-signing.env
```

No compartas `android-signing.env` ni lo copies al repositorio.

## 11. Verificación final

```bash
cd /opt/nexora-ai/app
VERIFY_PUBLIC_DOMAINS=true nexora verify
curl https://apighostnexoraai.duckdns.org/api/mobile/status
curl -I https://ghostnexoraai.duckdns.org/downloads/NexoraAI-0.5.0.apk
```

Comprueba además desde Android:

1. Abre un chat.
2. Envía una solicitud instantánea.
3. Confirma que aparecen etapas y cronómetro.
4. Crea un proyecto y un chat dentro.
5. Fija el proyecto y el chat.
6. Cierra y abre la app para comprobar persistencia.

## 12. Operación diaria

```bash
nexora status
nexora logs 200
nexora backup
nexora update
```

Consulta [Actualización](README-UPDATE.md) y [Solución de problemas](TROUBLESHOOTING.md) antes de intervenir producción.
