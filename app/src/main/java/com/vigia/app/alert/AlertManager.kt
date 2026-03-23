/**
 * Archivo: app/src/main/java/com/vigia/app/alert/AlertManager.kt
 * Propósito: Gestor de alertas automáticas que dispara envíos a Telegram cuando se detectan cambios relevantes.
 * Responsabilidad principal: Escuchar resultados de detección, aplicar protección anti-spam, coordinar el envío de mensaje + imagen y programar confirmación a los 3 minutos.
 * Alcance: Capa de alertas, puente entre detección y notificación.
 *
 * Decisiones técnicas relevantes:
 * - Cooldown simple basado en timestamp (60 segundos) para evitar spam
 * - Envío secuencial: primero mensaje de alerta, luego imagen
 * - StateFlow para exponer estado del último envío (UI puede observar)
 * - Delegación a TelegramService para el envío real
 * - Captura de frame vía FrameProcessor.getLastFrameJpegBytes()
 * - Confirmación diferida usando coroutine con delay de 3 minutos (180s)
 * - Job de confirmación se cancela si la vigilancia se detiene
 *
 * Limitaciones temporales del MVP:
 * - Cooldown fijo de 60 segundos (no configurable aún)
 * - Sin cola de alertas pendientes (si falla, se pierde)
 * - Una sola alerta por evento (sin sistema de "escalada")
 * - Sin persistencia de alertas enviadas
 * - Confirmación se pierde si la app se cierra antes de los 3 minutos
 *
 * Cambios recientes:
 * - Añadida confirmación diferida a los 3 minutos tras alerta exitosa
 * - Estados ConfirmationScheduled y ConfirmationState para tracking
 * - Método onFrameProcessorAvailable para captura en momento de confirmación
 * - Cancelación de confirmación pendiente en cleanup()
 */
package com.vigia.app.alert

import com.vigia.app.camera.FrameProcessor
import com.vigia.app.detection.DetectionResult
import com.vigia.app.domain.model.TelegramConfig
import com.vigia.app.telegram.TelegramResult
import com.vigia.app.telegram.TelegramService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de una alerta automática.
 */
sealed class AlertState {
    object Idle : AlertState()
    object Sending : AlertState()
    data class Success(val timestamp: Long, val message: String) : AlertState()
    data class Error(val timestamp: Long, val message: String) : AlertState()
    data class Cooldown(val remainingSeconds: Int) : AlertState()
}

/**
 * Estado de la confirmación diferida (3 minutos después).
 */
sealed class ConfirmationState {
    object Idle : ConfirmationState()
    data class Scheduled(val scheduledTimestamp: Long, val remainingSeconds: Int) : ConfirmationState()
    object Sending : ConfirmationState()
    data class Success(val timestamp: Long, val message: String) : ConfirmationState()
    data class Error(val timestamp: Long, val message: String) : ConfirmationState()
}

/**
 * Gestor de alertas automáticas basadas en detección de cambios.
 *
 * @param cooldownSeconds Tiempo mínimo entre alertas (default 60 segundos)
 * @param scope CoroutineScope para operaciones asíncronas
 */
class AlertManager(
    private val cooldownSeconds: Int = 60,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _alertState = MutableStateFlow<AlertState>(AlertState.Idle)
    /**
     * Estado actual de la última alerta (para feedback en UI).
     */
    val alertState: StateFlow<AlertState> = _alertState.asStateFlow()

    private val _confirmationState = MutableStateFlow<ConfirmationState>(ConfirmationState.Idle)
    /**
     * Estado de la confirmación diferida (para feedback en UI).
     */
    val confirmationState: StateFlow<ConfirmationState> = _confirmationState.asStateFlow()

    private var lastAlertTimestamp: Long = 0
    private var isSending = false
    private var confirmationJob: Job? = null
    
    // Referencia al FrameProcessor para captura en momento de confirmación
    private var frameProcessorRef: FrameProcessor? = null
    private var telegramConfigRef: TelegramConfig? = null

    /**
     * Procesa un resultado de detección y dispara alerta si corresponde.
     * Debe llamarse desde el observer de detectionResult.
     *
     * @param detectionResult Resultado del análisis de detección
     * @param telegramConfig Configuración de Telegram (null si no está configurado)
     * @param frameProcessor Procesador de frames para capturar imagen
     */
    fun onDetectionResult(
        detectionResult: DetectionResult,
        telegramConfig: TelegramConfig?,
        frameProcessor: FrameProcessor?
    ) {
        // Solo alertar si hay cambio real
        if (!detectionResult.hasChange) {
            return
        }

        // Verificar configuración de Telegram
        if (telegramConfig == null || !telegramConfig.isValid()) {
            _alertState.value = AlertState.Error(
                timestamp = System.currentTimeMillis(),
                message = "Alerta no enviada: Telegram no configurado"
            )
            return
        }

        // Verificar disponibilidad de frame processor
        if (frameProcessor == null) {
            _alertState.value = AlertState.Error(
                timestamp = System.currentTimeMillis(),
                message = "Alerta no enviada: Cámara no disponible"
            )
            return
        }

        // Verificar cooldown anti-spam
        val now = System.currentTimeMillis()
        val elapsedSinceLastAlert = (now - lastAlertTimestamp) / 1000

        if (elapsedSinceLastAlert < cooldownSeconds) {
            val remaining = cooldownSeconds - elapsedSinceLastAlert.toInt()
            _alertState.value = AlertState.Cooldown(remaining)
            return
        }

        // Enviar alerta
        sendAlert(telegramConfig, frameProcessor)
    }

    /**
     * Envía la alerta: mensaje + imagen.
     */
    private fun sendAlert(config: TelegramConfig, frameProcessor: FrameProcessor) {
        if (isSending) return // Evitar envíos concurrentes

        isSending = true
        _alertState.value = AlertState.Sending

        scope.launch {
            try {
                // Capturar imagen primero
                val imageBytes = frameProcessor.getLastFrameJpegBytes()

                if (imageBytes == null) {
                    _alertState.value = AlertState.Error(
                        timestamp = System.currentTimeMillis(),
                        message = "No se pudo capturar la imagen"
                    )
                    isSending = false
                    return@launch
                }

                // Enviar mensaje de alerta
                val service = TelegramService(config)
                val messageResult = service.sendMessage("🚨 <b>Minda Requiere Atención</b>\n\nSe ha detectado un cambio relevante en el área vigilada.")

                if (messageResult is TelegramResult.Error) {
                    _alertState.value = AlertState.Error(
                        timestamp = System.currentTimeMillis(),
                        message = "Error al enviar mensaje: ${messageResult.message}"
                    )
                    isSending = false
                    return@launch
                }

                // Enviar imagen
                val imageResult = service.sendImage(
                    imageData = imageBytes,
                    caption = "📸 Captura automática del evento"
                )

                when (imageResult) {
                    is TelegramResult.Success -> {
                        lastAlertTimestamp = System.currentTimeMillis()
                        _alertState.value = AlertState.Success(
                            timestamp = lastAlertTimestamp,
                            message = "Alerta enviada correctamente"
                        )
                        // Programar confirmación a los 3 minutos
                        scheduleConfirmation(config, frameProcessor)
                    }
                    is TelegramResult.Error -> {
                        _alertState.value = AlertState.Error(
                            timestamp = System.currentTimeMillis(),
                            message = "Mensaje enviado, pero error en imagen: ${imageResult.message}"
                        )
                    }
                }

            } catch (e: Exception) {
                _alertState.value = AlertState.Error(
                    timestamp = System.currentTimeMillis(),
                    message = "Error inesperado: ${e.message}"
                )
            } finally {
                isSending = false
            }
        }
    }

    /**
     * Programa la confirmación diferida a los 3 minutos.
     */
    private fun scheduleConfirmation(config: TelegramConfig, frameProcessor: FrameProcessor) {
        // Guardar referencias para uso en el momento de la confirmación
        telegramConfigRef = config
        frameProcessorRef = frameProcessor
        
        // Cancelar job anterior si existe
        confirmationJob?.cancel()
        
        val scheduledTime = System.currentTimeMillis() + CONFIRMATION_DELAY_MS
        _confirmationState.value = ConfirmationState.Scheduled(
            scheduledTimestamp = scheduledTime,
            remainingSeconds = (CONFIRMATION_DELAY_MS / 1000).toInt()
        )
        
        // Iniciar countdown para actualizar UI
        startConfirmationCountdown(scheduledTime)
        
        // Programar el envío
        confirmationJob = scope.launch {
            delay(CONFIRMATION_DELAY_MS)
            sendConfirmation()
        }
    }
    
    /**
     * Inicia countdown para actualizar remainingSeconds en UI.
     */
    private fun startConfirmationCountdown(scheduledTime: Long) {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = ((scheduledTime - now) / 1000).toInt()
                
                if (remaining <= 0) {
                    // La confirmación está por enviarse, salir del countdown
                    break
                }
                
                // Actualizar estado con tiempo restante
                val currentState = _confirmationState.value
                if (currentState is ConfirmationState.Scheduled) {
                    _confirmationState.value = ConfirmationState.Scheduled(
                        scheduledTimestamp = scheduledTime,
                        remainingSeconds = remaining
                    )
                }
                
                delay(1000) // Actualizar cada segundo
            }
        }
    }

    /**
     * Envía la imagen de confirmación a los 3 minutos.
     */
    private suspend fun sendConfirmation() {
        val config = telegramConfigRef
        val frameProcessor = frameProcessorRef
        
        if (config == null || frameProcessor == null) {
            _confirmationState.value = ConfirmationState.Error(
                timestamp = System.currentTimeMillis(),
                message = "No se pudo enviar confirmación: config o cámara no disponibles"
            )
            return
        }
        
        _confirmationState.value = ConfirmationState.Sending
        
        try {
            // Capturar imagen nueva (no reutilizar la primera)
            val imageBytes = frameProcessor.getLastFrameJpegBytes()
            
            if (imageBytes == null) {
                _confirmationState.value = ConfirmationState.Error(
                    timestamp = System.currentTimeMillis(),
                    message = "No se pudo capturar imagen de confirmación"
                )
                return
            }
            
            // Enviar imagen de confirmación
            val service = TelegramService(config)
            val result = service.sendImage(
                imageData = imageBytes,
                caption = "📸 Confirmación 3 minutos después - Estado actual del área"
            )
            
            when (result) {
                is TelegramResult.Success -> {
                    _confirmationState.value = ConfirmationState.Success(
                        timestamp = System.currentTimeMillis(),
                        message = "Confirmación enviada correctamente"
                    )
                }
                is TelegramResult.Error -> {
                    _confirmationState.value = ConfirmationState.Error(
                        timestamp = System.currentTimeMillis(),
                        message = "Error al enviar confirmación: ${result.message}"
                    )
                }
            }
        } catch (e: Exception) {
            _confirmationState.value = ConfirmationState.Error(
                timestamp = System.currentTimeMillis(),
                message = "Error inesperado en confirmación: ${e.message}"
            )
        }
    }

    /**
     * Resetea el estado de alerta a Idle.
     * Útil para limpiar mensajes de éxito/error en la UI.
     */
    fun clearState() {
        _alertState.value = AlertState.Idle
    }
    
    /**
     * Resetea el estado de confirmación a Idle.
     */
    fun clearConfirmationState() {
        _confirmationState.value = ConfirmationState.Idle
    }

    /**
     * Fuerza el reset del cooldown (para testing o casos especiales).
     */
    fun resetCooldown() {
        lastAlertTimestamp = 0
    }
    
    /**
     * Cancela la confirmación programada (útil al detener vigilancia).
     */
    fun cancelConfirmation() {
        confirmationJob?.cancel()
        confirmationJob = null
        if (_confirmationState.value is ConfirmationState.Scheduled) {
            _confirmationState.value = ConfirmationState.Idle
        }
    }

    /**
     * Libera recursos al destruir el manager.
     */
    fun cleanup() {
        confirmationJob?.cancel()
        scope.cancel()
    }
    
    companion object {
        /**
         * Delay para confirmación: 3 minutos en milisegundos.
         */
        const val CONFIRMATION_DELAY_MS = 3 * 60 * 1000L // 180,000 ms = 3 minutos
    }
}
