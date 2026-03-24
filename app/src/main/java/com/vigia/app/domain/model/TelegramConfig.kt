/**
 * Archivo: app/src/main/java/com/vigia/app/domain/model/TelegramConfig.kt
 * Propósito: Definir la entidad de configuración de Telegram para VIGIA1.
 * Responsabilidad principal: Almacenar credenciales y configuración del bot de Telegram.
 * Alcance: Modelo de datos para integración con Telegram Bot API.
 *
 * Decisiones técnicas relevantes:
 * - Data class inmutable para seguridad básica
 * - Validación básica de formato de token (no vacío)
 * - chatId como String para soportar IDs negativos de grupos
 * - EMPTY como función lazy para evitar crash en inicialización de companion object
 *
 * Limitaciones temporales del MVP:
 * - Sin encriptación de credenciales (se guardan en SharedPreferences/DataStore plano)
 * - Sin soporte para múltiples destinatarios
 *
 * Cambios recientes:
 * - Corregido crash: EMPTY ahora es lazy para no violar validaciones del init
 * - Eliminado isEmpty() en favor de !isValid()
 */
package com.vigia.app.domain.model

/**
 * Configuración necesaria para enviar mensajes mediante Telegram Bot API.
 *
 * @property botToken Token del bot proporcionado por @BotFather
 * @property chatId ID del chat destino (puede ser ID de usuario o de grupo, positivo o negativo)
 */
data class TelegramConfig(
    val botToken: String,
    val chatId: String
) {
    init {
        require(botToken.isNotBlank()) { "botToken no puede estar vacío" }
        require(chatId.isNotBlank()) { "chatId no puede estar vacío" }
    }

    /**
     * Verifica si la configuración tiene valores básicos válidos.
     * No garantiza que las credenciales sean correctas, solo que no están vacías.
     */
    fun isValid(): Boolean = botToken.isNotBlank() && chatId.isNotBlank()

    companion object {
        /**
         * Configuración vacía/no configurada.
         * Lazy para evitar crash al inicializar el companion object (las validaciones del init rechazan strings vacíos).
         */
        val EMPTY: TelegramConfig by lazy { 
            TelegramConfig("__empty__", "__empty__") 
        }

        /**
         * URL base de la API de Telegram Bot.
         */
        const val TELEGRAM_API_BASE = "https://api.telegram.org"
    }
}

/**
 * Extensión para verificar si la configuración está vacía.
 * Usa isValid() negado para consistencia.
 */
fun TelegramConfig.isEmpty(): Boolean = !isValid()
