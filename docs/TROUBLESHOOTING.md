# 🛠️ Solución de problemas

## Puertos 80 o 443 ocupados

```bash
sudo ss -ltnp | grep -E ':(80|443)\b'
sudo nginx -t
sudo systemctl status nginx --no-pager
```

Nexora debe escuchar en `127.0.0.1:3000`; Nginx es el único proceso esperado en 80/443. No detengas `sshd` ni intentes reutilizar el puerto 22.

## Permiso denegado con Docker

```bash
sudo usermod -aG docker "$USER"
```

Cierra toda la sesión SSH, vuelve a entrar y ejecuta:

```bash
docker version
docker compose version
```

## `docker compose` no existe

```bash
sudo apt-get update
sudo apt-get install -y docker-compose-plugin
```

Si el paquete no existe, vuelve a ejecutar `bootstrap-vps.sh`: configura el repositorio oficial de Docker o instala el binario oficial verificando su SHA-256. El proyecto requiere Compose v2 porque usa perfiles y la especificación moderna.

## La API no responde

```bash
nexora status
nexora logs 200
curl -v http://127.0.0.1:3000/api/health
```

Si el contenedor reinicia, revisa `.env.production`, RAM disponible y build logs.

## Ollama tarda demasiado

```bash
docker compose -f docker-compose.vps.yml exec ollama ollama list
docker stats --no-stream
free -h
```

Acciones:

- usa inteligencia instantánea;
- reduce el modelo o contexto;
- mantén `OLLAMA_MULTI_AGENT_PARALLEL=false`;
- aumenta RAM/swap con cuidado;
- revisa `OLLAMA_*_TIMEOUT_MS`.

## La app recibe respuesta fallback

Mira los logs:

```bash
nexora logs 300
```

Busca `[NexoraAI] Ollama inference failed` y el código `timeout`, `network`, `http` o `empty`.

## TLS o SNI incorrecto

```bash
sudo certbot certificates
openssl s_client -connect apighostnexoraai.duckdns.org:443 \
  -servername apighostnexoraai.duckdns.org </dev/null
```

El certificado debe incluir exactamente el dominio API. Después:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## La descarga APK devuelve 404

```bash
sudo nexora android-release
ls -l /opt/nexora-ai/releases/
curl -I https://ghostnexoraai.duckdns.org/downloads/NexoraAI-latest.apk
```

El build actual publica de forma atómica el APK versionado, `NexoraAI-latest.apk` y
`latest.json`. No edites `ANDROID_APK_URL` ni reinicies la aplicación. Si el archivo existe
pero la URL responde 404, confirma que Nginx usa `deploy/nginx/nexoraia-vps.conf` y ejecuta
`sudo nginx -t` antes de recargarlo.

## Android no compila en ARM64

El servidor funciona en ARM64, pero el build Android VPS está limitado a Linux AMD64. Usa el workflow Android CI con la misma keystore configurada en GitHub Secrets.

## APK no puede actualizar la instalación anterior

La firma cambió. Debes compilar con la keystore original. Verifica que `ANDROID_KEYSTORE_PATH`, alias y contraseñas correspondan a la copia persistente. No generes otra keystore.

## `libnexora.so` falta

```bash
unzip -l NexoraAI-0.6.0.apk | grep libnexora.so
```

Debe aparecer en cuatro rutas ABI. Si falta, revisa NDK/CMake y el job “Verify native library”.

## Laboratorio desactivado o no disponible

```bash
grep -E '^(ALLOW_CODE_EXECUTION|SANDBOX_RUNNER_URL)=' .env.production
docker compose --profile sandbox -f docker-compose.vps.yml ps sandbox
docker compose --profile sandbox -f docker-compose.vps.yml logs sandbox
```

No expongas el runner para “solucionarlo”. Debe seguir siendo interno.

## Actualización rechazada por cambios locales

```bash
git status --short
git diff
```

Confirma, guarda o revierte conscientemente esos cambios. `nexora update` no los destruye.

## Diagnóstico completo

```bash
bash deploy/scripts/platform-check.sh
nexora status
nexora verify
nexora logs 200
df -h
free -h
sudo ss -ltnp
```

No publiques `.env.production`, contraseñas, tokens, keystore ni logs con secretos al pedir ayuda.
