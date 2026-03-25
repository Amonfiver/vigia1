/**
 * Archivo: app/src/main/java/com/vigia/app/ui/MainViewModel.kt
 * Propósito: ViewModel para la pantalla principal de VIGIA1.
 * Responsabilidad principal: Gestionar estado de UI, selección de ROI, configuración Telegram, captura de imagen,
 * coordinar acciones del usuario, baseline manual de estado OK, modo de entrenamiento supervisado y evidencias visuales separadas.
 * Alcance: Capa de presentación, lógica de la pantalla principal.
 *
 * Decisiones técnicas relevantes:
 * - StateFlow para estado reactivo de la UI
 * - ViewModel de androidx.lifecycle para sobrevivir a cambios de configuración
 * - Inyección de dependencias mediante constructor para testabilidad
 * - Uso de MonitoringManager para gestión del estado de vigilancia y análisis
 * - Persistencia de ROI y TelegramConfig mediante repositorios (DataStore)
 * - Persistencia de baseline OK mediante FileOkBaselineRepository
 * - Persistencia de dataset de entrenamiento mediante FileTrainingDatasetRepository
 * - Modo de entrenamiento supervisado con tres clases: OK, OBSTACULO, FALLO
 * - Observación de resultados de detección para mostrar en UI
 * - Conexión del flujo de frames de cámara al MonitoringManager
 * - Referencia a FrameProcessor para captura de imagen bajo demanda y evidencias visuales
 * - AlertManager para alertas automáticas basadas en detección
 * - Estado de baseline OK: captura, conteo, reset
 * - Estado de entrenamiento: selección de clase, captura de muestras, contadores por clase
 * - Estado de evidencias visuales separadas: referencia actual, último evento, última confirmación
 *
 * Limitaciones temporales del MVP:
 * - Lógica de detección usa luminancia simple (FrameData real pero análisis básico)
 * - Sin manejo avanzado de errores de red
 * - Alerta automática implementada con cooldown de 60 segundos
 * - Confirmación diferida a 3 minutos implementada (se pierde si app se cierra)
 * - Baseline OK solo almacena imágenes, sin análisis automático de ellas todavía
 * - Dataset de entrenamiento solo almacena, sin clasificación automática todavía
 *
 * Cambios recientes:
 * - AÑADIDO: Modo de entrenamiento supervisado manual con tres clases (OK, OBSTACULO, FALLO)
 * - AÑADIDO: Dataset local etiquetado con contadores por clase
 * - AÑADIDO: UI de captura de muestras de entrenamiento
 * - AÑADIDO: Baseline manual de estado OK (captura, almacenamiento, conteo, reset)
 * - AÑADIDO: Evidencias visuales separadas (referencia actual, último evento, última confirmación)
 * - CORREGIDO: Pipeline de captura en color ya no tiene artefactos verde/magenta
 * - MANTENIDO: Compatibilidad con todas las funcionalidades previas
 */
package com.vigia.app.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.app.alert.AlertManager
import com.vigia.app.alert.AlertState
import com.vigia.app.alert.ConfirmationState
import com.vigia.app.camera.FrameProcessor
import com.vigia.app.data.local.FileOkBaselineRepository
import com.vigia.app.data.local.FileTrainingDatasetRepository
import com.vigia.app.detection.DetectionResult
import com.vigia.app.detection.FrameData
import com.vigia.app.domain.model.ClassLabel
import com.vigia.app.domain.model.OkBaselineSample
import com.vigia.app.domain.model.OkBaselineState
import com.vigia.app.domain.model.Roi
import com.vigia.app.domain.model.TelegramConfig
import com.vigia.app.domain.model.TrainingCaptureState
import com.vigia.app.domain.model.TrainingDatasetState
import com.vigia.app.domain.model.TrainingSample
import com.vigia.app.domain.repository.OkBaselineRepository
import com.vigia.app.domain.repository.RoiRepository
import com.vigia.app.domain.repository.TelegramConfigRepository
import com.vigia.app.domain.repository.TrainingDatasetRepository
import com.vigia.app.monitoring.MonitoringManager
import com.vigia.app.telegram.TelegramResult
import com.vigia.app.telegram.TelegramService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Modo de la pantalla principal.
 */
enum class ScreenMode {
    NORMAL,           // Vista normal con preview y controles
    ROI_SELECTION,    // Modo de selección de ROI
    TRAINING          // NUEVO: Modo de entrenamiento supervisado
}

/**
 * Estado de la prueba de Telegram.
 */
sealed class TelegramTestState {
    object Idle : TelegramTestState()
    object Loading : TelegramTestState()
    data class Success(val message: String) : TelegramTestState()
    data class Error(val message: String) : TelegramTestState()
}

/**
 * Estado de la captura y envío de imagen.
 */
sealed class ImageCaptureState {
    object Idle : ImageCaptureState()
    object Capturing : ImageCaptureState()
    object Sending : ImageCaptureState()
    data class Success(val message: String) : ImageCaptureState()
    data class Error(val message: String) : ImageCaptureState()
}

/**
 * Estado del baseline manual de estado OK.
 */
sealed class OkBaselineCaptureState {
    object Idle : OkBaselineCaptureState()
    object Capturing : OkBaselineCaptureState()
    object Saving : OkBaselineCaptureState()
    data class Success(val message: String) : OkBaselineCaptureState()
    data class Error(val message: String) : OkBaselineCaptureState()
}

/**
 * Estado de las evidencias visuales separadas para observabilidad.
 */
data class VisualEvidenceState(
    val referenceCrop: Bitmap? = null,
    val lastEventCrop: Bitmap? = null,
    val lastConfirmationCrop: Bitmap? = null,
    val referenceTimestamp: Long = 0,
    val lastEventTimestamp: Long = 0,
    val lastConfirmationTimestamp: Long = 0
)

/**
 * Estado de la UI de la pantalla principal.
 */
data class MainUiState(
    val screenMode: ScreenMode = ScreenMode.NORMAL,
    val isMonitoring: Boolean = false,
    val statusMessage: String = "Monitorización detenida",
    val telegramConfig: TelegramConfig? = null,
    val currentRoi: Roi? = null,
    val hasRoi: Boolean = false,
    val detectionResult: DetectionResult? = null,
    val telegramTestState: TelegramTestState = TelegramTestState.Idle,
    val imageCaptureState: ImageCaptureState = ImageCaptureState.Idle,
    val alertState: AlertState = AlertState.Idle,
    val confirmationState: ConfirmationState = ConfirmationState.Idle,
    // Baseline OK
    val okBaselineState: OkBaselineState = OkBaselineState(),
    val okBaselineCaptureState: OkBaselineCaptureState = OkBaselineCaptureState.Idle,
    val okBaselineCount: Int = 0,
    // Entrenamiento supervisado
    val trainingDatasetState: TrainingDatasetState = TrainingDatasetState(),
    val trainingCaptureState: TrainingCaptureState = TrainingCaptureState.Idle,
    val selectedTrainingClass: ClassLabel = ClassLabel.OK,
    // Evidencias visuales
    val visualEvidenceState: VisualEvidenceState = VisualEvidenceState()
)

/**
 * ViewModel para la pantalla principal de VIGIA.
 */
class MainViewModel(
    private val roiRepository: RoiRepository? = null,
    private val telegramConfigRepository: TelegramConfigRepository? = null,
    private val okBaselineRepository: OkBaselineRepository? = null,
    private val trainingDatasetRepository: TrainingDatasetRepository? = null,
    private val monitoringManager: MonitoringManager = MonitoringManager(),
    private val alertManager: AlertManager = AlertManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var frameProcessor: FrameProcessor? = null

    init {
        loadSavedData()
        setupObservers()
    }

    private fun setupObservers() {
        viewModelScope.launch {
            monitoringManager.isMonitoring.collect { isMonitoring ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isMonitoring = isMonitoring,
                        statusMessage = if (isMonitoring) "Monitorización activa" else "Monitorización detenida"
                    )
                }
            }
        }

        viewModelScope.launch {
            monitoringManager.detectionResult.collect { result ->
                _uiState.update { currentState ->
                    currentState.copy(detectionResult = result)
                }
                updateReferenceEvidence()
                result?.let { detectionResult ->
                    if (monitoringManager.isMonitoring.value && detectionResult.hasChange) {
                        captureEventEvidence()
                        alertManager.onDetectionResult(
                            detectionResult = detectionResult,
                            telegramConfig = _uiState.value.telegramConfig,
                            frameProcessor = frameProcessor
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            alertManager.alertState.collect { alertState ->
                _uiState.update { currentState -> currentState.copy(alertState = alertState) }
            }
        }

        viewModelScope.launch {
            alertManager.confirmationState.collect { confirmationState ->
                _uiState.update { currentState ->
                    currentState.copy(confirmationState = confirmationState)
                }
                if (confirmationState is ConfirmationState.Success) {
                    captureConfirmationEvidence()
                }
            }
        }
    }

    private fun loadSavedData() {
        viewModelScope.launch {
            val savedRoi = roiRepository?.getRoi()
            val hasSavedRoi = roiRepository?.hasRoi() ?: false
            val telegramConfig = telegramConfigRepository?.getConfig()
            val baselineState = okBaselineRepository?.getBaselineState() ?: OkBaselineState()
            val baselineCount = okBaselineRepository?.getSampleCount() ?: 0
            val trainingState = trainingDatasetRepository?.getDatasetState() ?: TrainingDatasetState()

            _uiState.update { currentState ->
                currentState.copy(
                    currentRoi = savedRoi,
                    hasRoi = hasSavedRoi,
                    telegramConfig = telegramConfig,
                    okBaselineState = baselineState,
                    okBaselineCount = baselineCount,
                    trainingDatasetState = trainingState
                )
            }
        }
    }

    // ==================== CÁMARA Y EVIDENCIAS ====================

    fun connectCamera(
        colorFrameDataFlow: StateFlow<com.vigia.app.detection.ColorFrameData?>,
        frameDataFlow: StateFlow<FrameData?>? = null,
        processor: FrameProcessor
    ) {
        this.frameProcessor = processor
        monitoringManager.connectColorFrames(colorFrameDataFlow)
        frameDataFlow?.let { monitoringManager.connectCameraFrames(it) }
        updateReferenceEvidence()
    }

    @Deprecated("Usar connectCamera con ColorFrameData")
    fun connectCameraLegacy(frameDataFlow: StateFlow<FrameData?>, processor: FrameProcessor) {
        this.frameProcessor = processor
        monitoringManager.connectCameraFrames(frameDataFlow)
    }

    private fun updateReferenceEvidence() {
        val processor = frameProcessor ?: return
        val roi = _uiState.value.currentRoi ?: return
        val crop = processor.getRoiCrop(roi) ?: return
        
        _uiState.update { currentState ->
            currentState.copy(
                visualEvidenceState = currentState.visualEvidenceState.copy(
                    referenceCrop = crop,
                    referenceTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    private fun captureEventEvidence() {
        val processor = frameProcessor ?: return
        val roi = _uiState.value.currentRoi ?: return
        val crop = processor.getRoiCrop(roi) ?: return
        
        _uiState.update { currentState ->
            currentState.copy(
                visualEvidenceState = currentState.visualEvidenceState.copy(
                    lastEventCrop = crop,
                    lastEventTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    private fun captureConfirmationEvidence() {
        val processor = frameProcessor ?: return
        val roi = _uiState.value.currentRoi ?: return
        val crop = processor.getRoiCrop(roi) ?: return
        
        _uiState.update { currentState ->
            currentState.copy(
                visualEvidenceState = currentState.visualEvidenceState.copy(
                    lastConfirmationCrop = crop,
                    lastConfirmationTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // ==================== NAVEGACIÓN Y MODOS ====================

    fun enterRoiSelectionMode() {
        _uiState.update { it.copy(screenMode = ScreenMode.ROI_SELECTION) }
    }

    fun enterTrainingMode() {
        _uiState.update { it.copy(screenMode = ScreenMode.TRAINING) }
    }

    fun exitToNormalMode() {
        _uiState.update { it.copy(screenMode = ScreenMode.NORMAL) }
    }

    fun confirmRoiSelection(roi: Roi) {
        viewModelScope.launch {
            roiRepository?.saveRoi(roi)
            _uiState.update { currentState ->
                currentState.copy(
                    currentRoi = roi,
                    hasRoi = true,
                    screenMode = ScreenMode.NORMAL
                )
            }
            updateReferenceEvidence()
        }
    }

    fun cancelRoiSelection() {
        _uiState.update { it.copy(screenMode = ScreenMode.NORMAL) }
    }

    // ==================== VIGILANCIA ====================

    fun startMonitoring() {
        monitoringManager.startMonitoring(_uiState.value.currentRoi)
    }

    fun stopMonitoring() {
        monitoringManager.stopMonitoring()
        alertManager.cancelConfirmation()
    }

    // ==================== TELEGRAM ====================

    fun saveTelegramConfig(botToken: String, chatId: String) {
        viewModelScope.launch {
            try {
                val config = TelegramConfig(botToken.trim(), chatId.trim())
                telegramConfigRepository?.saveConfig(config)
                _uiState.update { it.copy(telegramConfig = config) }
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(telegramTestState = TelegramTestState.Error("Datos inválidos")) }
            }
        }
    }

    fun testTelegramConnection() {
        val config = _uiState.value.telegramConfig
        if (config == null || !config.isValid()) {
            _uiState.update { it.copy(telegramTestState = TelegramTestState.Error("Configuración incompleta")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(telegramTestState = TelegramTestState.Loading) }
            val service = TelegramService(config)
            val result = service.sendMessage("🧪 <b>Prueba de VIGIA</b>\n\nConexión configurada correctamente.")
            
            _uiState.update { currentState ->
                when (result) {
                    is TelegramResult.Success -> currentState.copy(telegramTestState = TelegramTestState.Success(result.message))
                    is TelegramResult.Error -> currentState.copy(telegramTestState = TelegramTestState.Error(result.message))
                }
            }
        }
    }

    fun clearTelegramTestState() {
        _uiState.update { it.copy(telegramTestState = TelegramTestState.Idle) }
    }

    fun captureAndSendImage() {
        val config = _uiState.value.telegramConfig
        if (config == null || !config.isValid()) {
            _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Error("Configuración de Telegram incompleta")) }
            return
        }

        val processor = frameProcessor
        if (processor == null) {
            _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Error("Cámara no inicializada")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Capturing) }
            val imageBytes = processor.getLastFrameJpegBytes()
            
            if (imageBytes == null) {
                _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Error("No se pudo capturar la imagen")) }
                return@launch
            }

            _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Sending) }
            val service = TelegramService(config)
            val result = service.sendImage(imageData = imageBytes, caption = "📸 Captura manual desde VIGIA")
            
            _uiState.update { currentState ->
                when (result) {
                    is TelegramResult.Success -> currentState.copy(imageCaptureState = ImageCaptureState.Success("Imagen enviada correctamente"))
                    is TelegramResult.Error -> currentState.copy(imageCaptureState = ImageCaptureState.Error(result.message))
                }
            }
        }
    }

    fun clearImageCaptureState() {
        _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Idle) }
    }

    // ==================== MODO ENTRENAMIENTO SUPERVISADO ====================

    /**
     * Selecciona la clase actual para captura de muestras de entrenamiento.
     */
    fun selectTrainingClass(label: ClassLabel) {
        _uiState.update { it.copy(selectedTrainingClass = label) }
    }

    /**
     * Captura una muestra de entrenamiento para la clase seleccionada.
     */
    fun captureTrainingSample() {
        val processor = frameProcessor
        if (processor == null) {
            _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("Cámara no inicializada")) }
            return
        }

        val roi = _uiState.value.currentRoi
        if (roi == null) {
            _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("Defina un ROI primero")) }
            return
        }

        val repository = trainingDatasetRepository
        if (repository == null) {
            _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("Repositorio no disponible")) }
            return
        }

        val selectedClass = _uiState.value.selectedTrainingClass

        viewModelScope.launch {
            _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Capturing) }

            // Verificar límite de muestras
            val currentCount = repository.getSampleCount(selectedClass)
            if (currentCount >= TrainingSample.MAX_SAMPLES_PER_CLASS) {
                _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("Límite de ${TrainingSample.MAX_SAMPLES_PER_CLASS} muestras alcanzado para ${selectedClass.name}")) }
                return@launch
            }

            // Capturar crop del ROI
            val cropBitmap = processor.getRoiCrop(roi)
            if (cropBitmap == null) {
                _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("No se pudo capturar el crop")) }
                return@launch
            }

            _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Saving) }

            // Convertir a JPEG
            val jpegBytes = bitmapToJpeg(cropBitmap)
            if (jpegBytes == null) {
                _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("Error al convertir imagen")) }
                return@launch
            }

            // Crear muestra
            val sampleId = repository.generateSampleId(selectedClass)
            val sample = TrainingSample(
                id = sampleId,
                label = selectedClass,
                timestamp = System.currentTimeMillis(),
                imageData = jpegBytes,
                roi = roi,
                subRegion = TrainingSample.SubRegionInfo(
                    left = 0.2f,
                    top = 0.28f,
                    right = 0.8f,
                    bottom = 0.72f,
                    description = "Subregión activa (60% centro)"
                )
            )

            // Guardar
            val success = repository.saveSample(sample)

            if (success) {
                // Recargar estado
                val newState = repository.getDatasetState()
                val newCount = repository.getSampleCount(selectedClass)
                
                _uiState.update { currentState ->
                    currentState.copy(
                        trainingDatasetState = newState,
                        trainingCaptureState = TrainingCaptureState.Success(
                            "Muestra guardada: ${selectedClass.name} ($newCount/${TrainingSample.MAX_SAMPLES_PER_CLASS})"
                        )
                    )
                }
            } else {
                _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("Error al guardar la muestra")) }
            }
        }
    }

    /**
     * Reinicia/elimina todas las muestras de una clase específica.
     */
    fun clearTrainingClass(label: ClassLabel) {
        val repository = trainingDatasetRepository ?: return
        
        viewModelScope.launch {
            val success = repository.clearSamplesByLabel(label)
            
            if (success) {
                val newState = repository.getDatasetState()
                _uiState.update { currentState ->
                    currentState.copy(
                        trainingDatasetState = newState,
                        trainingCaptureState = TrainingCaptureState.Success("Clase ${label.name} reiniciada")
                    )
                }
            } else {
                _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("Error al reiniciar clase ${label.name}")) }
            }
        }
    }

    /**
     * Reinicia/elimina todo el dataset de entrenamiento.
     */
    fun clearAllTrainingData() {
        val repository = trainingDatasetRepository ?: return
        
        viewModelScope.launch {
            val success = repository.clearAllSamples()
            
            if (success) {
                _uiState.update { currentState ->
                    currentState.copy(
                        trainingDatasetState = TrainingDatasetState(),
                        trainingCaptureState = TrainingCaptureState.Success("Dataset de entrenamiento reiniciado")
                    )
                }
            } else {
                _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Error("Error al reiniciar dataset")) }
            }
        }
    }

    /**
     * Recarga el estado del dataset desde el repositorio.
     */
    fun refreshTrainingDatasetState() {
        val repository = trainingDatasetRepository ?: return
        
        viewModelScope.launch {
            val state = repository.getDatasetState()
            _uiState.update { currentState ->
                currentState.copy(trainingDatasetState = state)
            }
        }
    }

    /**
     * Limpia el estado de captura de entrenamiento.
     */
    fun clearTrainingCaptureState() {
        _uiState.update { it.copy(trainingCaptureState = TrainingCaptureState.Idle) }
    }

    // ==================== BASELINE OK (LEGACY) ====================

    fun captureOkBaselineSample() {
        val processor = frameProcessor
        if (processor == null) {
            _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Error("Cámara no inicializada")) }
            return
        }

        val roi = _uiState.value.currentRoi
        if (roi == null) {
            _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Error("Defina un ROI primero")) }
            return
        }

        val repository = okBaselineRepository
        if (repository == null) {
            _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Error("Repositorio no disponible")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Capturing) }
            val cropBitmap = processor.getRoiCrop(roi)
            
            if (cropBitmap == null) {
                _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Error("No se pudo capturar el crop")) }
                return@launch
            }

            _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Saving) }
            val jpegBytes = bitmapToJpeg(cropBitmap)
            
            if (jpegBytes == null) {
                _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Error("Error al convertir imagen")) }
                return@launch
            }

            val nextIndex = _uiState.value.okBaselineState.nextIndex()
            if (nextIndex == null) {
                _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Error("Límite de 10 muestras alcanzado")) }
                return@launch
            }

            val sample = OkBaselineSample(
                index = nextIndex,
                timestamp = System.currentTimeMillis(),
                imageData = jpegBytes,
                roi = roi,
                subRegion = OkBaselineSample.SubRegionInfo(
                    left = 0.2f, top = 0.28f, right = 0.8f, bottom = 0.72f,
                    description = "Subregión activa (60% centro)"
                )
            )

            val success = repository.saveSample(sample)
            if (success) {
                val newState = repository.getBaselineState()
                val newCount = repository.getSampleCount()
                _uiState.update { currentState ->
                    currentState.copy(
                        okBaselineState = newState,
                        okBaselineCount = newCount,
                        okBaselineCaptureState = OkBaselineCaptureState.Success("Muestra $nextIndex/${OkBaselineSample.MAX_SAMPLES} guardada")
                    )
                }
            } else {
                _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Error("Error al guardar")) }
            }
        }
    }

    fun resetOkBaseline() {
        val repository = okBaselineRepository ?: return
        viewModelScope.launch {
            val success = repository.clearAllSamples()
            if (success) {
                _uiState.update { currentState ->
                    currentState.copy(
                        okBaselineState = OkBaselineState(),
                        okBaselineCount = 0,
                        okBaselineCaptureState = OkBaselineCaptureState.Success("Baseline reiniciado")
                    )
                }
            }
        }
    }

    fun clearOkBaselineCaptureState() {
        _uiState.update { it.copy(okBaselineCaptureState = OkBaselineCaptureState.Idle) }
    }

    // ==================== UTILIDADES ====================

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray? {
        return try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun hasCurrentRoi(): Boolean = _uiState.value.currentRoi != null
    fun hasTelegramConfig(): Boolean = _uiState.value.telegramConfig?.isValid() ?: false

    fun clearAlertState() = alertManager.clearState()
    fun clearConfirmationState() = alertManager.clearConfirmationState()

    override fun onCleared() {
        super.onCleared()
        monitoringManager.cleanup()
        alertManager.cleanup()
    }
}