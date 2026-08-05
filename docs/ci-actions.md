# ✅ GitHub Actions y validación continua

| Workflow | Valida |
|---|---|
| `web-api-ci.yml` | Preflight, estructura, dataset, secretos, TypeScript, build Next.js y APK debug |
| `android-ci.yml` | APK debug, release firmado condicional, NDK y cuatro ABI |
| `docker-vps-ci.yml` | Bash, Compose, Nginx, imagen web y runner sandbox |
| `platform-compatibility.yml` | Ubuntu/Debian, AMD64/ARM64 y plataformas de imágenes |
| `training-ci.yml` | Sintaxis y contenido del dataset JSONL |

## Android firmado

Secrets requeridos:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Si faltan, el job release se omite y el debug sigue validándose. GitHub Actions nunca debe crear una keystore nueva en cada ejecución.

## Gates recomendados para `main`

- Web API CI / Build and validate Next.js API
- Android CI / Compile debug APK
- Docker VPS CI / Validate Docker, Nginx and VPS scripts
- Linux Compatibility CI
- Training Dataset CI cuando cambie `training/`

El APK debe contener `libnexora.so` en `armeabi-v7a`, `arm64-v8a`, `x86` y `x86_64`.
