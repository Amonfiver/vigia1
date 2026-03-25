/**
 * Archivo: app/src/main/java/com/vigia/app/domain/repository/OkBaselineRepository.kt
 * Propósito: Interfaz de repositorio para persistencia del baseline de estado OK.
 * Responsabilidad principal: Definir operaciones de guardado, carga y eliminación de muestras OK.
 * Alcance: Capa de dominio, contrato de persistencia.
 *
 * Decisiones técnicas relevantes:
 * - Interfaz limpia separada de implementación de almacenamiento
 * - Operaciones suspendidas para no bloquear UI
 * - Soporte para almacenar múltiples muestras (hasta 10)
 *
 * Limitaciones temporales del MVP:
 * - Sin sincronización con backend/cloud
 * - Almacenamiento local únicamente
 *
 * Cambios recientes:
 * - Creación inicial para soporte de baseline manual
 */
package com.vigia.app.domain.repository

import com.vigia.app.domain.model.OkBaselineSample
import com.vigia.app.domain.model.OkBaselineState

/**
 * Repositorio para persistencia del baseline de estado OK.
 */
interface OkBaselineRepository {
    
    /**
     * Guarda una nueva muestra de estado OK.
     * @return true si se guardó correctamente
     */
    suspend fun saveSample(sample: OkBaselineSample): Boolean
    
    /**
     * Obtiene todas las muestras guardadas.
     */
    suspend fun getAllSamples(): List<OkBaselineSample>
    
    /**
     * Obtiene el estado completo del baseline.
     */
    suspend fun getBaselineState(): OkBaselineState
    
    /**
     * Elimina una muestra específica por su índice.
     */
    suspend fun deleteSample(index: Int): Boolean
    
    /**
     * Elimina todas las muestras (reset del baseline).
     */
    suspend fun clearAllSamples(): Boolean
    
    /**
     * Verifica si hay muestras guardadas.
     */
    suspend fun hasSamples(): Boolean
    
    /**
     * Obtiene el número de muestras guardadas.
     */
    suspend fun getSampleCount(): Int
}