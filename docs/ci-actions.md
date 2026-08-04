# CI, Android y validación VPS

Esta guía explica cómo se valida NexoraAI antes de llevarlo a producción en una VPS propia.

## Objetivo

La rama principal debe quedar protegida por pruebas automáticas que validen:

- Panel web y API Next.js.
- Configuración Docker para VPS.
- Scripts Bash de operación.
- Dataset mínimo de entrenamiento.
- Compilación de la app Android.
- Configuración release/debug de la app móvil.

## Workflows

| Workflow | Archivo | Objetivo |
|---|---|---|
| Web API CI | `.github/workflows/web-api-ci.yml` | Instala dependencias, corre preflight, valida estructura, dataset, seguridad, TypeScript y build. |
| Docker VPS CI | `.github/workflows/docker-vps-ci.yml` | Crea `.env.production` temporal, valida scripts Bash, Docker Compose y build Docker. |
| Training Dataset CI | `.github/workflows/training-ci.yml` | Compila el validador Python y valida el JSONL de entrenamiento. |
| Android CI | `.github/workflows/android-ci.yml` | Compila APK debug y sube artifact cuando cambie `apps/android/**`. |

## Correcciones aplicadas

### `setup-node` sin lockfile

El primer fallo de Web API CI se produjo porque `actions/setup-node` usaba `cache: npm` sin `package-lock.json`.

Solución aplicada:

```yaml
with:
  node-version: 22
```

Se quitó `cache: npm` hasta que exista un lockfile comprometido en el repo.

### Docker Compose sin `.env.production`

El primer fallo de Docker VPS CI se produjo porque `docker-compose.vps.yml` espera `.env.production`.

Solución aplicada:

```bash
cp .env.vps.example .env.production
```

Esto permite validar Compose en CI sin usar secretos reales.

## Preflight

El script `scripts/ci-preflight.mjs` valida archivos críticos antes de ejecutar builds costosos.

Comando local:

```bash
npm run ci:preflight
```

## Validación local recomendada

```bash
npm install
npm run ci:preflight
npm run validate:repo
npm run dataset:validate
npm run security:check
npm run typecheck
npm run build
cp .env.vps.example .env.production
docker compose -f docker-compose.vps.yml config
```

## Android

La app Android usa:

```txt
Debug:   http://10.0.2.2:3000/
Release: https://api.nexoraia.com/
```

El dominio no necesita existir para compilar. Solo será necesario cuando se pruebe la app contra producción real.
