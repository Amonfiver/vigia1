/**
 * Archivo: app/src/main/java/com/vigia/app/camera/FrameProcessor.kt
 * Propósito: Procesar frames de CameraX y convertirlos a FrameData para análisis.
 * Responsabilidad principal: Extraer luminancia de frames YUV y entregarlos al detector.
 * Alcance: Capa de cámara, puente entre CameraX y el sistema de detección.
 *
 * Decisiones técnicas relevantes:
 * - ImageAnalysis.Analyzer de CameraX para recibir frames en tiempo real
 * - Conversión YUV a luminancia (grayscale) sin librerías externas
 * - Downscaling opcional para reducir carga de procesamiento
 * - StateFlow para entregar frames de forma reactiva
 *
 * Limitaciones temporales del MVP:
 * - Conversión YUV simple, sin optimizaciones SIMD o GPU
 * - Sin compensación de rotación automática del frame
 * - Resolución fija del análisis (puede diferir de preview)
 *
 * Cambios recientes: Creación inicial del procesador de frames.
 */
package com.vigia.app.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.vigia.app.detection.FrameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/**
 * Analizador de frames de CameraX que extrae luminancia y la entrega como FrameData.
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

    private var frameSkipCounter = 0

    override fun analyze(image: ImageProxy) {
        // Procesar solo cada N frames para no sobrecargar (ajustable)
        frameSkipCounter++
        if (frameSkipCounter < 5) { // Analizar 1 de cada 5 frames (~6fps a 30fps)
            image.close()
            return
        }
        frameSkipCounter = 0

        try {
            val frameData = processImage(image)
            _frameData.value = frameData
        } finally {
            image.close()
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