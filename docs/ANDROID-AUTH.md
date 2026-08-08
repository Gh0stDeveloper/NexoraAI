# Autenticación Android y sincronización de chats

Nexora AI 0.7.0 incorpora identidad de usuario autohospedada sobre la API y PostgreSQL existentes. La aplicación Android permite iniciar sesión con Google, Facebook, Discord o correo electrónico y mantiene chats y proyectos asociados a la cuenta.

## Arquitectura

- Android abre los proveedores sociales en el navegador del sistema.
- El flujo móvil utiliza OAuth 2.0 con `state` y PKCE (`S256`).
- Google, Facebook y Discord se usan únicamente como proveedores de identidad.
- Los `client_secret` viven exclusivamente en la VPS.
- La API crea sesiones propias de Nexora AI.
- El access token dura 1 hora.
- El refresh token dura 30 días y se rota al renovarse.
- En PostgreSQL solo se guardan hashes SHA-256 de los tokens de sesión.
- Las contraseñas de correo se derivan mediante `scrypt` con salt aleatorio; nunca se almacena la contraseña original.
- En Android, la sesión y el estado OAuth temporal se cifran con AES-GCM usando una clave del Android Keystore.
- El historial continúa teniendo una copia local para respuesta inmediata/offline y se sincroniza con `mobile_user_chat_state` cuando existe una sesión válida.

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
```

Nunca publiques los secretos en GitHub ni los incluyas en `build.gradle.kts`, recursos Android o `BuildConfig`.

## Google

Crea/configura una aplicación OAuth para la API pública y registra esta URL de redirección autorizada:

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

## Correo y contraseña

El endpoint móvil admite registro e inicio de sesión con correo. La versión 0.7.0 implementa almacenamiento seguro de contraseña y sesiones, pero todavía no envía correos de verificación ni incluye recuperación de contraseña. Para una apertura pública masiva conviene añadir verificación de email, restablecimiento con token de un solo uso y protección anti-abuso específica para esos endpoints.

## Endpoints añadidos

| Método | Ruta | Función |
|---|---|---|
| GET | `/api/auth/mobile/start` | Inicia OAuth social y guarda `state` + PKCE. |
| GET | `/api/auth/mobile/callback/[provider]` | Recibe el callback del proveedor y entrega un código efímero al APK. |
| POST | `/api/auth/mobile/exchange` | Canjea el código efímero + verifier PKCE por sesión Nexora. |
| POST | `/api/auth/mobile/email` | Registro/inicio de sesión por correo. |
| POST | `/api/auth/mobile/refresh` | Rota access y refresh tokens. |
| GET | `/api/auth/mobile/me` | Devuelve el usuario de la sesión. |
| POST | `/api/auth/mobile/logout` | Revoca la sesión actual. |
| GET | `/api/mobile/user/state` | Descarga historial/proyectos de la cuenta. |
| PUT | `/api/mobile/user/state` | Guarda historial/proyectos de la cuenta. |

## Sincronización y aislamiento de cuentas

Al iniciar sesión, Android combina la copia local con la copia remota por identificador y `updatedAt`. Los mensajes de una misma conversación se fusionan también por identificador.

Al cerrar sesión:

1. Se intenta realizar un último `push` de chats y proyectos.
2. La sesión se revoca en la API.
3. Se elimina la copia local del historial.
4. La copia remota permanece asociada a la cuenta.

Esto impide que una segunda cuenta que use el mismo teléfono herede conversaciones de la anterior.

## Despliegue

Después de configurar los proveedores:

```bash
cd /opt/nexora-ai
sudo nexora update
```

Si el despliegue todavía no contiene el comando de actualización, usa el flujo documentado de Docker Compose del proyecto. La inicialización de la API crea las tablas nuevas mediante `ensureDatabase()`.

Comprueba posteriormente:

1. Registro por correo.
2. Cierre y reapertura de la app manteniendo sesión.
3. Login Google → navegador → retorno a Nexora AI.
4. Login Facebook → navegador → retorno a Nexora AI.
5. Login Discord → navegador → retorno a Nexora AI.
6. Crear un chat, cerrar sesión y comprobar que desaparece localmente.
7. Volver a entrar con la misma cuenta y comprobar que el chat se restaura.
8. Entrar con una cuenta distinta y comprobar que no aparecen chats ajenos.
