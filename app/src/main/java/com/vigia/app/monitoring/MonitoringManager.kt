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
 *
 * Limitaciones temporales del MVP:
 * - Análisis periódico simple, no sincronizado exactamente con cada frame de cámara
 * - Si no hay frames de cámara disponibles, el análisis no produce resultados
 * - Sin buffer de frames ni cola de procesamiento
 *
 * Cambios recientes:
 * - Eliminada generación de datos simulados
 * - Añadida recepción de frames reales de CameraX
 * - Análisis ahora usa luminancia real del ROI
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
import kotlinx.coroutines.flow.first

/**
 * Gestor del estado de monitorización y análisis de ROI.
 * Controla si la vigilancia está activa y coordina el análisis del detector.
 *
 * @param detector Detector de cambios a utilizar (default: SimpleFrameDifferenceDetector)
 * @param scope CoroutineScope para lanzar el análisis periódico
 */
class MonitoringManager(
    private val detector: RoiDetector = SimpleFrameDifferenceDetector(threshold = 30),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _isMonitoring = MutableStateFlow(false)
    /**
     * Estado actual de la monitorización (true = vigilancia activa).
     */
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _detectionResult = MutableStateFlow<DetectionResult?>(null)
    /**
     * Último resultado de detección (null si no hay análisis reciente).
     */
    val detectionResult: StateFlow<DetectionResult?> = _detectionResult.asStateFlow()

    private var analysisJob: Job? = null
    private var currentRoi: Roi? = null
    private var frameDataFlow: StateFlow<FrameData?>? = null

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
        detector.reset() // Establecer nueva referencia base

        // Iniciar análisis periódico
        analysisJob = scope.launch {
            while (isActive) {
                performAnalysis()
                delay(500) // Analizar cada 500ms (2 veces por segundo)
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
        }
    }

    /**
     * Realiza un ciclo de análisis usando frames reales de la cámara.
     * Espera el último frame disponible y lo analiza con el detector.
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

        // Obtener el último frame disponible (sin bloquear indefinidamente)
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
        val result = detector.analyze(frameData, roi)
        _detectionResult.value = result
    }

    /**
     * Libera recursos al destruir el manager.
     */
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}