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