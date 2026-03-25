/**
 * Archivo: app/src/main/java/com/vigia/app/monitoring/MonitoringManager.kt
 * Propósito: Gestor del estado de monitorización y análisis de ROI en VIGIA1.
 * Responsabilidad principal: Coordinar el inicio/parada de vigilancia y el análisis periódico del ROI.
 * Alcance: Capa de monitorización, control del ciclo de vigilancia y detección.
 *
 * Decisiones técnicas relevantes:
 * - StateFlow para estado reactivo de vigilancia
 * - StateFlow para resultado de detección (para observación desde UI)
 * - Integración con RoiDetector mediante inyección de dependencias
 * - Soporte DUAL: FrameData legacy (luminancia) y ColorFrameData (cromático)
 * - Job de coroutine para análisis periódico que se inicia/para con la vigilancia
 * - Recepción de frames reales de CameraX vía StateFlow
 * - CAPA DE ESTABILIZACIÓN: Requiere detecciones consecutivas antes de confirmar cambio
 * - SUBREGIÓN ACTIVA: Deriva región de análisis dentro del ROI global
 * - CLASIFICACIÓN CON CACHE: Usa DatasetClassifier con precálculo de features del dataset
 *
 * Heurística espacial implementada:
 * - Dentro del ROI global definido por el usuario, se deriva una subregión activa
 * - Por defecto: 60% del área central, con sesgo vertical hacia arriba (0.4)
 * - Esto concentra el análisis en el cuerpo del transfer
 * - Reduce influencia del fondo de vía (blanco + líneas negras paralelas)
 *
 * Estrategia de sincronización del dataset:
 * - syncTrainingDataset() debe llamarse explícitamente cuando cambia el dataset
 * - La primera sincronización ocurre automáticamente en startMonitoring si hay dataset
 * - El estado de sincronización se expone vía datasetSyncStatus para la UI
 * - Resincronización manual disponible via forceResyncDataset()
 *
 * Limitaciones temporales del MVP:
 * - Análisis periódico simple, no sincronizado exactamente con cada frame de cámara
 * - Si no hay frames de cámara disponibles, el análisis no produce resultados
 * - Sin buffer de frames ni cola de procesamiento
 * - Lógica de estabilización es simple (contador con timeout)
 *
 * Cambios recientes:
 * - ACTUALIZADO: Integración con DatasetClassifier usando caché de features
 * - AÑADIDO: Estado de sincronización del dataset (SYNCED, SYNCING, STALE, EMPTY)
 * - AÑADIDO: Método forceResyncDataset() para resincronización explícita
 * - AÑADIDO: Exposición de cacheStats para observabilidad de la caché
 * - AÑADIDO: Soporte para ColorFrameData con análisis cromático HSV
 * - AÑADIDO: Integración con ColorBasedDetector
 * - AÑADIDO: Exposición de información de subregión activa para observabilidad
 * - AÑADIDO: Clasificación automática basada en dataset etiquetado (k-NN)
 * - AÑADIDO: DatasetClassifier con features HSV (histograma + estadísticas)
 * - MANTENIDO: FrameData legacy para compatibilidad durante transición
 */
package com.vigia.app.monitoring

import com.vigia.app.classification.*
import com.vigia.app.detection.*
import com.vigia.app.domain.model.ClassLabel
import com.vigia.app.domain.model.Roi
import com.vigia.app.domain.model.TrainingSample
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de sincronización del dataset para la UI.
 */
enum class DatasetSyncUiState {
    EMPTY,      // No hay muestras en el dataset
    SYNCING,    // Sincronizando (precalculando features)
    SYNCED,     // Dataset cacheado y listo
    STALE       // Dataset cambió, necesita resincronización
}

/**
 * Gestor del estado de monitorización y análisis de ROI.
 * Controla si la vigilancia está activa y coordina el análisis del detector.
 *
 * Incluye:
 * - Capa de estabilización: requiere detecciones consecutivas antes de confirmar
 * - Soporte para análisis cromático (HSV) y legacy (luminancia)
 * - Subregión activa dentro del ROI global
 * - Clasificación automática con caché de features del dataset
 *
 * @param colorDetector Detector cromático a utilizar (default: ColorBasedDetector)
 * @param legacyDetector Detector legacy para compatibilidad (default: SimpleFrameDifferenceDetector)
 * @param scope CoroutineScope para lanzar el análisis periódico
 * @param consecutiveDetectionsRequired Número de detecciones consecutivas requeridas
 * @param stabilizationTimeoutMs Tiempo máximo entre detecciones antes de resetear
 */
class MonitoringManager(
    private val colorDetector: ColorBasedDetector = ColorBasedDetector(),
    private val legacyDetector: RoiDetector = SimpleFrameDifferenceDetector(threshold = 30),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val consecutiveDetectionsRequired: Int = DEFAULT_CONSECUTIVE_DETECTIONS,
    private val stabilizationTimeoutMs: Long = DEFAULT_STABILIZATION_TIMEOUT_MS
) {

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    // === Resultados de detección ===
    private val _detectionResult = MutableStateFlow<DetectionResult?>(null)
    val detectionResult: StateFlow<DetectionResult?> = _detectionResult.asStateFlow()

    private val _colorDetectionResult = MutableStateFlow<ColorDetectionResult?>(null)
    /**
     * Último resultado de análisis cromático.
     */
    val colorDetectionResult: StateFlow<ColorDetectionResult?> = _colorDetectionResult.asStateFlow()

    // === Información de subregión activa (para observabilidad) ===
    private val _activeSubRegion = MutableStateFlow<SubRegion?>(null)
    /**
     * Subregión activa actualmente usada para análisis.
     * Permite visualizar en UI qué parte del ROI se está analizando.
     */
    val activeSubRegion: StateFlow<SubRegion?> = _activeSubRegion.asStateFlow()

    // === Clasificación automática basada en dataset ===
    private val _classificationResult = MutableStateFlow<ClassificationResult?>(null)
    /**
     * Resultado de clasificación automática basada en dataset etiquetado.
     * Permite ver la clase estimada actual (OK, OBSTACULO, FALLO).
     */
    val classificationResult: StateFlow<ClassificationResult?> = _classificationResult.asStateFlow()

    private val datasetClassifier = DatasetClassifier(k = 3)
    private var trainingDataset: List<TrainingSample> = emptyList()
    
    // Estado de sincronización expuesto para la UI
    private val _datasetSyncStatus = MutableStateFlow(DatasetSyncUiState.EMPTY)
    val datasetSyncStatus: StateFlow<DatasetSyncUiState> = _datasetSyncStatus.asStateFlow()
    
    // Estadísticas de caché expuestas para la UI
    private val _cacheStats = MutableStateFlow<CacheStats?>(null)
    val cacheStats: StateFlow<CacheStats?> = _cacheStats.asStateFlow()

    private var analysisJob: Job? = null
    private var currentRoi: Roi? = null
    private var colorFrameDataFlow: StateFlow<ColorFrameData?>? = null
    private var legacyFrameDataFlow: StateFlow<FrameData?>? = null

    // Variables para lógica de estabilización
    private var consecutiveChangeCount: Int = 0
    private var lastDetectionTimestamp: Long = 0
    private var isChangeConfirmed: Boolean = false

    /**
     * Conecta el flujo de frames cromáticos de la cámara al manager.
     * Este es el método principal para el nuevo análisis por color.
     *
     * @param colorFrameDataFlow StateFlow que emite ColorFrameData procesados
     */
    fun connectColorFrames(colorFrameDataFlow: StateFlow<ColorFrameData?>) {
        this.colorFrameDataFlow = colorFrameDataFlow
    }

    /**
     * Conecta el flujo de frames legacy (luminancia) para compatibilidad.
     *
     * @param frameDataFlow StateFlow que emite FrameData legacy
     */
    @Deprecated("Usar connectColorFrames para análisis cromático")
    fun connectCameraFrames(frameDataFlow: StateFlow<FrameData?>) {
        this.legacyFrameDataFlow = frameDataFlow
    }

    /**
     * Conecta ambos flujos de frames (dual mode durante transición).
     */
    fun connectDualFrames(
        colorFrameDataFlow: StateFlow<ColorFrameData?>,
        legacyFrameDataFlow: StateFlow<FrameData?>
    ) {
        this.colorFrameDataFlow = colorFrameDataFlow
        this.legacyFrameDataFlow = legacyFrameDataFlow
    }

    /**
     * Inicia la monitorización con un ROI específico.
     * Si hay un dataset disponible, lo sincroniza automáticamente.
     */
    fun startMonitoring(roi: Roi? = null) {
        if (_isMonitoring.value) return

        currentRoi = roi
        _isMonitoring.value = true
        
        // Calcular y exponer subregión activa
        _activeSubRegion.value = calculateActiveSubRegion()
        
        resetStabilizationState()
        colorDetector.reset()
        legacyDetector.reset()
        
        // Sincronizar dataset automáticamente si hay datos
        if (trainingDataset.isNotEmpty()) {
            syncTrainingDatasetInternal()
        }

        // Iniciar análisis periódico
        analysisJob = scope.launch {
            while (isActive) {
                performColorAnalysis()
                delay(ANALYSIS_INTERVAL_MS)
            }
        }
    }

    /**
     * Detiene la monitorización.
     */
    fun stopMonitoring() {
        _isMonitoring.value = false
        analysisJob?.cancel()
        analysisJob = null
        _detectionResult.value = null
        _colorDetectionResult.value = null
        _activeSubRegion.value = null
        
        resetStabilizationState()
    }

    /**
     * Actualiza el ROI durante la monitorización.
     */
    fun updateRoi(roi: Roi?) {
        currentRoi = roi
        if (_isMonitoring.value) {
            _activeSubRegion.value = calculateActiveSubRegion()
            colorDetector.reset()
            legacyDetector.reset()
            resetStabilizationState()
        }
    }

    /**
     * Calcula la subregión activa basada en la configuración del detector.
     */
    private fun calculateActiveSubRegion(): SubRegion {
        // Extraer subregión de la descripción del detector
        // Por simplicidad, calculamos la subregión CENTERED_60 directamente
        return SubRegion.centered(0.6f, 0.4f, "Centro 60% del ROI (cuerpo del transfer)")
    }

    /**
     * Obtiene la descripción de la heurística espacial actual.
     */
    fun getSubRegionDescription(): String {
        return colorDetector.getSubRegionDescription()
    }

    private fun resetStabilizationState() {
        consecutiveChangeCount = 0
        lastDetectionTimestamp = 0
        isChangeConfirmed = false
    }

    /**
     * Realiza análisis cromático usando ColorFrameData.
     * Este es el método principal de análisis en la nueva arquitectura.
     */
    private suspend fun performColorAnalysis() {
        val roi = currentRoi
        val colorFlow = colorFrameDataFlow

        if (colorFlow == null) {
            _colorDetectionResult.value = ColorDetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Esperando frames de cámara...",
                detectedSignal = DetectedSignal.NONE
            )
            return
        }

        val colorFrameData = colorFlow.value
        
        if (colorFrameData == null) {
            _colorDetectionResult.value = ColorDetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Sin datos de frame disponibles",
                detectedSignal = DetectedSignal.NONE
            )
            return
        }

        // Realizar análisis cromático
        val rawResult = colorDetector.analyzeColor(colorFrameData, roi)
        
        // Aplicar lógica de estabilización
        val stabilizedResult = applyColorStabilization(rawResult)
        
        _colorDetectionResult.value = stabilizedResult
        
        // También actualizar el resultado legacy para compatibilidad
        _detectionResult.value = DetectionResult(
            hasChange = stabilizedResult.hasChange,
            confidence = stabilizedResult.confidence,
            message = stabilizedResult.message
        )

        // Realizar clasificación automática basada en dataset (si hay caché disponible)
        if (datasetClassifier.getSyncStatus() == DatasetSyncStatus.SYNCED) {
            performClassification(colorFrameData)
        }
    }

    /**
     * Actualiza el dataset de entrenamiento y sincroniza la caché de features.
     * Llamar cuando se capturen nuevas muestras o se modifique el dataset.
     * Esta operación precalcula las features de todas las muestras (costosa, no bloqueante).
     *
     * @param dataset Nuevo dataset completo
     */
    fun updateTrainingDataset(dataset: List<TrainingSample>) {
        trainingDataset = dataset
        // Marcar como STALE para forzar resincronización
        datasetClassifier.invalidateCache()
        _datasetSyncStatus.value = DatasetSyncUiState.STALE
    }

    /**
     * Sincroniza internamente el dataset (llamado desde startMonitoring).
     */
    private fun syncTrainingDatasetInternal() {
        if (trainingDataset.isEmpty()) {
            _datasetSyncStatus.value = DatasetSyncUiState.EMPTY
            return
        }
        
        _datasetSyncStatus.value = DatasetSyncUiState.SYNCING
        
        // Realizar sincronización (precálculo de features)
        val result = datasetClassifier.syncDataset(trainingDataset)
        
        // Actualizar estado según resultado
        _datasetSyncStatus.value = when (result.status) {
            DatasetSyncStatus.SYNCED -> DatasetSyncUiState.SYNCED
            DatasetSyncStatus.EMPTY -> DatasetSyncUiState.EMPTY
            DatasetSyncStatus.STALE -> DatasetSyncUiState.STALE
        }
        
        // Actualizar estadísticas de caché
        _cacheStats.value = datasetClassifier.getCacheStats()
    }

    /**
     * Fuerza una resincronización del dataset.
     * Útil cuando el usuario quiere asegurarse de que el dataset está actualizado.
     */
    fun forceResyncDataset() {
        syncTrainingDatasetInternal()
    }

    /**
     * Obtiene el estado de sincronización actual del dataset.
     */
    fun getDatasetSyncStatus(): DatasetSyncUiState = _datasetSyncStatus.value

    /**
     * Obtiene estadísticas de la caché actual.
     */
    fun getCacheStats(): CacheStats? = _cacheStats.value

    /**
     * Obtiene estadísticas del dataset para observabilidad.
     */
    fun getDatasetStats(): DatasetStats {
        return datasetClassifier.getDatasetStats(trainingDataset)
    }

    /**
     * Verifica si el dataset tiene suficientes muestras para clasificar.
     */
    fun isDatasetReady(): Boolean {
        return datasetClassifier.isDatasetReady()
    }

    /**
     * Realiza clasificación automática del frame actual contra el dataset cacheado.
     * Usa las features precalculadas en caché (sin decodificación JPEG).
     */
    private fun performClassification(frameData: ColorFrameData) {
        val result = datasetClassifier.classify(frameData)
        _classificationResult.value = result
        
        // Actualizar estadísticas de caché periódicamente (cada clasificación por simplicidad)
        _cacheStats.value = datasetClassifier.getCacheStats()
    }

    /**
     * Aplica lógica de estabilización al resultado cromático.
     */
    private fun applyColorStabilization(rawResult: ColorDetectionResult): ColorDetectionResult {
        val now = System.currentTimeMillis()
        
        // Si no hay señal detectada, resetear
        if (!rawResult.hasChange) {
            if (consecutiveChangeCount > 0 || isChangeConfirmed) {
                consecutiveChangeCount = 0
                isChangeConfirmed = false
                
                return rawResult.copy(
                    message = "Señal no confirmada - se perdió (${rawResult.message})"
                )
            }
            return rawResult
        }
        
        // Hay señal, aplicar estabilización
        
        // Verificar timeout
        if (lastDetectionTimestamp > 0 && (now - lastDetectionTimestamp) > stabilizationTimeoutMs) {
            consecutiveChangeCount = 0
            isChangeConfirmed = false
        }
        
        consecutiveChangeCount++
        lastDetectionTimestamp = now
        
        // Verificar si alcanzamos el umbral para confirmar
        if (consecutiveChangeCount >= consecutiveDetectionsRequired) {
            if (!isChangeConfirmed) {
                isChangeConfirmed = true
                
                return rawResult.copy(
                    message = "✓ ${rawResult.detectedSignal.name} CONFIRMADO (${consecutiveChangeCount}/${consecutiveDetectionsRequired} análisis)"
                )
            } else {
                return rawResult.copy(
                    message = "${rawResult.detectedSignal.name} confirmado persiste (${consecutiveChangeCount} análisis)"
                )
            }
        } else {
            // Señal detectada pero aún no confirmada
            return rawResult.copy(
                hasChange = false, // No confirmar todavía
                confidence = rawResult.confidence * (consecutiveChangeCount / consecutiveDetectionsRequired.toFloat()),
                message = "Detectando ${rawResult.detectedSignal.name}... (${consecutiveChangeCount}/${consecutiveDetectionsRequired})"
            )
        }
    }

    /**
     * Libera recursos al destruir el manager.
     */
    fun cleanup() {
        stopMonitoring()
        datasetClassifier.clearCache()
        scope.cancel()
    }
    
    companion object {
        const val ANALYSIS_INTERVAL_MS = 500L
        const val DEFAULT_CONSECUTIVE_DETECTIONS = 3
        const val DEFAULT_STABILIZATION_TIMEOUT_MS = 2000L
    }
}