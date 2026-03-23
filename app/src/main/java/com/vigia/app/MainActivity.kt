/**
 * Archivo: app/src/main/java/com/vigia/app/MainActivity.kt
 * Propósito: Activity principal de VIGIA1, punto de entrada de la aplicación.
 * Responsabilidad principal: Contener la UI principal y gestionar permisos de cámara.
 * Alcance: Capa de presentación, pantalla principal de la app.
 *
 * Decisiones técnicas relevantes:
 * - Jetpack Compose para UI declarativa y moderna
 * - ComponentActivity como base para Compose
 * - ViewModel para gestión de estado
 * - Solicitud de permisos en tiempo de ejecución para cámara
 * - Preview de cámara real usando CameraX
 *
 * Limitaciones temporales del MVP:
 * - Sin implementación real de monitorización (solo estado visual)
 * - Placeholders para configuración de Telegram (campos visuales sin guardado)
 *
 * Cambios recientes:
 * - Integración de CameraPreview real con CameraX
 * - Mejorada gestión de permisos con estado visual
 * - Añadida pantalla de permiso no concedido
 */
package com.vigia.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigia.app.camera.CameraPreview
import com.vigia.app.data.local.DataStoreRoiRepository
import com.vigia.app.data.local.DataStoreTelegramConfigRepository
import com.vigia.app.ui.MainViewModel
import com.vigia.app.utils.PermissionsHelper

/**
 * Activity principal de VIGIA.
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar ViewModel con repositorios
        viewModel = MainViewModel(
            roiRepository = DataStoreRoiRepository(this),
            telegramConfigRepository = DataStoreTelegramConfigRepository(this)
        )

        // Configurar Compose
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VigiaApp(
                        viewModel = viewModel,
                        onRequestPermission = { requestCameraPermission() }
                    )
                }
            }
        }

        // Solicitar permiso de cámara al inicio si no está concedido
        if (!PermissionsHelper.hasCameraPermission(this)) {
            requestCameraPermission()
        }
    }

    /**
     * Solicita el permiso de cámara al usuario.
     */
    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // El resultado se maneja en la UI mediante re-composición
        // ya que PermissionsHelper.hasCameraPermission() se evalúa en cada recomposición
    }
}

/**
 * Composable raíz de la aplicación VIGIA.
 *
 * @param viewModel ViewModel para gestión de estado
 * @param onRequestPermission Callback para solicitar permiso de cámara
 */
@Composable
fun VigiaApp(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasCameraPermission = PermissionsHelper.hasCameraPermission(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título VIGIA
        Text(
            text = "VIGIA",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        // Área de preview de cámara o mensaje de permiso
        CameraPreviewArea(
            hasPermission = hasCameraPermission,
            onRequestPermission = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Texto de estado de monitorización
        Text(
            text = uiState.statusMessage,
            fontSize = 18.sp,
            color = if (uiState.isMonitoring) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Botones de control
        ControlButtons(
            isMonitoring = uiState.isMonitoring,
            onStartClick = { viewModel.startMonitoring() },
            onStopClick = { viewModel.stopMonitoring() },
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Sección de configuración de Telegram (placeholder)
        TelegramConfigSection(
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/**
 * Área que muestra la preview de cámara o un mensaje si no hay permiso.
 *
 * @param hasPermission Indica si el permiso de cámara está concedido
 * @param onRequestPermission Callback para solicitar permiso
 * @param modifier Modificador para el layout
 */
@Composable
fun CameraPreviewArea(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasPermission) {
        // Mostrar preview de cámara real
        CameraPreview(
            modifier = modifier,
            onError = { error ->
                // En fase posterior se manejará el error de forma más específica
            }
        )
    } else {
        // Mostrar mensaje solicitando permiso
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Se necesita permiso de cámara",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRequestPermission) {
                    Text("Conceder permiso")
                }
            }
        }
    }
}

/**
 * Botones de control de vigilancia.
 *
 * @param isMonitoring Indica si la vigilancia está activa
 * @param onStartClick Callback al pulsar iniciar
 * @param onStopClick Callback al pulsar detener
 * @param modifier Modificador para layout
 */
@Composable
fun ControlButtons(
    isMonitoring: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onStartClick,
            enabled = !isMonitoring,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Iniciar vigilancia")
        }

        Button(
            onClick = onStopClick,
            enabled = isMonitoring,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Detener vigilancia")
        }
    }
}

/**
 * Sección de configuración de Telegram (placeholder visual).
 *
 * @param modifier Modificador para layout
 */
@Composable
fun TelegramConfigSection(modifier: Modifier = Modifier) {
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Configuración Telegram",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Placeholder: campos de entrada para token y chat ID
            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                label = { Text("Bot Token") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = chatId,
                onValueChange = { chatId = it },
                label = { Text("Chat ID") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                singleLine = true
            )

            // Nota: La funcionalidad de guardar se implementará en fase posterior
            Text(
                text = "(Funcionalidad de guardado en desarrollo)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}