# Autenticación Android, cuentas y sincronización

Nexora AI 0.9.0 usa identidad de usuario autohospedada sobre la API y PostgreSQL de la VPS. Android permite iniciar sesión con Google, Facebook, Discord o correo electrónico; sincroniza chats/proyectos y ofrece un Centro de cuenta para perfil, correo, métodos de acceso y sesiones.

## Arquitectura de seguridad

- Android abre Google, Facebook y Discord en el navegador del sistema.
- OAuth móvil utiliza `state` + PKCE S256.
- Los `client_secret` permanecen únicamente en la VPS.
- La API emite sesiones propias de Nexora AI.
- Access token: 1 hora.
- Refresh token: 30 días, rotado al renovarse.
- PostgreSQL persiste hashes SHA-256 de tokens, no los tokens originales.
- Contraseñas: `scrypt` + salt aleatorio.
- OTP de verificación/recuperación: 6 dígitos, 10 minutos, máximo 5 intentos y solo HMAC SHA-256 persistido.
- Android cifra sesión y estado OAuth temporal mediante AES-GCM + Android Keystore.
- El historial local/offline se sincroniza con `mobile_user_chat_state`.
- Cada sesión registra `X-Nexora-Device` y puede revocarse remotamente.

## Nexora Mail

Desde 0.9 el correo transaccional ya no depende de un webhook externo. El Docker Compose incluye **Nexora Mail**, formado por un gateway HTTP privado, Postfix y OpenDKIM.

La API utiliza internamente:

```text
http://mailer:8025/send
```

Esa dirección no se publica en la VPS. Consulta `docs/NEXORA-MAIL.md` para configuración, DKIM, SPF, DMARC, PTR/rDNS y pruebas de entrega.

Una instalación anterior puede actualizar con:

```bash
sudo nexora update
```

sin reemplazar `.env.production`. Si `AUTH_CODE_PEPPER` o `AUTH_EMAIL_WEBHOOK_SECRET` todavía no existen, la transición inicial usa secretos derivados de forma separada del secreto PostgreSQL existente. Se recomienda sustituirlos posteriormente por secretos dedicados.

## Deep link Android

Todos los retornos OAuth utilizan:

```text
nexoraai://auth/callback
```

`singleTop` evita crear una segunda instancia visual al volver del navegador.

## Proveedores OAuth

Variables en `.env.production`:

```dotenv
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
FACEBOOK_CLIENT_ID=
FACEBOOK_CLIENT_SECRET=
DISCORD_CLIENT_ID=
DISCORD_CLIENT_SECRET=
```

Redirect URIs:

```text
https://apighostnexoraai.duckdns.org/api/auth/mobile/callback/google
https://apighostnexoraai.duckdns.org/api/auth/mobile/callback/facebook
https://apighostnexoraai.duckdns.org/api/auth/mobile/callback/discord
```

Los mismos callbacks sirven para login normal y vinculación explícita porque el servidor mantiene estados OAuth separados para cada operación.

## Vinculación y desvinculación explícita

El Centro de cuenta permite vincular o quitar Google, Facebook y Discord.

La vinculación **no** se basa en coincidencia de correo. El flujo exige:

1. sesión Nexora válida;
2. nuevo `state` específico de vinculación;
3. PKCE S256;
4. autenticación completa con el proveedor;
5. autorización SQL efímera de 60 segundos;
6. escritura del proveedor únicamente dentro de esa autorización.

El trigger `nexora_prevent_implicit_auth_link()` sigue bloqueando cualquier asociación silenciosa.

Si el proveedor ya pertenece a otra cuenta Nexora que contiene contraseña, varios proveedores o historial remoto propio, el servidor responde con conflicto y **no fusiona automáticamente las cuentas**. Esto evita perder chats, sesiones o credenciales.

Solo se permite mover automáticamente una identidad desde una cuenta social efímera sin otros datos propios.

Al desvincular, Nexora comprueba que quede al menos otro método de acceso. No es posible eliminar el único método con el que se podría recuperar la cuenta.

## Foto de perfil

Cuando Google, Facebook o Discord entregan una imagen HTTPS, Android la muestra mediante Coil. Si no existe imagen o la URL no es HTTPS, la interfaz usa un avatar local con la inicial del nombre.

## Correo, verificación y recuperación

Nexora incorpora:

- registro/inicio de sesión con correo y contraseña;
- verificación de correo mediante Nexora Mail;
- `Olvidé mi contraseña`;
- OTP de un solo uso;
- respuesta indistinguible durante la solicitud de reset para evitar enumeración de cuentas;
- revocación de sesiones después de un reset.

## Cambiar o añadir contraseña desde una sesión

El Centro de cuenta permite:

- **Cuenta con contraseña:** cambiarla demostrando primero la contraseña actual.
- **Cuenta social sin contraseña:** añadir una contraseña si el correo ya está verificado.

Al cambiar o añadir contraseña se mantienen la sesión actual y se revocan las demás sesiones activas, reduciendo el riesgo de que un dispositivo antiguo conserve acceso.

## Centro de cuenta Android

Desde el avatar flotante el usuario puede:

- ver avatar, nombre y correo;
- editar el nombre;
- verificar correo;
- vincular Google/Facebook/Discord;
- desvincular un proveedor cuando no sea el único acceso;
- crear o cambiar contraseña;
- ver dispositivos/sesiones activas;
- cerrar una sesión remota;
- cerrar todas las demás sesiones;
- cerrar la sesión actual.

## Endpoints

| Método | Ruta | Función |
|---|---|---|
| GET | `/api/auth/mobile/start` | Inicia login OAuth normal. |
| GET | `/api/auth/mobile/callback/[provider]` | Completa login o vinculación según el estado efímero. |
| POST | `/api/auth/mobile/exchange` | Canjea código efímero + PKCE por sesión Nexora. |
| POST | `/api/auth/mobile/email` | Registro/login por correo. |
| POST | `/api/auth/mobile/refresh` | Rota access/refresh tokens. |
| GET | `/api/auth/mobile/me` | Devuelve usuario actual. |
| POST | `/api/auth/mobile/logout` | Revoca sesión actual. |
| GET | `/api/auth/mobile/account` | Perfil, proveedores y sesiones. |
| PATCH | `/api/auth/mobile/account` | Actualiza nombre. |
| POST | `/api/auth/mobile/account/verify` | Solicita/confirma verificación de correo. |
| DELETE | `/api/auth/mobile/account/sessions` | Revoca una sesión o las demás. |
| POST | `/api/auth/mobile/account/link` | Inicia vinculación OAuth explícita. |
| DELETE | `/api/auth/mobile/account/link` | Desvincula un proveedor. |
| PUT | `/api/auth/mobile/account/password` | Añade/cambia contraseña autenticada. |
| POST | `/api/auth/mobile/password/reset` | Solicita/confirma recuperación. |
| GET | `/api/mobile/user/state` | Descarga historial/proyectos. |
| PUT | `/api/mobile/user/state` | Guarda historial/proyectos. |

## Sincronización y aislamiento

Al iniciar sesión Android combina la copia local con la remota por ID y `updatedAt`.

Al cerrar sesión:

1. intenta un último `push`;
2. revoca la sesión;
3. elimina la copia local;
4. conserva la copia remota asociada a la cuenta.

Una segunda cuenta en el mismo teléfono no hereda las conversaciones del usuario anterior.

Las ramas y variantes creadas al editar/regenerar mensajes también forman parte del estado sincronizado porque se almacenan como sesiones normales con `parentSessionId`, `branchedFromMessageId`, `variantGroupId` y `variantIndex`.

## Despliegue desde la versión instalada

Cuando 0.9 llegue a `main`:

```bash
cd /opt/NexoraAI
sudo nexora update
```

No es necesario reinstalar. Las migraciones PostgreSQL son aditivas, Nexora Mail se construye dentro del mismo flujo, su healthcheck se valida antes de considerar correcta la actualización y cualquier fallo activa el rollback existente.

Después:

```bash
sudo nexora verify
sudo nexora mail-dns
sudo nexora mail-test usuario@example.com
```

## Comprobaciones recomendadas

1. Login por correo y permanencia de sesión.
2. Login Google/Facebook/Discord y retorno al mismo Activity.
3. Vincular un proveedor nuevo desde una cuenta iniciada.
4. Intentar vincular un proveedor perteneciente a una cuenta con datos y confirmar que se rechaza.
5. Desvincular un proveedor manteniendo otro método de acceso.
6. Verificar correo mediante Nexora Mail.
7. Recuperar contraseña desde login.
8. Cambiar contraseña desde una sesión autenticada y comprobar revocación de otros dispositivos.
9. Ver foto social y fallback de inicial.
10. Crear chats, cerrar sesión y comprobar aislamiento local.
11. Volver a entrar y comprobar restauración remota.
12. Editar/regenerar una respuesta y verificar que la conversación original sigue disponible.
