# NexoraAI

Plataforma base de IA local entrenable para programación, ciberseguridad defensiva, agentes de datos, API web/móvil y despliegue en VPS propia.

## Módulos

- Next.js API/panel web.
- App Android Kotlin/Compose que consume la API.
- Docker Compose para VPS con app, Ollama y PostgreSQL.
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

Los dominios `nexoraia.com` y `api.nexoraia.com` pueden configurarse después; el CI no depende de que ya existan.
