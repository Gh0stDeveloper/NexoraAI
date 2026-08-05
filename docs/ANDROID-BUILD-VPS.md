# 📱 Compilación Android release en la VPS

## Compatibilidad del host

| Host | Servidor Nexora | Build Android VPS |
|---|---:|---:|
| Linux AMD64 | ✅ | ✅ |
| Linux ARM64 | ✅ | ⚠️ Usa GitHub Actions |
| Windows/macOS VPS | No validado | Usa GitHub Actions |

La arquitectura del host no limita las ABI del APK. El build genera:

- `armeabi-v7a`
- `arm64-v8a`
- `x86`
- `x86_64`

## Primera compilación

```bash
cd /opt/nexora-ai/app
sudo nexora android-release
```

Se conservan estos recursos:

| Recurso | Ruta |
|---|---|
| Android SDK/NDK/CMake | `/opt/nexora-ai/android-sdk` |
| Gradle 8.10.2 | `/opt/nexora-ai/gradle-8.10.2` |
| Caché de dependencias | `/opt/nexora-ai/cache/gradle` |
| Keystore | `/opt/nexora-ai/secrets/android-release.keystore` |
| Credenciales | `/opt/nexora-ai/secrets/android-signing.env` |
| APK y SHA-256 | `/opt/nexora-ai/releases/` |

Las siguientes compilaciones no vuelven a crear la keystore ni descargan recursos ya presentes.
La primera descarga de Android Command-line Tools y Gradle se comprueba con los SHA-256 oficiales antes de instalarse. Si personalizas una URL mediante variables de entorno, proporciona también su variable `*_SHA256` correspondiente.

## Respaldo obligatorio

Copia ambos archivos a almacenamiento cifrado y separado:

```text
android-release.keystore
android-signing.env
```

No basta con respaldar solo la keystore: también necesitas alias y contraseñas. No los subas a Git, correo sin cifrar ni chats públicos.

Si pierdes esa firma, Android no aceptará una actualización sobre la app instalada anteriormente.

## GitHub Actions como alternativa

Convierte la keystore existente a Base64 sin saltos:

```bash
base64 -w 0 /opt/nexora-ai/secrets/android-release.keystore > keystore.base64
```

Configura en GitHub:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Usa exactamente los valores de `android-signing.env`. El workflow jamás genera una firma nueva silenciosamente: si faltan secretos, omite el release.

## Verificar un APK

```bash
sha256sum -c /opt/nexora-ai/releases/NexoraAI-0.5.0.apk.sha256
unzip -l /opt/nexora-ai/releases/NexoraAI-0.5.0.apk | grep libnexora.so
```

Debes encontrar `libnexora.so` en las cuatro ABI. Para comprobar firma:

```bash
/opt/nexora-ai/android-sdk/build-tools/35.0.0/apksigner verify --verbose \
  /opt/nexora-ai/releases/NexoraAI-0.5.0.apk
```

## Cambiar la URL de API

Cambiar el endpoint requiere incrementar la versión, ejecutar CI y recompilar el APK con la misma keystore. El proyecto evita publicar nombres descriptivos para el componente que conserva esos metadatos.

Ocultar una URL no equivale a autenticación. La aplicación debe poder resolverla y un analista puede observar la conexión.
