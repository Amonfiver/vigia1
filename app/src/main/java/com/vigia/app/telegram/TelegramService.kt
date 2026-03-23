/**
 * Archivo: app/src/main/java/com/vigia/app/telegram/TelegramService.kt
 * Propósito: Servicio para envío de mensajes e imágenes por Telegram Bot API.
 * Responsabilidad principal: Comunicación HTTP con la API de Telegram para envío de alertas.
 * Alcance: Capa de telegram, envío de mensajes de texto e imágenes.
 *
 * Decisiones técnicas relevantes:
 * - OkHttp para peticiones HTTP asíncronas
 * - Coroutines con suspend functions para operaciones de red
 * - Validación de configuración antes de enviar
 * - Manejo básico de errores de red y respuesta
 *
 * Limitaciones temporales del MVP:
 * - Sin reintentos automáticos avanzados
 * - Sin cola de mensajes pendientes
 * - Sin caché de imágenes
 * - Validación simple de respuesta (solo código HTTP 200)
 *
 * Cambios recientes:
 * - Implementación real de envío de mensajes con OkHttp
 * - Manejo de errores y resultado de operación
 */
package com.vigia.app.telegram

import com.vigia.app.domain.model.TelegramConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resultado de una operación de envío por Telegram.
 */
sealed class TelegramResult {
    data class Success(val message: String) : TelegramResult()
    data class Error(val exception: Throwable, val message: String) : TelegramResult()
}

/**
 * Servicio para envío de mensajes por Telegram.
 *
 * @property config Configuración del bot y chat destino
 */
class TelegramService(private val config: TelegramConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Envía un mensaje de texto por Telegram de forma asíncrona.
     *
     * @param message Texto del mensaje a enviar
     * @return Resultado de la operación (Success o Error)
     */
    suspend fun sendMessage(message: String): TelegramResult = withContext(Dispatchers.IO) {
        // Validar configuración
        if (!config.isValid()) {
            return@withContext TelegramResult.Error(
                IllegalArgumentException("Configuración inválida"),
                "Bot token o Chat ID vacíos"
            )
        }

        val url = "${TelegramConfig.TELEGRAM_API_BASE}/bot${config.botToken}/sendMessage"

        val requestBody = FormBody.Builder()
            .add("chat_id", config.chatId)
            .add("text", message)
            .add("parse_mode", "HTML")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return@withContext executeRequest(request)
    }

    /**
     * Envía una imagen por Telegram.
     * NOTA: Implementación básica para MVP. En futuras iteraciones se expandirá.
     *
     * @param imageData Bytes de la imagen a enviar
     * @param caption Texto opcional asociado a la imagen
     * @return Resultado de la operación
     */
    suspend fun sendImage(imageData: ByteArray, caption: String? = null): TelegramResult = withContext(Dispatchers.IO) {
        if (!config.isValid()) {
            return@withContext TelegramResult.Error(
                IllegalArgumentException("Configuración inválida"),
                "Bot token o Chat ID vacíos"
            )
        }

        // TODO: Implementar envío multipart de imagen en iteración de captura
        return@withContext TelegramResult.Error(
            NotImplementedError("Envío de imágenes pendiente"),
            "Envío de imágenes no implementado todavía"
        )
    }

    /**
     * Ejecuta una petición HTTP y procesa la respuesta.
     */
    private fun executeRequest(request: Request): TelegramResult {
        return try {
            client.newCall(request).execute().use { response ->
                handleResponse(response)
            }
        } catch (e: IOException) {
            TelegramResult.Error(e, "Error de red: ${e.message}")
        } catch (e: Exception) {
            TelegramResult.Error(e, "Error inesperado: ${e.message}")
        }
    }

    /**
     * Procesa la respuesta HTTP de la API de Telegram.
     */
    private fun handleResponse(response: Response): TelegramResult {
        return if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: "Sin respuesta"
            TelegramResult.Success("Mensaje enviado correctamente")
        } else {
            val errorBody = response.body?.string() ?: "Error desconocido"
            TelegramResult.Error(
                IOException("HTTP ${response.code}"),
                "Error de Telegram (${response.code}): $errorBody"
            )
        }
    }
}