# 🔄 Actualizar Nexora AI y volver atrás

Esta guía evita perder `.env.production`, PostgreSQL, modelos Ollama, cachés Android o la keystore release.

## Antes de actualizar

```bash
cd /opt/NexoraAI
nexora status
git status --short
nexora backup
```

La actualización se detiene si encuentra cambios locales. No uses `git reset --hard` para ocultar cambios que necesites conservar.

Respalda periódicamente fuera de la VPS:

- `/opt/nexora-ai/backups/`
- `/opt/nexora-ai/secrets/android-release.keystore`
- `/opt/nexora-ai/secrets/android-signing.env`
- `/opt/nexora-ai/secrets/user-builds/`
- `.env.production`

## Actualización automática

```bash
nexora update
```

El comando realiza en orden:

1. Comprueba que Git esté limpio.
2. Adquiere un bloqueo para impedir actualizaciones, rollback o compilaciones simultáneas.
3. Descarga la referencia objetivo y no reinicia nada si ya está instalada.
4. Crea un `pg_dump` comprimido si PostgreSQL está activo.
5. Guarda el commit anterior en `/opt/nexora-ai/state/previous-version`.
6. Solo acepta un avance rápido y reconstruye reutilizando la caché Docker.
7. Espera el healthcheck y reintenta web, API y sandbox ante fallos transitorios.
8. Si falla build, arranque o verificación, restaura automáticamente el commit anterior.

Por defecto sigue `origin/main`. También acepta una referencia Git explícita:

```bash
nexora update origin/main
```

El tiempo de arranque predeterminado es 180 segundos. En una VPS especialmente lenta puede
ampliarse solo para esa ejecución:

```bash
NEXORA_VERIFY_TIMEOUT_SECONDS=300 nexora update
```

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

## Migración única de Nginx para 0.6

La ruta temporal excluye su token del access log y el alias `NexoraAI-latest.apk` desactiva la
caché. En una VPS que viene de 0.5, instala una sola vez la configuración nueva conservando un
respaldo del sitio activo:

```bash
ls -l /etc/nginx/sites-enabled/
sudo cp -a /etc/nginx/sites-available/ghost-nexora-ai \
  /etc/nginx/sites-available/ghost-nexora-ai.pre-v060
sudo cp deploy/nginx/nexoraia-vps.conf \
  /etc/nginx/sites-available/ghost-nexora-ai
sudo nginx -t
sudo certbot --nginx --reinstall --redirect --non-interactive --agree-tos \
  --email ghostnexora@gmail.com \
  -d ghostnexoraai.duckdns.org \
  -d apighostnexoraai.duckdns.org
sudo nginx -t
sudo systemctl reload nginx
```

Si el enlace activo usa otro nombre, sustituye `ghost-nexora-ai` por ese archivo. Si alguna
validación falla, restaura `.pre-v060` antes de recargar Nginx.

## Recompilar Android

Cuando cambie `versionCode`, `versionName`, Kotlin, Compose, C o JNI:

```bash
sudo nexora android-release
```

El compilador reutiliza la misma keystore, SDK, NDK, Gradle y dependencias.
El build 0.6.0 publica de forma atómica el APK versionado, `NexoraAI-latest.apk`, su SHA-256 y
`latest.json`. La API lee el manifiesto montado en solo lectura, por lo que no debes editar
`APP_VERSION`, `ANDROID_APK_URL` ni reiniciar contenedores después de compilar.

## Rollback

Si una actualización falla, el rollback se ejecuta automáticamente y el comando termina con
error para dejar constancia de que no se instaló la versión nueva. El rollback manual sigue
disponible:

```bash
nexora rollback <sha-anterior>
```

Si omites el SHA, usa `/opt/nexora-ai/state/previous-version` cuando exista:

```bash
nexora rollback
```

El rollback modifica únicamente el checkout dedicado de Nexora AI y se niega a continuar si hay cambios locales.

Los respaldos PostgreSQL con más de 30 días se eliminan después de crear uno nuevo. Para
cambiar la retención usa `NEXORA_BACKUP_RETENTION_DAYS`; el valor `0` desactiva esa limpieza.

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
