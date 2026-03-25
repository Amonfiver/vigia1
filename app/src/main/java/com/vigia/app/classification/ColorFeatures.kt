/**
 * Archivo: app/src/main/java/com/vigia/app/classification/ColorFeatures.kt
 * Propósito: Modelo de características de color extraídas de imágenes para clasificación.
 * Responsabilidad principal: Representar features simples, explicables y comparables entre muestras.
 * Alcance: Capa de clasificación, base para comparación de imágenes del dataset.
 *
 * Decisiones técnicas relevantes:
 * - Histograma H (Hue) con 16 bins para balance entre precisión y generalización
 * - Estadísticas resumidas de S (Saturation) y V (Value): media y desviación estándar
 * - Features cromáticas específicas: % naranja, % rojo (ya calculadas en ColorStats)
 * - Todas las features normalizadas a rangos comparables (0.0-1.0)
 * - Sin dependencias de ML externo, completamente explicable
 *
 * Limitaciones temporales del MVP:
 * - Histograma con pocos bins (16) puede perder detalles finos
 * - Sin ponderación adaptativa de features
 * - Sin reducción de dimensionalidad
 * - Features globales de la imagen, sin análisis de regiones locales
 *
 * Cambios recientes:
 * - Creación inicial para clasificación automática basada en dataset etiquetado
 */
package com.vigia.app.classification

import com.vigia.app.detection.ColorFrameData
import com.vigia.app.detection.ColorStats
import kotlin.math.sqrt

/**
 * Características de color extraídas de una imagen para clasificación.
 *
 * @property hueHistogram Histograma normalizado de Hue (16 bins, suma = 1.0)
 * @property saturationMean Media de saturación (0.0-1.0)
 * @property saturationStd Desviación estándar de saturación (0.0-1.0)
 * @property valueMean Media de value/brillo (0.0-1.0)
 * @property valueStd Desviación estándar de value (0.0-1.0)
 * @property orangePercentage Porcentaje de píxeles naranja (0.0-100.0)
 * @property redPercentage Porcentaje de píxeles rojo (0.0-100.0)
 * @property colorfulPercentage Porcentaje de píxeles con color (no grises) (0.0-100.0)
 * @property timestamp Momento de extracción
 */
data class ColorFeatures(
    val hueHistogram: FloatArray,
    val saturationMean: Float,
    val saturationStd: Float,
    val valueMean: Float,
    val valueStd: Float,
    val orangePercentage: Float,
    val redPercentage: Float,
    val colorfulPercentage: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    init {
        require(hueHistogram.size == HUE_BINS) { "Histograma debe tener $HUE_BINS bins" }
        require(hueHistogram.sum() in 0.99f..1.01f) { "Histograma debe estar normalizado (suma ≈ 1.0)" }
    }

    companion object {
        const val HUE_BINS = 16
        const val HUE_BIN_SIZE = 256 / HUE_BINS // 16 valores por bin

        /**
         * Extrae características de color de un ColorFrameData.
         *
         * @param frameData Frame con información HSV
         * @return ColorFeatures extraídas
         */
        fun fromColorFrameData(frameData: ColorFrameData): ColorFeatures {
            val pixelCount = frameData.hsvArray.size

            // Calcular histograma de Hue
            val histogram = IntArray(HUE_BINS)
            var saturationSum = 0L
            var saturationSqSum = 0L
            var valueSum = 0L
            var valueSqSum = 0L
            var orangeCount = 0
            var redCount = 0
            var colorfulCount = 0

            for (pixel in frameData.hsvArray) {
                // Histograma H
                val bin = pixel.h / HUE_BIN_SIZE
                histogram[bin.coerceIn(0, HUE_BINS - 1)]++

                // Acumuladores para S y V
                saturationSum += pixel.s
                saturationSqSum += pixel.s * pixel.s
                valueSum += pixel.v
                valueSqSum += pixel.v * pixel.v

                // Contadores de colores específicos
                if (pixel.isOrange()) orangeCount++
                if (pixel.isRed()) redCount++
                if (pixel.isColorful()) colorfulCount++
            }

            // Normalizar histograma
            val normalizedHistogram = histogram.map { it.toFloat() / pixelCount }.toFloatArray()

            // Calcular medias
            val satMean = saturationSum.toFloat() / (pixelCount * 255)
            val valMean = valueSum.toFloat() / (pixelCount * 255)

            // Calcular desviaciones estándar
            val satVariance = (saturationSqSum.toFloat() / pixelCount - (saturationSum.toFloat() / pixelCount).pow2()) / (255 * 255)
            val valVariance = (valueSqSum.toFloat() / pixelCount - (valueSum.toFloat() / pixelCount).pow2()) / (255 * 255)

            return ColorFeatures(
                hueHistogram = normalizedHistogram,
                saturationMean = satMean.coerceIn(0f, 1f),
                saturationStd = sqrt(satVariance.coerceAtLeast(0f)).coerceIn(0f, 1f),
                valueMean = valMean.coerceIn(0f, 1f),
                valueStd = sqrt(valVariance.coerceAtLeast(0f)).coerceIn(0f, 1f),
                orangePercentage = (orangeCount * 100f) / pixelCount,
                redPercentage = (redCount * 100f) / pixelCount,
                colorfulPercentage = (colorfulCount * 100f) / pixelCount
            )
        }

        /**
         * Extrae características de color de un ColorStats (para compatibilidad).
         */
        fun fromColorStats(colorStats: ColorStats): ColorFeatures {
            // Cuando solo tenemos ColorStats, usamos distribución uniforme para histograma
            // Esto es una aproximación para casos donde no tenemos el frame completo
            val uniformHistogram = FloatArray(HUE_BINS) { 1f / HUE_BINS }

            return ColorFeatures(
                hueHistogram = uniformHistogram,
                saturationMean = colorStats.avgSaturation / 255f,
                saturationStd = 0.1f, // Valor por defecto desconocido
                valueMean = colorStats.avgValue / 255f,
                valueStd = 0.1f, // Valor por defecto desconocido
                orangePercentage = colorStats.orangePercentage,
                redPercentage = colorStats.redPercentage,
                colorfulPercentage = (colorStats.colorfulPixelCount * 100f) / colorStats.totalPixels
            )
        }

        private fun Float.pow2(): Float = this * this
    }

    /**
     * Calcula la distancia euclidiana entre dos vectores de features.
     * Pondera cada tipo de feature según su importancia relativa.
     *
     * @param other Otra instancia de ColorFeatures para comparar
     * @return Distancia (0.0 = idénticas, mayor = más diferentes)
     */
    fun distanceTo(other: ColorFeatures): Float {
        var distance = 0f

        // Distancia de histograma (Chi-cuadrado modificado)
        var histogramDist = 0f
        for (i in hueHistogram.indices) {
            val diff = hueHistogram[i] - other.hueHistogram[i]
            val sum = hueHistogram[i] + other.hueHistogram[i] + 1e-6f // Evitar división por cero
            histogramDist += (diff * diff) / sum
        }
        distance += histogramDist * HISTOGRAM_WEIGHT

        // Distancia de estadísticas S/V
        val statDist = listOf(
            (saturationMean - other.saturationMean),
            (saturationStd - other.saturationStd),
            (valueMean - other.valueMean),
            (valueStd - other.valueStd)
        ).sumOf { (it * it).toDouble() }.toFloat()
        distance += sqrt(statDist) * STATS_WEIGHT

        // Distancia de porcentajes cromáticos
        val colorDist = sqrt(
            (orangePercentage - other.orangePercentage).pow2() +
            (redPercentage - other.redPercentage).pow2() +
            (colorfulPercentage - other.colorfulPercentage).pow2()
        )
        distance += colorDist * COLOR_WEIGHT

        return distance
    }

    /**
     * Calcula similitud (0.0-1.0) donde 1.0 = idénticas.
     */
    fun similarityTo(other: ColorFeatures): Float {
        val dist = distanceTo(other)
        // Transformar distancia a similitud usando función exponencial
        return kotlin.math.exp(-dist / NORMALIZATION_FACTOR).coerceIn(0f, 1f)
    }

    /**
     * Obtiene el bin dominante del histograma de Hue.
     * Útil para diagnóstico rápido.
     */
    fun dominantHueBin(): Int {
        return hueHistogram.indices.maxByOrNull { hueHistogram[it] } ?: 0
    }

    /**
     * Describe textualmente las features principales.
     */
    fun summary(): String {
        val dominantBin = dominantHueBin()
        val hueRange = when (dominantBin) {
            0, 15 -> "Rojo"
            in 1..2 -> "Naranja/Rojo"
            in 3..5 -> "Amarillo/Naranja"
            in 6..8 -> "Verde"
            in 9..11 -> "Cyan/Azul"
            in 12..13 -> "Azul/Morado"
            else -> "Rosa/Magenta"
        }

        return buildString {
            append("Hue dominante: $hueRange (bin $dominantBin), ")
            append("S=${"%.2f".format(saturationMean)}±${"%.2f".format(saturationStd)}, ")
            append("V=${"%.2f".format(valueMean)}±${"%.2f".format(valueStd)}, ")
            append("🟠${"%.1f".format(orangePercentage)}% ")
            append("🔴${"%.1f".format(redPercentage)}%")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ColorFeatures
        return hueHistogram.contentEquals(other.hueHistogram) &&
               saturationMean == other.saturationMean &&
               saturationStd == other.saturationStd &&
               valueMean == other.valueMean &&
               valueStd == other.valueStd &&
               orangePercentage == other.orangePercentage &&
               redPercentage == other.redPercentage &&
               colorfulPercentage == other.colorfulPercentage
    }

    override fun hashCode(): Int {
        var result = hueHistogram.contentHashCode()
        result = 31 * result + saturationMean.hashCode()
        result = 31 * result + saturationStd.hashCode()
        result = 31 * result + valueMean.hashCode()
        result = 31 * result + valueStd.hashCode()
        result = 31 * result + orangePercentage.hashCode()
        result = 31 * result + redPercentage.hashCode()
        result = 31 * result + colorfulPercentage.hashCode()
        return result
    }

    private fun Float.pow2(): Float = this * this
}

// Pesos para cálculo de distancia (ajustables)
private const val HISTOGRAM_WEIGHT = 0.4f
private const val STATS_WEIGHT = 0.3f
private const val COLOR_WEIGHT = 0.3f
private const val NORMALIZATION_FACTOR = 0.5f // Para transformación exp(-dist/N)