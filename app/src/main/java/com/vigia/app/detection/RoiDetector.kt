/**
 * Archivo: app/src/main/java/com/vigia/app/detection/RoiDetector.kt
 * Propósito: Interfaz para la lógica de detección de cambios en el ROI.
 * Responsabilidad principal: Definir el contrato que deben implementar los detectores de cambio.
 * Alcance: Capa de detección, abstracción de la estrategia de detección.
 *
 * Decisiones técnicas relevantes:
 * - Interfaz para permitir múltiples implementaciones (estrategia de diseño)
 * - Método simple analyze() que recibe datos del frame y devuelve resultado
 * - Soporte para ROI nulo (detector puede operar sobre frame completo o ignorar)
 *
 * Limitaciones temporales del MVP:
 * - Solo análisis 2D básico, sin reconocimiento de objetos complejos
 * - Sin histórico de frames ni aprendizaje
 *
 * Cambios recientes: Creación inicial de la interfaz.
 */
package com.vigia.app.detection

import com.vigia.app.domain.model.Roi

/**
 * Resultado del análisis de detección.
 *
 * @property hasChange Indica si se detectó un cambio significativo
 * @property confidence Nivel de confianza del resultado (0.0 - 1.0)
 * @property message Descripción legible del resultado para debug/UX
 */
data class DetectionResult(
    val hasChange: Boolean,
    val confidence: Float,
    val message: String
)

/**
 * Interfaz para detectores de cambio en ROI.
 */
interface RoiDetector {

    /**
     * Analiza un frame para detectar cambios en el área del ROI.
     *
     * @param frameData Datos del frame (bytes, bitmap o matriz según implementación)
     * @param roi Región de interés a analizar (puede ser null para frame completo)
     * @return Resultado del análisis con indicación de cambio detectado
     */
    fun analyze(frameData: FrameData, roi: Roi?): DetectionResult

    /**
     * Reinicia el estado del detector (por ejemplo, referencia base).
     */
    fun reset()
}

/**
 * Datos básicos de un frame capturado.
 *
 * @property timestamp Momento de captura (ms desde época)
 * @property width Ancho del frame en píxeles
 * @property height Alto del frame en píxeles
 * @property luminanceArray Array de luminancia (grayscale) para análisis simple
 */
data class FrameData(
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val luminanceArray: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameData
        return timestamp == other.timestamp &&
               width == other.width &&
               height == other.height &&
               luminanceArray.contentEquals(other.luminanceArray)
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + luminanceArray.contentHashCode()
        return result
    }
}