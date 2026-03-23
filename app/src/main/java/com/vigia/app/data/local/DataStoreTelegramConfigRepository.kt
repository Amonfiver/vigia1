/**
 * Archivo: app/src/main/java/com/vigia/app/data/local/DataStoreTelegramConfigRepository.kt
 * Propósito: Implementación de TelegramConfigRepository usando DataStore.
 * Responsabilidad principal: Guardar y recuperar configuración de Telegram.
 * Alcance: Capa de datos, implementación concreta del repositorio.
 *
 * Decisiones técnicas relevantes:
 * - DataStore Preferences para persistencia tipo clave-valor con corrutinas
 * - Almacenamiento separado de botToken y chatId
 * - Context como dependencia para acceso a DataStore
 *
 * Limitaciones temporales del MVP:
 * - Sin encriptación de credenciales
 * - Sin migración desde SharedPreferences
 *
 * Cambios recientes: Creación inicial de la implementación.
 */
package com.vigia.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vigia.app.domain.model.TelegramConfig
import com.vigia.app.domain.repository.TelegramConfigRepository
import kotlinx.coroutines.flow.first

private val Context.telegramDataStore: DataStore<Preferences> by preferencesDataStore(name = "telegram_preferences")

/**
 * Implementación de [TelegramConfigRepository] usando DataStore.
 *
 * @param context Contexto de aplicación para acceder a DataStore
 */
class DataStoreTelegramConfigRepository(private val context: Context) : TelegramConfigRepository {

    private object Keys {
        val BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val CHAT_ID = stringPreferencesKey("telegram_chat_id")
    }

    override suspend fun saveConfig(config: TelegramConfig) {
        context.telegramDataStore.edit { preferences ->
            preferences[Keys.BOT_TOKEN] = config.botToken
            preferences[Keys.CHAT_ID] = config.chatId
        }
    }

    override suspend fun getConfig(): TelegramConfig? {
        val preferences = context.telegramDataStore.data.first()
        val botToken = preferences[Keys.BOT_TOKEN]
        val chatId = preferences[Keys.CHAT_ID]

        return if (!botToken.isNullOrBlank() && !chatId.isNullOrBlank()) {
            try {
                TelegramConfig(botToken, chatId)
            } catch (e: IllegalArgumentException) {
                null
            }
        } else {
            null
        }
    }

    override suspend fun clearConfig() {
        context.telegramDataStore.edit { preferences ->
            preferences.remove(Keys.BOT_TOKEN)
            preferences.remove(Keys.CHAT_ID)
        }
    }

    override suspend fun hasConfig(): Boolean {
        val preferences = context.telegramDataStore.data.first()
        return !preferences[Keys.BOT_TOKEN].isNullOrBlank()
    }
}