/**
 * Archivo: app/src/main/java/com/vigia/app/camera/CameraPreview.kt
 * Propósito: Composable que muestra el preview de la cámara y configura el análisis de frames.
 * Responsabilidad principal: Renderizar la vista previa y proporcionar frames para análisis.
 * Alcance: Capa de cámara, integración de CameraX con Jetpack Compose.
 *
 * Decisiones técnicas relevantes:
 * - Uso de PreviewView de CameraX para renderizado eficiente
 * - ImageAnalysis use case para procesamiento de frames en tiempo real
 * - AndroidView de Compose para integrar vista tradicional Android
 * - Lifecycle-aware para vincular cámara al ciclo de vida del Composable
 * - FrameProcessor como analyzer para extraer luminancia
 *
 * Limitaciones temporales del MVP:
 * - Cámara trasera fija, sin selector de cámara
 * - Resolución de análisis fija (320x240 para rendimiento)
 * - Sin manejo avanzado de rotación de frames
 *
 * Cambios recientes:
 * - Añadido ImageAnalysis use case para procesamiento de frames
 * - Integración con FrameProcessor para extraer luminancia
 * - Callback onFrameData para entregar frames al sistema de detección
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
import com.vigia.app.detection.FrameData
import kotlinx.coroutines.flow.StateFlow

/**
 * Composable que muestra el preview de la cámara y proporciona frames para análisis.
 *
 * @param modifier Modificador para el layout
 * @param onFrameData Callback opcional para recibir FrameData del procesador (no usado directamente, usar frameData Flow)
 * @param onError Callback para errores de inicialización de cámara
 * @return StateFlow<FrameData?> que emite los frames procesados (null si no hay frame)
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameData: ((FrameData) -> Unit)? = null,
    onError: (Throwable) -> Unit = {}
): StateFlow<FrameData?> {
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

    // Efecto de limpieza al desmontar
    DisposableEffect(Unit) {
        onDispose {
            // La cámara se desvincula automáticamente por el lifecycle
        }
    }

    return frameProcessor.frameData
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