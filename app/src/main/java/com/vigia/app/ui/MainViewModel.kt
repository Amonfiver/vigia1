/**
 * Archivo: app/src/main/java/com/vigia/app/ui/MainViewModel.kt
 * Propósito: ViewModel para la pantalla principal de VIGIA1.
 * Responsabilidad principal: Gestionar estado de UI y coordinar acciones del usuario.
 * Alcance: Capa de presentación, lógica de la pantalla principal.
 *
 * Decisiones técnicas relevantes:
 * - StateFlow para estado reactivo de la UI
 * - ViewModel de androidx.lifecycle para sobrevivir a cambios de configuración
 * - Inyección de dependencias mediante constructor para testabilidad
 * - Uso de MonitoringManager para gestión del estado de vigilancia
 *
 * Limitaciones temporales del MVP:
 * - Lógica de vigilancia simulada (no implementa detección real todavía)
 * - Sin manejo avanzado de errores de red
 *
 * Cambios recientes:
 * - Integración con MonitoringManager
 * - Mejorada separación de responsabilidades
 */
package com.vigia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Estado de la UI de la pantalla principal.
 *
 * @property isMonitoring Indica si la vigilancia está activa
 * @property statusMessage Mensaje de estado mostrado al usuario
 * @property telegramConfig Configuración actual de Telegram (puede ser null)
 * @property hasRoi Indica si hay un ROI guardado
 */
data class MainUiState(
    val isMonitoring: Boolean = false,
    val statusMessage: String = "Monitorización detenida",
    val telegramConfig: TelegramConfig? = null,
    val hasRoi: Boolean = false
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
    }

    /**
     * Carga datos guardados al inicializar.
     */
    private fun loadSavedData() {
        viewModelScope.launch {
            val hasRoi = roiRepository?.hasRoi() ?: false
            val telegramConfig = telegramConfigRepository?.getConfig()

            _uiState.update { currentState ->
                currentState.copy(
                    hasRoi = hasRoi,
                    telegramConfig = telegramConfig
                )
            }
        }
    }

    /**
     * Inicia la vigilancia.
     */
    fun startMonitoring() {
        monitoringManager.startMonitoring()
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
                // En fase posterior se mostrará error al usuario
            }
        }
    }

    /**
     * Verifica si la configuración de Telegram está completa.
     *
     * @return true si hay configuración válida
     */
    fun hasTelegramConfig(): Boolean {
        return _uiState.value.telegramConfig != null
    }
}