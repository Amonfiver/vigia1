# VIGIA1 - Diario de desarrollo

## Formato de entradas

Cada entrada debe incluir:
- fecha
- iteración o tarea
- objetivo
- qué se implementó
- archivos tocados
- limitaciones temporales
- siguiente paso recomendado

---

## Entrada 001 - Arranque del proyecto

### Fecha
2026-03-23

### Objetivo
Crear la base documental y estructural del proyecto VIGIA1 en modo SDD.

### Qué se hizo
- definición inicial del MVP
- creación de README y documentación base
- definición de arquitectura inicial
- definición de estrategia ROI manual
- creación de prompts iniciales para Cline
- conexión del repositorio GitHub oficial

### Archivos/áreas relevantes
- README.md
- docs/vision.md
- docs/mvp-scope.md
- docs/architecture.md
- docs/roi-strategy.md
- docs/telegram-flow.md
- docs/setup-dev.md
- docs/sdd/*
- prompts/*

### Limitaciones temporales
- todavía no existía proyecto Android funcional
- todo estaba en fase de definición

### Siguiente paso recomendado
Bootstrap del proyecto Android en Kotlin.

---

## Entrada 002 - Bootstrap Android

### Fecha
2026-03-23

### Objetivo
Crear la base del proyecto Android con estructura inicial.

### Qué se hizo
- creación del proyecto Android en Kotlin
- integración base de Jetpack Compose
- dependencias iniciales de CameraX, OkHttp y DataStore
- permisos de cámara e internet
- estructura inicial de paquetes
- pantalla principal base con placeholders

### Archivos/áreas relevantes
- build.gradle.kts
- settings.gradle.kts
- app/build.gradle.kts
- AndroidManifest.xml
- MainActivity.kt
- MainViewModel.kt
- modelos y repositorios base

### Limitaciones temporales
- preview de cámara todavía no real
- Telegram y ROI aún sin funcionalidad real

### Siguiente paso recomendado
Implementar preview de cámara real.

---

## Entrada 003 - Preview real de cámara

### Fecha
2026-03-23

### Objetivo
Integrar CameraX con preview real y completar la estructura de paquetes principal.

### Qué se hizo
- preview real con CameraX
- gestión de permisos de cámara
- placeholder sustituido por cámara real
- creación/preparación explícita de paquetes faltantes
- separación más clara entre UI, cámara y monitorización

### Archivos/áreas relevantes
- MainActivity.kt
- camera/CameraPreview.kt
- monitoring/MonitoringManager.kt
- telegram/TelegramService.kt
- utils/PermissionsHelper.kt

### Limitaciones temporales
- sin definición de ROI todavía
- sin análisis real
- sin Telegram real

### Siguiente paso recomendado
Implementar selección manual de ROI.

---

## Entrada 004 - Selección manual de ROI

### Fecha
2026-03-23

### Objetivo
Permitir al usuario dibujar manualmente un ROI sobre la preview.

### Qué se hizo
- modo de selección manual de ROI
- overlay táctil sobre la cámara
- dibujo de rectángulo
- confirmación y cancelación
- visualización del ROI seleccionado
- botón definir/redefinir ROI

### Archivos/áreas relevantes
- MainActivity.kt
- ui/components/RoiSelector.kt
- ui/components/RoiOverlay.kt
- MainViewModel.kt

### Limitaciones temporales
- ROI todavía no persistido en almacenamiento local
- sin análisis visual real

### Siguiente paso recomendado
Persistir el ROI y permitir reposicionarlo antes de confirmar.

---

## Entrada 005 - Persistencia y reposicionamiento del ROI

### Fecha
2026-03-23

### Objetivo
Guardar el ROI y mejorar la UX permitiendo moverlo antes de confirmar.

### Qué se hizo
- guardado real del ROI en DataStore
- recuperación automática del ROI al abrir la app
- visualización del ROI persistido
- posibilidad de reposicionar el ROI manteniendo pulsado dentro de él
- redefinición de ROI desde el flujo actual

### Archivos/áreas relevantes
- ui/components/RoiSelector.kt
- MainViewModel.kt
- DataStoreRoiRepository.kt

### Limitaciones temporales
- sin borrado explícito del ROI
- sin múltiples ROIs
- sin análisis real de cámara todavía

### Siguiente paso recomendado
Conectar frames reales de CameraX al análisis del ROI.

---

## Entrada 006 - Detección provisional desacoplada

### Fecha
2026-03-23

### Objetivo
Crear una base modular de detección visual para el ROI.

### Qué se hizo
- creación del paquete detection
- interfaz RoiDetector
- implementación provisional SimpleFrameDifferenceDetector
- integración del resultado de detección en MonitoringManager
- visualización del estado provisional de detección en UI

### Archivos/áreas relevantes
- detection/RoiDetector.kt
- detection/SimpleFrameDifferenceDetector.kt
- monitoring/MonitoringManager.kt
- MainViewModel.kt
- MainActivity.kt

### Limitaciones temporales
- la detección usa datos simulados, no frames reales
- no analiza aún píxeles reales del ROI
- no dispara alertas reales

### Siguiente paso recomendado
Sustituir la simulación por frames reales de CameraX.

---

## Entrada 007 - Frames reales de CameraX en detección

### Fecha
2026-03-23

### Objetivo
Sustituir la simulación de datos por frames reales de cámara en el análisis del ROI.

### Qué se hizo
- creación de FrameProcessor como ImageAnalysis.Analyzer
- conversión YUV a luminancia (grayscale) en tiempo real
- downscaling a 320x240 para rendimiento
- CameraPreview ahora expone StateFlow<FrameData?>
- MonitoringManager recibe frames reales vía connectCameraFrames()
- eliminada generación de datos aleatorios
- análisis usa luminancia real del ROI definido
- actualizada documentación viva (project-status, code-map, dev-log)

### Archivos/áreas relevantes
- camera/FrameProcessor.kt (nuevo)
- camera/CameraPreview.kt (modificado - añadido ImageAnalysis)
- monitoring/MonitoringManager.kt (modificado - frames reales)
- MainActivity.kt (modificado - conexión flujo de frames)
- MainViewModel.kt (modificado - método connectCameraFrames)
- docs/project-status.md (actualizado)
- docs/code-map.md (actualizado)
- docs/dev-log.md (esta entrada)

### Limitaciones temporales
- algoritmo de detección sigue siendo simple (diferencia de luminancia básica)
- sin compensación de iluminación ni normalización avanzada
- resolución de análisis fija a 320x240
- procesa 1 de cada 5 frames (~6fps) para no sobrecargar
- sin detección de movimiento ni análisis de textura

### Próximo paso recomendado
Implementar configuración y prueba funcional de Telegram.

---

## Entrada 008 - Configuración y prueba de Telegram

### Fecha
2026-03-23

### Objetivo
Hacer funcional la configuración de Telegram con guardado local y prueba manual real.

### Qué se hizo
- implementación real de TelegramService con OkHttp
- envío de mensajes de texto a Telegram Bot API
- manejo de errores de red y respuestas HTTP
- estado TelegramTestState para feedback visual (Idle, Loading, Success, Error)
- UI de configuración con campos Bot Token y Chat ID
- botón "Guardar" que persiste en DataStore
- botón "Probar" que envía mensaje de prueba real
- indicadores visuales de éxito (✓) o error (✗)
- recuperación automática de configuración al abrir la app
- validación de campos vacíos antes de guardar/probar

### Archivos/áreas relevantes
- telegram/TelegramService.kt (modificado - implementación real con OkHttp)
- ui/MainViewModel.kt (modificado - gestión config y prueba Telegram)
- MainActivity.kt (modificado - UI config Telegram funcional)
- DataStoreTelegramConfigRepository.kt (ya existente, sin cambios)
- docs/project-status.md (actualizado)
- docs/code-map.md (actualizado)
- docs/dev-log.md (esta entrada)

### Limitaciones temporales
- envío de imágenes pendiente (solo mensajes de texto)
- sin reintentos automáticos ante fallos de red
- tokens guardados en texto plano (sin cifrado)
- prueba es manual (no automática al detectar cambio)
- sin validación de formato de token ni chat_id

### Próximo paso recomendado
Implementar captura y envío manual de imagen a Telegram.

---

## Entrada 009 - Captura y envío manual de imagen

### Fecha
2026-03-23

### Objetivo
Implementar captura real de imagen desde cámara y envío manual a Telegram.

### Qué se hizo
- FrameProcessor modificado para almacenar último frame YUV completo
- métodos getLastFrameBitmap() y getLastFrameJpegBytes() para captura
- conversión YUV → NV21 → JPEG sin librerías externas
- TelegramService implementa sendImage() con multipart/form-data
- ImageCaptureState para estados de captura (Capturing, Sending, Success, Error)
- UI de captura con botón "📸 Capturar y enviar imagen"
- feedback visual durante captura y envío (spinners)
- mensajes de éxito/error en UI
- CameraPreview expone FrameProcessor para acceso a captura

### Archivos/áreas relevantes
- camera/FrameProcessor.kt (modificado - almacenamiento y captura de frames)
- telegram/TelegramService.kt (modificado - sendImage con multipart)
- ui/MainViewModel.kt (modificado - captureAndSendImage, ImageCaptureState)
- camera/CameraPreview.kt (modificado - onCameraReady con FrameProcessor)
- MainActivity.kt (modificado - ImageCaptureSection UI)
- docs/project-status.md (actualizado)
- docs/code-map.md (actualizado)
- docs/dev-log.md (esta entrada)

### Limitaciones temporales
- captura usa último frame disponible, no frame exclusivo del momento exacto
- sin compresión avanzada de imágenes
- resolución de captura es la de análisis (típicamente 640x480 o menor)
- sin manejo de rotación de imagen
- proceso manual (no automático al detectar cambio)

### Próximo paso recomendado
Implementar envío automático de alerta cuando se detecte cambio: mensaje "Minda Requiere Atención" + imagen automática + segunda captura a los 3 minutos.

---

## Entrada 010 - Alertas automáticas con Telegram

### Fecha
2026-03-23

### Objetivo
Conectar el detector con el flujo automático de alerta a Telegram cuando se detecte un cambio relevante.

### Qué se hizo
- creación de AlertManager en paquete alert/ para gestionar alertas automáticas
- AlertState con estados: Idle, Sending, Success, Error, Cooldown
- integración de AlertManager en MainViewModel como dependencia inyectada
- observación de detectionResult desde MainViewModel para disparar alertas
- envío automático cuando hasChange == true durante monitoring activo
- mensaje automático: "🚨 <b>Minda Requiere Atención</b>" con texto explicativo
- captura automática de imagen del momento del cambio
- protección anti-spam: cooldown de 60 segundos entre alertas
- feedback visual en UI del estado de alerta (enviando, éxito, error, cooldown)
- método clearAlertState() para limpiar mensajes de la UI
- limpieza de recursos en onCleared() del ViewModel
- AutoAlertSection composable con diseño visual distintivo por estado
- timestamps en mensajes de éxito/error para trazabilidad
- actualización de cabeceras de documentación en archivos modificados

### Archivos/áreas relevantes
- alert/AlertManager.kt (nuevo - gestor de alertas automáticas)
- ui/MainViewModel.kt (modificado - integración AlertManager y observación de alertas)
- MainActivity.kt (modificado - AutoAlertSection UI y actualización de imports)
- docs/project-status.md (actualizado)
- docs/code-map.md (actualizado)
- docs/dev-log.md (esta entrada)

### Limitaciones temporales
- cooldown fijo de 60 segundos (no configurable por UI)
- sin cola de alertas pendientes (si falla, se pierde la alerta)
- una sola alerta por evento (sin sistema de escalada)
- sin persistencia de alertas enviadas
- captura usa último frame disponible, no frame exclusivo del momento exacto
- segunda captura a los 3 minutos NO implementada todavía (siguiente iteración)

### Próximo paso recomendado
Implementar segunda captura a los 3 minutos después de la primera alerta como confirmación del estado.

---

## Entrada 011 - Confirmación diferida a 3 minutos

### Fecha
2026-03-23

### Objetivo
Implementar segunda captura de confirmación exactamente 3 minutos después de una alerta automática exitosa.

### Qué se hizo
- añadido `ConfirmationState` con estados: Idle, Scheduled, Sending, Success, Error
- `scheduleConfirmation()` programa el envío 3 minutos después usando coroutine delay
- countdown en tiempo real que actualiza UI cada segundo (180s → 0s)
- captura de imagen nueva en el momento de la confirmación (no reutiliza la primera)
- caption claro: "📸 Confirmación 3 minutos después - Estado actual del área"
- `cancelConfirmation()` para cancelar si se detiene la vigilancia
- `ConfirmationSection` composable con diseño visual distintivo (púrpura/azul)
- integración en MainViewModel con observación de estado
- método `stopMonitoring()` cancela confirmación pendiente

### Archivos/áreas relevantes
- alert/AlertManager.kt (modificado - ConfirmationState, scheduleConfirmation, countdown, sendConfirmation)
- ui/MainViewModel.kt (modificado - confirmationState en UI, cancelConfirmation en stopMonitoring)
- MainActivity.kt (modificado - ConfirmationSection UI, imports)
- docs/project-status.md (actualizado)
- docs/dev-log.md (esta entrada)
- docs/code-map.md (actualizado)

### Limitaciones temporales
- confirmación se pierde si la app se cierra antes de los 3 minutos (sin persistencia)
- delay exacto de 3 minutos (180s), no configurable
- sin reintentos si falla el envío de confirmación
- una sola confirmación por alerta

### Próximo paso recomendado
Implementar estabilización del detector para reducir falsos positivos por picos breves.

---

## Entrada 012 - Estabilización del detector

### Fecha
2026-03-23

### Objetivo
Reducir falsos positivos implementando lógica de confirmación consecutiva antes de disparar alertas.

### Qué se hizo
- añadida capa de estabilización en MonitoringManager (no se tocó el detector)
- lógica de detección consecutiva: requiere 3 detecciones seguidas para confirmar cambio
- timeout de 2 segundos entre detecciones: si pasa más tiempo, se resetea el contador
- mensajes de estado progresivos: "Detectando... (1/3)" → "Detectando... (2/3)" → "✓ Cambio CONFIRMADO"
- parámetros configurables por constructor: consecutiveDetectionsRequired, stabilizationTimeoutMs
- constantes en companion object: DEFAULT_CONSECUTIVE_DETECTIONS = 3, DEFAULT_STABILIZATION_TIMEOUT_MS = 2000
- reseteo completo de estado de estabilización al detener vigilancia o cambiar ROI
- método privado applyStabilization() que filtra el resultado crudo del detector
- hasChange solo es true cuando el cambio está confirmado, nunca antes
- documentación extensiva de la lógica de estabilización en la cabecera del archivo

### Archivos/áreas relevantes
- monitoring/MonitoringManager.kt (modificado - capa de estabilización completa)
- docs/project-status.md (actualizado - sección de estabilización)
- docs/dev-log.md (esta entrada)
- docs/code-map.md (actualizado - descripción de MonitoringManager)

### Limitaciones temporales
- lógica de estabilización es simple (contador con timeout), no analiza patrones complejos
- puede perder cambios muy rápidos pero reales (< 1 segundo de duración)
- sin ajuste de sensibilidad desde UI (threshold, detecciones requeridas)
- número de detecciones consecutivas fijo en código, no configurable por usuario final
- detector base sigue siendo SimpleFrameDifferenceDetector (sin mejoras al algoritmo)

### Cómo se prueba manualmente
1. Iniciar vigilancia con ROI definido
2. Hacer un cambio breve en el área (ej: pasar la mano rápido)
3. Verificar que UI muestra "Detectando... (1/3)" pero NO dispara alerta
4. Verificar que al cabo de unos segundos se resetea ("Cambio no confirmado - se perdió la señal")
5. Hacer un cambio sostenido en el área (ej: dejar un objeto durante 2 segundos)
6. Verificar que UI progresa: (1/3) → (2/3) → "✓ Cambio CONFIRMADO"
7. Verificar que al confirmarse se dispara la alerta automática a Telegram
8. Detener vigilancia y verificar que el estado se resetea correctamente

### Próximo paso recomendado
MVP de vigilancia básica **completo y estabilizado**. Posibles mejoras:
- Ajustar sensibilidad/cooldown/detecciones consecutivas desde UI
- Persistencia de alertas ante cierre de app
- Sistema de múltiples ROIs
- Historial/visualizador de alertas pasadas

---

## Entrada 013 - Cierre de fase: revisión y saneamiento

### Fecha
2026-03-23

### Objetivo
Revisar el estado real del proyecto tras generación del Gradle Wrapper, cerrar correctamente la fase anterior y dejar el proyecto listo para continuar.

### Qué se encontró durante revisión
1. **Gradle Wrapper generado con versión inestable**: Android Studio generó `gradle-9.0-milestone-1-bin.zip` (pre-release)
2. **Error de compilación en RoiSelector.kt**: Faltaba import `androidx.compose.ui.unit.sp` para usar `12.sp`
3. **Infraestructura incompleta**: El wrapper funcionaba pero no se había verificado compilación real
4. **Documentación necesitaba actualización**: Project-status no reflejaba estado de cierre de fase

### Qué se corrigió
1. **gradle-wrapper.properties**: 
   - Cambiado de `gradle-9.0-milestone-1-bin.zip` a `gradle-8.5-bin.zip` (versión estable)
   - Añadida cabecera de documentación consistente con el proyecto

2. **RoiSelector.kt**:
   - Añadido import faltante: `import androidx.compose.ui.unit.sp`
   - Compilación ahora exitosa

3. **Verificación de build**:
   - Ejecutado `.\gradlew.bat :app:compileDebugKotlin`
   - Build exitoso con solo 2 warnings menores (no bloqueantes)

### Archivos/áreas relevantes
- gradle/wrapper/gradle-wrapper.properties (modificado - corrección de versión)
- app/src/main/java/com/vigia/app/ui/components/RoiSelector.kt (modificado - import faltante)
- docs/project-status.md (actualizado - fase marcada como CERRADA)
- docs/dev-log.md (esta entrada)

### Estado final de la fase

**FASE CERRADA**: MVP funcional básico + estabilización

| Aspecto | Estado |
|---------|--------|
| Compilación | ✅ ÉXITO |
| Gradle Wrapper | ✅ 8.5 estable |
| Funcionalidades MVP | ✅ Completas |
| Estabilización detector | ✅ Implementada |
| Documentación viva | ✅ Actualizada |
| Warnings | 2 menores (no bloqueantes) |

### Tracking de subtareas completadas

- [x] Revisar estado del proyecto post-generación de wrapper
- [x] Corregir versión de Gradle (9.0-milestone-1 → 8.5 estable)
- [x] Corregir error de compilación (import faltante en RoiSelector.kt)
- [x] Verificar build exitoso
- [x] Actualizar docs/project-status.md (fase cerrada)
- [x] Añadir entrada en docs/dev-log.md (cierre de fase)
- [x] Documentar estado final y tracking completo

### Limitaciones temporales
- Build tiene 2 warnings menores (no afectan funcionamiento)
- No se añadieron nuevas funcionalidades (solo correcciones)
- Pruebas manuales en dispositivo real pendientes (requieren despliegue)

### Cómo verificar manualmente el estado actual

1. **Verificar compilación**:
   ```bash
   .\gradlew.bat :app:compileDebugKotlin
   ```
   Resultado esperado: `BUILD SUCCESSFUL`

2. **Verificar estructura**:
   - Revisar que `gradle/wrapper/gradle-wrapper.properties` apunte a Gradle 8.5

3. **Verificar código**:
   - Revisar que `RoiSelector.kt` tenga import de `androidx.compose.ui.unit.sp`

4. **Para probar en dispositivo**:
   ```bash
   .\gradlew.bat :app:installDebug
   ```

### Fase cerrada

✅ **La fase MVP funcional básico + estabilización puede darse por CERRADA**

El proyecto está listo para continuar con nuevas mejoras en fases posteriores.

---

## Entrada 014 - Corrección de bugs críticos en pruebas reales

### Fecha
2026-03-24

### Objetivo
Corregir bugs críticos detectados durante pruebas en dispositivo físico antes de continuar con nuevas funcionalidades.

### Problemas detectados y corregidos

#### 1. Crash al guardar configuración de Telegram
**Síntoma**: Al pulsar "Guardar" en la configuración de Telegram, la app crasheaba inmediatamente.

**Causa raíz**: En `TelegramConfig.kt`, el companion object intentaba crear una instancia estática:
```kotlin
val EMPTY = TelegramConfig("", "")
```
Esto violaba las validaciones del bloque `init`:
```kotlin
init {
    require(botToken.isNotBlank()) { "botToken no puede estar vacío" }
    require(chatId.isNotBlank()) { "chatId no puede estar vacío" }
}
```
Cuando la clase se cargaba, el companion object se inicializaba, lanzando `IllegalArgumentException` inmediatamente, independientemente de si se usaba `EMPTY` o no.

**Corrección**: Cambiado a inicialización lazy:
```kotlin
val EMPTY: TelegramConfig by lazy { 
    TelegramConfig("__empty__", "__empty__") 
}
```

#### 2. Crash potencial en ROI (mismo patrón)
**Síntoma**: Potencial crash similar al de TelegramConfig.

**Causa raíz**: En `Roi.kt`:
```kotlin
val UNDEFINED = Roi(0f, 0f, 0f, 0f)
```
Violaba:
```kotlin
init {
    require(left < right) { "left debe ser menor que right" }
    require(top < bottom) { "top debe ser menor que bottom" }
}
```

**Corrección**: Cambiado a lazy con coordenadas válidas:
```kotlin
val UNDEFINED: Roi by lazy { 
    Roi(0f, 0f, 0.1f, 0.1f)
}
```
Y añadido método `isValid()` para verificación consistente.

#### 3. Orientación no forzada a landscape
**Síntoma**: La app rotaba libremente, pero el caso de uso industrial requiere orientación horizontal fija.

**Causa raíz**: `AndroidManifest.xml` no tenía configurada la orientación de MainActivity.

**Corrección**: Añadido en el manifest:
```xml
<activity
    android:screenOrientation="landscape"
    android:configChanges="orientation|screenSize">
```

### Archivos modificados
- `app/src/main/AndroidManifest.xml` - Orientación forzada landscape
- `app/src/main/java/com/vigia/app/domain/model/TelegramConfig.kt` - Fix crash companion object
- `app/src/main/java/com/vigia/app/domain/model/Roi.kt` - Fix crash companion object + isValid()

### Limitaciones temporales
- Pendiente verificación completa en dispositivo físico
- El ROI persistía correctamente, pero la UX podría mejorar para hacer más visible el estado guardado

### Tracking de subtareas

- [x] Identificar causa del crash de Telegram
- [x] Corregir TelegramConfig.EMPTY (lazy initialization)
- [x] Identificar causa del crash potencial de ROI
- [x] Corregir Roi.UNDEFINED (lazy initialization)
- [x] Añadir método isValid() a Roi para consistencia
- [x] Forzar orientación landscape en AndroidManifest
- [x] Verificar compilación exitosa
- [x] Actualizar docs/project-status.md
- [x] Añadir entrada en docs/dev-log.md

### Fase actual
**CORRECCIÓN DE BUGS COMPLETADA** - Pendiente validación en dispositivo físico.

### Siguiente paso recomendado
1. Probar en dispositivo físico que:
   - La app se abre en horizontal
   - Se puede guardar token y chat_id de Telegram sin crash
   - Al reabrir la app, los datos de Telegram se recuperan
   - El ROI se guarda y se recupera correctamente
2. Si todo funciona, cerrar esta fase y continuar con mejoras del MVP

---

## Entrada 015 - Corrección del flujo de selección de ROI

### Fecha
2026-03-24

### Objetivo
Corregir el problema crítico donde el ROI no quedaba fijado al soltar el dedo, dejando al usuario atrapado en el modo de selección.

### Problema observado en dispositivo
- Usuario entra en modo "Definir ROI"
- Dibuja el rectángulo del tamaño deseado
- Al soltar el dedo, el rectángulo NO queda fijado visualmente
- No hay sensación clara de ROI ya definido
- El usuario queda atrapado en la pantalla de definición

### Causa raíz del problema
El componente usaba `detectDragGestures` de Compose, que tiene comportamientos internos complejos:
- Requiere un umbral mínimo de movimiento para considerar que hay un "drag"
- El callback `onDragEnd` no siempre se invoca si el gesto no cumple ciertos criterios internos
- En dispositivos táctiles reales, un simple "tocar y soltar rápido" podía no activar el flujo completo de drag

Código problemático (ANTES):
```kotlin
detectDragGestures(
    onDragStart = { ... },
    onDrag = { change, dragAmount -> ... },
    onDragEnd = {
        // Este callback NO siempre se ejecutaba
        selectionState = RoiSelectionState.Selected(rect)
    }
)
```

### Solución aplicada
Reemplazado `detectDragGestures` por `awaitPointerEventScope` con manejo explícito de eventos táctiles:
- `PointerEventType.Press`: Inicia la selección o el movimiento
- `PointerEventType.Move`: Actualiza el rectángulo durante el arrastre
- `PointerEventType.Release`: **Siempre** finaliza la selección y fija el ROI

Código corregido (DESPUÉS):
```kotlin
awaitPointerEventScope {
    while (true) {
        val event = awaitPointerEvent()
        when (event.type) {
            PointerEventType.Press -> { ... }
            PointerEventType.Move -> { ... }
            PointerEventType.Release -> {
                // AHORA SIEMPRE se ejecuta al soltar el dedo
                selectionState = RoiSelectionState.Selected(rect)
            }
        }
    }
}
```

### Cambios adicionales
- Reducido el umbral mínimo de ROI de 5% a 2% para ser más permisivo
- Eliminadas importaciones no necesarias (`detectDragGestures`)
- Actualizada documentación del archivo

### Archivos modificados
- `app/src/main/java/com/vigia/app/ui/components/RoiSelector.kt` - Reescrito completamente con awaitPointerEventScope

### Tracking de subtareas

- [x] Identificar causa del problema de selección de ROI
- [x] Reemplazar detectDragGestures por awaitPointerEventScope
- [x] Implementar manejo explícito de Press/Move/Release
- [x] Asegurar que el ROI queda fijado al soltar el dedo
- [x] Mantener funcionalidad de mover ROI existente
- [x] Verificar compilación exitosa
- [x] Actualizar docs/project-status.md
- [x] Añadir entrada en docs/dev-log.md

### Resultado esperado
- Al soltar el dedo, el ROI queda fijado visualmente (rectángulo verde)
- Aparecen los botones "Confirmar ROI" / "Cancelar"
- El usuario puede confirmar y salir del modo de definición
- Desaparece la sensación de estar "atrapado"

### Fase actual
**CORRECCIÓN DE BUGS COMPLETADA** - Pendiente validación final en dispositivo físico.

---

## Entrada 016 - [Siguiente fase pendiente]
