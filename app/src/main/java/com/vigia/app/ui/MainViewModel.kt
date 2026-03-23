/**
 * Archivo: app/src/main/java/com/vigia/app/ui/MainViewModel.kt
 * Propósito: ViewModel para la pantalla principal de VIGIA1.
 * Responsabilidad principal: Gestionar estado de UI, selección de ROI, persistencia y coordinar acciones del usuario.
 * Alcance: Capa de presentación, lógica de la pantalla principal.
 *
 * Decisiones técnicas relevantes:
 * - StateFlow para estado reactivo de la UI
 * - ViewModel de androidx.lifecycle para sobrevivir a cambios de configuración
 * - Inyección de dependencias mediante constructor para testabilidad
 * - Uso de MonitoringManager para gestión del estado de vigilancia y análisis
 * - Persistencia de ROI mediante RoiRepository (DataStore)
 * - Observación de resultados de detección para mostrar en UI
 * - Conexión del flujo de frames de cámara al MonitoringManager
 *
 * Limitaciones temporales del MVP:
 * - Lógica de detección usa luminancia simple ( FrameData real pero análisis básico)
 * - Sin manejo avanzado de errores de red
 *
 * Cambios recientes:
 * - Añadido método connectCameraFrames para vincular frames reales de CameraX
 * - Análisis ahora usa datos reales de luminancia en lugar de simulación
 */
package com.vigia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.app.detection.DetectionResult
import com.vigia.app.detection.FrameData
import com.vigia.app.domain.model.Roi
import com.vigia.app.domain.model.TelegramConfig
import com.vigia.app.domain.repository.RoiRepository
import com.vigia.app.domain.repository.TelegramConfigRepository
import com.vigia.app.monitoring.MonitoringManager
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
 * Estado de la UI de la pantalla principal.
 *
 * @property screenMode Modo actual de la pantalla (normal o selección ROI)
 * @property isMonitoring Indica si la vigilancia está activa
 * @property statusMessage Mensaje de estado mostrado al usuario
 * @property telegramConfig Configuración actual de Telegram (puede ser null)
 * @property currentRoi ROI actualmente seleccionado (persistido o temporal)
 * @property hasRoi Indica si hay un ROI guardado persistentemente
 * @property detectionResult Último resultado de detección (null si no hay análisis)
 */
data class MainUiState(
    val screenMode: ScreenMode = ScreenMode.NORMAL,
    val isMonitoring: Boolean = false,
    val statusMessage: String = "Monitorización detenida",
    val telegramConfig: TelegramConfig? = null,
    val currentRoi: Roi? = null,
    val hasRoi: Boolean = false,
    val detectionResult: DetectionResult? = null
)

/**
 * ViewModel para la pantalla principal de VIGIA.
 *
 * @param roiRepository Repositorio para persistencia de ROI
 * @param telegramConfigRepository Repositorio para configuración de Telegram
 * @param monitoringManager Gestor del estado de monitorización
 */
class MainViewModel(
    private val roiRepository: RoiRepository? = null,
    private val telegramConfigRepository: TelegramConfigRepository? = null,
    private val monitoringManager: MonitoringManager = MonitoringManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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

        // Observar resultados de detección
        viewModelScope.launch {
            monitoringManager.detectionResult.collect { result ->
                _uiState.update { currentState ->
                    currentState.copy(detectionResult = result)
                }
            }
        }
    }

    /**
     * Conecta el flujo de frames de la cámara al sistema de monitorización.
     * Debe llamarse cuando el CameraPreview esté inicializado.
     *
     * @param frameDataFlow StateFlow que emite frames procesados de CameraX
     */
    fun connectCameraFrames(frameDataFlow: StateFlow<FrameData?>) {
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
            }
        }
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
        return _uiState.value.telegramConfig != null
    }

    override fun onCleared() {
        super.onCleared()
        monitoringManager.cleanup()
    }
}