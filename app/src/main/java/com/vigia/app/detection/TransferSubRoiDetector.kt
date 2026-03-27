/**
 * Archivo: app/src/main/java/com/vigia/app/detection/TransferSubRoiDetector.kt
 * Propósito: Detectar y extraer la subregión del cuerpo del transfer dentro del ROI global.
 * Responsabilidad principal: Derivar una subROI más informativa que el ROI completo, ignorando
 * el fondo estructural de la vía (blanco + líneas negras) para concentrarse en el transfer.
 * Alcance: Capa de detección, refinamiento espacial del ROI para clasificación.
 *
 * Decisiones técnicas relevantes:
 * - Heurística basada en análisis de color/estructura para identificar región del transfer
 * - El transfer suele tener color distinto al fondo blanco de la vía
 * - Se excluyen píxeles blancos (fondo) y negros (líneas) cuando es posible
 * - Se busca el bounding box de la región con contenido cromático significativo
 * - Fallback a heurística centrada si la detección automática falla
 *
 * Estrategia de detección:
 * 1. Analizar distribución de color en el ROI
 * 2. Identificar píxeles candidatos a transfer (no blancos, no negros, con color)
 * 3. Calcular bounding box de la región con mayor densidad de píxeles candidatos
 * 4. Validar que el bounding box tiene tamaño razonable (>30% del ROI)
 * 5. Si no es válido, usar heurística centrada con 60% del área
 *
 * Limitaciones temporales:
 * - Detección basada en color simple, sin formas ni contornos complejos
 * - Asume que el transfer tiene color distintivo respecto al fondo
 * - Sin tracking temporal (cada frame es independiente)
 * - Parámetros de umbral fijos, no adaptativos
 *
 * Cambios recientes: Creación inicial para refinamiento de ROI en clasificación.
 */
package com.vigia.app.detection

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.vigia.app.domain.model.Roi
import kotlin.math.max
import kotlin.math.min

/**
 * Resultado de la detección de subROI del transfer.
 *
 * @property subRegion Subregión detectada dentro del ROI global (coordenadas relativas al ROI)
 * @property confidence Confianza en la detección (0.0-1.0)
 * @property method Método usado para la detección
 * @property stats Estadísticas de la detección para observabilidad
 */
data class TransferSubRoiResult(
    val subRegion: SubRegion,
    val confidence: Float,
    val method: DetectionMethod,
    val stats: SubRoiDetectionStats
) {
    /**
     * Convierte la subregión relativa al ROI en coordenadas absolutas del frame.
     */
    fun toAbsoluteCoordinates(roi: Roi): SubRegion {
        val roiWidth = roi.right - roi.left
        val roiHeight = roi.bottom - roi.top
        
        return SubRegion(
            left = roi.left + subRegion.left * roiWidth,
            top = roi.top + subRegion.top * roiHeight,
            right = roi.left + subRegion.right * roiWidth,
            bottom = roi.top + subRegion.bottom * roiHeight,
            description = subRegion.description
        )
    }
}

/**
 * Métodos de detección disponibles.
 */
enum class DetectionMethod {
    MANUAL,             // SubROI definida manualmente por el usuario (preferida)
    COLOR_MASK,         // Detección basada en máscara de color
    CENTERED_HEURISTIC, // Heurística centrada (fallback)
    FULL_ROI            // Usar ROI completo (último fallback)
}

/**
 * Estadísticas del proceso de detección.
 */
data class SubRoiDetectionStats(
    val totalPixelsAnalyzed: Int,
    val candidatePixelsFound: Int,
    val candidatePercentage: Float,
    val boundingBoxCoverage: Float,
    val avgColorfulness: Float,
    val detectionTimeMs: Long
)

/**
 * Detector de subROI del transfer dentro del ROI global.
 *
 * @param minColorfulness Umbral mínimo de "color" para considerar un píxel (0-255)
 * @param minSubRoiSize Tamaño mínimo de la subROI como fracción del ROI (0.0-1.0)
 * @param maxSubRoiSize Tamaño máximo de la subROI como fracción del ROI (0.0-1.0)
 */
class TransferSubRoiDetector(
    private val minColorfulness: Int = 40,
    private val minSubRoiSize: Float = 0.3f,
    private val maxSubRoiSize: Float = 0.9f
) {
    /**
     * Detecta la subregión del transfer dentro del ROI dado un frame.
     *
     * @param frameData Frame con información cromática
     * @param roi ROI global definido por el usuario
     * @return Resultado de la detección con subregión y metadatos
     */
    fun detectTransferSubRoi(
        frameData: ColorFrameData,
        roi: Roi
    ): TransferSubRoiResult {
        val startTime = System.currentTimeMillis()
        
        // Extraer región del ROI del frame
        val roiFrame = try {
            frameData.extractRegion(roi.left, roi.top, roi.right, roi.bottom)
        } catch (e: Exception) {
            // Si falla la extracción, usar ROI completo
            return createFallbackResult(DetectionMethod.FULL_ROI, startTime)
        }
        
        // Método 1: Intentar detección por máscara de color
        val colorMaskResult = detectByColorMask(roiFrame)
        
        if (colorMaskResult != null && isValidSubRegion(colorMaskResult)) {
            val detectionTime = System.currentTimeMillis() - startTime
            return TransferSubRoiResult(
                subRegion = colorMaskResult,
                confidence = calculateConfidence(colorMaskResult, roiFrame),
                method = DetectionMethod.COLOR_MASK,
                stats = generateStats(colorMaskResult, roiFrame, detectionTime)
            )
        }
        
        // Método 2: Fallback a heurística centrada
        val heuristicRegion = SubRegion.centered(0.6f, 0.4f, "Centro 60% (heurística)")
        val detectionTime = System.currentTimeMillis() - startTime
        
        return TransferSubRoiResult(
            subRegion = heuristicRegion,
            confidence = 0.5f,
            method = DetectionMethod.CENTERED_HEURISTIC,
            stats = generateStats(heuristicRegion, roiFrame, detectionTime)
        )
    }
    
    /**
     * Detecta la subregión basándose en máscara de color.
     * Busca píxeles con color significativo (no blancos, no negros).
     */
    private fun detectByColorMask(roiFrame: ColorFrameData): SubRegion? {
        val width = roiFrame.width
        val height = roiFrame.height
        
        // Crear máscara binaria: true = píxel candidato a transfer
        val mask = BooleanArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val pixel = roiFrame.getPixel(x, y)
            
            // Píxel candidato: tiene color (no es gris/blanco/negro puro)
            // y saturación suficiente
            isColorfulPixel(pixel)
        }
        
        // Encontrar bounding box de la máscara
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var candidateCount = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                    candidateCount++
                }
            }
        }
        
        // Si no hay suficientes píxeles candidatos, fallar
        if (candidateCount < (width * height * 0.05)) { // Mínimo 5% de píxeles
            return null
        }
        
        // Convertir a coordenadas normalizadas
        val left = minX.toFloat() / width
        val top = minY.toFloat() / height
        val right = (maxX + 1).toFloat() / width
        val bottom = (maxY + 1).toFloat() / height
        
        return SubRegion(
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
            description = "Transfer detectado por color (${(candidateCount * 100 / (width * height))}% píxeles)"
        )
    }
    
    /**
     * Verifica si un píxel HSV es "colorido" (no blanco, negro, o gris puro).
     */
    private fun isColorfulPixel(pixel: HsvPixel): Boolean {
        // No negro puro (value muy bajo)
        if (pixel.v < 30) return false
        
        // No blanco puro (saturación muy baja + value alto)
        if (pixel.s < 20 && pixel.v > 200) return false
        
        // Debe tener suficiente color (saturación suficiente)
        return pixel.s >= minColorfulness
    }
    
    /**
     * Valida que la subregión detectada tenga tamaño razonable.
     */
    private fun isValidSubRegion(subRegion: SubRegion): Boolean {
        val width = subRegion.width
        val height = subRegion.height
        val area = subRegion.area
        
        // Debe tener tamaño mínimo
        if (width < minSubRoiSize || height < minSubRoiSize) return false
        if (area < minSubRoiSize * minSubRoiSize) return false
        
        // No debe ser excesivamente grande
        if (area > maxSubRoiSize) return false
        
        return true
    }
    
    /**
     * Calcula confianza en la detección basada en cobertura y distribución.
     */
    private fun calculateConfidence(subRegion: SubRegion, frame: ColorFrameData): Float {
        // Mayor área = más confianza (hasta un punto)
        val areaScore = when {
            subRegion.area < 0.3f -> 0.3f
            subRegion.area < 0.5f -> 0.6f
            subRegion.area < 0.8f -> 0.9f
            else -> 0.7f // Demasiado grande, bajar confianza
        }
        
        // Centricidad: preferir regiones centradas
        val centerX = (subRegion.left + subRegion.right) / 2
        val centerY = (subRegion.top + subRegion.bottom) / 2
        val distanceFromCenter = kotlin.math.sqrt(
            (centerX - 0.5f) * (centerX - 0.5f) +
            (centerY - 0.5f) * (centerY - 0.5f)
        )
        val centralityScore = 1f - distanceFromCenter.coerceIn(0f, 1f)
        
        return ((areaScore * 0.6f) + (centralityScore * 0.4f)).coerceIn(0f, 1f)
    }
    
    /**
     * Genera estadísticas del proceso de detección.
     */
    private fun generateStats(
        subRegion: SubRegion,
        frame: ColorFrameData,
        detectionTimeMs: Long
    ): SubRoiDetectionStats {
        val totalPixels = frame.width * frame.height
        
        // Contar píxeles coloridos en la subregión
        val subWidth = ((subRegion.right - subRegion.left) * frame.width).toInt()
        val subHeight = ((subRegion.bottom - subRegion.top) * frame.height).toInt()
        val startX = (subRegion.left * frame.width).toInt()
        val startY = (subRegion.top * frame.height).toInt()
        
        var colorfulPixels = 0
        
        for (y in startY until min(startY + subHeight, frame.height)) {
            for (x in startX until min(startX + subWidth, frame.width)) {
                if (isColorfulPixel(frame.getPixel(x, y))) {
                    colorfulPixels++
                }
            }
        }
        
        return SubRoiDetectionStats(
            totalPixelsAnalyzed = totalPixels,
            candidatePixelsFound = colorfulPixels,
            candidatePercentage = (colorfulPixels * 100f) / (subWidth * subHeight),
            boundingBoxCoverage = subRegion.area,
            avgColorfulness = 128f, // Valor estimado
            detectionTimeMs = detectionTimeMs
        )
    }
    
    /**
     * Crea resultado de fallback cuando la detección falla.
     */
    private fun createFallbackResult(
        method: DetectionMethod,
        startTime: Long
    ): TransferSubRoiResult {
        val detectionTime = System.currentTimeMillis() - startTime
        
        return TransferSubRoiResult(
            subRegion = SubRegion.FULL,
            confidence = 0.3f,
            method = method,
            stats = SubRoiDetectionStats(
                totalPixelsAnalyzed = 0,
                candidatePixelsFound = 0,
                candidatePercentage = 0f,
                boundingBoxCoverage = 1f,
                avgColorfulness = 0f,
                detectionTimeMs = detectionTime
            )
        )
    }
    
    /**
     * Genera un bitmap de debug mostrando la máscara de detección.
     * Útil para inspección visual del proceso.
     */
    fun generateDebugMaskBitmap(frameData: ColorFrameData, roi: Roi): Bitmap? {
        return try {
            val roiFrame = frameData.extractRegion(roi.left, roi.top, roi.right, roi.bottom)
            val width = roiFrame.width
            val height = roiFrame.height
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = roiFrame.getPixel(x, y)
                    val isCandidate = isColorfulPixel(pixel)
                    
                    val color = if (isCandidate) {
                        // Verde para candidatos
                        AndroidColor.argb(255, 0, 255, 0)
                    } else {
                        // Gris semitransparente para no-candidatos
                        AndroidColor.argb(128, 128, 128, 128)
                    }
                    
                    bitmap.setPixel(x, y, color)
                }
            }
            
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}