# 🧪 Laboratorio efímero de código

El laboratorio ayuda a validar bloques generados, pero no es una frontera de seguridad perfecta. Está **desactivado por defecto**.

## Flujo

1. El usuario activa “Probar código” en Android.
2. El modelo genera una respuesta normal.
3. El servidor toma el primer bloque compatible de hasta 120 KB.
4. El runner selecciona un comando fijo para Python, JavaScript o Bash.
5. Docker crea un contenedor aislado.
6. Se devuelve estado, código de salida, duración y salida limitada.
7. Contenedor y archivos temporales se eliminan.

No se aceptan comandos arbitrarios desde el cliente.

## Controles

- `--network none`
- raíz `--read-only`
- `--cap-drop ALL`
- `no-new-privileges`
- 1 CPU, 512 MB RAM y 64 procesos
- usuario sin privilegios dentro del contenedor
- un máximo predeterminado de 2 trabajos simultáneos
- timeout máximo de 60 segundos
- salida máxima configurable
- código montado en solo lectura
- token interno de al menos 24 caracteres
- servicio no publicado en puertos del host

## Riesgo residual

El runner usa `/var/run/docker.sock` para crear contenedores. Quien comprometa el runner podría intentar controlar Docker y, por extensión, el host. Por eso:

- nunca publiques el puerto 8787;
- conserva el token solo en `.env.production`;
- mantén `ALLOW_CODE_EXECUTION=false` si no necesitas pruebas;
- actualiza Docker y el kernel;
- no permitas instalar paquetes desde Internet durante trabajos;
- revisa logs y límites;
- considera mover el runner a otra VM para aislamiento fuerte.

## Activación

Genera o conserva un token aleatorio:

```bash
openssl rand -hex 32
```

Edita `.env.production`:

```env
ALLOW_CODE_EXECUTION=true
SANDBOX_RUNNER_TOKEN=<valor-aleatorio>
SANDBOX_MAX_CONCURRENT_JOBS=2
```

Inicia y verifica:

```bash
nexora install
nexora verify
docker compose --profile sandbox -f docker-compose.vps.yml logs sandbox
```

Para desactivar:

```env
ALLOW_CODE_EXECUTION=false
```

```bash
nexora restart
docker compose --profile sandbox -f docker-compose.vps.yml stop sandbox
```

## Limpieza

Los trabajos se eliminan al terminar. Las imágenes de runtime permanecen en caché para evitar descargas repetidas. El comando siguiente elimina únicamente contenedores detenidos e imágenes sin uso antiguas:

```bash
nexora cleanup
```

No uses `docker system prune --volumes`: podría borrar PostgreSQL u Ollama.
