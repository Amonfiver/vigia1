/**
 * Archivo: app/src/main/java/com/vigia/app/domain/repository/TelegramConfigRepository.kt
 * Propósito: Interfaz de repositorio para operaciones de persistencia de configuración Telegram.
 * Responsabilidad principal: Definir contrato para guardar y recuperar credenciales de Telegram.
 * Alcance: Capa de dominio, abstracción de fuente de datos.
 *
 * Decisiones técnicas relevantes:
 * - Interfaz en capa de dominio siguiendo principio de inversión de dependencias
 * - Operaciones suspendidas para soportar implementaciones asíncronas
 * - Separación clara entre configuración de ROI y de Telegram
 *
 * Limitaciones temporales del MVP:
 * - Solo una configuración de Telegram (un bot, un chat)
 * - Sin encriptación de credenciales en la interfaz
 *
 * Cambios recientes: Creación inicial de la interfaz.
 */
package com.vigia.app.domain.repository

import com.vigia.app.domain.model.TelegramConfig

/**
 * Contrato para persistencia de configuración de Telegram.
 */
interface TelegramConfigRepository {
    
    /**
     * Guarda la configuración de Telegram.
     * Sobrescribe cualquier configuración anterior.
     *
     * @param config Configuración a guardar
     */
    suspend fun saveConfig(config: TelegramConfig)
    
    /**
     * Recupera la configuración guardada.
     *
     * @return Configuración guardada o null si no existe
     */
    suspend fun getConfig(): TelegramConfig?
    
    /**
     * Elimina la configuración guardada.
     */
    suspend fun clearConfig()
    
    /**
     * Verifica si existe configuración guardada.
     *
     * @return true si hay configuración
     */
    suspend fun hasConfig(): Boolean
}