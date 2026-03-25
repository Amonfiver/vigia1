/**
 * Archivo: app/src/main/java/com/vigia/app/domain/model/OkBaselineSample.kt
 * Propósito: Modelo de datos para muestras de estado OK del transfer.
 * Responsabilidad principal: Representar una muestra capturada manualmente del estado normal/OK.
 * Alcance: Capa de dominio, modelo de baseline para comparación.
 *
 * Decisiones técnicas relevantes:
 * - Almacenamiento de imagen como ByteArray (JPEG) para persistencia
 * - Timestamp para trazabilidad de cuándo se capturó
 * - Índice de muestra (1-10) para identificación ordenada
 * - Coordenadas del ROI en el momento de la captura para contexto
 *
 * Limitaciones temporales del MVP:
 * - Máximo 10 muestras para no saturar almacenamiento
 * - Sin análisis automático de las muestras (solo almacenamiento)
 * - Sin metadatos avanzados (histograma, estadísticas, etc.)
 *
 * Cambios recientes:
 * - Creación inicial para soporte de baseline manual de estado OK
 */
package com.vigia.app.domain.model

/**
 * Muestra de estado OK capturada manualmente.
 *
 * @property index Número de muestra (1-10)
 * @property timestamp Momento de captura en ms desde época
 * @property imageData Imagen en formato JPEG
 * @property roi Roi activo en el momento de la captura
 * @property subRegion Subregión analizada (si aplica)
 */
data class OkBaselineSample(
    val index: Int,
    val timestamp: Long,
    val imageData: ByteArray,
    val roi: Roi,
    val subRegion: SubRegionInfo? = null
) {
    init {
        require(index in 1..MAX_SAMPLES) { "Index debe estar entre 1 y $MAX_SAMPLES" }
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
        const val MAX_SAMPLES = 10
        const val MIN_SAMPLES = 5
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OkBaselineSample
        return index == other.index &&
               timestamp == other.timestamp &&
               imageData.contentEquals(other.imageData) &&
               roi == other.roi &&
               subRegion == other.subRegion
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + imageData.contentHashCode()
        result = 31 * result + roi.hashCode()
        result = 31 * result + (subRegion?.hashCode() ?: 0)
        return result
    }
}

/**
 * Estado del baseline de muestras OK.
 *
 * @property samples Lista de muestras capturadas
 * @property isComplete true si se tienen al menos MIN_SAMPLES
 */
data class OkBaselineState(
    val samples: List<OkBaselineSample> = emptyList()
) {
    val isComplete: Boolean get() = samples.size >= OkBaselineSample.MIN_SAMPLES
    val canAddMore: Boolean get() = samples.size < OkBaselineSample.MAX_SAMPLES
    val count: Int get() = samples.size

    /**
     * Obtiene la siguiente posición disponible para una nueva muestra (1-10).
     * @return null si ya hay MAX_SAMPLES
     */
    fun nextIndex(): Int? {
        return if (samples.size < OkBaselineSample.MAX_SAMPLES) samples.size + 1 else null
    }
}