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
├── data/                              # Persistencia
│   └── local/
│       ├── DataStoreRoiRepository.kt  # Persistencia de ROI
│       ├── DataStoreTelegramConfigRepository.kt # Persistencia Telegram
│       ├── FileOkBaselineRepository.kt # Baseline OK legacy (obsoleto)
│       └── FileTrainingDatasetRepository.kt # NUEVO: Dataset entrenamiento OK/OBSTACULO/FALLO
├── detection/                         # Lógica de detección
│   ├── RoiDetector.kt                 # Interfaz del detector (legacy)
│   ├── SimpleFrameDifferenceDetector.kt # Implementación simple luminancia (legacy)
│   ├── ColorFrameData.kt              # Estructura de datos cromáticos HSV
│   └── ColorBasedDetector.kt          # Detector basado en análisis cromático
├── domain/                            # Modelos y contratos
│   ├── model/
│   │   ├── Roi.kt                     # Entidad ROI
│   │   ├── TelegramConfig.kt          # Entidad config Telegram
│   │   ├── OkBaselineSample.kt        # Baseline OK legacy
│   │   └── TrainingSample.kt          # NUEVO: Muestras de entrenamiento etiquetadas
│   └── repository/
│       ├── RoiRepository.kt           # Interfaz persistencia ROI
│       ├── TelegramConfigRepository.kt # Interfaz persistencia Telegram
│       ├── OkBaselineRepository.kt    # Interfaz baseline OK legacy
│       └── TrainingDatasetRepository.kt # NUEVO: Interfaz dataset entrenamiento
├── monitoring/                        # Coordinación vigilancia + estabilización
│   └── MonitoringManager.kt           # Estado vigilancia + análisis cromático periódico
├── telegram/                          # Envío de alertas
│   └── TelegramService.kt             # Servicio Telegram con OkHttp
├── ui/                                # Interfaz de usuario
│   ├── MainViewModel.kt               # ViewModel principal (entrenamiento, baseline, vigilancia)
│   └── components/
│       ├── RoiOverlay.kt              # Dibuja ROI sobre cámara
│       └── RoiSelector.kt             # Selector táctil de ROI
└── utils/                             # Utilidades
    └── PermissionsHelper.kt           # Gestión de permisos
```

---

## Flujo de datos principal

### Modo de entrenamiento supervisado (NUEVO)
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

### Clasificación automática basada en dataset (NUEVO)
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
MonitoringManager.performClassification()
  ↓
DatasetClassifier.classify(ColorFrameData)
  ↓
Extrae features del frame actual (1 vez)
  ↓
Compara contra caché de features (O(n), sin decodificación)
  ↓
ClassificationResult → UI (clase, confianza, scores)
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

### MonitoringManager (monitoring/MonitoringManager.kt)
- Coordina vigilancia activa + estabilización + clasificación
- Análisis cada 500ms usando ColorBasedDetector
- Subregión activa: 60% centro con sesgo vertical 0.4
- Estabilización: 3 detecciones consecutivas antes de confirmar
- **CLASIFICACIÓN AUTOMÁTICA**: Integra DatasetClassifier con sincronización
- Estado de sincronización: DatasetSyncUiState (EMPTY, SYNCING, SYNCED, STALE)
- Resincronización: `forceResyncDataset()` para actualizar caché

### ColorBasedDetector (detection/ColorBasedDetector.kt)
- Análisis cromático HSV
- Naranja (Hue 15-35), Rojo (Hue 0-15 o 230-255)
- Umbral: 5% de píxeles (provisionales)

### DatasetClassifier (classification/DatasetClassifier.kt) - NUEVO
- Clasificador k-NN (k=3) basado en features HSV
- **CACHÉ DE FEATURES**: Precalcula features del dataset en sincronización
- Estructura: `CachedSampleFeatures` almacena features por muestra
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
  - `NORMAL`: Vigilancia, Telegram, ROI
  - `ROI_SELECTION`: Selector de ROI
  - `TRAINING`: Entrenamiento supervisado
- **UI de entrenamiento**:
  - `ClassSelector`: 3 botones (OK/OBSTACULO/FALLO) con colores
  - `ClassCounters`: contadores X/50 por clase
  - `TrainingCaptureButton`: captura con feedback
  - `TrainingManagementButtons`: reiniciar/volver

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

### Para mejorar detección
Crear nuevo detector implementando lógica de comparación contra dataset entrenado, inyectar en MonitoringManager.

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
- Dataset local preparado para clasificación futura

## Estado de infraestructura

- **Gradle Wrapper**: 8.5 (estable)
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Estado de build**: ✅ COMPILA CORRECTAMENTE
- **Warnings**: 3 menores (no bloqueantes)

### Comandos útiles

```bash
# Compilar
.\gradlew.bat :app:compileDebugKotlin

# Instalar en dispositivo
.\gradlew.bat :app:installDebug

# Limpiar build
.\gradlew.bat clean