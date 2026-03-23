# VIGIA1 - Mapa del código

## Objetivo

Este documento describe la estructura actual del código para que cualquier desarrollador o agente pueda entender rápidamente qué existe, para qué sirve y dónde tocar según la tarea.

---

## Estructura principal

```text
app/src/main/java/com/vigia/app/
├── MainActivity.kt                    # Activity principal, UI raíz (incluye AutoAlertSection)
├── alert/                             # Alertas automáticas basadas en detección
│   └── AlertManager.kt                # Gestor de alertas automáticas con anti-spam
├── camera/                            # Captura y procesamiento de frames
│   ├── CameraPreview.kt               # Preview + ImageAnalysis use case
│   └── FrameProcessor.kt              # Analizador YUV → luminancia + captura imagen
├── data/                              # Persistencia
│   └── local/
│       ├── DataStoreRoiRepository.kt  # Persistencia de ROI
│       └── DataStoreTelegramConfigRepository.kt # Persistencia Telegram
├── detection/                         # Lógica de detección (reemplazable)
│   ├── RoiDetector.kt                 # Interfaz del detector
│   └── SimpleFrameDifferenceDetector.kt # Implementación simple (provisional)
├── domain/                            # Modelos y contratos
│   ├── model/
│   │   ├── Roi.kt                     # Entidad ROI
│   │   └── TelegramConfig.kt          # Entidad config Telegram
│   └── repository/
│       ├── RoiRepository.kt           # Interfaz persistencia ROI
│       └── TelegramConfigRepository.kt # Interfaz persistencia Telegram
├── monitoring/                        # Coordinación vigilancia + estabilización
│   └── MonitoringManager.kt           # Estado vigilancia + análisis periódico + CAPA DE ESTABILIZACIÓN
├── telegram/                          # Envío de alertas
│   └── TelegramService.kt             # Servicio Telegram con OkHttp (mensajes + imágenes)
├── ui/                                # Interfaz de usuario
│   ├── MainViewModel.kt               # ViewModel principal (gestión estado completa)
│   └── components/
│       ├── RoiOverlay.kt              # Dibuja ROI sobre cámara
│       └── RoiSelector.kt             # Selector táctil de ROI
└── utils/                             # Utilidades
    └── PermissionsHelper.kt           # Gestión de permisos
```

---

## Flujo de datos principal

### Análisis y detección (con estabilización)
```
CameraX → FrameProcessor → FrameData → MonitoringManager
                                              ↓
                              [Capa de estabilización]
                              Requiere 3 detecciones consecutivas
                              dentro de 2 segundos para confirmar
                                              ↓
RoiDetector.analyze(frameData, roi) → DetectionResult crudo
                                              ↓
                              applyStabilization() → DetectionResult estabilizado
                                              ↓
                                       MainViewModel → UI
```

### Telegram (flujo manual)
```
UI → MainViewModel → TelegramService → OkHttp → Telegram Bot API
                         ↓
                   TelegramResult → UI (estado éxito/error)
```

### Captura de imagen (flujo manual)
```
UI → MainViewModel → FrameProcessor.getLastFrameJpegBytes()
                                              ↓
                              TelegramService.sendImage()
                                              ↓
                              TelegramResult → UI
```

### Alertas automáticas (flujo cuando se detecta cambio confirmado)
```
MonitoringManager.detectionResult → MainViewModel
                                              ↓
                         if hasChange == true (cambio confirmado por estabilización)
                                              ↓
                              AlertManager.onDetectionResult()
                                              ↓
                    ┌─────────────────────────┼─────────────────────────┐
                    ↓                         ↓                         ↓
           [Cooldown check]      FrameProcessor.getLastFrameJpegBytes()
           [Config check]                                  ↓
                    ↓                         ↓                         ↓
           AlertState.Cooldown    TelegramService.sendMessage()  TelegramService.sendImage()
           (si aplica)                      ↓                         ↓
                                            ↓                         ↓
                                    AlertState.Success/Error ←───────┘

```

---

## Descripción de componentes clave

### CameraPreview (camera/CameraPreview.kt)
- **Qué hace**: Muestra preview de cámara Y configura análisis de frames
- **Retorna**: StateFlow<FrameData?> y FrameProcessor vía onCameraReady
- **Use cases vinculados**: Preview + ImageAnalysis
- **Cuándo tocar**: Cambios en resolución de análisis, formato de salida

### FrameProcessor (camera/FrameProcessor.kt)
- **Qué hace**: Recibe ImageProxy de CameraX, extrae luminancia Y, downscalea, almacena frames para captura
- **Frecuencia**: Procesa 1 de cada 5 frames (~6fps)
- **Output**: FrameData con array de luminancia 320x240
- **Captura**: getLastFrameBitmap(), getLastFrameJpegBytes() - convierte YUV → JPEG
- **Cuándo tocar**: Optimizaciones de rendimiento, cambio de resolución, mejoras en captura

### MonitoringManager (monitoring/MonitoringManager.kt)
- **Qué hace**: Coordina vigilancia activa, análisis periódico Y **estabilización de detección**
- **Recibe**: Flujo de FrameData vía connectCameraFrames()
- **Hace**: Analiza cada 500ms usando RoiDetector
- **CAPA DE ESTABILIZACIÓN**: 
  - Requiere 3 detecciones consecutivas antes de confirmar cambio (configurable)
  - Timeout de 2 segundos entre detecciones, si se pasa se resetea contador
  - Mensajes progresivos: "Detectando... (1/3)" → "✓ Cambio CONFIRMADO"
  - hasChange solo es true cuando el cambio está confirmado
- **Emite**: Estado de vigilancia + DetectionResult estabilizado
- **Parámetros configurables**: consecutiveDetectionsRequired, stabilizationTimeoutMs
- **Cuándo tocar**: Cambios en frecuencia de análisis, lógica de estabilización, umbral de confirmación

### RoiDetector (detection/RoiDetector.kt)
- **Qué es**: Interfaz para estrategias de detección
- **Métodos**: analyze(frameData, roi), reset()
- **Implementación actual**: SimpleFrameDifferenceDetector
- **Cuándo tocar**: Crear nuevas estrategias (movimiento, ML, etc.)

### SimpleFrameDifferenceDetector (detection/SimpleFrameDifferenceDetector.kt)
- **Qué hace**: Compara luminancia actual vs referencia
- **Algoritmo**: Diferencia de checksum normalizado
- **Umbral**: Configurable (default 30)
- **Estado**: PROVISIONAL - debe reemplazarse por análisis más robusto
- **Nota**: La estabilización NO está aquí, está en MonitoringManager (capa superior)
- **Cuándo tocar**: Nunca mejorarlo, reemplazar por nueva implementación de RoiDetector

### AlertManager (alert/AlertManager.kt)
- **Qué hace**: Gestiona envío automático de alertas y confirmación diferida a 3 minutos
- **Anti-spam**: Cooldown de 60 segundos entre alertas (evita spam continuo)
- **Estados AlertState**: Idle | Sending | Success | Error | Cooldown
- **Estados ConfirmationState**: Idle | Scheduled | Sending | Success | Error
- **Confirmación**: Programada 3 minutos después de alerta exitosa, captura imagen nueva
- **Métodos clave**:
  - onDetectionResult() - punto de entrada, verifica condiciones y dispara alerta
  - sendAlert() - envía mensaje + imagen de forma secuencial
  - scheduleConfirmation() - programa confirmación a los 3 minutos con countdown
  - sendConfirmation() - envía imagen de confirmación con caption apropiado
  - cancelConfirmation() - cancela confirmación programada (útil al detener vigilancia)
  - clearState() / clearConfirmationState() - resetea estados
- **Cuándo tocar**: Cambiar cooldown, modificar delay de confirmación, añadir persistencia

### TelegramService (telegram/TelegramService.kt)
- **Qué hace**: Envía mensajes e imágenes vía Telegram Bot API
- **Dependencia**: OkHttp para HTTP
- **Métodos principales**: 
  - sendMessage() - mensajes de texto con FormBody
  - sendImage() - imágenes con MultipartBody (sendPhoto)
  - sendMessageWithImage() - mensaje + imagen combinados
- **Retorna**: TelegramResult (Success o Error)
- **Configuración**: Requiere TelegramConfig válido
- **Cuándo tocar**: Añadir reintentos, cola de mensajes, compresión avanzada

### MainViewModel (ui/MainViewModel.kt)
- **Qué hace**: Gestiona todo el estado de la UI y coordina acciones
- **Gestiona**: ROI, Telegram, Monitorización, Detección, Captura de imagen, Alertas automáticas
- **Estados**: MainUiState con ScreenMode, TelegramTestState, DetectionResult, ImageCaptureState, AlertState
- **Dependencias**: AlertManager (inyectado por constructor)
- **Métodos clave**:
  - connectCamera() - vincula cámara y FrameProcessor
  - saveTelegramConfig() - guarda config Telegram
  - testTelegramConnection() - prueba manual de envío de mensaje
  - captureAndSendImage() - captura y envío manual de imagen
  - startMonitoring() / stopMonitoring() - control vigilancia
  - clearAlertState() - limpia estado de alerta en UI

---

## Puntos de extensión recomendados

### Para ajustar estabilización de detección
Modificar parámetros en constructor de MonitoringManager:
- consecutiveDetectionsRequired: número de detecciones consecutivas (default: 3)
- stabilizationTimeoutMs: tiempo máximo entre detecciones (default: 2000ms)

### Para mejorar detección (reemplazar algoritmo)
Crear nueva clase implementando RoiDetector, inyectar en MonitoringManager. La capa de estabilización seguirá funcionando.

### Para modificar comportamiento de alertas automáticas
Modificar AlertManager (cooldown, lógica de envío, mensajes) sin tocar TelegramService.

### Para permitir ajuste de estabilización desde UI
Añadir campos en MainUiState, métodos en MainViewModel para modificar los parámetros de MonitoringManager dinámicamente.

### Para modificar delay de confirmación (3 minutos)
Cambiar constante CONFIRMATION_DELAY_MS en AlertManager companion object.

### Para persistir confirmación ante cierre de app
Implementar persistencia del Job de confirmación usando WorkManager o alarmas del sistema.

### Para múltiples ROIs
Cambiar currentRoi: Roi? por List<Roi> en MonitoringManager y adaptar UI. La estabilización funciona igual para cada ROI.

### Para reintentos de Telegram
Ampliar AlertManager o TelegramService con cola de mensajes pendientes y lógica de reintento.

### Para compresión de imágenes
Añadir opciones de calidad JPEG en FrameProcessor antes de envío.

### Para múltiples tipos de alerta
Extender AlertState con nuevos tipos y modificar UI en AutoAlertSection.

---

## Archivos a NO tocar sin razón fuerte

- domain/model/Roi.kt: Modelo estable, cambios rompen persistencia
- domain/model/TelegramConfig.kt: Modelo estable, cambios rompen persistencia
- domain/repository/*: Contratos de persistencia, afectan DataStore
- data/local/*: Implementación de persistencia, migraciones complejas

---

## Dependencias externas clave

- CameraX: Preview, ImageAnalysis (androidx.camera)
- Compose: UI (androidx.compose)
- DataStore: Persistencia (androidx.datastore)
- OkHttp: HTTP para Telegram (com.squareup.okhttp3)
- Kotlin Coroutines: Flujos asíncronos (kotlinx.coroutines)

---

## Notas de arquitectura

- Separación por capas: UI → Monitoring → Detection → Camera
- UI → Telegram: Directo vía ViewModel para pruebas manuales
- Inyección de dependencias: Constructor-based, fácil de testear
- Estado reactivo: StateFlow en todos los niveles
- Lifecycle-aware: Camera vinculado a lifecycle de Compose/Activity
- Captura de imagen: FrameProcessor almacena último frame, conversión YUV→JPEG bajo demanda
- Alertas automáticas: DetectionResult → AlertManager → TelegramService, con anti-spam integrado
- Confirmación diferida: AlertManager programa segunda captura a 3 minutos con countdown en UI
- **Estabilización de detección**: Capa en MonitoringManager que requiere 3 detecciones consecutivas antes de confirmar cambio, reduciendo falsos positivos por picos breves

## Estado de infraestructura (post-revisión)

Después de la revisión de cierre de fase:

- **Gradle Wrapper**: 8.5 (corregido desde 9.0-milestone-1 inestable)
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Estado de build**: ✅ COMPILA CORRECTAMENTE
- **Scripts disponibles**: `gradlew` (Linux/Mac), `gradlew.bat` (Windows)
- **Warnings**: 2 menores (no bloqueantes)

### Comandos útiles

```bash
# Compilar
.\gradlew.bat :app:compileDebugKotlin

# Instalar en dispositivo
.\gradlew.bat :app:installDebug

# Limpiar build
.\gradlew.bat clean
```

### Fase actual

**CERRADA**: MVP funcional básico + estabilización del detector completados.
