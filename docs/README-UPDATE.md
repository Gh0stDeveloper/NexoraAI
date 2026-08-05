# 🔄 Actualizar Nexora AI y volver atrás

Esta guía evita perder `.env.production`, PostgreSQL, modelos Ollama, cachés Android o la keystore release.

## Antes de actualizar

```bash
cd /opt/nexora-ai/app
nexora status
git status --short
nexora backup
```

La actualización se detiene si encuentra cambios locales. No uses `git reset --hard` para ocultar cambios que necesites conservar.

Respalda periódicamente fuera de la VPS:

- `/opt/nexora-ai/backups/`
- `/opt/nexora-ai/secrets/android-release.keystore`
- `/opt/nexora-ai/secrets/android-signing.env`
- `.env.production`

## Actualización automática

```bash
nexora update
```

El comando realiza en orden:

1. Comprueba que Git esté limpio.
2. Crea un `pg_dump` comprimido si PostgreSQL está activo.
3. Guarda el commit anterior en `/opt/nexora-ai/state/previous-version`.
4. Descarga `origin/main` y solo acepta avance rápido.
5. Reconstruye la aplicación reutilizando la caché Docker.
6. Inicia servicios y elimina contenedores obsoletos.
7. Verifica Compose, web, API y sandbox si está habilitado.

## Cambios de configuración

Después de actualizar compara nuevas variables públicas:

```bash
diff -u .env.vps.example .env.production || true
```

No reemplaces `.env.production` completo. Agrega únicamente las claves nuevas y conserva contraseñas/tokens existentes.

Aplica cambios:

```bash
nexora restart
nexora verify
```

## Recompilar Android

Cuando cambie `versionCode`, `versionName`, Kotlin, Compose, C o JNI:

```bash
sudo nexora android-release
```

El compilador reutiliza la misma keystore, SDK, NDK, Gradle y dependencias. Actualiza `ANDROID_APK_URL` si cambia el nombre del APK y reinicia la app web:

```bash
nexora restart
```

## Rollback

Si la verificación falla, el comando muestra el commit anterior:

```bash
nexora rollback <sha-anterior>
```

Si omites el SHA, usa `/opt/nexora-ai/state/previous-version` cuando exista:

```bash
nexora rollback
```

El rollback modifica únicamente el checkout dedicado de Nexora AI y se niega a continuar si hay cambios locales.

Para restaurar PostgreSQL manualmente:

```bash
gunzip -c /opt/nexora-ai/backups/postgres-FECHA.sql.gz | \
  docker compose -f docker-compose.vps.yml exec -T postgres \
  psql -U nexora -d nexora_ai
```

Hazlo solo si la actualización incluyó una migración incompatible y después de confirmar el archivo correcto.

## Verificación posterior

```bash
nexora status
nexora verify
nexora logs 100
curl https://apighostnexoraai.duckdns.org/api/mobile/status
```

No elimines imágenes, volúmenes o cachés inmediatamente después de actualizar. Mantenerlos facilita rollback y evita descargas repetidas.
