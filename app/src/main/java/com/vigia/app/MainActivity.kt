/**
 * Archivo: app/src/main/java/com/vigia/app/MainActivity.kt
 * Propósito: Activity principal de VIGIA1, punto de entrada de la aplicación.
 * Responsabilidad principal: Contener la UI principal, gestionar permisos de cámara, modo de selección de ROI,
 * configuración de Telegram, captura de imagen, mostrar estado de detección, alertas automáticas,
 * y modo de entrenamiento supervisado manual.
 * Alcance: Capa de presentación, pantalla principal de la app.
 *
 * Decisiones técnicas relevantes:
 * - Jetpack Compose para UI declarativa y moderna
 * - ComponentActivity como base para Compose
 * - ViewModel para gestión de estado
 * - Solicitud de permisos en tiempo de ejecución para cámara
 * - Preview de cámara real usando CameraX con análisis de frames
 * - Modo de selección de ROI con superposición táctil
 * - Modo de entrenamiento supervisado con tres clases (OK, OBSTACULO, FALLO)
 * - Configuración de Telegram funcional con prueba manual
 * - Captura y envío manual de imagen a Telegram
 * - Visualización de estado de detección en tiempo real
 * - Visualización de estado de alertas automáticas
 *
 * Limitaciones temporales del MVP:
 * - FrameData de análisis es procesado a 320x240 para rendimiento
 * - Lógica de detección provisional basada en luminancia simple
 * - Cooldown de alertas fijo a 60 segundos
 * - Confirmación se pierde si la app se cierra antes de los 3 minutos
 * - Dataset de entrenamiento solo almacena, sin clasificación automática todavía
 *
 * Cambios recientes:
 * - AÑADIDO: Modo de entrenamiento supervisado manual con UI completa
 * - AÑADIDO: Sección de entrenamiento con selección de clase y contadores
 * - AÑADIDO: Botón para acceder al modo de entrenamiento desde vista normal
 * - MANTENIDO: Todas las funcionalidades previas (vigilancia, Telegram, ROI)
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
import com.vigia.app.alert.AlertState
import com.vigia.app.alert.ConfirmationState
import com.vigia.app.camera.CameraPreview
import com.vigia.app.camera.FrameProcessor
import com.vigia.app.data.local.DataStoreRoiRepository
import com.vigia.app.data.local.DataStoreTelegramConfigRepository
import com.vigia.app.data.local.FileTrainingDatasetRepository
import com.vigia.app.detection.DetectionResult
import com.vigia.app.detection.FrameData
import com.vigia.app.domain.model.ClassLabel
import com.vigia.app.domain.model.TrainingCaptureState
import com.vigia.app.domain.model.TrainingSample
import com.vigia.app.ui.ImageCaptureState
import com.vigia.app.ui.MainUiState
import com.vigia.app.ui.MainViewModel
import com.vigia.app.ui.ScreenMode
import com.vigia.app.ui.TelegramTestState
import com.vigia.app.domain.model.TrainingDatasetState
import com.vigia.app.ui.components.RoiOverlay
import com.vigia.app.ui.components.RoiSelector
import com.vigia.app.utils.PermissionsHelper
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            telegramConfigRepository = DataStoreTelegramConfigRepository(this),
            trainingDatasetRepository = FileTrainingDatasetRepository(this)
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
            onCameraReady = { colorFrameFlow, legacyFrameFlow, processor ->
                viewModel.connectCamera(colorFrameFlow, legacyFrameFlow, processor)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )

        // Controles según el modo
        when (uiState.screenMode) {
            ScreenMode.NORMAL -> {
                NormalModeContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            ScreenMode.ROI_SELECTION -> {
                Spacer(modifier = Modifier.height(12.dp))
            }
            ScreenMode.TRAINING -> {
                TrainingModeContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * Contenido del modo normal (vigilancia, Telegram, etc.).
 */
@Composable
fun NormalModeContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Controles de vigilancia y ROI
        NormalModeControls(
            isMonitoring = uiState.isMonitoring,
            hasRoi = uiState.currentRoi != null,
            detectionResult = uiState.detectionResult,
            onStartMonitoring = { viewModel.startMonitoring() },
            onStopMonitoring = { viewModel.stopMonitoring() },
            onDefineRoi = { viewModel.enterRoiSelectionMode() },
            onEnterTraining = { viewModel.enterTrainingMode() }
        )

        // Sección de clasificación automática (visible durante vigilancia)
        if (uiState.isMonitoring) {
            ClassificationSection(
                classificationResult = uiState.classificationResult,
                datasetSyncStatus = uiState.datasetSyncStatus,
                datasetStats = uiState.datasetStats,
                cacheStats = uiState.cacheStats,
                onResync = { viewModel.forceResyncDataset() },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Sección de alertas automáticas
        if (uiState.isMonitoring) {
            AutoAlertSection(
                alertState = uiState.alertState,
                onClearState = { viewModel.clearAlertState() },
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            ConfirmationSection(
                confirmationState = uiState.confirmationState,
                onClearState = { viewModel.clearConfirmationState() },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Captura manual
        ImageCaptureSection(
            captureState = uiState.imageCaptureState,
            hasTelegramConfig = uiState.telegramConfig?.isValid() == true,
            onCaptureAndSend = { viewModel.captureAndSendImage() },
            onClearState = { viewModel.clearImageCaptureState() },
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Configuración Telegram
        TelegramConfigSection(
            telegramConfig = uiState.telegramConfig,
            testState = uiState.telegramTestState,
            onSaveConfig = { botToken, chatId -> viewModel.saveTelegramConfig(botToken, chatId) },
            onTestConnection = { viewModel.testTelegramConnection() },
            onClearTestState = { viewModel.clearTelegramTestState() },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Estado
        Text(
            text = uiState.statusMessage,
            fontSize = 18.sp,
            color = if (uiState.isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

/**
 * Contenido del modo de entrenamiento supervisado.
 */
@Composable
fun TrainingModeContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título del modo
        Text(
            text = "🎓 Modo Entrenamiento",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Selector de clase
        ClassSelector(
            selectedClass = uiState.selectedTrainingClass,
            onClassSelected = { viewModel.selectTrainingClass(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Contadores por clase
        ClassCounters(
            datasetState = uiState.trainingDatasetState
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Botón de captura
        TrainingCaptureButton(
            captureState = uiState.trainingCaptureState,
            selectedClass = uiState.selectedTrainingClass,
            sampleCount = uiState.trainingDatasetState.countForLabel(uiState.selectedTrainingClass),
            onCapture = { viewModel.captureTrainingSample() },
            onClearState = { viewModel.clearTrainingCaptureState() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Botones de gestión
        TrainingManagementButtons(
            onClearSelectedClass = { viewModel.clearTrainingClass(uiState.selectedTrainingClass) },
            onClearAll = { viewModel.clearAllTrainingData() },
            onExit = { viewModel.exitToNormalMode() }
        )
    }
}

/**
 * Selector de clase para entrenamiento.
 */
@Composable
fun ClassSelector(
    selectedClass: ClassLabel,
    onClassSelected: (ClassLabel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Selecciona la clase a capturar",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ClassButton(
                    label = "OK",
                    emoji = "✓",
                    color = Color(0xFF4CAF50),
                    isSelected = selectedClass == ClassLabel.OK,
                    onClick = { onClassSelected(ClassLabel.OK) }
                )
                
                ClassButton(
                    label = "OBSTÁCULO",
                    emoji = "⚠️",
                    color = Color(0xFFFF9800),
                    isSelected = selectedClass == ClassLabel.OBSTACULO,
                    onClick = { onClassSelected(ClassLabel.OBSTACULO) }
                )
                
                ClassButton(
                    label = "FALLO",
                    emoji = "🚨",
                    color = Color(0xFFF44336),
                    isSelected = selectedClass == ClassLabel.FALLO,
                    onClick = { onClassSelected(ClassLabel.FALLO) }
                )
            }
        }
    }
}

/**
 * Botón individual de clase.
 */
@Composable
fun ClassButton(
    label: String,
    emoji: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSelected) color else color.copy(alpha = 0.3f)
            ),
            modifier = Modifier.size(80.dp, 60.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) color else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Contadores de muestras por clase.
 */
@Composable
fun ClassCounters(
    datasetState: TrainingDatasetState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Muestras guardadas",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ClassCounterRow(
                label = "OK",
                count = datasetState.okSamples.size,
                color = Color(0xFF4CAF50)
            )
            ClassCounterRow(
                label = "OBSTÁCULO",
                count = datasetState.obstaculoSamples.size,
                color = Color(0xFFFF9800)
            )
            ClassCounterRow(
                label = "FALLO",
                count = datasetState.falloSamples.size,
                color = Color(0xFFF44336)
            )
            
            val total = datasetState.totalCount
            Text(
                text = "Total: $total muestras",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Fila de contador individual.
 */
@Composable
fun ClassCounterRow(
    label: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "$count/${TrainingSample.MAX_SAMPLES_PER_CLASS}",
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Botón de captura de muestra de entrenamiento.
 */
@Composable
fun TrainingCaptureButton(
    captureState: TrainingCaptureState,
    selectedClass: ClassLabel,
    sampleCount: Int,
    onCapture: () -> Unit,
    onClearState: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(captureState) {
        if (captureState is TrainingCaptureState.Success || captureState is TrainingCaptureState.Error) {
            kotlinx.coroutines.delay(3000)
            onClearState()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (captureState) {
                is TrainingCaptureState.Success -> Color(0xFFE8F5E9)
                is TrainingCaptureState.Error -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val buttonColor = when (selectedClass) {
                ClassLabel.OK -> Color(0xFF4CAF50)
                ClassLabel.OBSTACULO -> Color(0xFFFF9800)
                ClassLabel.FALLO -> Color(0xFFF44336)
            }

            Button(
                onClick = onCapture,
                enabled = captureState !is TrainingCaptureState.Capturing && 
                         captureState !is TrainingCaptureState.Saving &&
                         sampleCount < TrainingSample.MAX_SAMPLES_PER_CLASS,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                when (captureState) {
                    is TrainingCaptureState.Capturing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capturando...")
                        }
                    }
                    is TrainingCaptureState.Saving -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardando...")
                        }
                    }
                    else -> Text("📸 Capturar muestra ${selectedClass.name}")
                }
            }

            when (captureState) {
                is TrainingCaptureState.Success -> {
                    Text(
                        text = "✓ ${captureState.message}",
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                is TrainingCaptureState.Error -> {
                    Text(
                        text = "✗ ${captureState.message}",
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                else -> {
                    if (sampleCount >= TrainingSample.MAX_SAMPLES_PER_CLASS) {
                        Text(
                            text = "Límite alcanzado para esta clase",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Botones de gestión del modo entrenamiento.
 */
@Composable
fun TrainingManagementButtons(
    onClearSelectedClass: () -> Unit,
    onClearAll: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onClearSelectedClass,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🗑️ Reiniciar clase seleccionada")
        }

        OutlinedButton(
            onClick = onClearAll,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("🗑️ Reiniciar TODO el dataset")
        }

        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("← Volver al modo normal")
        }
    }
}

/**
 * Área de cámara con overlay según el modo.
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
    onCameraReady: (StateFlow<com.vigia.app.detection.ColorFrameData?>, StateFlow<FrameData?>?, FrameProcessor) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasPermission) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

    Box(modifier = modifier) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onCameraReadyColor = { colorFlow, legacyFlow, processor ->
                onCameraReady(colorFlow, legacyFlow, processor)
            },
            onError = { }
        )

        when (screenMode) {
            ScreenMode.ROI_SELECTION -> {
                RoiSelector(
                    onRoiSelected = onRoiSelected,
                    onCancel = onRoiSelectionCancelled,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ScreenMode.NORMAL -> {
                if (currentRoi != null) {
                    RoiOverlay(
                        roi = currentRoi,
                        isActive = isMonitoring,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            ScreenMode.TRAINING -> {
                // En modo training mostramos el ROI pero sin indicador de activo
                if (currentRoi != null) {
                    RoiOverlay(
                        roi = currentRoi,
                        isActive = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Controles del modo normal.
 */
@Composable
fun NormalModeControls(
    isMonitoring: Boolean,
    hasRoi: Boolean,
    detectionResult: DetectionResult?,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onDefineRoi: () -> Unit,
    onEnterTraining: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Indicador de detección
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Iniciar vigilancia")
            }

            Button(
                onClick = onStopMonitoring,
                enabled = isMonitoring,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Detener vigilancia")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botón definir ROI
        OutlinedButton(
            onClick = onDefineRoi,
            enabled = !isMonitoring,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(if (hasRoi) "Redefinir ROI" else "Definir ROI")
        }

        // Botón modo entrenamiento
        OutlinedButton(
            onClick = onEnterTraining,
            enabled = !isMonitoring,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text("🎓 Modo Entrenamiento")
        }

        // Indicador ROI
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
                detectionResult.hasChange -> Color(0xFFFFEBEE)
                else -> Color(0xFFE8F5E9)
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

    LaunchedEffect(telegramConfig) {
        telegramConfig?.let {
            botToken = it.botToken
            chatId = it.chatId
        }
    }

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

/**
 * Sección de alertas automáticas.
 */
@Composable
fun AutoAlertSection(
    alertState: AlertState,
    onClearState: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (alertState is AlertState.Idle) {
        return
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (alertState) {
                is AlertState.Success -> Color(0xFFE8F5E9)
                is AlertState.Error -> Color(0xFFFFEBEE)
                is AlertState.Cooldown -> Color(0xFFFFF3E0)
                is AlertState.Sending -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (alertState) {
                is AlertState.Sending -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Enviando alerta automática...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                is AlertState.Success -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🚨 Alerta automática enviada",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "${alertState.message} • ${timeFormat.format(Date(alertState.timestamp))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32).copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(
                            onClick = onClearState,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Ocultar", fontSize = 12.sp)
                        }
                    }
                }
                is AlertState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⚠️ Alerta fallida",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Text(
                            text = alertState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828).copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(
                            onClick = onClearState,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Ocultar", fontSize = 12.sp)
                        }
                    }
                }
                is AlertState.Cooldown -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⏱️ Cooldown activo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF6C00)
                        )
                        Text(
                            text = "Alerta bloqueada para evitar spam • Espera ${alertState.remainingSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF6C00).copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

/**
 * Sección de clasificación automática en tiempo real.
 * Muestra la clase estimada, confianza, scores por clase y estado del dataset.
 */
@Composable
fun ClassificationSection(
    classificationResult: com.vigia.app.classification.ClassificationResult?,
    datasetSyncStatus: com.vigia.app.monitoring.DatasetSyncUiState,
    datasetStats: com.vigia.app.classification.DatasetStats,
    cacheStats: com.vigia.app.classification.CacheStats?,
    onResync: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determinar color según la clase predicha
    val predictedClassColor = when (classificationResult?.predictedClass) {
        com.vigia.app.domain.model.ClassLabel.OK -> Color(0xFF4CAF50)      // Verde
        com.vigia.app.domain.model.ClassLabel.OBSTACULO -> Color(0xFFFF9800) // Naranja
        com.vigia.app.domain.model.ClassLabel.FALLO -> Color(0xFFF44336)   // Rojo
        else -> MaterialTheme.colorScheme.outline
    }

    // Determinar emoji según la clase
    val predictedClassEmoji = when (classificationResult?.predictedClass) {
        com.vigia.app.domain.model.ClassLabel.OK -> "✓"
        com.vigia.app.domain.model.ClassLabel.OBSTACULO -> "⚠️"
        com.vigia.app.domain.model.ClassLabel.FALLO -> "🚨"
        else -> "?"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                classificationResult == null -> MaterialTheme.colorScheme.surfaceVariant
                else -> predictedClassColor.copy(alpha = 0.1f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = predictedClassColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Título y estado de sincronización
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 Clasificación",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Indicador de estado de sincronización
                when (datasetSyncStatus) {
                    com.vigia.app.monitoring.DatasetSyncUiState.SYNCED -> {
                        Text(
                            text = "✓ Sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    com.vigia.app.monitoring.DatasetSyncUiState.SYNCING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    com.vigia.app.monitoring.DatasetSyncUiState.STALE -> {
                        Text(
                            text = "⚠ Stale",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800)
                        )
                    }
                    com.vigia.app.monitoring.DatasetSyncUiState.EMPTY -> {
                        Text(
                            text = "✗ Empty",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clase estimada principal
            if (classificationResult != null && datasetSyncStatus == com.vigia.app.monitoring.DatasetSyncUiState.SYNCED) {
                // Emoji grande y clase
                Text(
                    text = "$predictedClassEmoji ${classificationResult.predictedClass.name}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = predictedClassColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Barra de confianza
                val confidencePercent = (classificationResult.confidence * 100).toInt()
                LinearProgressIndicator(
                    progress = classificationResult.confidence,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = predictedClassColor,
                    trackColor = predictedClassColor.copy(alpha = 0.2f)
                )
                Text(
                    text = "Confianza: $confidencePercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scores por clase
                Column(modifier = Modifier.fillMaxWidth()) {
                    classificationResult.classScores.forEach { (label, score) ->
                        val scoreColor = when (label) {
                            com.vigia.app.domain.model.ClassLabel.OK -> Color(0xFF4CAF50)
                            com.vigia.app.domain.model.ClassLabel.OBSTACULO -> Color(0xFFFF9800)
                            com.vigia.app.domain.model.ClassLabel.FALLO -> Color(0xFFF44336)
                        }
                        val scorePercent = (score * 100).toInt()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = scoreColor,
                                modifier = Modifier.width(80.dp)
                            )
                            LinearProgressIndicator(
                                progress = score,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp),
                                color = scoreColor,
                                trackColor = scoreColor.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "$scorePercent%",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Contadores de muestras disponibles
                cacheStats?.let { stats ->
                    Text(
                        text = "Muestras: OK=${stats.okCached} OBST=${stats.obstaculoCached} FALL=${stats.falloCached}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Botón de resincronización
                if (datasetSyncStatus == com.vigia.app.monitoring.DatasetSyncUiState.STALE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onResync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔄 Resincronizar dataset")
                    }
                }
            } else {
                // Sin clasificación disponible
                when (datasetSyncStatus) {
                    com.vigia.app.monitoring.DatasetSyncUiState.EMPTY -> {
                        Text(
                            text = "Sin dataset de entrenamiento",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Captura muestras en modo entrenamiento",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    com.vigia.app.monitoring.DatasetSyncUiState.SYNCING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Sincronizando dataset...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    com.vigia.app.monitoring.DatasetSyncUiState.STALE -> {
                        Text(
                            text = "Dataset desactualizado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onResync,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔄 Resincronizar ahora")
                        }
                    }
                    else -> {
                        Text(
                            text = "Iniciando clasificación...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sección de confirmación diferida (3 minutos después de la alerta).
 */
@Composable
fun ConfirmationSection(
    confirmationState: ConfirmationState,
    onClearState: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (confirmationState is ConfirmationState.Idle) {
        return
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (confirmationState) {
                is ConfirmationState.Success -> Color(0xFFE3F2FD)
                is ConfirmationState.Error -> Color(0xFFFFEBEE)
                is ConfirmationState.Scheduled -> Color(0xFFF3E5F5)
                is ConfirmationState.Sending -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (confirmationState) {
                is ConfirmationState.Scheduled -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⏱️ Confirmación programada",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF7B1FA2)
                        )
                        Text(
                            text = "Se enviará imagen de confirmación en ${confirmationState.remainingSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7B1FA2).copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                is ConfirmationState.Sending -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Enviando confirmación...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                is ConfirmationState.Success -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📸 Confirmación enviada",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                        Text(
                            text = "${confirmationState.message} • ${timeFormat.format(Date(confirmationState.timestamp))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1565C0).copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(
                            onClick = onClearState,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Ocultar", fontSize = 12.sp)
                        }
                    }
                }
                is ConfirmationState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⚠️ Confirmación fallida",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Text(
                            text = confirmationState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828).copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(
                            onClick = onClearState,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Ocultar", fontSize = 12.sp)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
