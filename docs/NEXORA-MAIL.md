# Nexora Mail — correo transaccional autohospedado

Nexora AI 0.9.0 incluye **Nexora Mail**, un servicio de correo de salida ejecutado dentro de la misma VPS y de la misma red privada de Docker que la API.

Su objetivo es enviar únicamente mensajes transaccionales de Nexora AI:

- verificación de correo;
- recuperación de contraseña;
- pruebas operativas y futuros avisos de seguridad.

No es un servidor de buzones. No publica IMAP, POP3, SMTP AUTH ni el puerto 25 de entrada.

## Arquitectura

```text
Android
   |
   | HTTPS
   v
Nexora API
   |
   | http://mailer:8025/send (red Docker privada)
   v
Nexora Mail Gateway
   |
   v
Postfix -> OpenDKIM -> Internet
```

El gateway HTTP exige `Authorization: Bearer ...`, limita el tamaño de los mensajes y solo acepta tipos `nexora.*`. Postfix escucha únicamente en loopback dentro del contenedor. OpenDKIM firma el correo antes de la entrega.

## Actualización desde una instalación existente

La instalación que ya está ejecutando `main` no necesita reinstalarse.

Cuando 0.9.0 llegue a `main`:

```bash
sudo nexora update
```

El flujo mantiene el contrato existente:

1. comprueba que el árbol Git esté limpio;
2. descarga `origin/main`;
3. exige un avance `fast-forward`;
4. crea un respaldo PostgreSQL;
5. cambia al nuevo commit;
6. construye Nexora Mail y la aplicación;
7. levanta los contenedores conservando los volúmenes existentes;
8. espera el healthcheck de Nexora Mail;
9. verifica la API y los servicios opcionales;
10. si algo falla, vuelve automáticamente al commit anterior.

El volumen PostgreSQL no se recrea. Las migraciones de autenticación son aditivas (`CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS`). El volumen `mailer-keys` conserva la clave DKIM de Nexora Mail entre actualizaciones.

### Compatibilidad de `.env.production`

Una instalación anterior no contiene todavía `MAIL_DOMAIN`, `AUTH_CODE_PEPPER` ni `AUTH_EMAIL_WEBHOOK_SECRET`. Eso no bloquea la primera actualización:

- `MAIL_DOMAIN` cae en `PUBLIC_DOMAIN`;
- `MAIL_FROM` cae en `noreply@MAIL_DOMAIN`;
- `AUTH_CODE_PEPPER` se deriva de forma separada a partir del secreto PostgreSQL existente;
- `AUTH_EMAIL_WEBHOOK_SECRET` se deriva de forma separada a partir del secreto PostgreSQL existente.

Estos valores derivados permiten una migración sin intervención. Para una instalación definitiva se recomienda configurar después secretos independientes de 32 bytes:

```bash
openssl rand -hex 32
```

Y guardarlos solo en `.env.production`:

```dotenv
AUTH_CODE_PEPPER=<64 caracteres hex>
AUTH_EMAIL_WEBHOOK_SECRET=<64 caracteres hex>
```

Después:

```bash
sudo nexora restart
sudo nexora verify
```

## Configuración de remitente

Ejemplo:

```dotenv
MAIL_DOMAIN=example.com
MAIL_HOSTNAME=mail.example.com
MAIL_FROM=noreply@example.com
MAIL_DKIM_SELECTOR=nexora
```

Para una entrega fiable conviene usar un dominio donde puedas administrar DNS y configurar PTR/rDNS con tu proveedor de VPS.

## DNS y reputación

Después de iniciar Nexora Mail ejecuta:

```bash
sudo nexora mail-dns
```

El comando imprime los valores que debes publicar para:

- **A/AAAA** del hostname de correo;
- **PTR/rDNS** de la IP pública;
- **SPF**;
- **DKIM**, leyendo la clave pública generada realmente por OpenDKIM;
- **DMARC**.

La clave privada DKIM nunca se imprime y permanece en el volumen Docker `mailer-keys`.

### Requisitos externos inevitables

Autohospedar el software no elimina los requisitos de Internet para entregar email. Para que Gmail, Outlook y otros servidores acepten el correo normalmente necesitas:

1. TCP/25 **de salida** permitido por el proveedor de la VPS;
2. una IP pública con reputación razonable;
3. PTR/rDNS que apunte a `MAIL_HOSTNAME`;
4. SPF correcto;
5. DKIM correcto;
6. DMARC correcto.

Nexora no necesita abrir TCP/25 de entrada.

## Prueba de entrega

```bash
sudo nexora mail-test usuario@example.com
```

El comando envía un mensaje usando el mismo gateway autenticado, Postfix y DKIM de producción.

Si el gateway acepta el mensaje pero no llega al destino:

```bash
sudo nexora logs 300
```

O específicamente:

```bash
docker compose --env-file .env.production -f docker-compose.vps.yml logs --tail=200 mailer
```

Busca errores de conexión TCP/25, rechazo por reputación, SPF/DKIM o restricciones del proveedor de VPS.

## Seguridad

- El puerto 8025 usa `expose`, no `ports`; no queda publicado en la interfaz de la VPS.
- Postfix usa `inet_interfaces = loopback-only` dentro del contenedor.
- No se habilita relay público.
- El gateway compara el secreto con `hmac.compare_digest`.
- Los OTP continúan guardándose únicamente como HMAC SHA-256 en PostgreSQL.
- La API nunca entrega secretos de Nexora Mail al APK.
- Android solo habla con la API pública mediante HTTPS.

## Rollback

`nexora rollback` sigue siendo compatible con versiones anteriores a Nexora Mail. El CLI comprueba si el `docker-compose` de la versión objetivo contiene el servicio `mailer` antes de intentar construirlo. Al volver a una versión antigua, Docker elimina el contenedor huérfano, pero conserva el volumen de claves para una actualización futura.
