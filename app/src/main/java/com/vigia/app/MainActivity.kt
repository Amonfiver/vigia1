/**
 * Archivo: app/src/main/java/com/vigia/app/MainActivity.kt
 * Propósito: Activity principal de VIGIA1, punto de entrada de la aplicación.
 * Responsabilidad principal: Contener la UI principal, gestionar permisos de cámara, modo de selección de ROI,
 * configuración de Telegram, captura de imagen y mostrar estado de detección.
 * Alcance: Capa de presentación, pantalla principal de la app.
 *
 * Decisiones técnicas relevantes:
 * - Jetpack Compose para UI declarativa y moderna
 * - ComponentActivity como base para Compose
 * - ViewModel para gestión de estado
 * - Solicitud de permisos en tiempo de ejecución para cámara
 * - Preview de cámara real usando CameraX con análisis de frames
 * - Modo de selección de ROI con superposición táctil
 * - Configuración de Telegram funcional con prueba manual
 * - Captura y envío manual de imagen a Telegram
 * - Visualización de estado de detección en tiempo real basado en frames reales
 *
 * Limitaciones temporales del MVP:
 * - FrameData de análisis es procesado a 320x240 para rendimiento
 * - Sin implementación automática de envío a Telegram al detectar cambio
 * - Lógica de detección provisional basada en luminancia simple
 *
 * Cambios recientes:
 * - Añadida captura y envío manual de imagen a Telegram
 * - UI de captura con feedback visual del proceso
 * - Separación clara entre captura de imagen y envío
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigia.app.camera.CameraPreview
import com.vigia.app.camera.FrameProcessor
import com.vigia.app.data.local.DataStoreRoiRepository
import com.vigia.app.data.local.DataStoreTelegramConfigRepository
import com.vigia.app.detection.DetectionResult
import com.vigia.app.detection.FrameData
import com.vigia.app.ui.ImageCaptureState
import com.vigia.app.ui.MainViewModel
import com.vigia.app.ui.ScreenMode
import com.vigia.app.ui.TelegramTestState
import com.vigia.app.ui.components.RoiOverlay
import com.vigia.app.ui.components.RoiSelector
import com.vigia.app.utils.PermissionsHelper
import kotlinx.coroutines.flow.StateFlow

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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título VIGIA
        Text(
            text = "VIGIA",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        // Área de preview de cámara con ROI o selector
        CameraArea(
            hasPermission = hasCameraPermission,
            screenMode = uiState.screenMode,
            currentRoi = uiState.currentRoi,
            isMonitoring = uiState.isMonitoring,
            onRequestPermission = onRequestPermission,
            onRoiSelected = { roi -> viewModel.confirmRoiSelection(roi) },
            onRoiSelectionCancelled = { viewModel.cancelRoiSelection() },
            onCameraReady = { frameFlow, processor ->
                viewModel.connectCamera(frameFlow, processor)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )

        // Controles inferiores según el modo
        when (uiState.screenMode) {
            ScreenMode.NORMAL -> {
                NormalModeControls(
                    isMonitoring = uiState.isMonitoring,
                    hasRoi = uiState.currentRoi != null,
                    detectionResult = uiState.detectionResult,
                    onStartMonitoring = { viewModel.startMonitoring() },
                    onStopMonitoring = { viewModel.stopMonitoring() },
                    onDefineRoi = { viewModel.enterRoiSelectionMode() },
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Sección de captura y envío de imagen
                ImageCaptureSection(
                    captureState = uiState.imageCaptureState,
                    hasTelegramConfig = uiState.telegramConfig?.isValid() == true,
                    onCaptureAndSend = { viewModel.captureAndSendImage() },
                    onClearState = { viewModel.clearImageCaptureState() },
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Sección de configuración de Telegram
                TelegramConfigSection(
                    telegramConfig = uiState.telegramConfig,
                    testState = uiState.telegramTestState,
                    onSaveConfig = { botToken, chatId -> viewModel.saveTelegramConfig(botToken, chatId) },
                    onTestConnection = { viewModel.testTelegramConnection() },
                    onClearTestState = { viewModel.clearTelegramTestState() },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            ScreenMode.ROI_SELECTION -> {
                // En modo selección los controles están dentro del selector
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Texto de estado de monitorización
        if (uiState.screenMode == ScreenMode.NORMAL) {
            Text(
                text = uiState.statusMessage,
                fontSize = 18.sp,
                color = if (uiState.isMonitoring) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

/**
 * Área que muestra la preview de cámara, selector de ROI o overlay de ROI.
 */
@Composable
fun CameraArea(
    hasPermission: Boolean,
    screenMode: ScreenMode,
    currentRoi: com.vigia.app.domain.model.Roi?,
    isMonitoring: Boolean,
    onRequestPermission: () -> Unit,
    onRoiSelected: (com.vigia.app.domain.model.Roi) -> Unit,
    onRoiSelectionCancelled: () -> Unit,
    onCameraReady: (StateFlow<FrameData?>, FrameProcessor) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasPermission) {
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
        return
    }

    // Contenedor de la cámara con posible overlay
    Box(modifier = modifier) {
        // Preview de cámara real con análisis de frames
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onCameraReady = onCameraReady,
            onError = { /* Manejar error en fase posterior */ }
        )

        when (screenMode) {
            ScreenMode.ROI_SELECTION -> {
                // Modo selección: mostrar selector táctil
                RoiSelector(
                    onRoiSelected = onRoiSelected,
                    onCancel = onRoiSelectionCancelled,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ScreenMode.NORMAL -> {
                // Modo normal: mostrar overlay del ROI si existe
                if (currentRoi != null) {
                    RoiOverlay(
                        roi = currentRoi,
                        isActive = isMonitoring,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Sección de captura y envío de imagen a Telegram.
 */
@Composable
fun ImageCaptureSection(
    captureState: ImageCaptureState,
    hasTelegramConfig: Boolean,
    onCaptureAndSend: () -> Unit,
    onClearState: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Limpiar estado cuando se vuelve a mostrar la sección
    LaunchedEffect(Unit) {
        if (captureState !is ImageCaptureState.Idle) {
            onClearState()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (captureState) {
                is ImageCaptureState.Success -> Color(0xFFE8F5E9)
                is ImageCaptureState.Error -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Captura de imagen",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = onCaptureAndSend,
                enabled = hasTelegramConfig && 
                    captureState !is ImageCaptureState.Capturing && 
                    captureState !is ImageCaptureState.Sending,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (captureState) {
                    is ImageCaptureState.Capturing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Text("Capturando...")
                        }
                    }
                    is ImageCaptureState.Sending -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Text("Enviando...")
                        }
                    }
                    else -> Text("📸 Capturar y enviar imagen")
                }
            }

            // Mensaje de estado
            when (captureState) {
                is ImageCaptureState.Success -> {
                    Text(
                        text = "✓ ${captureState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is ImageCaptureState.Error -> {
                    Text(
                        text = "✗ ${captureState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                else -> {
                    if (!hasTelegramConfig) {
                        Text(
                            text = "Configura Telegram primero para enviar imágenes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Controles del modo normal (vigilancia, definición de ROI y estado de detección).
 */
@Composable
fun NormalModeControls(
    isMonitoring: Boolean,
    hasRoi: Boolean,
    detectionResult: DetectionResult?,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onDefineRoi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Indicador de estado de detección (solo cuando vigilancia activa)
        if (isMonitoring) {
            DetectionStatusCard(
                detectionResult = detectionResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        // Botones de vigilancia
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onStartMonitoring,
                enabled = !isMonitoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Iniciar vigilancia")
            }

            Button(
                onClick = onStopMonitoring,
                enabled = isMonitoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Detener vigilancia")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botón para definir/redefinir ROI
        OutlinedButton(
            onClick = onDefineRoi,
            enabled = !isMonitoring,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(if (hasRoi) "Redefinir ROI" else "Definir ROI")
        }

        // Indicador de estado del ROI
        if (hasRoi) {
            Text(
                text = "✓ ROI definido",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Text(
                text = "Sin ROI definido",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Tarjeta que muestra el estado de detección en tiempo real.
 */
@Composable
fun DetectionStatusCard(
    detectionResult: DetectionResult?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when {
                detectionResult == null -> MaterialTheme.colorScheme.surfaceVariant
                detectionResult.hasChange -> Color(0xFFFFEBEE) // Rojo claro
                else -> Color(0xFFE8F5E9) // Verde claro
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                detectionResult == null -> {
                    Text(
                        text = "Análisis: Iniciando...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                detectionResult.hasChange -> {
                    Text(
                        text = "⚠️ Cambio detectado",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828)
                    )
                    Text(
                        text = detectionResult.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828).copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                else -> {
                    Text(
                        text = "✓ Sin cambio relevante",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = detectionResult.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32).copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Sección de configuración de Telegram funcional.
 */
@Composable
fun TelegramConfigSection(
    telegramConfig: com.vigia.app.domain.model.TelegramConfig?,
    testState: TelegramTestState,
    onSaveConfig: (String, String) -> Unit,
    onTestConnection: () -> Unit,
    onClearTestState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var botToken by remember { mutableStateOf(telegramConfig?.botToken ?: "") }
    var chatId by remember { mutableStateOf(telegramConfig?.chatId ?: "") }

    // Actualizar campos cuando cambia la configuración guardada
    LaunchedEffect(telegramConfig) {
        telegramConfig?.let {
            botToken = it.botToken
            chatId = it.chatId
        }
    }

    // Limpiar estado de prueba cuando se modifican los campos
    LaunchedEffect(botToken, chatId) {
        if (testState !is TelegramTestState.Idle) {
            onClearTestState()
        }
    }

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
                modifier = Modifier.padding(bottom = 12.dp)
            )

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

            Spacer(modifier = Modifier.height(8.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSaveConfig(botToken, chatId) },
                    enabled = botToken.isNotBlank() && chatId.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Guardar")
                }

                Button(
                    onClick = onTestConnection,
                    enabled = botToken.isNotBlank() && chatId.isNotBlank() && testState !is TelegramTestState.Loading,
                    modifier = Modifier.weight(1f),
                    colors = if (testState is TelegramTestState.Success) {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    when (testState) {
                        is TelegramTestState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                        is TelegramTestState.Success -> Text("✓ Probar")
                        else -> Text("Probar")
                    }
                }
            }

            // Estado de la prueba
            when (testState) {
                is TelegramTestState.Success -> {
                    Text(
                        text = "✓ ${testState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is TelegramTestState.Error -> {
                    Text(
                        text = "✗ ${testState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                else -> {}
            }

            // Indicador de configuración guardada
            if (telegramConfig?.isValid() == true && testState is TelegramTestState.Idle) {
                Text(
                    text = "✓ Configuración guardada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}