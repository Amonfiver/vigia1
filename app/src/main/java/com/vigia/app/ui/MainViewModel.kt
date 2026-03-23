/**
 * Archivo: app/src/main/java/com/vigia/app/ui/MainViewModel.kt
 * Propósito: ViewModel para la pantalla principal de VIGIA1.
 * Responsabilidad principal: Gestionar estado de UI, selección de ROI, configuración Telegram, captura de imagen y coordinar acciones del usuario.
 * Alcance: Capa de presentación, lógica de la pantalla principal.
 *
 * Decisiones técnicas relevantes:
 * - StateFlow para estado reactivo de la UI
 * - ViewModel de androidx.lifecycle para sobrevivir a cambios de configuración
 * - Inyección de dependencias mediante constructor para testabilidad
 * - Uso de MonitoringManager para gestión del estado de vigilancia y análisis
 * - Persistencia de ROI y TelegramConfig mediante repositorios (DataStore)
 * - Observación de resultados de detección para mostrar en UI
 * - Conexión del flujo de frames de cámara al MonitoringManager
 * - Referencia a FrameProcessor para captura de imagen bajo demanda
 * - AlertManager para alertas automáticas basadas en detección
 *
 * Limitaciones temporales del MVP:
 * - Lógica de detección usa luminancia simple (FrameData real pero análisis básico)
 * - Sin manejo avanzado de errores de red
 * - Alerta automática implementada con cooldown de 60 segundos
 * - Segunda captura a los 3 minutos NO implementada todavía
 *
 * Cambios recientes:
 * - Añadida gestión completa de configuración de Telegram (guardar, cargar, probar)
 * - Implementada prueba manual de envío con feedback de resultado
 * - Añadida funcionalidad de captura y envío manual de imagen
 * - Estados de UI para captura de imagen: loading, success, error
 * - INTEGRACIÓN: AlertManager conectado para alertas automáticas al detectar cambios
 * - Estado de alerta automática añadido a la UI
 */
package com.vigia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.app.alert.AlertManager
import com.vigia.app.alert.AlertState
import com.vigia.app.camera.FrameProcessor
import com.vigia.app.detection.DetectionResult
import com.vigia.app.detection.FrameData
import com.vigia.app.domain.model.TelegramConfig
import com.vigia.app.domain.model.Roi
import com.vigia.app.domain.repository.RoiRepository
import com.vigia.app.domain.repository.TelegramConfigRepository
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
    ROI_SELECTION     // Modo de selección de ROI
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
 * Estado de la UI de la pantalla principal.
 *
 * @property screenMode Modo actual de la pantalla (normal o selección ROI)
 * @property isMonitoring Indica si la vigilancia está activa
 * @property statusMessage Mensaje de estado mostrado al usuario
 * @property telegramConfig Configuración actual de Telegram (puede ser null)
 * @property currentRoi ROI actualmente seleccionado (persistido o temporal)
 * @property hasRoi Indica si hay un ROI guardado persistentemente
 * @property detectionResult Último resultado de detección (null si no hay análisis)
 * @property telegramTestState Estado de la prueba de Telegram
 * @property imageCaptureState Estado de la captura y envío de imagen
 * @property alertState Estado de la última alerta automática
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
    val alertState: AlertState = AlertState.Idle
)

/**
 * ViewModel para la pantalla principal de VIGIA.
 *
 * @param roiRepository Repositorio para persistencia de ROI
 * @param telegramConfigRepository Repositorio para configuración de Telegram
 * @param monitoringManager Gestor del estado de monitorización
 * @param alertManager Gestor de alertas automáticas
 */
class MainViewModel(
    private val roiRepository: RoiRepository? = null,
    private val telegramConfigRepository: TelegramConfigRepository? = null,
    private val monitoringManager: MonitoringManager = MonitoringManager(),
    private val alertManager: AlertManager = AlertManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Referencia al procesador de frames para captura de imagen
    private var frameProcessor: FrameProcessor? = null

    init {
        loadSavedData()
        
        // Observar cambios en el estado de monitorización
        viewModelScope.launch {
            monitoringManager.isMonitoring.collect { isMonitoring ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isMonitoring = isMonitoring,
                        statusMessage = if (isMonitoring) {
                            "Monitorización activa"
                        } else {
                            "Monitorización detenida"
                        }
                    )
                }
            }
        }

        // Observar resultados de detección y enviar a alertas automáticas
        viewModelScope.launch {
            monitoringManager.detectionResult.collect { result ->
                _uiState.update { currentState ->
                    currentState.copy(detectionResult = result)
                }
                
                // Si hay resultado válido y monitoring activo, procesar alerta
                result?.let { detectionResult ->
                    if (monitoringManager.isMonitoring.value) {
                        alertManager.onDetectionResult(
                            detectionResult = detectionResult,
                            telegramConfig = _uiState.value.telegramConfig,
                            frameProcessor = frameProcessor
                        )
                    }
                }
            }
        }

        // Observar estado de alertas automáticas
        viewModelScope.launch {
            alertManager.alertState.collect { alertState ->
                _uiState.update { currentState ->
                    currentState.copy(alertState = alertState)
                }
            }
        }
    }

    /**
     * Conecta el flujo de frames de la cámara al sistema de monitorización.
     * Debe llamarse cuando el CameraPreview esté inicializado.
     *
     * @param frameDataFlow StateFlow que emite frames procesados de CameraX
     * @param processor FrameProcessor para captura de imagen
     */
    fun connectCamera(frameDataFlow: StateFlow<FrameData?>, processor: FrameProcessor) {
        this.frameProcessor = processor
        monitoringManager.connectCameraFrames(frameDataFlow)
    }

    /**
     * Carga datos guardados al inicializar.
     * Recupera el ROI persistido y la configuración de Telegram.
     */
    private fun loadSavedData() {
        viewModelScope.launch {
            val savedRoi = roiRepository?.getRoi()
            val hasSavedRoi = roiRepository?.hasRoi() ?: false
            val telegramConfig = telegramConfigRepository?.getConfig()

            _uiState.update { currentState ->
                currentState.copy(
                    currentRoi = savedRoi,
                    hasRoi = hasSavedRoi,
                    telegramConfig = telegramConfig
                )
            }
        }
    }

    /**
     * Entra en modo de selección de ROI.
     */
    fun enterRoiSelectionMode() {
        _uiState.update { it.copy(screenMode = ScreenMode.ROI_SELECTION) }
    }

    /**
     * Sale del modo de selección de ROI y vuelve a la vista normal.
     */
    fun exitRoiSelectionMode() {
        _uiState.update { it.copy(screenMode = ScreenMode.NORMAL) }
    }

    /**
     * Confirma un ROI seleccionado y lo guarda persistentemente.
     *
     * @param roi ROI seleccionado por el usuario
     */
    fun confirmRoiSelection(roi: Roi) {
        viewModelScope.launch {
            // Guardar en persistencia
            roiRepository?.saveRoi(roi)
            
            // Actualizar estado de UI
            _uiState.update { currentState ->
                currentState.copy(
                    currentRoi = roi,
                    hasRoi = true,
                    screenMode = ScreenMode.NORMAL
                )
            }
        }
    }

    /**
     * Cancela la selección de ROI actual y vuelve a la vista normal.
     */
    fun cancelRoiSelection() {
        _uiState.update { it.copy(screenMode = ScreenMode.NORMAL) }
    }

    /**
     * Inicia la vigilancia con el ROI actual.
     */
    fun startMonitoring() {
        val roi = _uiState.value.currentRoi
        monitoringManager.startMonitoring(roi)
    }

    /**
     * Detiene la vigilancia.
     */
    fun stopMonitoring() {
        monitoringManager.stopMonitoring()
    }

    /**
     * Guarda la configuración de Telegram.
     *
     * @param botToken Token del bot
     * @param chatId ID del chat
     */
    fun saveTelegramConfig(botToken: String, chatId: String) {
        viewModelScope.launch {
            try {
                val config = TelegramConfig(botToken.trim(), chatId.trim())
                telegramConfigRepository?.saveConfig(config)
                _uiState.update { it.copy(telegramConfig = config) }
            } catch (e: IllegalArgumentException) {
                // Datos inválidos, no guardar
                _uiState.update { it.copy(telegramTestState = TelegramTestState.Error("Datos inválidos")) }
            }
        }
    }

    /**
     * Realiza una prueba manual de envío de mensaje a Telegram.
     */
    fun testTelegramConnection() {
        val config = _uiState.value.telegramConfig
        
        if (config == null || !config.isValid()) {
            _uiState.update { it.copy(telegramTestState = TelegramTestState.Error("Configuración incompleta")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(telegramTestState = TelegramTestState.Loading) }
            
            val service = TelegramService(config)
            val result = service.sendMessage("🧪 <b>Prueba de VIGIA</b>\n\nConexión configurada correctamente.\n\nSi recibes este mensaje, el bot está funcionando.")
            
            _uiState.update { currentState ->
                when (result) {
                    is TelegramResult.Success -> {
                        currentState.copy(telegramTestState = TelegramTestState.Success(result.message))
                    }
                    is TelegramResult.Error -> {
                        currentState.copy(telegramTestState = TelegramTestState.Error(result.message))
                    }
                }
            }
        }
    }

    /**
     * Limpia el estado de la prueba de Telegram.
     */
    fun clearTelegramTestState() {
        _uiState.update { it.copy(telegramTestState = TelegramTestState.Idle) }
    }

    /**
     * Captura la imagen actual y la envía a Telegram de forma manual.
     * Este método es llamado por acción manual del usuario.
     */
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
            
            // Capturar imagen
            val imageBytes = processor.getLastFrameJpegBytes()
            
            if (imageBytes == null) {
                _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Error("No se pudo capturar la imagen")) }
                return@launch
            }

            _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Sending) }
            
            // Enviar imagen a Telegram
            val service = TelegramService(config)
            val result = service.sendImage(
                imageData = imageBytes,
                caption = "📸 Captura manual desde VIGIA"
            )
            
            _uiState.update { currentState ->
                when (result) {
                    is TelegramResult.Success -> {
                        currentState.copy(imageCaptureState = ImageCaptureState.Success("Imagen enviada correctamente"))
                    }
                    is TelegramResult.Error -> {
                        currentState.copy(imageCaptureState = ImageCaptureState.Error(result.message))
                    }
                }
            }
        }
    }

    /**
     * Limpia el estado de captura de imagen.
     */
    fun clearImageCaptureState() {
        _uiState.update { it.copy(imageCaptureState = ImageCaptureState.Idle) }
    }

    /**
     * Verifica si hay un ROI seleccionado actualmente.
     *
     * @return true si hay ROI seleccionado (persistido o en memoria)
     */
    fun hasCurrentRoi(): Boolean {
        return _uiState.value.currentRoi != null
    }

    /**
     * Verifica si la configuración de Telegram está completa.
     *
     * @return true si hay configuración válida
     */
    fun hasTelegramConfig(): Boolean {
        return _uiState.value.telegramConfig?.isValid() ?: false
    }

    /**
     * Limpia el estado de la alerta automática.
     */
    fun clearAlertState() {
        alertManager.clearState()
    }

    override fun onCleared() {
        super.onCleared()
        monitoringManager.cleanup()
        alertManager.cleanup()
    }
}