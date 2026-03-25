/**
 * Archivo: app/src/main/java/com/vigia/app/detection/ColorBasedDetector.kt
 * Propósito: Detector de cambios basado en análisis cromático HSV.
 * Responsabilidad principal: Detectar presencia de colores significativos (naranja/rojo) en el ROI/subregión.
 * Alcance: Capa de detección, implementación concreta para análisis por color.
 *
 * Decisiones técnicas relevantes:
 * - Usa ColorFrameData con información HSV en lugar de luminancia
 * - Implementa estrategia de subregión activa dentro del ROI global
 * - Detección de naranja: candidato a obstáculo / atención inmediata
 * - Detección de rojo: candidato a fallo confirmado
 * - Sin dependencia semántica de grayscale/luminancia para decisión principal
 * - Luminancia solo como apoyo técnico para filtrado de condiciones extremas
 *
 * Heurística espacial (subregión activa):
 * - Dentro del ROI global definido por el usuario, se deriva una subregión activa
 * - Por defecto: 60% del área central, con sesgo vertical hacia arriba (0.4)
 * - Esto concentra el análisis en el cuerpo del transfer
 * - Reduce influencia del fondo de vía (blanco + líneas negras paralelas)
 * - La subregión es configurable pero documentada
 *
 * Limitaciones temporales del MVP:
 * - Umbrales de detección provisionales (5% de píxeles)
 * - Sin lógica temporal compleja de persistencia (20 segundos) en esta iteración
 * - Sin calibración automática de umbrales
 * - Detección por frame individual, sin tracking de estado entre frames
 *
 * Cambios recientes: Creación inicial - migración de grayscale a análisis cromático.
 */
package com.vigia.app.detection

import com.vigia.app.domain.model.Roi

/**
 * Resultado del análisis cromático.
 *
 * @property hasChange Indica si se detectó un cambio/significado significativo
 * @property confidence Nivel de confianza del resultado (0.0 - 1.0)
 * @property message Descripción legible del resultado para debug/UX
 * @property colorStats Estadísticas de color calculadas (para diagnóstico)
 * @property detectedSignal Tipo de señal detectada (None, Orange, Red)
 */
data class ColorDetectionResult(
    val hasChange: Boolean,
    val confidence: Float,
    val message: String,
    val colorStats: ColorStats? = null,
    val detectedSignal: DetectedSignal = DetectedSignal.NONE
)

/**
 * Tipos de señales visuales detectables.
 */
enum class DetectedSignal {
    NONE,           // Sin señal significativa
    ORANGE,         // Naranja: obstáculo / atención inmediata
    RED             // Rojo: fallo confirmado
}

/**
 * Detector basado en análisis cromático HSV.
 * Reemplaza la dependencia de grayscale como señal principal.
 *
 * @param orangeThreshold Umbral mínimo de % píxeles naranja para detección (default 5%)
 * @param redThreshold Umbral mínimo de % píxeles rojo para detección (default 5%)
 * @param subRegionStrategy Estrategia para derivar subregión activa (default: centrado 60%)
 */
class ColorBasedDetector(
    private val orangeThreshold: Float = 5f,
    private val redThreshold: Float = 5f,
    private val subRegionStrategy: SubRegionStrategy = SubRegionStrategy.CENTERED_60
) : RoiDetector {

    /**
     * Estrategias para definir la subregión activa dentro del ROI global.
     */
    enum class SubRegionStrategy {
        FULL_ROI,           // Usar ROI completo sin recorte
        CENTERED_80,        // 80% central del ROI
        CENTERED_60,        // 60% central del ROI (default)
        CENTERED_40         // 40% central del ROI
    }

    private var referenceStats: ColorStats? = null
    private var frameCount: Int = 0

    override fun analyze(frameData: FrameData, roi: Roi?): DetectionResult {
        // Este método existe por compatibilidad con interfaz RoiDetector
        // En la práctica, el detector cromático necesita ColorFrameData
        
        // Si no hay ROI, no se puede analizar
        if (roi == null) {
            return DetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Sin ROI definido"
            )
        }

        // En esta implementación, rechazamos FrameData legacy
        // El sistema debería usar analyzeColor() con ColorFrameData
        return DetectionResult(
            hasChange = false,
            confidence = 0f,
            message = "Detector cromático requiere ColorFrameData. Use analyzeColor()."
        )
    }

    /**
     * Análisis cromático principal.
     * Este es el método que debe usarse para detección por color.
     *
     * @param colorFrameData Frame con información HSV
     * @param roi ROI global definido por el usuario
     * @return Resultado del análisis cromático
     */
    fun analyzeColor(colorFrameData: ColorFrameData, roi: Roi?): ColorDetectionResult {
        frameCount++

        if (roi == null) {
            return ColorDetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Sin ROI definido",
                detectedSignal = DetectedSignal.NONE
            )
        }

        // Calcular subregión activa dentro del ROI global
        val subRegion = calculateSubRegion(subRegionStrategy)

        // Extraer región del frame correspondiente al ROI global
        val roiFrame = colorFrameData.extractRegion(roi.left, roi.top, roi.right, roi.bottom)

        // Aplicar subregión activa si no es el ROI completo
        val analysisFrame = if (subRegionStrategy != SubRegionStrategy.FULL_ROI) {
            roiFrame.extractRegion(subRegion.left, subRegion.top, subRegion.right, subRegion.bottom)
        } else {
            roiFrame
        }

        // Primera vez: establecer referencia
        if (referenceStats == null) {
            referenceStats = analysisFrame.calculateColorStats()
            return ColorDetectionResult(
                hasChange = false,
                confidence = 0f,
                message = "Referencia cromática base establecida (${analysisFrame.width}x${analysisFrame.height})",
                detectedSignal = DetectedSignal.NONE
            )
        }

        // Calcular estadísticas actuales
        val currentStats = analysisFrame.calculateColorStats()

        // Análisis de señales cromáticas
        val orangeDetected = currentStats.hasSignificantOrange(orangeThreshold)
        val redDetected = currentStats.hasSignificantRed(redThreshold)

        // Determinar señal predominante y confianza
        val (detectedSignal, confidence, message) = when {
            redDetected && orangeDetected -> {
                // Ambos detectados - priorizar rojo (más crítico)
                Triple(
                    DetectedSignal.RED,
                    currentStats.redPercentage / 100f,
                    "🔴 Rojo detectado (${"%.1f".format(currentStats.redPercentage)}%) + Naranja (${"%.1f".format(currentStats.orangePercentage)}%)"
                )
            }
            redDetected -> {
                Triple(
                    DetectedSignal.RED,
                    currentStats.redPercentage / 100f,
                    "🔴 Rojo detectado: ${"%.1f".format(currentStats.redPercentage)}% píxeles"
                )
            }
            orangeDetected -> {
                Triple(
                    DetectedSignal.ORANGE,
                    currentStats.orangePercentage / 100f,
                    "🟠 Naranja detectado: ${"%.1f".format(currentStats.orangePercentage)}% píxeles"
                )
            }
            else -> {
                Triple(
                    DetectedSignal.NONE,
                    0f,
                    "Sin señal cromática significativa (N:${"%.1f".format(currentStats.orangePercentage)}% R:${"%.1f".format(currentStats.redPercentage)}%)"
                )
            }
        }

        // hasChange = true si se detectó alguna señal
        val hasChange = detectedSignal != DetectedSignal.NONE

        return ColorDetectionResult(
            hasChange = hasChange,
            confidence = confidence.coerceIn(0f, 1f),
            message = message,
            colorStats = currentStats,
            detectedSignal = detectedSignal
        )
    }

    /**
     * Calcula la subregión activa según la estrategia configurada.
     */
    private fun calculateSubRegion(strategy: SubRegionStrategy): SubRegion {
        return when (strategy) {
            SubRegionStrategy.FULL_ROI -> SubRegion.FULL
            SubRegionStrategy.CENTERED_80 -> SubRegion.centered(0.8f, 0.4f, "Centro 80% del ROI")
            SubRegionStrategy.CENTERED_60 -> SubRegion.centered(0.6f, 0.4f, "Centro 60% del ROI (cuerpo del transfer)")
            SubRegionStrategy.CENTERED_40 -> SubRegion.centered(0.4f, 0.4f, "Centro 40% del ROI")
        }
    }

    /**
     * Obtiene la descripción de la heurística espacial actual.
     * Útil para documentación y debugging.
     */
    fun getSubRegionDescription(): String {
        val subRegion = calculateSubRegion(subRegionStrategy)
        return """
            Estrategia: ${subRegionStrategy.name}
            ${subRegion.description}
            Área: ${"%.1f".format(subRegion.area * 100)}% del ROI global
            Coordenadas relativas al ROI: [${"%.2f".format(subRegion.left)}, ${"%.2f".format(subRegion.top)}] - [${"%.2f".format(subRegion.right)}, ${"%.2f".format(subRegion.bottom)}]
        """.trimIndent()
    }

    override fun reset() {
        referenceStats = null
        frameCount = 0
    }

    /**
     * Obtiene estadísticas de la última referencia (para diagnóstico).
     */
    fun getReferenceStats(): ColorStats? = referenceStats
}