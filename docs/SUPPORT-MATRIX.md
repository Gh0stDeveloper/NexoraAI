# 🧩 Matriz de compatibilidad

## Sistemas de servidor

| Sistema | AMD64 | ARM64 | Estado |
|---|---:|---:|---|
| Ubuntu 26.04 LTS | ✅ | ✅ | Soportado |
| Ubuntu 24.04 LTS | ✅ | ✅ | Recomendado |
| Ubuntu 22.04 LTS | ✅ | ✅ | Soportado |
| Debian 13 | ✅ | ✅ | Soportado |
| Debian 12 | ✅ | ✅ | Soportado |
| Debian 11 | ✅ | ✅ | Mientras conserve soporte de seguridad del proveedor |
| Ubuntu 23.04/23.10 | ❌ | ❌ | EOL |
| Debian 8/9/10 | ❌ | ❌ | EOL |
| Otro Linux con Docker 24+ | 🧪 | 🧪 | Mejor esfuerzo, no validado |

Las versiones EOL no reciben parches suficientes para exponer Nginx, Docker y una API de IA a Internet. GitHub Actions valida las distribuciones admitidas; no se desactiva seguridad para ampliar artificialmente la matriz.

Los scripts instalan Docker Engine y Compose v2 desde el repositorio oficial de Docker cuando el host no los tiene. Esto evita depender del Compose legado de Debian 11.

## Arquitecturas

| Componente | AMD64 | ARM64 | ARMv7 | x86 Android |
|---|---:|---:|---:|---:|
| Web/API Docker | ✅ | ✅ | ❌ | N/A |
| Ollama Docker | ✅ | ✅ | ❌ | N/A |
| PostgreSQL/pgvector | ✅ | ✅ | ❌ | N/A |
| Compilador Android en VPS | ✅ | ⚠️ Actions | ❌ | N/A |
| APK Android | N/A | `arm64-v8a` | `armeabi-v7a` | `x86`, `x86_64` |

## Android

- `minSdk 26`: Android 8.0.
- `targetSdk 35`.
- Pantalla y orientación sin bloqueo.
- Diseño flexible desde teléfonos compactos hasta pantallas grandes.
- Release rechaza todo tráfico HTTP.
- La variante debug permite desarrollo HTTP únicamente para `localhost` y `10.0.2.2`.

## Recursos por modelo

Los requisitos dependen más del modelo y cuantización que del número de agentes. Los agentes secuenciales reutilizan el mismo modelo; el modo paralelo aumenta RAM/VRAM.

| Escenario | Recomendación |
|---|---|
| Solo web/API | 2 vCPU, 4 GB RAM, 20 GB SSD |
| Modelo 3B–4B Q4 | 4 vCPU, 8 GB RAM, 40 GB SSD |
| Modelo 7B/8B Q4 | 8 vCPU, 16 GB RAM, 80 GB SSD |
| Agentes paralelos | 16+ vCPU, 32 GB RAM o GPU apropiada |
| GPU | 12+ GB VRAM según modelo/contexto |

Mantén `OLLAMA_MULTI_AGENT_PARALLEL=false` hasta medir memoria y latencia reales.
