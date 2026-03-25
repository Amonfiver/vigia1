/**
 * Archivo: app/src/main/java/com/vigia/app/domain/repository/TrainingDatasetRepository.kt
 * Propósito: Interfaz de repositorio para persistencia del dataset de entrenamiento supervisado.
 * Responsabilidad principal: Definir operaciones de guardado, carga y gestión de muestras etiquetadas.
 * Alcance: Capa de dominio, contrato de persistencia.
 *
 * Decisiones técnicas relevantes:
 * - Interfaz limpia separada de implementación de almacenamiento
 * - Operaciones suspendidas para no bloquear UI
 * - Soporte para tres clases: OK, OBSTACULO, FALLO
 * - Gestión por clase con conteos independientes
 *
 * Limitaciones temporales del MVP:
 * - Sin sincronización con backend/cloud
 * - Almacenamiento local únicamente
 *
 * Cambios recientes:
 * - Creación inicial para modo de entrenamiento supervisado manual
 */
package com.vigia.app.domain.repository

import com.vigia.app.domain.model.ClassLabel
import com.vigia.app.domain.model.TrainingDatasetState
import com.vigia.app.domain.model.TrainingSample

/**
 * Repositorio para persistencia del dataset de entrenamiento supervisado.
 */
interface TrainingDatasetRepository {
    
    /**
     * Guarda una nueva muestra de entrenamiento.
     * @return true si se guardó correctamente
     */
    suspend fun saveSample(sample: TrainingSample): Boolean
    
    /**
     * Obtiene todas las muestras de una clase específica.
     */
    suspend fun getSamplesByLabel(label: ClassLabel): List<TrainingSample>
    
    /**
     * Obtiene el estado completo del dataset (todas las clases).
     */
    suspend fun getDatasetState(): TrainingDatasetState
    
    /**
     * Obtiene el número de muestras para una clase específica.
     */
    suspend fun getSampleCount(label: ClassLabel): Int
    
    /**
     * Elimina una muestra específica por su ID.
     */
    suspend fun deleteSample(sampleId: String): Boolean
    
    /**
     * Elimina todas las muestras de una clase específica.
     */
    suspend fun clearSamplesByLabel(label: ClassLabel): Boolean
    
    /**
     * Elimina todo el dataset (todas las clases).
     */
    suspend fun clearAllSamples(): Boolean
    
    /**
     * Verifica si hay muestras guardadas para una clase.
     */
    suspend fun hasSamples(label: ClassLabel): Boolean
    
    /**
     * Genera un ID único para una nueva muestra.
     */
    fun generateSampleId(label: ClassLabel): String
}