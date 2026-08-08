# Actualizar Nexora AI y volver atrás

Esta guía evita perder `.env.production`, PostgreSQL, modelos Ollama, cachés Android, chats, claves DKIM o la keystore release.

## Actualización normal

La instalación existente en la VPS no se reinstala. Cuando una nueva versión validada llega a `main`, el flujo normal sigue siendo:

```bash
cd /opt/NexoraAI
sudo nexora update
```

El comando realiza en orden:

1. Comprueba que Git esté limpio.
2. Adquiere un bloqueo para impedir actualizaciones, rollback o compilaciones simultáneas.
3. Descarga `origin/main` y no reinicia nada si ya está instalada esa revisión.
4. Crea un `pg_dump` comprimido si PostgreSQL está activo.
5. Guarda el commit anterior en `/opt/nexora-ai/state/previous-version`.
6. Solo acepta un avance `fast-forward`.
7. Construye los servicios que existan en la nueva revisión.
8. Conserva los volúmenes PostgreSQL, Ollama, releases, cachés, keystores y claves persistentes.
9. Espera healthchecks y verifica API, servicios auxiliares y Nexora Mail cuando la revisión lo contiene.
10. Si falla build, arranque o verificación, vuelve automáticamente al commit anterior y levanta esa revisión.
11. Actualiza `/usr/local/bin/nexora` únicamente después de un despliegue correcto.

Por defecto sigue `origin/main`. También acepta una referencia Git explícita:

```bash
sudo nexora update origin/main
```

## Migración desde la versión actualmente instalada en `main` a 0.9+

No necesitas borrar contenedores, reinstalar PostgreSQL ni copiar una `.env.production` nueva.

Nexora 0.9 incorpora de forma aditiva:

- cuentas y sesiones ampliadas;
- vinculación social explícita;
- variantes/ramas de chat en Android;
- Nexora Mail dentro del mismo Docker Compose;
- un volumen `mailer-keys` para conservar la clave DKIM.

La base de datos se amplía con `CREATE TABLE IF NOT EXISTS` y `ADD COLUMN IF NOT EXISTS`. El volumen PostgreSQL existente no se sustituye.

Una `.env.production` de una versión anterior puede no contener todavía las variables de correo. La primera actualización sigue arrancando porque Nexora Mail usa valores compatibles derivados de la configuración ya existente. Después puedes endurecer la configuración con secretos dedicados sin reinstalar nada.

La versión pública de la API se obtiene de la release instalada (`package.json`) y no queda atrapada en un `APP_VERSION` antiguo de `.env.production`.

## Nexora Mail después de actualizar

El servicio se construye y valida automáticamente durante `nexora update`.

Comprueba su salud:

```bash
sudo nexora verify
```

Obtén los registros DNS recomendados:

```bash
sudo nexora mail-dns
```

Prueba una entrega real:

```bash
sudo nexora mail-test usuario@example.com
```

Nexora Mail no publica SMTP, IMAP ni POP3 hacia Internet. El gateway HTTP solo está disponible dentro de la red Docker. Para entregar correo a Gmail/Outlook u otros dominios sí son necesarios requisitos externos de Internet: TCP/25 de salida permitido por la VPS, PTR/rDNS y DNS SPF/DKIM/DMARC correctos. Consulta `docs/NEXORA-MAIL.md`.

## Antes de actualizar

El propio comando crea el respaldo de PostgreSQL, pero puedes ejecutar comprobaciones adicionales:

```bash
cd /opt/NexoraAI
sudo nexora status
git status --short
sudo nexora backup
```

La actualización se detiene si encuentra cambios locales. No uses `git reset --hard` para ocultar cambios que necesites conservar.

Respalda periódicamente fuera de la VPS:

- `/opt/nexora-ai/backups/`
- `/opt/nexora-ai/secrets/android-release.keystore`
- `/opt/nexora-ai/secrets/android-signing.env`
- `/opt/nexora-ai/secrets/user-builds/`
- `.env.production`

Los volúmenes Docker también deben formar parte de una estrategia externa de respaldo si el servicio ya contiene usuarios reales.

## VPS lenta

El tiempo de arranque predeterminado puede ampliarse solo para una ejecución:

```bash
NEXORA_VERIFY_TIMEOUT_SECONDS=300 sudo nexora update
```

## Configuración persistente

No reemplaces `.env.production` completo con `.env.vps.example`; perderías secretos reales. Usa el ejemplo únicamente como referencia.

Para Nexora Mail 0.9 puedes configurar posteriormente secretos independientes:

```bash
openssl rand -hex 32
openssl rand -hex 32
```

Y guardarlos en `.env.production` como:

```dotenv
AUTH_CODE_PEPPER=<primer secreto>
AUTH_EMAIL_WEBHOOK_SECRET=<segundo secreto>
```

También puedes elegir un dominio/hostname de correo propio:

```dotenv
MAIL_DOMAIN=example.com
MAIL_HOSTNAME=mail.example.com
MAIL_FROM=noreply@example.com
MAIL_DKIM_SELECTOR=nexora
```

Después:

```bash
sudo nexora restart
sudo nexora verify
sudo nexora mail-dns
```

## Recompilar Android

Cuando cambien `versionCode`, `versionName`, Kotlin, Compose, C o JNI:

```bash
sudo nexora android-release
```

El compilador reutiliza la misma keystore, SDK, NDK, Gradle y dependencias. Publica de forma atómica el APK versionado, `NexoraAI-latest.apk`, su SHA-256 y `latest.json`. No es necesario editar `APP_VERSION` después de compilar.

## Rollback

Si una actualización falla, el rollback se ejecuta automáticamente y el comando termina con error para dejar constancia de que la versión nueva no quedó instalada.

Rollback manual:

```bash
sudo nexora rollback <sha-anterior>
```

Si omites el SHA usa `/opt/nexora-ai/state/previous-version` cuando exista:

```bash
sudo nexora rollback
```

El CLI 0.9 comprueba qué servicios existen en la revisión objetivo. Por eso puede volver a una versión anterior que todavía no contenía `mailer`: no intenta construir un servicio inexistente. Docker retira el contenedor huérfano, mientras el volumen `mailer-keys` puede conservarse para una futura actualización.

Los respaldos PostgreSQL con más de 30 días se eliminan después de crear uno nuevo. `NEXORA_BACKUP_RETENTION_DAYS=0` desactiva esa limpieza.

Para restaurar PostgreSQL manualmente:

```bash
gunzip -c /opt/nexora-ai/backups/postgres-FECHA.sql.gz | \
  docker compose --env-file .env.production -f docker-compose.vps.yml exec -T postgres \
  psql -U nexora -d nexora_ai
```

Hazlo solo después de confirmar que realmente es necesario; las migraciones de 0.9 están diseñadas para ser aditivas.

## Verificación posterior

```bash
sudo nexora status
sudo nexora verify
sudo nexora logs 100
curl https://apighostnexoraai.duckdns.org/api/mobile/status
```

No elimines imágenes, volúmenes o cachés inmediatamente después de actualizar. Mantenerlos facilita rollback y evita descargas repetidas.
