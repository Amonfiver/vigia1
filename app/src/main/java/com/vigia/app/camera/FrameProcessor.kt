/**
 * Archivo: app/src/main/java/com/vigia/app/camera/FrameProcessor.kt
 * Propósito: Procesar frames de CameraX y convertirlos a FrameData para análisis, con capacidad de captura de imagen.
 * Responsabilidad principal: Extraer luminancia de frames YUV, entregarlos al detector y proporcionar captura de imagen.
 * Alcance: Capa de cámara, puente entre CameraX y el sistema de detección/captura.
 *
 * Decisiones técnicas relevantes:
 * - ImageAnalysis.Analyzer de CameraX para recibir frames en tiempo real
 * - Conversión YUV a luminancia (grayscale) sin librerías externas
 * - Almacenamiento del último frame completo para captura bajo demanda
 * - Downscaling opcional para reducir carga de procesamiento
 * - StateFlow para entregar frames de forma reactiva
 *
 * Limitaciones temporales del MVP:
 * - Conversión YUV simple, sin optimizaciones SIMD o GPU
 * - Sin compensación de rotación automática del frame
 * - Resolución fija del análisis (puede diferir de preview)
 * - La captura usa el último frame procesado, no un frame exclusivo
 *
 * Cambios recientes:
 * - Añadida capacidad de captura de imagen (getLastFrameBitmap)
 * - Almacenamiento del último frame YUV completo
 */
package com.vigia.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.vigia.app.detection.FrameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Analizador de frames de CameraX que extrae luminancia y la entrega como FrameData,
 * con capacidad adicional de capturar el último frame como imagen.
 *
 * @param targetWidth Ancho objetivo para el frame procesado (default 320 para rendimiento)
 * @param targetHeight Alto objetivo para el frame procesado (default 240 para rendimiento)
 */
class FrameProcessor(
    private val targetWidth: Int = 320,
    private val targetHeight: Int = 240
) : ImageAnalysis.Analyzer {

    private val _frameData = MutableStateFlow<FrameData?>(null)
    /**
     * Último frame procesado como FrameData para análisis.
     */
    val frameData: StateFlow<FrameData?> = _frameData.asStateFlow()

    // Almacenamiento del último frame completo para captura
    private var lastYBuffer: ByteArray? = null
    private var lastUVBuffer: ByteArray? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    private var frameSkipCounter = 0

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
            
            val frameData = processImage(image)
            _frameData.value = frameData
        } finally {
            image.close()
        }
    }

    /**
     * Almacena el frame actual para posible captura posterior.
     */
    private fun storeFrameForCapture(image: ImageProxy) {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        // Crear copias de los buffers
        lastYBuffer = ByteArray(yBuffer.remaining()).also { yBuffer.get(it) }
        
        // Para NV21 necesitamos UV intercalado
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
     * Obtiene el último frame capturado como Bitmap.
     * NOTA: Este método es thread-safe pero debe llamarse fuera del callback de análisis.
     *
     * @return Bitmap del último frame, o null si no hay frame disponible
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

            // Decodificar a Bitmap
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
     * @return ByteArray JPEG del último frame, o null si no hay frame disponible
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
     * Procesa un ImageProxy de CameraX extrayendo la luminancia.
     */
    private fun processImage(image: ImageProxy): FrameData {
        val width = image.width
        val height = image.height

        // Extraer buffer Y (luminancia) del formato YUV
        val yBuffer = image.planes[0].buffer
        val yRowStride = image.planes[0].rowStride
        val yPixelStride = image.planes[0].pixelStride

        // Crear array de luminancia con downscaling al target
        val luminanceArray = IntArray(targetWidth * targetHeight)

        val scaleX = width.toFloat() / targetWidth
        val scaleY = height.toFloat() / targetHeight

        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val srcX = min((x * scaleX).toInt(), width - 1)
                val srcY = min((y * scaleY).toInt(), height - 1)

                val yIndex = srcY * yRowStride + srcX * yPixelStride
                val luminance = yBuffer.get(yIndex).toInt() and 0xFF

                luminanceArray[y * targetWidth + x] = luminance
            }
        }

        return FrameData(
            timestamp = System.currentTimeMillis(),
            width = targetWidth,
            height = targetHeight,
            luminanceArray = luminanceArray
        )
    }
}