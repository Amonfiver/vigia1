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
 * - CORRECCIÓN COLOR: Conversión YUV→RGB respeta rowStride/pixelStride correctamente
 *
 * Limitaciones temporales del MVP:
 * - Conversión YUV→HSV aproximada sin tabla de lookup optimizada
 * - Sin compensación de rotación automática del frame
 * - Resolución fija del análisis (320x240) para rendimiento
 * - La captura usa el último frame procesado, no un frame exclusivo del momento exacto
 *
 * Cambios recientes:
 * - CORREGIDO: Pipeline de captura en color - ahora respeta rowStride/pixelStride de planos YUV
 * - ELIMINADOS: Artefactos verde/magenta por conversión incorrecta de YUV
 * - DOCUMENTADO: Causa raíz era desalineación de buffers U/V al ignorar rowStride
 * - AÑADIDO: Métodos de evidencia separada para debug visual (referencia, evento, confirmación)
 * - MANTENIDO: Compatibilidad con análisis HSV existente
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

    // Almacenamiento del último frame completo para captura CORREGIDO
    // Ahora almacenamos el ImageProxy completo de forma segura para conversión correcta
    private var lastImageProxy: ImageProxy? = null
    private val imageProxyLock = Any()

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
     * CORREGIDO: Ahora almacena referencia segura al ImageProxy para conversión correcta.
     * 
     * CAUSA RAÍZ DEL BUG PREVIO:
     * - Antes: Se copiaban buffers Y,U,V sin respetar rowStride, causando desalineación
     * - Resultado: Artefactos verde/magenta por mezcla incorrecta de crominancia
     * - Solución: Usar conversión directa de ImageProxy respetando stride de cada plano
     */
    private fun storeFrameForCapture(image: ImageProxy) {
        synchronized(imageProxyLock) {
            // Cerrar el anterior si existe
            lastImageProxy?.close()
            // Almacenar referencia al ImageProxy actual - la conversión se hará bajo demanda
            // Nota: El ImageProxy se cierra en analyze(), así que necesitamos copiar los datos
            // Vamos a copiar los datos crudos respetando los strides
            lastImageProxy = null // Resetear, usaremos los buffers copiados en su lugar
            
            // Copiar datos respetando stride para conversión correcta
            copyImageProxyData(image)
        }
    }

    // Buffers para almacenar datos del último frame (corregidos)
    private var lastYBuffer: ByteArray? = null
    private var lastUBuffer: ByteArray? = null
    private var lastVBuffer: ByteArray? = null
    private var lastYRowStride: Int = 0
    private var lastUVRowStride: Int = 0
    private var lastUVPixelStride: Int = 0
    private var lastImageWidth: Int = 0
    private var lastImageHeight: Int = 0

    /**
     * Copia los datos del ImageProxy respetando strides para conversión correcta.
     * Esta es la clave para eliminar los artefactos de color.
     */
    private fun copyImageProxyData(image: ImageProxy) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        lastYRowStride = yPlane.rowStride
        lastUVRowStride = uPlane.rowStride
        lastUVPixelStride = uPlane.pixelStride
        lastImageWidth = image.width
        lastImageHeight = image.height

        // Copiar buffer Y (incluyendo posible padding al final de cada fila)
        val yBuffer = yPlane.buffer
        lastYBuffer = ByteArray(yBuffer.remaining()).also { yBuffer.get(it) }

        // Copiar buffers U y V (con su stride)
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        lastUBuffer = ByteArray(uBuffer.remaining()).also { uBuffer.get(it) }
        lastVBuffer = ByteArray(vBuffer.remaining()).also { vBuffer.get(it) }
    }

    /**
     * Obtiene el último frame capturado como Bitmap en COLOR.
     * CORREGIDO: Ahora construye NV21 correctamente respetando strides.
     * 
     * @return Bitmap del último frame en color, o null si no hay frame disponible
     */
    fun getLastFrameBitmap(): Bitmap? {
        val yBytes = lastYBuffer ?: return null
        val uBytes = lastUBuffer ?: return null
        val vBytes = lastVBuffer ?: return null
        val width = lastImageWidth
        val height = lastImageHeight
        val yRowStride = lastYRowStride
        val uvRowStride = lastUVRowStride
        val uvPixelStride = lastUVPixelStride

        return try {
            // Crear array NV21 correctamente dimensionado
            val nv21 = ByteArray(width * height + (width * height) / 2)
            
            // Copiar plano Y (puede tener rowStride > width)
            for (row in 0 until height) {
                val srcPos = row * yRowStride
                val dstPos = row * width
                System.arraycopy(yBytes, srcPos, nv21, dstPos, width)
            }
            
            // Copiar planos U y V intercalados (formato NV21: VU VU VU...)
            // En YUV_420_888: U y V están separados, los intercalamos en NV21
            val uvHeight = height / 2
            val uvWidth = width / 2
            
            var nv21Index = width * height // Inicio de la sección UV en NV21
            
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uvIndex = row * uvRowStride + col * uvPixelStride
                    
                    // NV21 espera: V primero, luego U
                    if (uvIndex < vBytes.size) {
                        nv21[nv21Index++] = vBytes[uvIndex]
                    }
                    if (uvIndex < uBytes.size) {
                        nv21[nv21Index++] = uBytes[uvIndex]
                    }
                }
            }

            // Convertir a JPEG usando YuvImage
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, outputStream)
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
     * CORREGIDO: Usa conversión NV21 respetando strides.
     *
     * @return ByteArray JPEG del último frame (a color), o null si no hay frame disponible
     */
    fun getLastFrameJpegBytes(): ByteArray? {
        val yBytes = lastYBuffer ?: return null
        val uBytes = lastUBuffer ?: return null
        val vBytes = lastVBuffer ?: return null
        val width = lastImageWidth
        val height = lastImageHeight
        val yRowStride = lastYRowStride
        val uvRowStride = lastUVRowStride
        val uvPixelStride = lastUVPixelStride

        return try {
            // Crear array NV21 correctamente
            val nv21 = ByteArray(width * height + (width * height) / 2)
            
            // Copiar plano Y
            for (row in 0 until height) {
                val srcPos = row * yRowStride
                val dstPos = row * width
                System.arraycopy(yBytes, srcPos, nv21, dstPos, width)
            }
            
            // Copiar planos U y V intercalados (NV21: VU VU...)
            val uvHeight = height / 2
            val uvWidth = width / 2
            
            var nv21Index = width * height
            
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uvIndex = row * uvRowStride + col * uvPixelStride
                    
                    if (uvIndex < vBytes.size) {
                        nv21[nv21Index++] = vBytes[uvIndex]
                    }
                    if (uvIndex < uBytes.size) {
                        nv21[nv21Index++] = uBytes[uvIndex]
                    }
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera un crop de diagnóstico de una región específica del frame actual.
     * Útil para observabilidad: ver qué región está analizando el sistema.
     * CORREGIDO: Usa Bitmap correcto en color.
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
     * Genera un crop de la subregión activa dentro del ROI.
     * Útil para ver exactamente qué área se está analizando.
     *
     * @param roi ROI global
     * @param subLeft Offset izquierdo relativo al ROI (0.0-1.0)
     * @param subTop Offset superior relativo al ROI (0.0-1.0)
     * @param subRight Offset derecho relativo al ROI (0.0-1.0)
     * @param subBottom Offset inferior relativo al ROI (0.0-1.0)
     */
    fun getSubRegionCrop(
        roi: com.vigia.app.domain.model.Roi,
        subLeft: Float, subTop: Float, subRight: Float, subBottom: Float
    ): Bitmap? {
        // Calcular coordenadas absolutas
        val absLeft = roi.left + subLeft * (roi.right - roi.left)
        val absTop = roi.top + subTop * (roi.bottom - roi.top)
        val absRight = roi.left + subRight * (roi.right - roi.left)
        val absBottom = roi.top + subBottom * (roi.bottom - roi.top)
        return getRegionCrop(absLeft, absTop, absRight, absBottom)
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

                // Obtener índices en buffers YUV respetando rowStride
                val yIndex = srcY * yRowStride + srcX
                val uvRow = srcY / 2
                val uvCol = srcX / 2
                val uvIndex = uvRow * uvRowStride + uvCol * uvPixelStride

                // Luminancia (Y)
                val yVal = (yBuffer.get(yIndex).toInt() and 0xFF)

                // Cromaticidad (U, V) - crominancia submuestreada 4:2:0
                val uVal = if (uvIndex < uBuffer.remaining()) {
                    (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                } else 0
                
                val vVal = if (uvIndex < vBuffer.remaining()) {
                    (vBuffer.get(uvIndex).toInt() and 0xFF) - 128
                } else 0

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