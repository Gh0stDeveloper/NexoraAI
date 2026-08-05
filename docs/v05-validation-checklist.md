# Checklist de validación 0.5

## Web/API

- [ ] Landing no ofrece chat web.
- [ ] `/download` redirige al APK configurado.
- [ ] `/terms`, `/privacy`, `/robots.txt` y `/sitemap.xml` responden.
- [ ] `/api/mobile/chat/stream` emite NDJSON y termina con `result`.
- [ ] La respuesta contiene `elapsedMs`, `agentsUsed`, `trace` y `provider`.

## Android

- [ ] APK contiene `libnexora.so` para cuatro ABI.
- [ ] La URL y ruta no aparecen en Kotlin.
- [ ] Cronómetro avanza durante inferencia.
- [ ] Se actualizan etapas sin mostrar razonamiento privado.
- [ ] Tiempo y agentes se conservan al reabrir la app.
- [ ] Se pueden crear/fijar proyectos y chats.
- [ ] Los chats creados desde un proyecto permanecen dentro de él.
- [ ] Términos y privacidad se leen dentro de la app.
- [ ] Diseño funciona en pantalla compacta, normal y grande.

## VPS

- [ ] `platform-check.sh` acepta el sistema.
- [ ] `nexora install`, `status`, `backup`, `update` y `verify` funcionan.
- [ ] Nginx sirve landing, API y `/downloads/`.
- [ ] Certbot renueva ambos dominios.
- [ ] Keystore persiste y la segunda compilación conserva la firma.
- [ ] Rollback restaura el commit anterior.

## Sandbox opcional

- [ ] Está desactivado por defecto.
- [ ] No publica el puerto 8787.
- [ ] Rechaza peticiones sin token.
- [ ] El contenedor de trabajo no tiene red ni capacidades.
- [ ] Timeout elimina el contenedor.
- [ ] Los archivos temporales desaparecen.
