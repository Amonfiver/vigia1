/**
 * Archivo: app/src/main/java/com/vigia/app/camera/FrameProcessor.kt
 * Propósito: Procesar frames de CameraX y convertirlos a datos para análisis, con capacidad de captura de imagen.
 * Responsabilidad principal: Extraer información cromática (HSV) de frames YUV, entregarlos al detector y proporcionar captura de imagen.
 * Alcance: Capa de cámara, puente entre CameraX y el sistema de detección/captura.
 *
 * Decisiones técnicas relevantes:
 * - ImageAnalysis.Analyzer de CameraX para recibir frames en tiempo real
 * - Conversión YUV a HSV para análisis cromático (detección de naranja/rojo)
 * - Conversión YUV a luminancia (grayscale) mantenida para compatibilidad temporal
 * - Almacenamiento del último frame completo para captura bajo demanda
 * - Downscaling para reducir carga de procesamiento
 * - StateFlow para entregar frames de forma reactiva
 *
 * Limitaciones temporales del MVP:
 * - Conversión YUV→HSV aproximada sin tabla de lookup optimizada
 * - Sin compensación de rotación automática del frame
 * - Resolución fija del análisis (320x240) para rendimiento
 * - La captura usa el último frame procesado, no un frame exclusivo
 *
 * Cambios recientes:
 * - AÑADIDO: Generación de ColorFrameData con información cromática HSV
 * - AÑADIDO: StateFlow para datos de color (colorFrameData)
 * - MANTENIDO: FrameData de luminancia para compatibilidad durante transición
 * - CONVERSIÓN YUV→RGB→HSV implementada para detección de colores
 */
package com.vigia.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.vigia.app.detection.ColorFrameData
import com.vigia.app.detection.FrameData
import com.vigia.app.detection.HsvPixel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Analizador de frames de CameraX que extrae información cromática y luminancia,
 * con capacidad adicional de capturar el último frame como imagen.
 *
 * @param targetWidth Ancho objetivo para el frame procesado (default 320 para rendimiento)
 * @param targetHeight Alto objetivo para el frame procesado (default 240 para rendimiento)
 */
class FrameProcessor(
    private val targetWidth: Int = 320,
    private val targetHeight: Int = 240
) : ImageAnalysis.Analyzer {

    // === FrameData legacy (luminancia/grayscale) - mantenido para compatibilidad ===
    private val _frameData = MutableStateFlow<FrameData?>(null)
    @Deprecated("Usar colorFrameData para análisis cromático")
    val frameData: StateFlow<FrameData?> = _frameData.asStateFlow()

    // === ColorFrameData nuevo (información cromática HSV) ===
    private val _colorFrameData = MutableStateFlow<ColorFrameData?>(null)
    /**
     * Último frame procesado como ColorFrameData con información cromática HSV.
     * Usar este para análisis de color (naranja/rojo).
     */
    val colorFrameData: StateFlow<ColorFrameData?> = _colorFrameData.asStateFlow()

    // Almacenamiento del último frame completo para captura
    private var lastYBuffer: ByteArray? = null
    private var lastUBuffer: ByteArray? = null
    private var lastVBuffer: ByteArray? = null
    private var lastUVBuffer: ByteArray? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    private var frameSkipCounter = 0

    // Cache para conversión YUV→RGB evitando reallocaciones
    private var rgbCache: IntArray? = null

    override fun analyze(image: ImageProxy) {
        // Procesar solo cada N frames para no sobrecargar (ajustable)
        frameSkipCounter++
        if (frameSkipCounter < 5) { // Analizar 1 de cada 5 frames (~6fps a 30fps)
            // Guardar frame para posible captura aunque no lo procesemos para análisis
            storeFrameForCapture(image)
            image.close()
            return
        }
        frameSkipCounter = 0

        try {
            // Guardar frame para captura antes de cualquier procesamiento
            storeFrameForCapture(image)
            
            // Procesar para análisis (ambos formatos durante transición)
            val (frameData, colorFrameData) = processImageDual(image)
            _frameData.value = frameData
            _colorFrameData.value = colorFrameData
        } finally {
            image.close()
        }
    }

    /**
     * Almacena el frame actual para posible captura posterior.
     * Guarda buffers Y, U, V por separado para conversión RGB flexible.
     */
    private fun storeFrameForCapture(image: ImageProxy) {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        // Crear copias de los buffers
        lastYBuffer = ByteArray(yBuffer.remaining()).also { yBuffer.get(it) }
        lastUBuffer = ByteArray(uBuffer.remaining()).also { uBuffer.get(it) }
        lastVBuffer = ByteArray(vBuffer.remaining()).also { vBuffer.get(it) }
        
        // Para NV21 necesitamos UV intercalado (formato para JPEG)
        val uvSize = uBuffer.remaining() + vBuffer.remaining()
        lastUVBuffer = ByteArray(uvSize)
        
        // Copiar U y V intercalados (NV21 format)
        val uArray = ByteArray(uBuffer.remaining()).also { uBuffer.get(it) }
        val vArray = ByteArray(vBuffer.remaining()).also { vBuffer.get(it) }
        
        var uvIndex = 0
        for (i in uArray.indices) {
            lastUVBuffer?.set(uvIndex++, vArray[i])
            lastUVBuffer?.set(uvIndex++, uArray[i])
        }

        lastWidth = image.width
        lastHeight = image.height
    }

    /**
     * Obtiene el último frame capturado como Bitmap en COLOR.
     * NOTA: Este método es thread-safe pero debe llamarse fuera del callback de análisis.
     *
     * @return Bitmap del último frame en color, o null si no hay frame disponible
     */
    fun getLastFrameBitmap(): Bitmap? {
        val yBytes = lastYBuffer ?: return null
        val uvBytes = lastUVBuffer ?: return null
        val width = lastWidth
        val height = lastHeight

        return try {
            // Crear array NV21 (YUV420sp)
            val nv21 = ByteArray(yBytes.size + uvBytes.size)
            System.arraycopy(yBytes, 0, nv21, 0, yBytes.size)
            System.arraycopy(uvBytes, 0, nv21, yBytes.size, uvBytes.size)

            // Convertir a JPEG usando YuvImage
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, outputStream)
            val jpegBytes = outputStream.toByteArray()

            // Decodificar a Bitmap (en color)
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Obtiene el último frame capturado como array de bytes JPEG.
     * Útil para envío directo por red sin procesar Bitmap.
     *
     * @return ByteArray JPEG del último frame (a color), o null si no hay frame disponible
     */
    fun getLastFrameJpegBytes(): ByteArray? {
        val yBytes = lastYBuffer ?: return null
        val uvBytes = lastUVBuffer ?: return null
        val width = lastWidth
        val height = lastHeight

        return try {
            val nv21 = ByteArray(yBytes.size + uvBytes.size)
            System.arraycopy(yBytes, 0, nv21, 0, yBytes.size)
            System.arraycopy(uvBytes, 0, nv21, yBytes.size, uvBytes.size)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera un crop de diagnóstico de una región específica del frame actual.
     * Útil para observabilidad: ver qué región está analizando el sistema.
     *
     * @param left Normalizado 0.0-1.0
     * @param top Normalizado 0.0-1.0
     * @param right Normalizado 0.0-1.0
     * @param bottom Normalizado 0.0-1.0
     * @return Bitmap del crop en color, o null si no hay frame disponible
     */
    fun getRegionCrop(left: Float, top: Float, right: Float, bottom: Float): Bitmap? {
        val fullBitmap = getLastFrameBitmap() ?: return null
        
        return try {
            val width = fullBitmap.width
            val height = fullBitmap.height
            
            // Convertir coordenadas normalizadas a píxeles
            val x = (left * width).toInt().coerceIn(0, width)
            val y = (top * height).toInt().coerceIn(0, height)
            val cropWidth = ((right - left) * width).toInt().coerceIn(1, width - x)
            val cropHeight = ((bottom - top) * height).toInt().coerceIn(1, height - y)
            
            // Crear crop
            Bitmap.createBitmap(fullBitmap, x, y, cropWidth, cropHeight)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera un crop del ROI global.
     */
    fun getRoiCrop(roi: com.vigia.app.domain.model.Roi): Bitmap? {
        return getRegionCrop(roi.left, roi.top, roi.right, roi.bottom)
    }

    /**
     * Procesa un ImageProxy de CameraX extrayendo tanto luminancia como color.
     * Genera ambos formatos para compatibilidad durante la transición.
     * 
     * @return Par de (FrameData legacy, ColorFrameData nuevo)
     */
    private fun processImageDual(image: ImageProxy): Pair<FrameData, ColorFrameData> {
        val width = image.width
        val height = image.height

        // Extraer buffers YUV
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        // Inicializar cache RGB si es necesario
        if (rgbCache == null || rgbCache!!.size < targetWidth * targetHeight) {
            rgbCache = IntArray(targetWidth * targetHeight)
        }

        // Arrays de salida
        val luminanceArray = IntArray(targetWidth * targetHeight)
        val hsvArray = Array(targetWidth * targetHeight) { HsvPixel(0, 0, 0) }

        val scaleX = width.toFloat() / targetWidth
        val scaleY = height.toFloat() / targetHeight

        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val srcX = min((x * scaleX).toInt(), width - 1)
                val srcY = min((y * scaleY).toInt(), height - 1)

                // Obtener índices en buffers YUV
                val yIndex = srcY * yRowStride + srcX
                val uvIndex = (srcY / 2) * uvRowStride + (srcX / 2) * uvPixelStride

                // Luminancia (Y)
                val yVal = (yBuffer.get(yIndex).toInt() and 0xFF)

                // Cromaticidad (U, V) - crominancia submuestreada 4:2:0
                val uVal = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                // Almacenar luminancia
                luminanceArray[y * targetWidth + x] = yVal

                // Convertir YUV a RGB
                val r = (yVal + 1.370705f * vVal).toInt().coerceIn(0, 255)
                val g = (yVal - 0.698001f * vVal - 0.337633f * uVal).toInt().coerceIn(0, 255)
                val b = (yVal + 1.732446f * uVal).toInt().coerceIn(0, 255)

                // Convertir RGB a HSV y almacenar
                hsvArray[y * targetWidth + x] = HsvPixel.fromRgb(r, g, b)
            }
        }

        val timestamp = System.currentTimeMillis()

        val frameData = FrameData(
            timestamp = timestamp,
            width = targetWidth,
            height = targetHeight,
            luminanceArray = luminanceArray
        )

        val colorFrameData = ColorFrameData(
            timestamp = timestamp,
            width = targetWidth,
            height = targetHeight,
            hsvArray = hsvArray
        )

        return Pair(frameData, colorFrameData)
    }
}
