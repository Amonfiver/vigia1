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
 *
 * Limitaciones temporales del MVP:
 * - FrameData simulado (no se capturan frames reales de la cámara todavía)
 * - Análisis periódico simple sin sincronización exacta con frames de cámara
 *
 * Cambios recientes:
 * - Añadida integración con RoiDetector
 * - Añadido estado de resultado de detección
 * - Implementado análisis periódico durante vigilancia activa
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
import kotlin.random.Random

/**
 * Gestor del estado de monitorización y análisis de ROI.
 * Controla si la vigilancia está activa y coordina el análisis del detector.
 *
 * @param detector Detector de cambios a utilizar (default: SimpleFrameDifferenceDetector)
 * @param scope CoroutineScope para lanzar el análisis periódico (default: GlobalScope)
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
                delay(1000) // Analizar cada segundo (en MVP, luego será configurable)
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
     * Realiza un ciclo de análisis.
     * PROVISIONAL: Genera FrameData simulado para demostrar el flujo.
     * En implementación real se obtendría el frame actual de la cámara.
     */
    private fun performAnalysis() {
        val roi = currentRoi

        // PROVISIONAL: Generar FrameData simulado
        // En implementación real: capturar frame de CameraX y convertir a FrameData
        val frameData = generateSimulatedFrameData()

        // Realizar análisis
        val result = detector.analyze(frameData, roi)
        _detectionResult.value = result
    }

    /**
     * Genera datos de frame simulados para el MVP.
     * NOTA: Este método es PROVISIONAL y se eliminará cuando se implemente
     * captura real de frames desde CameraX.
     */
    private fun generateSimulatedFrameData(): FrameData {
        // Generar array de luminancia aleatorio para simular frame
        val width = 640
        val height = 480
        val size = width * height

        // Simulación: 90% de probabilidad de frame similar, 10% de cambio
        val baseValue = if (Random.nextFloat() > 0.9f) {
            Random.nextInt(50, 200) // Cambio significativo
        } else {
            128 // Valor base similar
        }

        val luminanceArray = IntArray(size) {
            (baseValue + Random.nextInt(-20, 20)).coerceIn(0, 255)
        }

        return FrameData(
            timestamp = System.currentTimeMillis(),
            width = width,
            height = height,
            luminanceArray = luminanceArray
        )
    }

    /**
     * Libera recursos al destruir el manager.
     */
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}