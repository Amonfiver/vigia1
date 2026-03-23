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
 * - Job de coroutine para análisis periódico que se inicia/para con la vigilancia
 * - Recepción de frames reales de CameraX vía StateFlow<FrameData?>
 * - CAPA DE ESTABILIZACIÓN: Requiere detecciones consecutivas antes de confirmar cambio
 *   - Umbral configurable de frames consecutivos (default: 3)
 *   - Timeout de reinicio si el cambio no se mantiene (default: 2 segundos)
 *   - Evita falsos positivos por picos breves o cambios aislados
 *
 * Limitaciones temporales del MVP:
 * - Análisis periódico simple, no sincronizado exactamente con cada frame de cámara
 * - Si no hay frames de cámara disponibles, el análisis no produce resultados
 * - Sin buffer de frames ni cola de procesamiento
 * - Lógica de estabilización es simple (contador con timeout), no analiza patrones complejos
 *
 * Cambios recientes:
 * - Eliminada generación de datos simulados
 * - Añadida recepción de frames reales de CameraX
 * - Análisis ahora usa luminancia real del ROI
 * - AÑADIDA: Lógica de confirmación de cambio con detecciones consecutivas requeridas
 * - AÑADIDA: Timeout de reinicio para descartar cambios no sostenidos
 */
package com.vigia.app.monitoring

import com.vigia.app.detection.DetectionResult
import com.vigia.app.detection.FrameData
import com.vigia.app.detection.RoiDetector
import com.vigia.app.detection.SimpleFrameDifferenceDetector
import com.vigia.app.domain.model.Roi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor del estado de monitorización y análisis de ROI.
 * Controla si la vigilancia está activa y coordina el análisis del detector.
 *
 * Incluye capa de estabilización: requiere detecciones consecutivas antes de
 * confirmar un cambio como válido, reduciendo falsos positivos por picos breves.
 *
 * @param detector Detector de cambios a utilizar (default: SimpleFrameDifferenceDetector)
 * @param scope CoroutineScope para lanzar el análisis periódico
 * @param consecutiveDetectionsRequired Número de detecciones consecutivas requeridas para confirmar cambio (default: 3)
 * @param stabilizationTimeoutMs Tiempo máximo entre detecciones antes de resetear contador (default: 2000ms)
 */
class MonitoringManager(
    private val detector: RoiDetector = SimpleFrameDifferenceDetector(threshold = 30),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val consecutiveDetectionsRequired: Int = DEFAULT_CONSECUTIVE_DETECTIONS,
    private val stabilizationTimeoutMs: Long = DEFAULT_STABILIZATION_TIMEOUT_MS
) {

    private val _isMonitoring = MutableStateFlow(false)
    /**
     * Estado actual de la monitorización (true = vigilancia activa).
     */
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _detectionResult = MutableStateFlow<DetectionResult?>(null)
    /**
     * Último resultado de detección (null si no hay análisis reciente).
     * NOTA: hasChange solo será true después de que se cumpla el criterio de estabilización
     * (varias detecciones consecutivas), no en el primer pico detectado.
     */
    val detectionResult: StateFlow<DetectionResult?> = _detectionResult.asStateFlow()

    private var analysisJob: Job? = null
    private var currentRoi: Roi? = null
    private var frameDataFlow: StateFlow<FrameData?>? = null

    // Variables para lógica de estabilización
    private var consecutiveChangeCount: Int = 0
    private var lastDetectionTimestamp: Long = 0
    private var isChangeConfirmed: Boolean = false
    private var lastConfirmedResult: DetectionResult? = null

    /**
     * Conecta el flujo de frames de la cámara al manager.
     * Debe llamarse antes o durante startMonitoring().
     *
     * @param frameDataFlow StateFlow que emite frames procesados de CameraX
     */
    fun connectCameraFrames(frameDataFlow: StateFlow<FrameData?>) {
        this.frameDataFlow = frameDataFlow
    }

    /**
     * Inicia la monitorización con un ROI específico.
     *
     * @param roi ROI a analizar (puede ser null, el detector manejará ese caso)
     */
    fun startMonitoring(roi: Roi? = null) {
        if (_isMonitoring.value) return // Ya está activa

        currentRoi = roi
        _isMonitoring.value = true
        
        // Resetear estado de estabilización
        resetStabilizationState()
        detector.reset() // Establecer nueva referencia base

        // Iniciar análisis periódico
        analysisJob = scope.launch {
            while (isActive) {
                performAnalysis()
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
        
        // Limpiar estado de estabilización
        resetStabilizationState()
    }

    /**
     * Actualiza el ROI durante la monitorización (si cambia mientras se vigila).
     *
     * @param roi Nuevo ROI a analizar
     */
    fun updateRoi(roi: Roi?) {
        currentRoi = roi
        if (_isMonitoring.value) {
            detector.reset() // Reiniciar referencia con nuevo ROI
            resetStabilizationState() // Resetear también estabilización
        }
    }

    /**
     * Resetea el estado de estabilización de detección.
     */
    private fun resetStabilizationState() {
        consecutiveChangeCount = 0
        lastDetectionTimestamp = 0
        isChangeConfirmed = false
        lastConfirmedResult = null
    }

    /**
     * Realiza un ciclo de análisis usando frames reales de la cámara.
     * Espera el último frame disponible y lo analiza con el detector.
     * 
     * Aplica lógica de estabilización: solo confirma el cambio después de
     * varias detecciones consecutivas dentro de la ventana de tiempo.
     */
    private suspend fun performAnalysis() {
        val roi = currentRoi
        val frameFlow = frameDataFlow

        // Si no hay flujo de cámara conectado, no se puede analizar
        if (frameFlow == null) {
            _detectionResult.value = DetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Esperando frames de cámara..."
            )
            return
        }

        // Obtener el último frame disponible
        val frameData = frameFlow.value
        
        if (frameData == null) {
            _detectionResult.value = DetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Sin datos de frame disponibles"
            )
            return
        }

        // Realizar análisis con el frame real
        val rawResult = detector.analyze(frameData, roi)
        
        // Aplicar lógica de estabilización
        val stabilizedResult = applyStabilization(rawResult)
        
        _detectionResult.value = stabilizedResult
    }

    /**
     * Aplica lógica de estabilización al resultado del detector.
     * 
     * Estrategia:
     * 1. Si el detector NO detecta cambio -> resetear contador, no hay cambio
     * 2. Si el detector detecta cambio:
     *    - Verificar si pasó mucho tiempo desde última detección (timeout)
     *    - Incrementar contador de detecciones consecutivas
     *    - Si contador >= umbral -> confirmar cambio (hasChange = true)
     *    - Si contador < umbral -> reportar "detectando" pero hasChange = false
     * 
     * @param rawResult Resultado crudo del detector
     * @return Resultado estabilizado para consumo del resto del sistema
     */
    private fun applyStabilization(rawResult: DetectionResult): DetectionResult {
        val now = System.currentTimeMillis()
        
        // Si el detector no detecta cambio, resetear todo
        if (!rawResult.hasChange) {
            // Solo actualizar si había algo en progreso
            if (consecutiveChangeCount > 0 || isChangeConfirmed) {
                consecutiveChangeCount = 0
                isChangeConfirmed = false
                lastConfirmedResult = null
                
                return DetectionResult(
                    hasChange = false,
                    confidence = 0f,
                    message = "Cambio no confirmado - se perdió la señal"
                )
            }
            
            // Sin cambio detectado, mantener estado neutral
            return rawResult
        }
        
        // El detector detecta cambio, aplicar lógica de estabilización
        
        // Verificar timeout: si pasó mucho tiempo desde última detección, resetear
        if (lastDetectionTimestamp > 0 && (now - lastDetectionTimestamp) > stabilizationTimeoutMs) {
            consecutiveChangeCount = 0
            isChangeConfirmed = false
        }
        
        // Incrementar contador de detecciones consecutivas
        consecutiveChangeCount++
        lastDetectionTimestamp = now
        
        // Verificar si alcanzamos el umbral para confirmar
        if (consecutiveChangeCount >= consecutiveDetectionsRequired) {
            // Cambio confirmado
            if (!isChangeConfirmed) {
                isChangeConfirmed = true
                
                // Primera vez que confirmamos - construir mensaje informativo
                val confirmedResult = DetectionResult(
                    hasChange = true,
                    confidence = rawResult.confidence,
                    message = "✓ Cambio CONFIRMADO (${consecutiveChangeCount}/${consecutiveDetectionsRequired} análisis consecutivos)"
                )
                lastConfirmedResult = confirmedResult
                return confirmedResult
            } else {
                // Cambio ya estaba confirmado, seguir reportando
                return DetectionResult(
                    hasChange = true,
                    confidence = rawResult.confidence,
                    message = "Cambio confirmado persiste (${consecutiveChangeCount} análisis)"
                )
            }
        } else {
            // Detectando pero aún no confirmado
            return DetectionResult(
                hasChange = false, // IMPORTANTE: aún no confirmado
                confidence = rawResult.confidence * (consecutiveChangeCount / consecutiveDetectionsRequired.toFloat()),
                message = "Detectando... (${consecutiveChangeCount}/${consecutiveDetectionsRequired}) - Esperando confirmación"
            )
        }
    }

    /**
     * Libera recursos al destruir el manager.
     */
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
    
    companion object {
        /**
         * Intervalo entre análisis: 500ms (2 veces por segundo)
         */
        const val ANALYSIS_INTERVAL_MS = 500L
        
        /**
         * Número default de detecciones consecutivas requeridas para confirmar cambio.
         * Con análisis cada 500ms, 3 detecciones = 1-1.5 segundos de cambio sostenido.
         */
        const val DEFAULT_CONSECUTIVE_DETECTIONS = 3
        
        /**
         * Timeout default para estabilización: 2000ms (2 segundos).
         * Si entre detecciones pasa más tiempo, se resetea el contador.
         */
        const val DEFAULT_STABILIZATION_TIMEOUT_MS = 2000L
    }
}