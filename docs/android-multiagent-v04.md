# Android 0.4 y orquestación multiagente

## Corrección de barras del sistema y teclado

La actividad usa modo edge-to-edge controlado y la interfaz aplica insets explícitos:

- La barra superior utiliza el inset de estado de Material 3.
- El compositor aplica `WindowInsets.navigationBars` e `imePadding()`.
- El `Scaffold` no duplica los insets.
- El campo de texto permanece accesible por encima de los controles de navegación del teléfono.
- El panel del botón `+` se renderiza dentro del compositor, no como una ventana `Popup`.

Cuando el teclado está visible, abrir Modelo o Inteligencia conserva el foco del campo y vuelve a solicitar el teclado. Los selectores no usan `AlertDialog`, por lo que no crean otra ventana que oculte el IME.

## Splash nativo

La aplicación utiliza `androidx.core:core-splashscreen` y el tema `Theme.NexoraAI.Starting`.

El splash se crea por el sistema desde el inicio del proceso, antes de que Compose se inicialice. Incluye:

- Fondo oscuro Nexora.
- Marca vectorial propia.
- Fondo de icono.
- Animación de escala y desvanecimiento al entrar al chat.

No se utiliza una actividad splash separada ni una pantalla Compose tardía.

## Icono adaptativo

Los recursos `ic_launcher` e `ic_launcher_round` usan un icono adaptativo vectorial. El mismo lenguaje visual se reutiliza en el splash para evitar un salto de identidad.

## Niveles de inteligencia

| Nivel | Agentes | Flujo |
|---|---:|---|
| Instantánea | 1 | Especialista único |
| Media | 3 | Planificador → especialista → sintetizador |
| Alta | 4 | Planificador → especialista → revisor → sintetizador |
| Máxima | 6 | Planificador → especialista → seguridad → pruebas → crítico → sintetizador |

Los agentes comparten el plan y los hallazgos. El sintetizador recibe todas las notas y redacta una respuesta única.

## Modelos de Ollama

Cada rol puede usar un modelo distinto mediante:

```env
OLLAMA_MODEL_PLANNER=
OLLAMA_MODEL_SPECIALIST=
OLLAMA_MODEL_REVIEWER=
OLLAMA_MODEL_SECURITY=
OLLAMA_MODEL_TESTER=
OLLAMA_MODEL_CRITIC=
OLLAMA_MODEL_SYNTHESIZER=
```

Si una variable no está configurada, se usa el modelo especializado del modo o `OLLAMA_MODEL`.

Para imágenes se utiliza `OLLAMA_VISION_MODEL`.

## Ejecución secuencial o paralela

```env
OLLAMA_MULTI_AGENT_PARALLEL=false
```

- `false`: los agentes trabajan en secuencia y cada agente puede leer las notas anteriores. Consume menos RAM/VRAM.
- `true`: los agentes intermedios se ejecutan en paralelo después del planificador. Es más rápido, pero exige más memoria.

La configuración recomendada inicialmente para una VPS sin GPU grande es `false`.

## Persistencia de modelos

```env
OLLAMA_KEEP_ALIVE=15m
```

Evita recargar el modelo en cada paso del flujo. El valor puede reducirse si la VPS tiene poca memoria.
