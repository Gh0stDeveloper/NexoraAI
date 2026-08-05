# Android 0.5 y actividad multiagente

La aplicación usa `/api/mobile/chat/stream` y recibe eventos NDJSON sin esperar a que termine toda la inferencia.

## Eventos

| Tipo | Uso Android |
|---|---|
| `connected` | Confirma el identificador de solicitud |
| `progress` | Actualiza etapa, agente, posición y tiempo |
| `result` | Guarda respuesta, duración, agentes, proveedor y traza |
| `error` | Presenta una incidencia controlada |

Las etapas son metadatos operativos: solicitud, seguridad, planificación, trabajo, revisión, pruebas, síntesis y finalización. No contienen notas internas ni cadena de pensamiento.

## Inteligencia

| Nivel | Agentes | Flujo |
|---|---:|---|
| Instantánea | 1 | Especialista |
| Media | 3 | Planificador → especialista → síntesis |
| Alta | 4 | Añade revisión técnica |
| Máxima | 6 | Añade seguridad, pruebas y crítica |

El servidor puede ejecutar los agentes intermedios en paralelo con `OLLAMA_MULTI_AGENT_PARALLEL=true`, pero la configuración predeterminada es secuencial para reducir RAM/VRAM.

## Persistencia móvil

Android conserva localmente:

- chats y mensajes;
- proyectos y relación chat/proyecto;
- estado fijado;
- nivel y especialidad;
- etapas y duración final;
- resumen de laboratorio, cuando exista.
