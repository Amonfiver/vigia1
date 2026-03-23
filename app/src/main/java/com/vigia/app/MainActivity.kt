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
 *
 * Limitaciones temporales del MVP:
 * - Sin preview de cámara real todavía (placeholder visual)
 * - Sin implementación real de monitorización
 * - Placeholders para configuración de Telegram
 *
 * Cambios recientes: Creación inicial de la Activity con UI básica.
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vigia.app.data.local.DataStoreRoiRepository
import com.vigia.app.data.local.DataStoreTelegramConfigRepository
import com.vigia.app.ui.MainViewModel

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
                    VigiaApp(viewModel = viewModel)
                }
            }
        }

        // Solicitar permiso de cámara si no está concedido
        checkCameraPermission()
    }

    /**
     * Verifica y solicita permiso de cámara si es necesario.
     */
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permiso ya concedido, nada que hacer
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // Permiso denegado - en fase posterior se mostrará mensaje al usuario
        }
    }
}

/**
 * Composable raíz de la aplicación VIGIA.
 *
 * @param viewModel ViewModel para gestión de estado
 */
@Composable
fun VigiaApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

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
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
        )

        // Placeholder de preview de cámara
        CameraPreviewPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Texto de estado
        Text(
            text = uiState.statusMessage,
            fontSize = 18.sp,
            color = if (uiState.isMonitoring) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Botones de control
        ControlButtons(
            isMonitoring = uiState.isMonitoring,
            onStartClick = { viewModel.startMonitoring() },
            onStopClick = { viewModel.stopMonitoring() },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Sección de configuración de Telegram (placeholder)
        TelegramConfigSection(
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

/**
 * Placeholder visual para el área de preview de cámara.
 *
 * @param modifier Modificador para layout
 */
@Composable
fun CameraPreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Preview de cámara\n(disponible en fase posterior)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
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