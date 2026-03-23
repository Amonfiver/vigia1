/**
 * Archivo: app/src/main/java/com/vigia/app/alert/AlertManager.kt
 * Propósito: Gestor de alertas automáticas que dispara envíos a Telegram cuando se detectan cambios relevantes.
 * Responsabilidad principal: Escuchar resultados de detección, aplicar protección anti-spam y coordinar el envío de mensaje + imagen.
 * Alcance: Capa de alertas, puente entre detección y notificación.
 *
 * Decisiones técnicas relevantes:
 * - Cooldown simple basado en timestamp (60 segundos) para evitar spam
 * - Envío secuencial: primero mensaje de alerta, luego imagen
 * - StateFlow para exponer estado del último envío (UI puede observar)
 * - Delegación a TelegramService para el envío real
 * - Captura de frame vía FrameProcessor.getLastFrameJpegBytes()
 *
 * Limitaciones temporales del MVP:
 * - Cooldown fijo de 60 segundos (no configurable aún)
 * - Sin cola de alertas pendientes (si falla, se pierde)
 * - Una sola alerta por evento (sin sistema de "escalada")
 * - Sin persistencia de alertas enviadas
 * - Segunda captura a los 3 minutos NO implementada todavía
 *
 * Cambios recientes:
 * - Creación inicial del gestor de alertas automáticas
 * - Integración con MonitoringManager y TelegramService
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

    private var lastAlertTimestamp: Long = 0
    private var isSending = false

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
     * Resetea el estado de alerta a Idle.
     * Útil para limpiar mensajes de éxito/error en la UI.
     */
    fun clearState() {
        _alertState.value = AlertState.Idle
    }

    /**
     * Fuerza el reset del cooldown (para testing o casos especiales).
     */
    fun resetCooldown() {
        lastAlertTimestamp = 0
    }

    /**
     * Libera recursos al destruir el manager.
     */
    fun cleanup() {
        scope.cancel()
    }
}