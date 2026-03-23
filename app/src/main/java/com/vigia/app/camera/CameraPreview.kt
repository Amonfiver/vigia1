/**
 * Archivo: app/src/main/java/com/vigia/app/camera/CameraPreview.kt
 * Propósito: Composable que muestra el preview de la cámara usando CameraX.
 * Responsabilidad principal: Renderizar la vista previa de la cámara en la UI.
 * Alcance: Capa de cámara, integración de CameraX con Jetpack Compose.
 *
 * Decisiones técnicas relevantes:
 * - Uso de PreviewView de CameraX para renderizado eficiente
 * - AndroidView de Compose para integrar vista tradicional Android
 * - Lifecycle-aware para vincular cámara al ciclo de vida del Composable
 *
 * Limitaciones temporales del MVP:
 * - Cámara trasera fija, sin selector de cámara
 * - Sin análisis de frames todavía (solo preview visual)
 *
 * Cambios recientes: Creación inicial del componente de preview.
 */
package com.vigia.app.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

/**
 * Composable que muestra el preview de la cámara.
 *
 * @param modifier Modificador para el layout
 * @param onError Callback para errores de inicialización de cámara
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val previewView = PreviewView(context).apply {
                // Optimización de rendimiento para preview
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            // Inicializar cámara de forma asíncrona
            startCamera(context, lifecycleOwner, previewView, onError)

            previewView
        }
    )
}

/**
 * Inicializa la cámara y vincula el preview al lifecycle.
 *
 * @param context Contexto de aplicación
 * @param lifecycleOwner Lifecycle owner para vincular la cámara
 * @param previewView Vista de preview donde se renderizará
 * @param onError Callback para errores
 */
private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
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

            // Selector de cámara trasera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Desvincular use cases anteriores
            cameraProvider.unbindAll()

            // Vincular cámara al lifecycle
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar cámara", e)
            onError(e)
        }
    }, ContextCompat.getMainExecutor(context))
}

private const val TAG = "CameraPreview"