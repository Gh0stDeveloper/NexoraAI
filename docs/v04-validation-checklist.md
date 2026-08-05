# Checklist de validación 0.4

## Android

- [ ] El campo de entrada aparece por encima de la barra de navegación.
- [ ] Con teclado visible, el botón `+` mantiene el IME abierto.
- [ ] Modelo e Inteligencia se muestran dentro del compositor.
- [ ] Un chat nuevo no muestra el nivel de inteligencia en la cabecera ni en el estado vacío.
- [ ] El splash aparece desde el primer frame del proceso.
- [ ] El launcher usa icono adaptativo y variante redonda.
- [ ] APK debug contiene `libnexora_config.so`.

## API y Ollama

- [ ] Instantánea ejecuta un agente.
- [ ] Media ejecuta tres agentes.
- [ ] Alta ejecuta cuatro agentes.
- [ ] Máxima ejecuta seis agentes.
- [ ] El sintetizador recibe los hallazgos previos.
- [ ] El modo secuencial es el valor predeterminado.
- [ ] El paralelismo puede habilitarse por variable de entorno.
- [ ] Los adjuntos de texto también pasan por la política de seguridad.

## Producción

- [ ] TypeScript y Next.js compilan.
- [ ] Docker VPS construye correctamente.
- [ ] APK debug se genera y publica como artifact.
- [ ] APK release permanece condicionada a secretos de firma.
