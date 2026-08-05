# Chats persistentes y APK temporales

## Recuperación de solicitudes

Android 0.6.0 ya no depende de mantener abierto un stream HTTP hasta que Ollama termine.

1. El teléfono genera un UUID y un token aleatorio de 256 bits.
2. Guarda el mensaje y sus adjuntos en el almacenamiento privado de la aplicación.
3. WorkManager crea la solicitud idempotente en `/api/mobile/chat/jobs`.
4. PostgreSQL conserva estado, progreso y resultado aunque el cliente se desconecte.
5. WorkManager consulta el trabajo con el token privado y actualiza el historial local.

Si el usuario cierra la aplicación, cambia de chat, pierde la red o reinicia el teléfono, el
trabajo se reanuda sin duplicar la petición. Los tokens se guardan como SHA-256 en PostgreSQL y
no aparecen en las respuestas públicas.

## Publicación oficial Android

```bash
sudo nexora android-release
```

El comando usa únicamente la identidad oficial:

```text
/opt/nexora-ai/secrets/android-release.keystore
/opt/nexora-ai/secrets/android-signing.env
```

Firma explícitamente con V1+V2+V3, verifica el APK y actualiza `latest.json`. La API y `/download`
detectan la nueva versión al instante; no se recrea el contenedor web.

## Compilaciones solicitadas por usuarios

Activa el servicio una sola vez:

```bash
cd /opt/NexoraAI
sudo nexora user-builds-enable
```

El comando crea una identidad global separada:

```text
/opt/nexora-ai/secrets/user-builds/android-user-builds.keystore
/opt/nexora-ai/secrets/user-builds/android-user-signing.env
```

No sustituyas esa keystore: todas las aplicaciones temporales de usuarios usan la misma firma,
pero nunca la firma oficial de Nexora AI.

En una VPS AMD64 nueva, el mismo comando prepara primero SDK/Gradle mediante el compilador
oficial si todavía no existen. Si cualquier paso falla, vuelve a dejar la función desactivada.

El cliente muestra **Crear APK con esta respuesta**. La compilación usa una plantilla nativa fija
y copia únicamente texto y metadatos validados; no acepta archivos Gradle ni ejecuta código fuente
arbitrario proporcionado por Internet. Es una frontera deliberada para que un usuario público no
pueda convertir la VPS en un servicio de ejecución remota.

Protecciones:

- contenedor independiente sin socket Docker;
- usuario sin privilegios, raíz de solo lectura y capacidades Linux eliminadas;
- límite de 2 CPU, 2 GiB de RAM y 512 procesos;
- máximo predeterminado de tres solicitudes por dispositivo/IP cada hora;
- cola global predeterminada de diez trabajos;
- rutas confinadas a `/var/lib/nexora-ai/android-build-jobs`;
- Gradle compila sin recibir las credenciales; `apksigner` firma V1+V2+V3 en una fase separada;
- firma V1+V2+V3 verificada antes de exponer el APK;
- token temporal de 256 bits y descargas fuera del directorio público;
- token de URL excluido de los access logs de Nginx;
- APK eliminado y enlace invalidado una hora después de completarse.

Para desactivarlo:

```bash
sudo nexora user-builds-disable
```

La desactivación detiene el worker, invalida los trabajos y elimina inmediatamente todos los APK
temporales que todavía existan.

## Verificación

```bash
nexora status
nexora verify
curl -fsS https://apighostnexoraai.duckdns.org/api/mobile/status
```

El estado debe incluir `durable-chat-jobs`, `request-recovery` y, cuando esté activo,
`"userBuilds":{"enabled":true}`.
