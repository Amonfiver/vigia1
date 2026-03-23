# VIGIA1 - Mapa del código

## Objetivo

Este documento describe la estructura actual del código para que cualquier desarrollador o agente pueda entender rápidamente qué existe, para qué sirce y dónde tocar según la tarea.

---

## Estructura principal

```text
app/src/main/java/com/vigia/app/
├── MainActivity.kt                    # Activity principal, UI raíz
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
├── monitoring/                        # Coordinación vigilancia
│   └── MonitoringManager.kt           # Estado vigilancia + análisis periódico
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

### Análisis y detección
```
CameraX → FrameProcessor → FrameData → MonitoringManager
                                              ↓
RoiDetector.analyze(frameData, roi) → DetectionResult
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
- **Qué hace**: Coordina vigilancia activa y análisis periódico
- **Recibe**: Flujo de FrameData vía connectCameraFrames()
- **Hace**: Analiza cada 500ms usando RoiDetector
- **Emite**: Estado de vigilancia + DetectionResult
- **Cuándo tocar**: Cambios en frecuencia de análisis, lógica de trigger

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
- **Cuándo tocar**: Nunca mejorarlo, reemplazar por nueva implementación de RoiDetector

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
- **Gestiona**: ROI, Telegram, Monitorización, Detección, Captura de imagen
- **Estados**: MainUiState con ScreenMode, TelegramTestState, DetectionResult, ImageCaptureState
- **Métodos clave**:
  - connectCamera() - vincula cámara y FrameProcessor
  - saveTelegramConfig() - guarda config Telegram
  - testTelegramConnection() - prueba manual de envío de mensaje
  - captureAndSendImage() - captura y envío manual de imagen
  - startMonitoring() / stopMonitoring() - control vigilancia

---

## Puntos de extensión recomendados

### Para mejorar detección
Crear nueva clase implementando RoiDetector, inyectar en MonitoringManager.

### Para envío automático de alertas
En MainViewModel, observar detectionResult y cuando hasChange == true, llamar a TelegramService.sendMessage() y sendImage().

### Para segunda captura a los 3 minutos
Usar WorkManager o Handler.postDelayed() para programar segunda captura tras alerta inicial.

### Para múltiples ROIs
Cambiar currentRoi: Roi? por List<Roi> en MonitoringManager y adaptar UI.

### Para reintentos de Telegram
Ampliar TelegramService con cola de mensajes pendientes y lógica de reintento.

### Para compresión de imágenes
Añadir opciones de calidad JPEG en FrameProcessor antes de envío.

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