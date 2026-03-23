/**
 * Archivo: app/src/main/java/com/vigia/app/detection/SimpleFrameDifferenceDetector.kt
 * Propósito: Implementación provisional de detector de cambios basado en diferencia de frames.
 * Responsabilidad principal: Detectar cambios visuales comparando frames consecutivos del ROI.
 * Alcance: Capa de detección, implementación concreta para MVP.
 *
 * Decisiones técnicas relevantes:
 * - Implementación simple sin dependencias externas (OpenCV, MLKit, etc.)
 * - Comparación basada en luminancia media del área del ROI
 * - Umbral configurable para ajustar sensibilidad
 * - Referencia base que se establece al iniciar o reiniciar
 *
 * Limitaciones temporales del MVP:
 * - LÓGICA PROVISIONAL: Esta implementación es placeholder para demostrar el flujo
 * - Sin procesamiento real de píxeles (los FrameData se procesan de forma simulada)
 * - Sin normalización avanzada ni compensación de iluminación
 * - Sin detección de movimiento ni análisis de textura
 *
 * Cambios recientes: Creación inicial del detector provisional.
 */
package com.vigia.app.detection

import com.vigia.app.domain.model.Roi
import kotlin.math.abs

/**
 * Detector simple basado en diferencia de frames.
 *
 * NOTA: Esta es una implementación PROVISIONAL para el MVP.
 * En iteraciones futuras se reemplazará por análisis real de píxeles
 * o integración con librerías de visión por computador.
 *
 * @param threshold Umbral de diferencia para considerar cambio (0-255, default 30)
 */
class SimpleFrameDifferenceDetector(
    private val threshold: Int = 30
) : RoiDetector {

    private var referenceFrame: FrameData? = null
    private var frameCount: Int = 0

    override fun analyze(frameData: FrameData, roi: Roi?): DetectionResult {
        frameCount++

        // Si no hay ROI, no se puede analizar el área específica
        if (roi == null) {
            return DetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Sin ROI definido"
            )
        }

        // Primera vez: establecer referencia
        if (referenceFrame == null) {
            referenceFrame = frameData
            return DetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Referencia base establecida"
            )
        }

        // Calcular diferencia (simulada/provisional)
        // En implementación real: extraer ROI del frame, calcular histograma o diferencia píxel a píxel
        val diff = calculateDifference(referenceFrame!!, frameData, roi)

        val hasChange = diff > threshold
        val confidence = (diff / 255f).coerceIn(0f, 1f)

        // Actualizar referencia si no hay cambio (adaptación lenta) - opcional
        // if (!hasChange) { referenceFrame = frameData }

        return DetectionResult(
            hasChange = hasChange,
            confidence = confidence,
            message = if (hasChange) {
                "Cambio detectado (diff: ${diff.toInt()})"
            } else {
                "Sin cambio relevante (diff: ${diff.toInt()})"
            }
        )
    }

    override fun reset() {
        referenceFrame = null
        frameCount = 0
    }

    /**
     * Calcula la diferencia entre dos frames en el área del ROI.
     *
     * NOTA PROVISIONAL: Actualmente calcula una diferencia simulada basada en
     * estadísticas simples. En implementación real se debe:
     * 1. Extraer la región del ROI de ambos frames
     * 2. Calcular diferencia absoluta pixel a pixel
     * 3. Promediar o calcular métrica de cambio significativo
     */
    private fun calculateDifference(ref: FrameData, current: FrameData, roi: Roi): Float {
        // PROVISIONAL: Simulación simple usando checksum de los datos
        // En implementación real: procesamiento de matriz de píxeles del ROI

        val refChecksum = ref.luminanceArray.sum()
        val currentChecksum = current.luminanceArray.sum()

        // Factor de escala basado en tamaño del ROI (simulado)
        val roiArea = roi.width * roi.height

        // Diferencia normalizada (simulada)
        val rawDiff = abs(currentChecksum - refChecksum)
        val normalizedDiff = (rawDiff / (ref.luminanceArray.size * roiArea * 255f)) * 10000

        return normalizedDiff.coerceIn(0f, 255f)
    }
}