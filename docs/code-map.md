# VIGIA1 - Mapa del código

## Objetivo

Este documento describe la estructura actual del código para que cualquier desarrollador o agente pueda entender rápidamente qué existe, para qué sirce y dónde tocar según la tarea.

---

## Estructura principal

```text
app/src/main/java/com/vigia/app/
├── MainActivity.kt                    # Activity principal, UI raíz (modo normal + entrenamiento)
├── alert/                             # Alertas automáticas basadas en detección
│   └── AlertManager.kt                # Gestor de alertas automáticas con anti-spam
├── camera/                            # Captura y procesamiento de frames
│   ├── CameraPreview.kt               # Preview + ImageAnalysis use case (soporte cromático)
│   └── FrameProcessor.kt              # Analizador YUV → HSV (color) + captura imagen
├── classification/                    # Clasificación automática basada en dataset
│   ├── ColorFeatures.kt               # Features HSV (histograma + estadísticas)
│   └── DatasetClassifier.kt           # Clasificador k-NN con caché de features, TopMatchInfo
├── data/                              # Persistencia
│   └── local/
│       ├── DataStoreRoiRepository.kt  # Persistencia de ROI
│       ├── DataStoreTelegramConfigRepository.kt # Persistencia Telegram
│       ├── FileOkBaselineRepository.kt # Baseline OK legacy (obsoleto)
│       └── FileTrainingDatasetRepository.kt # Dataset entrenamiento OK/OBSTACULO/FALLO
├── detection/                         # Lógica de detección
│   ├── RoiDetector.kt                 # Interfaz del detector (legacy)
│   ├── SimpleFrameDifferenceDetector.kt # Implementación simple luminancia (legacy)
│   ├── ColorFrameData.kt              # Estructura de datos cromáticos HSV
│   ├── ColorBasedDetector.kt          # Detector basado en análisis cromático
│   └── TransferSubRoiDetector.kt      # NUEVO: Detector de subROI del transfer por color
├── domain/                            # Modelos y contratos
│   ├── model/
│   │   ├── Roi.kt                     # Entidad ROI
│   │   ├── TelegramConfig.kt          # Entidad config Telegram
│   │   ├── OkBaselineSample.kt        # Baseline OK legacy
│   │   └── TrainingSample.kt          # Muestras de entrenamiento etiquetadas
│   └── repository/
│       ├── RoiRepository.kt           # Interfaz persistencia ROI
│       ├── TelegramConfigRepository.kt # Interfaz persistencia Telegram
│       ├── OkBaselineRepository.kt    # Interfaz baseline OK legacy
│       └── TrainingDatasetRepository.kt # Interfaz dataset entrenamiento
├── monitoring/                        # Coordinación vigilancia + estabilización
│   └── MonitoringManager.kt           # Estado vigilancia + análisis cromático periódico + subROI + clasificación
├── telegram/                          # Envío de alertas
│   └── TelegramService.kt             # Servicio Telegram con OkHttp
├── ui/                                # Interfaz de usuario
│   ├── MainViewModel.kt               # ViewModel principal (entrenamiento, baseline, vigilancia, subROI, top match)
│   └── components/
│       ├── RoiOverlay.kt              # Dibuja ROI sobre cámara
│       └── RoiSelector.kt             # Selector táctil de ROI
└── utils/                             # Utilidades
    └── PermissionsHelper.kt           # Gestión de permisos
```

---

## Flujo de datos principal

### Clasificación con subROI del transfer (NUEVO)
```
ColorFrameData (frame completo)
  ↓
TransferSubRoiDetector.detectTransferSubRoi()
  ↓
Detecta subROI del transfer (excluye fondo blanco/negro)
  ↓
Extrae sub-frame de la subROI (extractRegion)
  ↓
DatasetClassifier.classify(subFrame)
  ↓
Extrae features de la subROI (NO del ROI completo)
  ↓
Compara contra caché de features del dataset
  ↓
ClassificationResult + TopMatchInfo
```

### Modo de entrenamiento supervisado
```
UI (MainActivity)
  ↓
MainViewModel.enterTrainingMode()
  ↓
MainViewModel.selectTrainingClass(OK/OBSTACULO/FALLO)
  ↓
FrameProcessor.getRoiCrop(roi) → Bitmap
  ↓
MainViewModel.captureTrainingSample()
  ↓
TrainingDatasetRepository.saveSample()
  ↓
FileTrainingDatasetRepository (archivos)
  ↓
filesDir/training/OK/     → sample.jpg + sample.meta
filesDir/training/OBSTACULO/ → sample.jpg + sample.meta
filesDir/training/FALLO/     → sample.jpg + sample.meta
  ↓
UI actualiza contadores (X/50)
```

### Análisis y detección (vigilancia)
```
CameraX → FrameProcessor → ColorFrameData (HSV)
                              ↓
                    MonitoringManager
                              ↓
              ColorBasedDetector.analyze() → ColorDetectionResult
                              ↓
                         MainViewModel → UI
```

### Clasificación automática basada en dataset
```
Al iniciar vigilancia:
MainViewModel.startMonitoring()
  ↓
MainViewModel.syncTrainingDatasetWithClassifier()
  ↓
MonitoringManager.updateTrainingDataset(samples)
  ↓
DatasetClassifier.syncDataset(samples) → Precalcula features
  ↓
Cache en memoria: Map<sampleId, CachedSampleFeatures>

Durante vigilancia (cada 500ms):
FrameProcessor → ColorFrameData
  ↓
TransferSubRoiDetector.detectTransferSubRoi() → subROI del transfer
  ↓
Extrae sub-frame de la subROI
  ↓
DatasetClassifier.classify(subFrame)
  ↓
Extrae features de la subROI (1 vez)
  ↓
Compara contra caché de features (O(n), sin decodificación)
  ↓
ClassificationResult → UI (clase, confianza, scores)
  ↓
TopMatchInfo → UI (muestra más cercana, similitud)
```

### Alertas automáticas
```
MonitoringManager.detectionResult → MainViewModel
                                              ↓
                         if hasChange == true (cambio confirmado)
                                              ↓
                              AlertManager.onDetectionResult()
                                              ↓
                    AlertState.Success/Error/Cooldown → UI
```

---

## Descripción de componentes clave

### CameraPreview (camera/CameraPreview.kt)
- Preview de cámara + ImageAnalysis
- Expone ColorFrameData + FrameData legacy
- Retorna FrameProcessor para captura bajo demanda

### FrameProcessor (camera/FrameProcessor.kt)
- Recibe ImageProxy, extrae HSV + luminancia
- **CORREGIDO**: Pipeline de color respeta rowStride (sin artefactos)
- Métodos: `getLastFrameBitmap()`, `getRoiCrop()`, `getRegionCrop()`
- Frecuencia: 1 de cada 5 frames (~6fps)

### TransferSubRoiDetector (detection/TransferSubRoiDetector.kt) - NUEVO
- Detecta la subregión del transfer dentro del ROI global
- **Estrategia COLOR_MASK**: Detecta píxeles con color (no blancos, no negros)
- **Heurística**: `isColorfulPixel()` con umbrales de saturación/value
- **Bounding box**: Calcula región de píxeles candidatos
- **Validación**: Verifica tamaño razonable (30%-90% del ROI)
- **Fallback**: `CENTERED_HEURISTIC` (60% centro) si color falla
- **Observabilidad**: `TransferSubRoiResult` con método, confianza, estadísticas

### MonitoringManager (monitoring/MonitoringManager.kt)
- Coordina vigilancia activa + estabilización + clasificación
- Análisis cada 500ms usando ColorBasedDetector
- **NUEVO**: Integra TransferSubRoiDetector para clasificación con subROI
- **Pipeline de clasificación**:
  1. Detectar subROI del transfer
  2. Extraer sub-frame de la subROI
  3. Extraer features de la subROI
  4. Comparar contra dataset cacheado
- Subregión activa: Detectada automáticamente por color (con fallback)
- Estabilización: 3 detecciones consecutivas antes de confirmar
- **CLASIFICACIÓN AUTOMÁTICA**: Integra DatasetClassifier con sincronización
- Estado de sincronización: DatasetSyncUiState (EMPTY, SYNCING, SYNCED, STALE)
- Estados de observabilidad: `transferSubRoiResult`, `topMatchInfo`
- Resincronización: `forceResyncDataset()` para actualizar caché

### ColorBasedDetector (detection/ColorBasedDetector.kt)
- Análisis cromático HSV
- Naranja (Hue 15-35), Rojo (Hue 0-15 o 230-255)
- Umbral: 5% de píxeles (provisionales)

### DatasetClassifier (classification/DatasetClassifier.kt)
- Clasificador k-NN (k=3) basado en features HSV
- **CACHÉ DE FEATURES**: Precalcula features del dataset en sincronización
- Estructura: `CachedSampleFeatures` almacena features por muestra
- **NUEVO**: `TopMatchInfo` para comparación visual contra muestra más cercana
- Método `syncDataset()`: Decodifica JPEGs y precalcula features (costoso, una vez)
- Método `classify()`: Compara features contra caché (rápido, O(n))
- Estado: `DatasetSyncStatus` (EMPTY, SYNCED, STALE)
- Estadísticas: `CacheStats` con contadores por clase

### MainViewModel (ui/MainViewModel.kt)
- Gestiona todo el estado de la UI
- **MODO ENTRENAMIENTO**:
  - `trainingDatasetState`: contadores por clase
  - `selectedTrainingClass`: clase actual (OK/OBSTACULO/FALLO)
  - `captureTrainingSample()`: captura y guarda muestra
  - `clearTrainingClass()`: reinicia clase específica
- **NUEVO - Observabilidad subROI**:
  - `transferSubRoiResult`: resultado de detección de subROI
  - `topMatchInfo`: información del match más cercano
  - Flags de inspección: `showRoiInspection`, `showSubRoiInspection`, `showTopMatchComparison`
- **Baseline OK** (legacy): métodos mantenidos para compatibilidad
- **Vigilancia**: start/stop monitoring, alertas

### FileTrainingDatasetRepository (data/local/FileTrainingDatasetRepository.kt)
- Persistencia de muestras de entrenamiento
- Estructura: `training/OK/`, `training/OBSTACULO/`, `training/FALLO/`
- Formato: JPEG + .meta (texto simple)
- Límite: 50 muestras por clase
- IDs: `${label}_${timestamp}_${uuid8}`

### MainActivity (MainActivity.kt)
- **Modos de pantalla**:
  - `NORMAL`: Vigilancia, Telegram, ROI, inspección visual
  - `ROI_SELECTION`: Selector de ROI
  - `TRAINING`: Entrenamiento supervisado
- **UI de entrenamiento**:
  - `ClassSelector`: 3 botones (OK/OBSTACULO/FALLO) con colores
  - `ClassCounters`: contadores X/50 por clase
  - `TrainingCaptureButton`: captura con feedback
  - `TrainingManagementButtons`: reiniciar/volver
- **UI de clasificación**:
  - `ClassificationSection`: clase estimada, confianza, scores por clase
  - Estado de sincronización del dataset
  - Botón de resincronización
- **UI de inspección visual (NUEVO)**:
  - `VisualInspectionSection`: tarjeta principal de inspección
  - `InspectionToggleButton`: botones toggle para activar/desactivar vistas
  - `VisualInspectionImages`: contenedor de imágenes de inspección
  - `InspectionImageItem`: componente para mostrar un crop con etiqueta
  - `TopMatchComparison`: comparación lado a lado (actual vs top-1 dataset)

---

## Puntos de extensión recomendados

### Para clasificación basada en dataset entrenado
1. Leer muestras desde `FileTrainingDatasetRepository.getDatasetState()`
2. Extraer características (histograma HSV, estadísticas)
3. Comparar imagen actual contra muestras almacenadas
4. Clasificar en OK/OBSTACULO/FALLO por similitud

### Para ajustar estabilización
Modificar parámetros en constructor de MonitoringManager:
- `consecutiveDetectionsRequired`: número de detecciones (default: 3)
- `stabilizationTimeoutMs`: timeout entre detecciones (default: 2000ms)

### Para mejorar detección de subROI
Modificar parámetros en constructor de TransferSubRoiDetector:
- `minColorfulness`: umbral de saturación para píxel colorido (default: 40)
- `minSubRoiSize`: tamaño mínimo de subROI (default: 0.3 = 30%)
- `maxSubRoiSize`: tamaño máximo de subROI (default: 0.9 = 90%)

---

## Archivos a NO tocar sin razón fuerte

- `domain/model/Roi.kt`: Modelo estable
- `domain/model/TelegramConfig.kt`: Modelo estable
- `domain/model/TrainingSample.kt`: Modelo estable, cambios rompen compatibilidad
- `domain/repository/*`: Contratos de persistencia
- `data/local/*`: Implementaciones de persistencia

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
- Inyección de dependencias: Constructor-based
- Estado reactivo: StateFlow en todos los niveles
- **NUEVO**: Modo entrenamiento supervisado con 3 clases
- **NUEVO**: Clasificación con subROI del transfer (ignora fondo de la vía)
- Dataset local preparado para clasificación futura

## Estado de infraestructura

- **Gradle Wrapper**: 8.5 (estable)
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Estado de build**: ✅ COMPILA CORRECTAMENTE
- **Warnings**: 6 menores (deprecated, parámetros no usados)

### Comandos útiles

```bash
# Compilar
.\gradlew.bat :app:compileDebugKotlin

# Instalar en dispositivo
.\gradlew.bat :app:installDebug

# Limpiar build
.\gradlew.bat clean