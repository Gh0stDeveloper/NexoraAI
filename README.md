# NexoraAI

Plataforma base de IA local entrenable para programación, ciberseguridad defensiva, agentes de datos, API web/móvil y despliegue en VPS propia.

## Producción

| Servicio | URL |
|---|---|
| Panel público | `https://ghostnexoraai.duckdns.org` |
| API web y Android | `https://apighostnexoraai.duckdns.org` |

Los dos dominios DuckDNS están configurados como destinos oficiales del proyecto y apuntan a la VPS del propietario.

## Módulos

- Next.js API/panel web.
- App Android Kotlin/Compose que consume la API.
- Docker Compose para VPS con app, Ollama y PostgreSQL.
- Nginx con separación entre dominio público y dominio API.
- GitHub Actions para validar web/API, Docker/VPS, dataset y Android.
- Documentación legible desde `/docs`.

## Local

```bash
npm install
npm run dev
```

## VPS

```bash
cp .env.vps.example .env.production
bash deploy/scripts/bootstrap-vps.sh
docker compose -f docker-compose.vps.yml up -d --build
```

Después instala la configuración Nginx y solicita TLS con Certbot siguiendo:

```txt
docs/duckdns-vps.md
```

Verificación completa:

```bash
VERIFY_PUBLIC_DOMAINS=true bash deploy/scripts/verify-vps.sh
```

## Android release

La versión `0.4.1-duckdns-production` usa como API de producción:

```txt
https://apighostnexoraai.duckdns.org/
```

La keystore no se almacena en el repositorio. El propietario agregará manualmente estos secretos de GitHub Actions:

```txt
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

## Validación CI

Después de cada push o PR se ejecutan workflows separados para validar web/API, Docker/VPS, dataset de entrenamiento y compilación Android debug. El release firmado solo se activa cuando existen los cuatro secretos Android.
