/**
 * Archivo: app/src/main/java/com/vigia/app/domain/model/TrainingSample.kt
 * Propósito: Modelo de datos para muestras de entrenamiento supervisado del transfer.
 * Responsabilidad principal: Representar una muestra visual etiquetada para clasificación posterior.
 * Alcance: Capa de dominio, modelo del dataset de entrenamiento.
 *
 * Decisiones técnicas relevantes:
 * - Uso de enum ClassLabel para etiquetas tipo-safe (OK, OBSTACULO, FALLO)
 * - Almacenamiento de imagen como ByteArray (JPEG) para persistencia
 * - Crop de la región relevante (ROI/subregión) en lugar de pantalla completa
 * - Timestamp para trazabilidad temporal
 * - Metadatos de ROI para contexto espacial
 *
 * Limitaciones temporales del MVP:
 * - Sin metadatos avanzados (histograma, características HSV extraídas)
 * - Sin normalización avanzada de la imagen
 * - Sin sincronización con backend/cloud
 *
 * Cambios recientes:
 * - Creación inicial para modo de entrenamiento supervisado manual
 */
package com.vigia.app.domain.model

/**
 * Etiquetas de clase para el dataset de entrenamiento.
 */
enum class ClassLabel {
    OK,           // Estado normal / sano / sin incidencia
    OBSTACULO,    // Señal naranja / atención inmediata
    FALLO         // Señal roja / franjas rojas / estado anómalo
}

/**
 * Muestra de entrenamiento etiquetada para clasificación supervisada.
 *
 * @property id Identificador único de la muestra (generado automáticamente)
 * @property label Clase de la muestra (OK, OBSTACULO, FALLO)
 * @property timestamp Momento de captura en ms desde época
 * @property imageData Imagen en formato JPEG (crop de la región relevante)
 * @property roi ROI activo en el momento de la captura
 * @property subRegion Subregión analizada dentro del ROI (si aplica)
 */
data class TrainingSample(
    val id: String,
    val label: ClassLabel,
    val timestamp: Long,
    val imageData: ByteArray,
    val roi: Roi,
    val subRegion: SubRegionInfo? = null
) {
    init {
        require(id.isNotBlank()) { "id no puede estar vacío" }
        require(imageData.isNotEmpty()) { "imageData no puede estar vacío" }
        require(timestamp > 0) { "timestamp debe ser positivo" }
    }

    /**
     * Información de la subregión analizada dentro del ROI.
     */
    data class SubRegionInfo(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val description: String
    )

    companion object {
        const val MAX_SAMPLES_PER_CLASS = 50  // Límite por clase para no saturar
        const val MIN_SAMPLES_PER_CLASS = 5   // Mínimo recomendado para entrenamiento básico
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TrainingSample
        return id == other.id &&
               label == other.label &&
               timestamp == other.timestamp &&
               imageData.contentEquals(other.imageData) &&
               roi == other.roi &&
               subRegion == other.subRegion
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + imageData.contentHashCode()
        result = 31 * result + roi.hashCode()
        result = 31 * result + (subRegion?.hashCode() ?: 0)
        return result
    }
}

/**
 * Estado del dataset de entrenamiento por clase.
 *
 * @property okSamples Muestras de clase OK
 * @property obstaculoSamples Muestras de clase OBSTACULO
 * @property falloSamples Muestras de clase FALLO
 */
data class TrainingDatasetState(
    val okSamples: List<TrainingSample> = emptyList(),
    val obstaculoSamples: List<TrainingSample> = emptyList(),
    val falloSamples: List<TrainingSample> = emptyList()
) {
    val totalCount: Int get() = okSamples.size + obstaculoSamples.size + falloSamples.size
    
    fun countForLabel(label: ClassLabel): Int = when (label) {
        ClassLabel.OK -> okSamples.size
        ClassLabel.OBSTACULO -> obstaculoSamples.size
        ClassLabel.FALLO -> falloSamples.size
    }
    
    fun canAddMore(label: ClassLabel): Boolean = countForLabel(label) < TrainingSample.MAX_SAMPLES_PER_CLASS
    
    fun isComplete(): Boolean {
        return okSamples.size >= TrainingSample.MIN_SAMPLES_PER_CLASS &&
               obstaculoSamples.size >= TrainingSample.MIN_SAMPLES_PER_CLASS &&
               falloSamples.size >= TrainingSample.MIN_SAMPLES_PER_CLASS
    }
    
    fun getAllSamples(): List<TrainingSample> {
        return okSamples + obstaculoSamples + falloSamples
    }
}

/**
 * Estado de captura de muestras de entrenamiento.
 */
sealed class TrainingCaptureState {
    object Idle : TrainingCaptureState()
    object Capturing : TrainingCaptureState()
    object Saving : TrainingCaptureState()
    data class Success(val message: String) : TrainingCaptureState()
    data class Error(val message: String) : TrainingCaptureState()
}