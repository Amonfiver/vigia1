/**
 * Archivo: app/src/main/java/com/vigia/app/data/local/DataStoreRoiRepository.kt
 * Propósito: Implementación de RoiRepository usando DataStore para persistencia.
 * Responsabilidad principal: Guardar y recuperar ROI y SubROI usando preferences DataStore.
 * Alcance: Capa de datos, implementación concreta del repositorio.
 *
 * Decisiones técnicas relevantes:
 * - DataStore Preferences para persistencia tipo clave-valor con corrutinas
 * - Serialización simple de ROI a 4 floats separados
 * - SubROI almacenada como 4 floats adicionales (relativos al ROI)
 * - Context como dependencia para acceso a DataStore
 *
 * Limitaciones temporales del MVP:
 * - Sin migración desde SharedPreferences
 * - Sin caché en memoria (lectura directa de DataStore)
 * - SubROI opcional: si no existe, se devuelve null
 *
 * Cambios recientes:
 * - AÑADIDO: Soporte para persistencia de SubROI manual del transfer
 * - 8 claves en total: 4 para ROI global, 4 opcionales para SubROI
 * - Método hasSubRoi() para verificar si existe SubROI guardada
 */
package com.vigia.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vigia.app.domain.model.RelativeSubRoi
import com.vigia.app.domain.model.Roi
import com.vigia.app.domain.repository.RoiRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.roiDataStore: DataStore<Preferences> by preferencesDataStore(name = "roi_preferences")

/**
 * Implementación de [RoiRepository] usando DataStore.
 *
 * @param context Contexto de aplicación para acceder a DataStore
 */
class DataStoreRoiRepository(private val context: Context) : RoiRepository {

    private object Keys {
        // ROI Global
        val LEFT = floatPreferencesKey("roi_left")
        val TOP = floatPreferencesKey("roi_top")
        val RIGHT = floatPreferencesKey("roi_right")
        val BOTTOM = floatPreferencesKey("roi_bottom")
        
        // SubROI (relativa al ROI)
        val HAS_SUB_ROI = booleanPreferencesKey("roi_has_sub_roi")
        val SUB_LEFT = floatPreferencesKey("roi_sub_left")
        val SUB_TOP = floatPreferencesKey("roi_sub_top")
        val SUB_RIGHT = floatPreferencesKey("roi_sub_right")
        val SUB_BOTTOM = floatPreferencesKey("roi_sub_bottom")
    }

    override suspend fun saveRoi(roi: Roi) {
        context.roiDataStore.edit { preferences ->
            // Guardar ROI global
            preferences[Keys.LEFT] = roi.left
            preferences[Keys.TOP] = roi.top
            preferences[Keys.RIGHT] = roi.right
            preferences[Keys.BOTTOM] = roi.bottom
            
            // Guardar SubROI si existe
            val subRoi = roi.relativeSubRoi
            if (subRoi != null && subRoi.isValid()) {
                preferences[Keys.HAS_SUB_ROI] = true
                preferences[Keys.SUB_LEFT] = subRoi.left
                preferences[Keys.SUB_TOP] = subRoi.top
                preferences[Keys.SUB_RIGHT] = subRoi.right
                preferences[Keys.SUB_BOTTOM] = subRoi.bottom
            } else {
                preferences[Keys.HAS_SUB_ROI] = false
                preferences.remove(Keys.SUB_LEFT)
                preferences.remove(Keys.SUB_TOP)
                preferences.remove(Keys.SUB_RIGHT)
                preferences.remove(Keys.SUB_BOTTOM)
            }
        }
    }

    override suspend fun getRoi(): Roi? {
        val preferences = context.roiDataStore.data.first()
        val left = preferences[Keys.LEFT]
        val top = preferences[Keys.TOP]
        val right = preferences[Keys.RIGHT]
        val bottom = preferences[Keys.BOTTOM]

        return if (left != null && top != null && right != null && bottom != null) {
            try {
                // Recuperar SubROI si existe
                val hasSubRoi = preferences[Keys.HAS_SUB_ROI] ?: false
                val relativeSubRoi = if (hasSubRoi) {
                    val subLeft = preferences[Keys.SUB_LEFT]
                    val subTop = preferences[Keys.SUB_TOP]
                    val subRight = preferences[Keys.SUB_RIGHT]
                    val subBottom = preferences[Keys.SUB_BOTTOM]
                    
                    if (subLeft != null && subTop != null && subRight != null && subBottom != null) {
                        try {
                            RelativeSubRoi(subLeft, subTop, subRight, subBottom)
                        } catch (e: IllegalArgumentException) {
                            // Datos de SubROI corruptos
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
                
                Roi(left, top, right, bottom, relativeSubRoi)
            } catch (e: IllegalArgumentException) {
                // Datos corruptos o inválidos
                null
            }
        } else {
            null
        }
    }

    override suspend fun clearRoi() {
        context.roiDataStore.edit { preferences ->
            preferences.remove(Keys.LEFT)
            preferences.remove(Keys.TOP)
            preferences.remove(Keys.RIGHT)
            preferences.remove(Keys.BOTTOM)
            preferences.remove(Keys.HAS_SUB_ROI)
            preferences.remove(Keys.SUB_LEFT)
            preferences.remove(Keys.SUB_TOP)
            preferences.remove(Keys.SUB_RIGHT)
            preferences.remove(Keys.SUB_BOTTOM)
        }
    }

    override suspend fun hasRoi(): Boolean {
        val preferences = context.roiDataStore.data.first()
        return preferences[Keys.LEFT] != null
    }
    
    /**
     * Verifica si existe una SubROI guardada.
     */
    suspend fun hasSubRoi(): Boolean {
        val preferences = context.roiDataStore.data.first()
        return preferences[Keys.HAS_SUB_ROI] == true
    }
}