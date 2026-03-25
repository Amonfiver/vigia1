/**
 * Archivo: app/src/main/java/com/vigia/app/camera/CameraPreview.kt
 * Propósito: Composable que muestra el preview de la cámara y configura el análisis de frames.
 * Responsabilidad principal: Renderizar la vista previa y proporcionar frames para análisis y captura.
 * Alcance: Capa de cámara, integración de CameraX con Jetpack Compose.
 *
 * Decisiones técnicas relevantes:
 * - Uso de PreviewView de CameraX para renderizado eficiente
 * - ImageAnalysis use case para procesamiento de frames en tiempo real
 * - AndroidView de Compose para integrar vista tradicional Android
 * - Lifecycle-aware para vincular cámara al ciclo de vida del Composable
 * - FrameProcessor como analyzer para extraer información cromática (HSV) y capturar imágenes
 *
 * Limitaciones temporales del MVP:
 * - Cámara trasera fija, sin selector de cámara
 * - Resolución de análisis fija (320x240 para rendimiento)
 * - Sin manejo avanzado de rotación de frames
 *
 * Cambios recientes:
 * - AÑADIDO: Exposición de ColorFrameData con información cromática HSV
 * - AÑADIDO: Callback onCameraReadyColor para nuevo análisis cromático
 * - MANTENIDO: Callback onCameraReady legacy para compatibilidad durante transición
 * - FrameProcessor ahora genera ambos tipos de datos (HSV + luminancia)
 */
package com.vigia.app.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vigia.app.detection.ColorFrameData
import com.vigia.app.detection.FrameData
import kotlinx.coroutines.flow.StateFlow

/**
 * Callback cuando la cámara está lista con datos cromáticos.
 * Versión actualizada para análisis por color.
 */
typealias OnCameraReadyColor = (
    colorFrameData: StateFlow<ColorFrameData?>,
    legacyFrameData: StateFlow<FrameData?>,
    processor: FrameProcessor
) -> Unit

/**
 * Composable que muestra el preview de la cámara y proporciona frames para análisis.
 *
 * @param modifier Modificador para el layout
 * @param onCameraReadyColor Callback cuando la cámara está lista (versión cromática)
 * @param onCameraReady Callback legacy para compatibilidad (deprecated)
 * @param onError Callback para errores de inicialización de cámara
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onCameraReadyColor: OnCameraReadyColor? = null,
    onCameraReady: ((StateFlow<FrameData?>, FrameProcessor) -> Unit)? = null,
    onError: (Throwable) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Crear y recordar el procesador de frames
    val frameProcessor = remember { FrameProcessor(targetWidth = 320, targetHeight = 240) }
    
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val previewView = PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            // Inicializar cámara con preview y análisis
            startCamera(context, lifecycleOwner, previewView, frameProcessor, onError)

            previewView
        }
    )

    // Notificar que la cámara está lista con ambos flujos de datos
    DisposableEffect(frameProcessor) {
        if (onCameraReadyColor != null) {
            // Nueva versión con soporte cromático
            onCameraReadyColor(
                frameProcessor.colorFrameData,
                frameProcessor.frameData,
                frameProcessor
            )
        } else if (onCameraReady != null) {
            // Versión legacy
            onCameraReady(frameProcessor.frameData, frameProcessor)
        }
        onDispose {
            // La cámara se desvincula automáticamente por el lifecycle
        }
    }
}

/**
 * Inicializa la cámara con preview y análisis de frames.
 *
 * @param context Contexto de aplicación
 * @param lifecycleOwner Lifecycle owner para vincular la cámara
 * @param previewView Vista de preview donde se renderizará
 * @param frameProcessor Procesador de frames para análisis
 * @param onError Callback para errores
 */
private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    frameProcessor: FrameProcessor,
    onError: (Throwable) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()

            // Configurar preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Configurar análisis de frames
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(
                        ContextCompat.getMainExecutor(context),
                        frameProcessor
                    )
                }

            // Selector de cámara trasera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Desvincular use cases anteriores
            cameraProvider.unbindAll()

            // Vincular cámara al lifecycle con ambos use cases
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar cámara", e)
            onError(e)
        }
    }, ContextCompat.getMainExecutor(context))
}

private const val TAG = "CameraPreview"