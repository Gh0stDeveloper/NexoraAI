# Producción con DuckDNS en la VPS

Esta guía configura los dominios definitivos de Ghost Nexora AI:

| Servicio | Dominio |
|---|---|
| Panel web público | `ghostnexoraai.duckdns.org` |
| API web y Android | `apighostnexoraai.duckdns.org` |

Ambos registros deben apuntar a la IP pública de la VPS antes de solicitar los certificados TLS. El usuario confirmó que esta parte ya está realizada.

## 1. Preparar el entorno

Desde la raíz del repositorio en la VPS:

```bash
cp .env.vps.example .env.production
nano .env.production
```

Cambia como mínimo:

```env
POSTGRES_PASSWORD=UNA_CONTRASENA_LARGA_Y_UNICA
DATABASE_URL=postgresql://nexora:UNA_CONTRASENA_LARGA_Y_UNICA@postgres:5432/nexora_ai
```

No cambies estas URL de producción:

```env
NEXT_PUBLIC_SITE_URL=https://ghostnexoraai.duckdns.org
NEXT_PUBLIC_API_URL=https://apighostnexoraai.duckdns.org
MOBILE_PRODUCTION_API_URL=https://apighostnexoraai.duckdns.org/
```

## 2. Preparar la VPS

```bash
bash deploy/scripts/bootstrap-vps.sh
```

El script instala y activa Nginx, Certbot, UFW y Fail2ban. Docker Engine debe instalarse desde el repositorio oficial de Docker si todavía no existe en la VPS.

## 3. Levantar Nexora AI

```bash
docker compose -f docker-compose.vps.yml up -d --build
docker compose -f docker-compose.vps.yml ps
```

La aplicación y Ollama solo se publican en `127.0.0.1`; el acceso externo entra exclusivamente por Nginx.

## 4. Instalar la configuración Nginx

```bash
sudo cp deploy/nginx/nexoraia-vps.conf /etc/nginx/sites-available/ghost-nexora-ai
sudo ln -sfn /etc/nginx/sites-available/ghost-nexora-ai /etc/nginx/sites-enabled/ghost-nexora-ai
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

La configuración separa los hosts:

- `ghostnexoraai.duckdns.org` sirve el panel y la aplicación web.
- `apighostnexoraai.duckdns.org` expone `/api/*` y `/openapi.json`.
- CORS de la API solo admite el dominio público de Ghost Nexora AI.
- Los timeouts permiten respuestas prolongadas de la orquestación multiagente.

## 5. Activar HTTPS automático

```bash
sudo certbot --nginx \
  --non-interactive \
  --agree-tos \
  --redirect \
  --email ghostnexora@gmail.com \
  -d ghostnexoraai.duckdns.org \
  -d apighostnexoraai.duckdns.org
```

Comprobar la renovación automática:

```bash
sudo systemctl status certbot.timer
sudo certbot renew --dry-run
```

Certbot modifica la configuración activa de Nginx para añadir los bloques TLS y la redirección permanente de HTTP a HTTPS.

## 6. Verificar producción

```bash
curl -fsS https://ghostnexoraai.duckdns.org/
curl -fsS https://apighostnexoraai.duckdns.org/
curl -fsS https://apighostnexoraai.duckdns.org/api/health
VERIFY_PUBLIC_DOMAINS=true bash deploy/scripts/verify-vps.sh
```

El dominio raíz de la API debe responder un JSON de identificación. Las rutas no pertenecientes a `/api/*` ni a `/openapi.json` devuelven `404` en el host de API.

## 7. Firma Android mediante GitHub Actions

La keystore nunca debe guardarse en el repositorio. Agrega manualmente estos secretos en **Settings → Secrets and variables → Actions**:

```txt
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

El workflow reconstruye temporalmente la keystore en el runner y ejecuta `assembleRelease`. Cuando los cuatro secretos no existen, el job release se omite sin hacer fallar CI.

La versión Android `0.6.0` utiliza como API oficial:

```txt
https://apighostnexoraai.duckdns.org/
```

La compilación debug conserva `http://10.0.2.2:3000/` para pruebas con emulador y no utiliza el servidor de producción. Ninguna técnica dentro de un APK convierte un endpoint público en un secreto.

## 8. Actualizaciones posteriores

```bash
nexora update
nexora verify
```

El comando crea un respaldo y conserva las cachés. Lee `docs/README-UPDATE.md` antes de actualizar producción. No subas `.env.production`, copias de la base de datos ni archivos de firma a GitHub.
