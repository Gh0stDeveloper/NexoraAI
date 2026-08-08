# Autenticación Android, cuentas y sincronización

Nexora AI 0.8.0 usa identidad de usuario autohospedada sobre la API y PostgreSQL de la VPS. La aplicación Android permite iniciar sesión con Google, Facebook, Discord o correo electrónico; sincroniza chats/proyectos y añade un Centro de cuenta para perfil, verificación de correo y administración de sesiones.

## Arquitectura

- Android abre los proveedores sociales en el navegador del sistema.
- El flujo móvil utiliza OAuth 2.0 con `state` y PKCE (`S256`).
- Google, Facebook y Discord se usan únicamente como proveedores de identidad.
- Los `client_secret` viven exclusivamente en la VPS.
- La API crea sesiones propias de Nexora AI.
- El access token dura 1 hora.
- El refresh token dura 30 días y se rota al renovarse.
- En PostgreSQL solo se guardan hashes SHA-256 de los tokens de sesión.
- Las contraseñas se derivan mediante `scrypt` con salt aleatorio.
- Los códigos de verificación/recuperación tienen 6 dígitos, duran 10 minutos y permiten como máximo 5 intentos.
- PostgreSQL guarda únicamente un HMAC SHA-256 del código OTP; el código en claro solo existe mientras se entrega al servicio de correo.
- En Android, la sesión y el estado OAuth temporal se cifran con AES-GCM usando una clave del Android Keystore.
- El historial mantiene copia local/offline y se sincroniza con `mobile_user_chat_state` cuando existe una sesión válida.
- Cada sesión registra un nombre de dispositivo enviado por Android mediante `X-Nexora-Device` y puede revocarse desde el Centro de cuenta.

## Deep link de Android

La aplicación recibe el resultado OAuth mediante:

```text
nexoraai://auth/callback
```

El `AndroidManifest.xml` registra el deep link como `BROWSABLE` y la actividad usa `singleTop`, de modo que volver del navegador no crea una segunda sesión visual ni descarta el chat actual.

## Variables de entorno

Añade los valores reales en `.env.production` de la VPS:

```dotenv
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
FACEBOOK_CLIENT_ID=
FACEBOOK_CLIENT_SECRET=
DISCORD_CLIENT_ID=
DISCORD_CLIENT_SECRET=

AUTH_CODE_PEPPER=
AUTH_EMAIL_WEBHOOK_URL=
AUTH_EMAIL_WEBHOOK_SECRET=
```

Genera `AUTH_CODE_PEPPER` con un secreto independiente, por ejemplo:

```bash
openssl rand -hex 32
```

No reutilices la contraseña de PostgreSQL, los secretos OAuth, el token del sandbox ni claves de firma Android.

Nunca publiques estos secretos en GitHub ni los incluyas en `build.gradle.kts`, recursos Android o `BuildConfig`.

## Google

Crea/configura una aplicación OAuth para la API pública y registra:

```text
https://apighostnexoraai.duckdns.org/api/auth/mobile/callback/google
```

Nexora solicita `openid email profile` y usa el endpoint OIDC de Google para obtener la identidad.

## Facebook

Configura Facebook Login en la aplicación de Meta y registra:

```text
https://apighostnexoraai.duckdns.org/api/auth/mobile/callback/facebook
```

Nexora solicita `email public_profile`.

## Discord

Crea una aplicación en Discord Developer Portal, habilita OAuth2 y registra:

```text
https://apighostnexoraai.duckdns.org/api/auth/mobile/callback/discord
```

Nexora solicita `identify email`.

## Correo, verificación y recuperación

Nexora 0.8.0 incorpora:

- registro e inicio de sesión con correo/contraseña;
- verificación de correo desde el Centro de cuenta;
- recuperación de contraseña desde `Olvidé mi contraseña`;
- OTP de un solo uso con caducidad y límite de intentos;
- respuesta indistinguible al solicitar recuperación para evitar enumeración de cuentas;
- revocación de todas las sesiones activas después de cambiar la contraseña.

La verificación de correo no es obligatoria para leer chats existentes, pero queda disponible como señal de seguridad y debe utilizarse antes de habilitar operaciones sensibles adicionales en futuras versiones.

## Webhook de correo

Nexora no incorpora credenciales SMTP ni secretos de un proveedor de email dentro del APK. La API entrega el mensaje a un webhook que puedes alojar en la misma VPS o en otro servicio controlado por ti.

Configura:

```dotenv
AUTH_EMAIL_WEBHOOK_URL=https://correo.example.com/nexora/send
AUTH_EMAIL_WEBHOOK_SECRET=UN_SECRETO_LARGO_E_INDEPENDIENTE
```

En producción se exige HTTPS para destinos remotos. Para un servicio local en la VPS también son válidos:

```text
http://127.0.0.1:PUERTO/...
http://localhost:PUERTO/...
```

Cuando existe `AUTH_EMAIL_WEBHOOK_SECRET`, Nexora envía:

```http
Authorization: Bearer <AUTH_EMAIL_WEBHOOK_SECRET>
Content-Type: application/json
```

El cuerpo tiene esta forma conceptual:

```json
{
  "type": "nexora.auth.verify_email",
  "to": "usuario@example.com",
  "name": "Usuario",
  "subject": "Verifica tu correo en Nexora AI",
  "text": "...",
  "html": "...",
  "code": "123456",
  "expiresInMinutes": 10
}
```

Para recuperación, `type` cambia a:

```text
nexora.auth.reset_password
```

El receptor del webhook debe:

1. validar el Bearer secret;
2. aceptar solo solicitudes desde la infraestructura esperada;
3. aplicar límites anti-abuso propios;
4. enviar el email sin registrar el OTP en logs permanentes;
5. devolver HTTP `2xx` únicamente cuando haya aceptado el mensaje.

## Centro de cuenta Android

El avatar flotante abre el Centro de cuenta. Desde ahí el usuario puede:

- ver nombre y correo;
- editar su nombre visible;
- verificar el correo mediante OTP;
- ver métodos de acceso conectados;
- consultar dispositivos/sesiones activas;
- cerrar una sesión remota individual;
- cerrar todas las demás sesiones;
- cerrar la sesión actual.

La vinculación de distintos proveedores sigue siendo explícita por diseño. El backend contiene una barrera SQL que impide asociar silenciosamente una identidad social con otra cuenta solo por compartir un correo.

## Endpoints

| Método | Ruta | Función |
|---|---|---|
| GET | `/api/auth/mobile/start` | Inicia OAuth social y guarda `state` + PKCE. |
| GET | `/api/auth/mobile/callback/[provider]` | Recibe el callback y entrega un código efímero al APK. |
| POST | `/api/auth/mobile/exchange` | Canjea código efímero + verifier PKCE por sesión Nexora. |
| POST | `/api/auth/mobile/email` | Registro/inicio de sesión por correo. |
| POST | `/api/auth/mobile/refresh` | Rota access y refresh tokens. |
| GET | `/api/auth/mobile/me` | Devuelve el usuario actual. |
| POST | `/api/auth/mobile/logout` | Revoca la sesión actual. |
| GET | `/api/auth/mobile/account` | Devuelve perfil, proveedores y sesiones activas. |
| PATCH | `/api/auth/mobile/account` | Actualiza el nombre del perfil. |
| POST | `/api/auth/mobile/account/verify` | Solicita/confirma verificación de correo. |
| DELETE | `/api/auth/mobile/account/sessions` | Revoca una sesión o todas las demás. |
| POST | `/api/auth/mobile/password/reset` | Solicita/confirma recuperación de contraseña. |
| GET | `/api/mobile/user/state` | Descarga historial/proyectos. |
| PUT | `/api/mobile/user/state` | Guarda historial/proyectos. |

## Sincronización y aislamiento de cuentas

Al iniciar sesión, Android combina la copia local con la remota por identificador y `updatedAt`. Los mensajes de una misma conversación se fusionan también por identificador.

Al cerrar sesión:

1. se intenta realizar un último `push` de chats y proyectos;
2. la sesión se revoca en la API;
3. se elimina la copia local del historial;
4. la copia remota permanece asociada a la cuenta.

Esto impide que una segunda cuenta que use el mismo teléfono herede conversaciones de la anterior.

Al restablecer una contraseña se revocan todas las sesiones activas de esa cuenta. El usuario debe volver a autenticarse en cada dispositivo.

## Despliegue

Después de configurar proveedores y correo:

```bash
cd /opt/nexora-ai
sudo nexora update
```

La inicialización de la API aplica las ampliaciones del esquema mediante `ensureDatabase()`. Las migraciones 0.8.0 son aditivas y no eliminan usuarios, sesiones históricas ni chats existentes.

## Lista de comprobación

Comprueba en producción:

1. registro por correo;
2. cierre y reapertura manteniendo la sesión;
3. Google → navegador → retorno a Nexora AI;
4. Facebook → navegador → retorno a Nexora AI;
5. Discord → navegador → retorno a Nexora AI;
6. envío y confirmación del código de verificación;
7. recuperación de contraseña desde la pantalla de login;
8. comprobar que el reset invalida sesiones previas;
9. abrir dos dispositivos y cerrar remotamente uno desde el Centro de cuenta;
10. crear un chat, cerrar sesión y comprobar que desaparece localmente;
11. volver a entrar con la misma cuenta y comprobar que el chat se restaura;
12. entrar con una cuenta distinta y comprobar que no aparecen chats ajenos.
