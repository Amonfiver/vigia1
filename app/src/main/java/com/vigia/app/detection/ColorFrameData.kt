/**
 * Archivo: app/src/main/java/com/vigia/app/detection/ColorFrameData.kt
 * Propósito: Datos de frame con información cromática para análisis de color.
 * Responsabilidad principal: Proporcionar acceso a píxeles en formato HSV para detección de colores.
 * Alcance: Capa de detección, reemplazo de FrameData para análisis cromático.
 *
 * Decisiones técnicas relevantes:
 * - Uso de HSV (Hue, Saturation, Value) en lugar de RGB para detección de colores
 *   * HSV separa el tono (color) de la intensidad, facilitando detección de naranja/rojo
 *   * El canal H (Hue) es invariante a cambios de iluminación (sombreado)
 *   * Más robusto que RGB para clasificación por color en entornos industriales variables
 * - Almacenamiento como array de Int con H, S, V empaquetados para eficiencia de memoria
 * - Mantenimiento de metadatos de ROI para subregión activa
 *
 * Limitaciones temporales del MVP:
 * - Conversión YUV→HSV aproximada sin tabla de lookup optimizada
 * - Sin compensación de balance de blancos automático
 * - Resolución fija de análisis (320x240) para rendimiento
 *
 * Cambios recientes: Creación inicial - migración de grayscale a análisis cromático.
 */
package com.vigia.app.detection

/**
 * Representa un píxel en espacio de color HSV.
 * Todos los valores en rangos de 0-255 para eficiencia de almacenamiento.
 *
 * @property h Hue (tono): 0-255 mapeado a 0°-360°
 *           - Rojo ~0-15 y ~230-255
 *           - Naranja ~15-35
 *           - Amarillo ~35-55
 *           - Verde ~55-95
 *           - etc.
 * @property s Saturation (saturación): 0-255 (0=gris, 255=color puro)
 * @property v Value (valor/brillo): 0-255 (0=negro, 255=brillo máximo)
 */
data class HsvPixel(
    val h: Int,
    val s: Int,
    val v: Int
) {
    /**
     * Verifica si este píxel está en el rango de tonos naranja.
     * Naranja: Hue entre 15-35 (aproximadamente 20°-50° en escala 0-360)
     * Requiere saturación suficiente para descartar grises.
     */
    fun isOrange(minSaturation: Int = 60): Boolean {
        return (h in 15..35) && s >= minSaturation
    }

    /**
     * Verifica si este píxel está en el rango de tonos rojo.
     * Rojo: Hue entre 0-15 o 230-255 (aproximadamente 0°-20° o 320°-360°)
     * Requiere saturación suficiente para descartar grises.
     */
    fun isRed(minSaturation: Int = 60): Boolean {
        return (h <= 15 || h >= 230) && s >= minSaturation
    }

    /**
     * Verifica si es un color "vivo" (saturado) vs gris/negro/blanco.
     */
    fun isColorful(minSaturation: Int = 40): Boolean {
        return s >= minSaturation && v >= 30
    }

    companion object {
        /**
         * Convierte RGB a HSV.
         * Optimizado para cálculo rápido sin crear objetos intermedios.
         */
        fun fromRgb(r: Int, g: Int, b: Int): HsvPixel {
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val diff = max - min

            // Value (brillo)
            val v = max

            // Saturation
            val s = if (max == 0) 0 else (diff * 255) / max

            // Hue
            val h = when {
                diff == 0 -> 0
                max == r -> ((g - b) * 60) / diff + if (g < b) 360 else 0
                max == g -> ((b - r) * 60) / diff + 120
                else -> ((r - g) * 60) / diff + 240
            }

            // Mapear hue de 0-360 a 0-255 para almacenamiento eficiente
            val hNormalized = (h * 255) / 360

            return HsvPixel(hNormalized.coerceIn(0, 255), s.coerceIn(0, 255), v.coerceIn(0, 255))
        }
    }
}

/**
 * Datos de frame con información cromática en HSV.
 * Reemplaza a FrameData para análisis basado en color.
 *
 * @property timestamp Momento de captura (ms desde época)
 * @property width Ancho del frame en píxeles
 * @property height Alto del frame en píxeles
 * @property hsvArray Array de píxeles HSV (size = width * height)
 * @property subRegion Región activa dentro del ROI global (null = usar ROI completo)
 */
data class ColorFrameData(
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val hsvArray: Array<HsvPixel>,
    val subRegion: SubRegion? = null
) {
    init {
        require(hsvArray.size == width * height) {
            "Tamaño del array (${hsvArray.size}) no coincide con dimensiones ${width}x${height}"
        }
    }

    /**
     * Obtiene el píxel HSV en coordenadas (x, y).
     */
    fun getPixel(x: Int, y: Int): HsvPixel {
        require(x in 0 until width) { "x=$x fuera de rango [0, $width)" }
        require(y in 0 until height) { "y=$y fuera de rango [0, $height)" }
        return hsvArray[y * width + x]
    }

    /**
     * Obtiene el píxel HSV en coordenadas normalizadas (0.0-1.0).
     */
    fun getPixelNormalized(nx: Float, ny: Float): HsvPixel {
        val x = (nx * width).toInt().coerceIn(0, width - 1)
        val y = (ny * height).toInt().coerceIn(0, height - 1)
        return getPixel(x, y)
    }

    /**
     * Extrae un sub-frame correspondiente a una región de interés.
     * Útil para análisis enfocado en subregión activa.
     *
     * @param left Normalizado 0.0-1.0
     * @param top Normalizado 0.0-1.0
     * @param right Normalizado 0.0-1.0
     * @param bottom Normalizado 0.0-1.0
     */
    fun extractRegion(left: Float, top: Float, right: Float, bottom: Float): ColorFrameData {
        val x1 = (left * width).toInt().coerceIn(0, width)
        val y1 = (top * height).toInt().coerceIn(0, height)
        val x2 = (right * width).toInt().coerceIn(0, width)
        val y2 = (bottom * height).toInt().coerceIn(0, height)

        val regionWidth = x2 - x1
        val regionHeight = y2 - y1

        require(regionWidth > 0 && regionHeight > 0) { "Región inválida: ancho=$regionWidth, alto=$regionHeight" }

        val regionArray = Array(regionWidth * regionHeight) { i ->
            val localX = i % regionWidth
            val localY = i / regionWidth
            val sourceX = x1 + localX
            val sourceY = y1 + localY
            hsvArray[sourceY * width + sourceX]
        }

        return ColorFrameData(
            timestamp = timestamp,
            width = regionWidth,
            height = regionHeight,
            hsvArray = regionArray
        )
    }

    /**
     * Calcula estadísticas de color para la región.
     */
    fun calculateColorStats(): ColorStats {
        var orangeCount = 0
        var redCount = 0
        var colorfulCount = 0
        var totalSaturation = 0L
        var totalValue = 0L

        for (pixel in hsvArray) {
            if (pixel.isOrange()) orangeCount++
            if (pixel.isRed()) redCount++
            if (pixel.isColorful()) colorfulCount++
            totalSaturation += pixel.s
            totalValue += pixel.v
        }

        val pixelCount = hsvArray.size
        return ColorStats(
            orangePixelCount = orangeCount,
            redPixelCount = redCount,
            colorfulPixelCount = colorfulCount,
            totalPixels = pixelCount,
            orangePercentage = (orangeCount * 100f) / pixelCount,
            redPercentage = (redCount * 100f) / pixelCount,
            avgSaturation = (totalSaturation / pixelCount).toInt(),
            avgValue = (totalValue / pixelCount).toInt()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ColorFrameData
        return timestamp == other.timestamp &&
               width == other.width &&
               height == other.height &&
               hsvArray.contentEquals(other.hsvArray)
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + hsvArray.contentHashCode()
        return result
    }

    companion object {
        /**
         * Crea un ColorFrameData vacío para casos de error o inicialización.
         */
        fun empty(width: Int = 320, height: Int = 240): ColorFrameData {
            return ColorFrameData(
                timestamp = 0,
                width = width,
                height = height,
                hsvArray = Array(width * height) { HsvPixel(0, 0, 0) }
            )
        }
    }
}

/**
 * Estadísticas de color calculadas sobre una región.
 */
data class ColorStats(
    val orangePixelCount: Int,
    val redPixelCount: Int,
    val colorfulPixelCount: Int,
    val totalPixels: Int,
    val orangePercentage: Float,
    val redPercentage: Float,
    val avgSaturation: Int,
    val avgValue: Int
) {
    /**
     * Determina si hay presencia significativa de naranja.
     * Umbral provisional: más del 5% de píxeles naranja.
     */
    fun hasSignificantOrange(thresholdPercent: Float = 5f): Boolean {
        return orangePercentage >= thresholdPercent
    }

    /**
     * Determina si hay presencia significativa de rojo.
     * Umbral provisional: más del 5% de píxeles rojo.
     */
    fun hasSignificantRed(thresholdPercent: Float = 5f): Boolean {
        return redPercentage >= thresholdPercent
    }

    /**
     * Determina si la región tiene color (no es solo grises/blancos/negros).
     */
    fun hasColorContent(minColorfulPercent: Float = 10f): Boolean {
        return (colorfulPixelCount * 100f) / totalPixels >= minColorfulPercent
    }
}

/**
 * Representa una subregión activa dentro del ROI global.
 * Permite concentrar el análisis en la zona más informativa (cuerpo del transfer).
 *
 * @property left Normalizado 0.0-1.0 relativo al ROI global
 * @property top Normalizado 0.0-1.0 relativo al ROI global
 * @property right Normalizado 0.0-1.0 relativo al ROI global
 * @property bottom Normalizado 0.0-1.0 relativo al ROI global
 * @property description Descripción de la heurística usada
 */
data class SubRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val description: String = "Subregión activa"
) {
    init {
        require(left in 0.0f..1.0f) { "left debe estar entre 0.0 y 1.0" }
        require(top in 0.0f..1.0f) { "top debe estar entre 0.0 y 1.0" }
        require(right in 0.0f..1.0f) { "right debe estar entre 0.0 y 1.0" }
        require(bottom in 0.0f..1.0f) { "bottom debe estar entre 0.0 y 1.0" }
        require(left < right) { "left debe ser menor que right" }
        require(top < bottom) { "top debe ser menor que bottom" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    companion object {
        /**
         * Crea una subregión centrada con un porcentaje del tamaño del ROI global.
         * Heurística simple para concentrar análisis en el cuerpo del transfer,
         * ignorando bordes donde pueden estar las líneas de la vía.
         *
         * @param scaleFactor Factor de escala (0.0-1.0). Default 0.6 = 60% del área central.
         * @param verticalBias Sesgo vertical (0.0=arriba, 0.5=centro, 1.0=abajo).
         *                     Default 0.4 = ligeramente hacia arriba donde suele estar el cuerpo.
         */
        fun centered(
            scaleFactor: Float = 0.6f,
            verticalBias: Float = 0.4f,
            description: String = "Centro del ROI (${(scaleFactor * 100).toInt()}%)"
        ): SubRegion {
            require(scaleFactor in 0.1f..0.95f) { "scaleFactor debe estar entre 0.1 y 0.95" }

            val marginX = (1f - scaleFactor) / 2f
            val marginY = (1f - scaleFactor) / 2f

            // Aplicar sesgo vertical
            val topMargin = marginY * (2f - verticalBias * 2f)
            val bottomMargin = marginY * (verticalBias * 2f)

            return SubRegion(
                left = marginX,
                top = topMargin,
                right = 1f - marginX,
                bottom = 1f - bottomMargin,
                description = description
            )
        }

        /**
         * Subregión que cubre todo el ROI (sin recorte).
         */
        val FULL = SubRegion(0f, 0f, 1f, 1f, "ROI completo")
    }
}