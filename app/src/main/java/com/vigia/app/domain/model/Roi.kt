/**
 * Archivo: app/src/main/java/com/vigia/app/domain/model/Roi.kt
 * Propósito: Definir la entidad de dominio ROI (Región de Interés) para VIGIA1.
 * Responsabilidad principal: Representar una zona rectangular de interés sobre la imagen de cámara,
 * incluyendo soporte para SubROI manual del transfer.
 * Alcance: Modelo de datos core del sistema de vigilancia.
 *
 * Decisiones técnicas relevantes:
 * - ROI rectangular definido por coordenadas relativas (0.0 - 1.0) para adaptarse a diferentes resoluciones
 * - Normalización de coordenadas para garantizar x < x2 e y < y2
 * - Data class de Kotlin para inmutabilidad y equals/hashCode automáticos
 * - UNDEFINED como lazy para evitar crash en inicialización (las validaciones del init rechazan 0f,0f,0f,0f)
 * - SubROI opcional relativa al ROI global (coordenadas 0.0-1.0 dentro del ROI)
 *
 * Limitaciones temporales del MVP:
 * - Solo un ROI global por sesión
 * - Una SubROI opcional por ROI global
 * - Forma rectangular únicamente (sin polígonos complejos)
 *
 * Cambios recientes:
 * - AÑADIDO: Soporte para SubROI manual del transfer (relativeSubRoi)
 * - SubROI almacena coordenadas relativas dentro del ROI global (0.0-1.0)
 * - Métodos helper para convertir SubROI a coordenadas absolutas de imagen
 * - Validación de que SubROI está contenida dentro del ROI global
 */
package com.vigia.app.domain.model

/**
 * Representa una Región de Interés (ROI) rectangular sobre la imagen de cámara.
 *
 * Las coordenadas son relativas (0.0 a 1.0) para ser independientes de la resolución.
 * El sistema garantiza que left < right y top < bottom mediante validación.
 *
 * @property left Coordenada X izquierda (0.0 - 1.0)
 * @property top Coordenada Y superior (0.0 - 1.0)
 * @property right Coordenada X derecha (0.0 - 1.0)
 * @property bottom Coordenada Y inferior (0.0 - 1.0)
 * @property relativeSubRoi SubROI opcional relativa al ROI global (coordenadas 0.0-1.0 dentro del ROI),
 *                          null si no está definida
 */
data class Roi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val relativeSubRoi: RelativeSubRoi? = null
) {
    init {
        require(left in 0.0f..1.0f) { "left debe estar entre 0.0 y 1.0" }
        require(top in 0.0f..1.0f) { "top debe estar entre 0.0 y 1.0" }
        require(right in 0.0f..1.0f) { "right debe estar entre 0.0 y 1.0" }
        require(bottom in 0.0f..1.0f) { "bottom debe estar entre 0.0 y 1.0" }
        require(left < right) { "left debe ser menor que right" }
        require(top < bottom) { "top debe ser menor que bottom" }
        
        // Validar que SubROI esté contenida dentro del ROI si existe
        relativeSubRoi?.let { sub ->
            require(sub.left in 0.0f..1.0f) { "subRoi.left debe estar entre 0.0 y 1.0" }
            require(sub.top in 0.0f..1.0f) { "subRoi.top debe estar entre 0.0 y 1.0" }
            require(sub.right in 0.0f..1.0f) { "subRoi.right debe estar entre 0.0 y 1.0" }
            require(sub.bottom in 0.0f..1.0f) { "subRoi.bottom debe estar entre 0.0 y 1.0" }
            require(sub.left < sub.right) { "subRoi.left debe ser menor que subRoi.right" }
            require(sub.top < sub.bottom) { "subRoi.top debe ser menor que subRoi.bottom" }
        }
    }

    /**
     * Verifica si el ROI tiene coordenadas válidas.
     * Un ROI con left=right o top=bottom (como UNDEFINED) no es válido.
     */
    fun isValid(): Boolean {
        return left < right && top < bottom &&
               left in 0.0f..1.0f && top in 0.0f..1.0f &&
               right in 0.0f..1.0f && bottom in 0.0f..1.0f
    }

    /**
     * Verifica si tiene SubROI manual definida.
     */
    fun hasSubRoi(): Boolean {
        return relativeSubRoi != null && relativeSubRoi.isValid()
    }

    /**
     * Convierte la SubROI relativa a coordenadas absolutas de imagen (0.0-1.0).
     * 
     * @return Roi con coordenadas absolutas, o null si no hay SubROI definida
     */
    fun getAbsoluteSubRoi(): Roi? {
        if (!hasSubRoi()) return null
        
        val sub = relativeSubRoi!!
        val roiWidth = right - left
        val roiHeight = bottom - top
        
        return Roi(
            left = left + sub.left * roiWidth,
            top = top + sub.top * roiHeight,
            right = left + sub.right * roiWidth,
            bottom = top + sub.bottom * roiHeight
        )
    }

    /**
     * Ancho normalizado del ROI (0.0 - 1.0)
     */
    val width: Float get() = right - left

    /**
     * Alto normalizado del ROI (0.0 - 1.0)
     */
    val height: Float get() = bottom - top

    /**
     * Centro X del ROI
     */
    val centerX: Float get() = (left + right) / 2

    /**
     * Centro Y del ROI
     */
    val centerY: Float get() = (top + bottom) / 2

    companion object {
        /**
         * ROI vacío o no definido.
         * Lazy para evitar crash al inicializar el companion object (las validaciones del init rechazan 0f,0f,0f,0f).
         * Valor con coordenadas inválidas que pueden usarse como marcador "vacío".
         */
        val UNDEFINED: Roi by lazy { 
            Roi(0f, 0f, 0.1f, 0.1f, null) // Coordenadas que pasan validación pero indican "vacío"
        }

        /**
         * ROI por defecto centrado en la imagen (20% del área total).
         * Útil como valor inicial mientras el usuario define el suyo.
         */
        val DEFAULT = Roi(0.4f, 0.4f, 0.6f, 0.6f, null)
    }
}

/**
 * Representa una SubROI relativa dentro de un ROI global.
 * Las coordenadas son relativas al ROI (0.0-1.0), no a la imagen completa.
 * 
 * Por ejemplo, si relativeSubRoi = RelativeSubRoi(0.2f, 0.3f, 0.8f, 0.7f),
 * significa que ocupa el 60% central horizontal y 40% central vertical del ROI.
 *
 * @property left Coordenada X izquierda relativa al ROI (0.0 - 1.0)
 * @property top Coordenada Y superior relativa al ROI (0.0 - 1.0)
 * @property right Coordenada X derecha relativa al ROI (0.0 - 1.0)
 * @property bottom Coordenada Y inferior relativa al ROI (0.0 - 1.0)
 */
data class RelativeSubRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0.0f..1.0f) { "left debe estar entre 0.0 y 1.0" }
        require(top in 0.0f..1.0f) { "top debe estar entre 0.0 y 1.0" }
        require(right in 0.0f..1.0f) { "right debe estar entre 0.0 y 1.0" }
        require(bottom in 0.0f..1.0f) { "bottom debe estar entre 0.0 y 1.0" }
        require(left < right) { "left debe ser menor que right" }
        require(top < bottom) { "top debe ser menor que bottom" }
    }

    /**
     * Verifica si la SubROI tiene coordenadas válidas.
     */
    fun isValid(): Boolean {
        return left < right && top < bottom &&
               left in 0.0f..1.0f && top in 0.0f..1.0f &&
               right in 0.0f..1.0f && bottom in 0.0f..1.0f
    }

    /**
     * Ancho relativo de la SubROI (0.0 - 1.0)
     */
    val width: Float get() = right - left

    /**
     * Alto relativo de la SubROI (0.0 - 1.0)
     */
    val height: Float get() = bottom - top
}

/**
 * Extensión para verificar si un ROI está definido (no es el valor UNDEFINED).
 * Usa isValid() para consistencia.
 */
fun Roi.isDefined(): Boolean = isValid() && this != Roi.UNDEFINED