# CI, Android y validación VPS

Esta guía explica cómo se valida NexoraAI antes de llevarlo a producción en una VPS propia.

## Dominios oficiales

```txt
Web: https://ghostnexoraai.duckdns.org
API: https://apighostnexoraai.duckdns.org
```

La configuración detallada de Nginx, Certbot y Docker está en `docs/duckdns-vps.md`.

## Objetivo

La rama principal debe quedar protegida por pruebas automáticas que validen:

- Panel web y API Next.js.
- Cliente web `/chat` consumiendo `/api/chat`.
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
| Android CI | `.github/workflows/android-ci.yml` | Compila APK debug siempre que cambie Android y compila release firmado solo si existen secretos. |

## Correcciones de CI aplicadas

### `setup-node` sin lockfile

El primer fallo de Web API CI se produjo porque `actions/setup-node` usaba `cache: npm` sin `package-lock.json`. Se quitó `cache: npm` hasta que exista un lockfile comprometido en el repositorio.

### Docker Compose sin `.env.production`

`docker-compose.vps.yml` espera `.env.production`. En CI se genera a partir de la plantilla pública:

```bash
cp .env.vps.example .env.production
```

La plantilla contiene únicamente valores no sensibles y marcadores que deben cambiarse en producción.

## Android debug y release

La app Android usa:

```txt
Debug:   http://10.0.2.2:3000/
Release: https://apighostnexoraai.duckdns.org/
```

La URL release se almacena ofuscada dentro de la biblioteca JNI `libnexora_config.so`. La versión actual es:

```txt
versionCode: 5
versionName: 0.4.1-duckdns-production
```

### APK debug

El APK debug se compila en pull requests y pushes que modifiquen Android.

Artifact:

```txt
nexora-ai-debug-apk
```

### APK release firmado

El APK release solo se compila en `push` o `workflow_dispatch`, nunca en pull requests, y únicamente cuando existen estos secretos:

```txt
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

El propietario agregará manualmente estos secretos en **Settings → Secrets and variables → Actions**. La keystore y sus contraseñas nunca deben guardarse en el repositorio.

Artifact cuando hay secretos:

```txt
nexora-ai-release-apk
```

Si los secretos no existen, el job release no falla: se omite el build firmado.

## Preflight

El script `scripts/ci-preflight.mjs` valida archivos críticos, dominios de producción, versión Android, configuración Nginx y secretos esperados antes de ejecutar builds costosos.

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

En la VPS, después de configurar HTTPS:

```bash
VERIFY_PUBLIC_DOMAINS=true bash deploy/scripts/verify-vps.sh
```

## Política para secretos

Nunca subas keystore, `.env.production` real, contraseñas, copias de PostgreSQL ni claves privadas al repositorio. La firma release debe entrar únicamente por GitHub Secrets y existir temporalmente dentro del runner.
