/**
 * Archivo: app/src/main/java/com/vigia/app/domain/repository/RoiRepository.kt
 * Propósito: Interfaz de repositorio para operaciones de persistencia de ROI.
 * Responsabilidad principal: Definir contrato para guardar y recuperar ROI definido por el usuario.
 * Alcance: Capa de dominio, abstracción de fuente de datos.
 *
 * Decisiones técnicas relevantes:
 * - Interfaz en capa de dominio siguiendo principio de inversión de dependencias
 * - Operaciones suspendidas para soportar implementaciones asíncronas (DataStore, Room, etc.)
 * - Retorno nullable para indicar ausencia de ROI guardado
 *
 * Limitaciones temporales del MVP:
 * - Solo operaciones CRUD básicas, sin historial ni múltiples ROIs
 *
 * Cambios recientes: Creación inicial de la interfaz.
 */
package com.vigia.app.domain.repository

import com.vigia.app.domain.model.Roi

/**
 * Contrato para persistencia de ROI.
 * La implementación concreta decidirá el mecanismo (DataStore, SharedPreferences, etc.)
 */
interface RoiRepository {
    
    /**
     * Guarda el ROI definido por el usuario.
     * Sobrescribe cualquier ROI anterior.
     *
     * @param roi ROI a guardar
     */
    suspend fun saveRoi(roi: Roi)
    
    /**
     * Recupera el ROI guardado previamente.
     *
     * @return ROI guardado o null si no existe
     */
    suspend fun getRoi(): Roi?
    
    /**
     * Elimina el ROI guardado.
     */
    suspend fun clearRoi()
    
    /**
     * Verifica si existe un ROI guardado.
     *
     * @return true si hay ROI guardado
     */
    suspend fun hasRoi(): Boolean
}