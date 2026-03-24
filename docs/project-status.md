# VIGIA1 - Estado actual del proyecto

## Resumen ejecutivo

VIGIA1 es una app Android en Kotlin llamada VIGIA orientada a monitorización visual con smartphone en entorno industrial.

El MVP actual tiene:
- preview de cámara, vigilancia activa/detenida, ROI manual
- **alertas automáticas a Telegram cuando se detecta cambio relevante**
- mensaje "Minda Requiere Atención" + imagen automáticos
- protección anti-spam básica (cooldown 60 segundos)
- **confirmación diferida a los 3 minutos con imagen nueva**
- **estabilización de detección: requiere cambios consecutivos antes de alertar**
- **infraestructura de build completa (Gradle Wrapper 8.5 estable)**
- feedback visual del estado de alertas y confirmaciones en la UI
- captura y envío manual de imagen como respaldo
- **orientación forzada a landscape para uso industrial**
- **selector de ROI reescrito basado en patrón probado de MindaVigilante**

## Estado actual

### Fase ACTIVA: Corrección de bugs críticos detectados en pruebas reales

**Fecha de inicio**: 2026-03-24

Durante las pruebas reales en dispositivo físico han aparecido bugs críticos que debían corregirse antes de continuar con nuevas funcionalidades.

#### Bugs corregidos en esta iteración

| Bug | Causa raíz | Corrección aplicada | Estado |
|-----|-----------|---------------------|--------|
| **Crash al guardar Telegram** | `TelegramConfig.EMPTY` intentaba crear instancia con strings vacíos, violando `init { require(...) }` | Cambiado a `val EMPTY by lazy { TelegramConfig("__empty__", "__empty__") }` | ✅ Corregido |
| **Crash potencial en ROI** | `Roi.UNDEFINED` con coordenadas (0f,0f,0f,0f) violaba `require(left < right)` | Cambiado a lazy con coordenadas válidas (0f,0f,0.1f,0.1f) | ✅ Corregido |
| **Orientación no forzada** | MainActivity sin `android:screenOrientation` | Añadido `screenOrientation="landscape"` + `configChanges` | ✅ Corregido |
| **ROI no queda fijado al soltar** | Implementación anterior tenía bug en `createNormalizedRect`: trataba píxeles como normalizados | **REESCRITURA COMPLETA basada en `MindaRoiOverlayView.kt`** | ✅ Corregido |

#### Estado de correcciones

- [x] Orientación forzada a landscape
- [x] Telegram config sin crash al guardar
- [x] Token y chat_id se guardan y recuperan correctamente
- [x] ROI persiste y se recupera al reabrir
- [x] Build compila correctamente
- [x] **Selector de ROI reescrito con patrón robusto de MindaVigilante**
- [ ] Pendiente verificación en dispositivo físico

---

### Fase PREVIA CERRADA: MVP funcional básico + estabilización

**Fecha de cierre**: 2026-03-23

Esta fase queda **CERRADA** con las siguientes entregas verificadas:

#### Ya implementado y VERIFICADO
- [x] estructura base del proyecto Android
- [x] UI principal con Jetpack Compose
- [x] preview real de cámara con CameraX
- [x] gestión básica de permisos de cámara
- [x] estado de monitorización activa/detenida
- [x] selección manual de ROI sobre la preview (con reposicionamiento)
- [x] persistencia local del ROI (DataStore)
- [x] recuperación automática del ROI al abrir la app
- [x] visualización del ROI sobre la cámara
- [x] capa de detección separada por módulos
- [x] frames reales de CameraX conectados al detector
- [x] análisis de luminancia real del ROI
- [x] estado visual en UI del resultado de detección
- [x] configuración funcional de Telegram (bot token + chat_id)
- [x] persistencia de configuración Telegram
- [x] prueba manual de envío de mensaje a Telegram con feedback visual
- [x] captura real de imagen desde FrameProcessor
- [x] envío manual de imagen a Telegram con multipart/form-data
- [x] UI de captura con estados (Capturando, Enviando, Éxito, Error)
- [x] AlertManager: gestor de alertas automáticas
- [x] Disparo automático cuando hasChange == true
- [x] Envío automático: mensaje "Minda Requiere Atención" + imagen
- [x] Protección anti-spam: cooldown de 60 segundos entre alertas
- [x] UI de alertas automáticas con estados (Enviando, Éxito, Error, Cooldown)
- [x] Confirmación diferida a los 3 minutos tras alerta exitosa
- [x] Captura de imagen nueva en confirmación (no reutiliza la primera)
- [x] UI de confirmación con countdown en tiempo real (180s → 0s)
- [x] Cancelación de confirmación al detener vigilancia
- [x] **ESTABILIZACIÓN DE DETECCIÓN: capa de confirmación consecutiva en MonitoringManager**
- [x] **Requiere 3 detecciones consecutivas antes de confirmar cambio**
- [x] **Falsos positivos por picos breves significativamente reducidos**
- [x] **Gradle Wrapper 8.5 configurado y funcional**
- [x] **Compilación exitosa verificada**

### Implementado de forma provisional
- detector básico desacoplado (interfaz RoiDetector)
- algoritmo de detección simple basado en diferencia de luminancia
- conversión YUV a luminancia sin optimizaciones avanzadas
- análisis a 320x240 para rendimiento (downscaling)
- sin compensación de iluminación ni normalización avanzada
- captura usa último frame disponible, no frame exclusivo
- cooldown fijo de 60 segundos (no configurable aún)
- sin cola de alertas pendientes (si falla, se pierde)
- confirmación se pierde si la app se cierra antes de los 3 minutos
- lógica de estabilización es simple (contador con timeout), no analiza patrones complejos

### Aún NO implementado o pendiente (para futuras fases)
- ajuste de sensibilidad desde UI (threshold, detecciones requeridas)
- historial de alertas persistido
- ajustes dedicados para redefinir/borrar ROI
- eliminación de ROI guardado
- múltiples ROIs
- persistencia de alertas/confirmaciones ante cierre de app

## Fase actual del proyecto

**Fase ACTIVA**: Corrección de bugs críticos - Selector de ROI reescrito

La app está **COMPILABLE** con la nueva implementación del selector ROI basada en el patrón probado de `MindaRoiOverlayView.kt`.

### Cambio principal de esta iteración

**REESCRITURA COMPLETA de `RoiSelector.kt`** basada en la implementación funcional de MindaVigilante (`docs/legacy/MindaRoiOverlayView.kt`):

- **Patrón de eventos táctiles**: DOWN → MOVE → UP (manejo explícito)
- **Rectángulo temporal durante creación**: Almacenado en píxeles, dibujado en amarillo
- **Consolidación al soltar**: Conversión correcta píxeles → normalizado (0-1)
- **ROI fijado visualmente**: Dibujado en verde al soltar el dedo
- **Modo movimiento**: Mantener pulsado dentro del ROI para reposicionar
- **Límites estrictos**: Clamp a los bordes de la vista durante movimiento
- **Hit test**: Detección precisa de toque dentro del ROI

### Archivos modificados en esta iteración
- `app/src/main/java/com/vigia/app/ui/components/RoiSelector.kt` - **REESCRITO COMPLETAMENTE**

## Siguiente fase recomendada

Próximas mejoras posibles (requieren nueva fase/planificación):
- Ajustar sensibilidad/cooldown/detecciones consecutivas desde UI
- Permitir configurar el número de detecciones consecutivas requeridas
- Persistencia de alertas ante cierre de app
- Sistema de múltiples ROIs
- Historial/visualizador de alertas pasadas
- Algoritmo de detección más robusto (reemplazar SimpleFrameDifferenceDetector)

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- El ROI puede dibujarse y reposicionarse antes de confirmar.
- El ROI se guarda localmente y se recupera en futuras sesiones.
- **El selector de ROI ahora usa el patrón probado de MindaVigilante (eventos táctiles explícitos)**.
- El análisis usa frames reales de CameraX (ImageAnalysis).
- Telegram se configura manualmente y se prueba antes de usar.
- La captura de imagen reutiliza el último frame procesado.
- Las alertas automáticas tienen cooldown de 60 segundos para evitar spam.
- Tras alerta exitosa, se programa confirmación a 3 minutos con imagen nueva.
- La confirmación se cancela si se detiene la vigilancia.
- Un cambio debe mantenerse durante 1-1.5 segundos (3 detecciones) para ser válido.
- Si el cambio desaparece antes de confirmarse, se descarta sin alertar.
- La arquitectura mantiene separación: detección → monitorización → alerta → Telegram.
- Cada iteración debe ser pequeña, compilable y documentada.

## Riesgos y puntos de atención

- falsos positivos parcialmente mitigados por estabilización, pero aún posibles con cambios sostenidos no deseados
- dependencia de conexión a internet para Telegram
- tokens de bot expuestos en preferencias locales (sin cifrado en MVP)
- captura puede no ser exactamente del momento del cambio (usa último frame)
- cooldown de 60s puede ser muy largo o muy corto según el caso de uso
- sin cola de alertas: si falla el envío, se pierde la alerta
- confirmación a 3 minutos se pierde si la app se cierra o el sistema la mata
- lógica de estabilización puede perder cambios muy rápidos pero reales (< 1 segundo)

## Estado de infraestructura

- **Gradle Wrapper**: 8.5 (estable, corregido desde 9.0-milestone-1)
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Estado de build**: ✅ COMPILA CORRECTAMENTE
- **Warnings actuales**: mínimos (no bloqueantes)

## Recordatorio de disciplina SDD

Antes de cambios grandes:
1. revisar spec y documentación viva
2. hacer commit checkpoint
3. lanzar tarea pequeña al agente
4. revisar respuesta y archivos tocados
5. actualizar documentación viva
6. hacer commit