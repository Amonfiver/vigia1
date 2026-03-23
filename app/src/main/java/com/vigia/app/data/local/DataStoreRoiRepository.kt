/**
 * Archivo: app/src/main/java/com/vigia/app/data/local/DataStoreRoiRepository.kt
 * Propósito: Implementación de RoiRepository usando DataStore para persistencia.
 * Responsabilidad principal: Guardar y recuperar ROI usando preferences DataStore.
 * Alcance: Capa de datos, implementación concreta del repositorio.
 *
 * Decisiones técnicas relevantes:
 * - DataStore Preferences para persistencia tipo clave-valor con corrutinas
 * - Serialización simple de ROI a 4 floats separados
 * - Context como dependencia para acceso a DataStore
 *
 * Limitaciones temporales del MVP:
 * - Sin migración desde SharedPreferences
 * - Sin caché en memoria (lectura directa de DataStore)
 *
 * Cambios recientes: Creación inicial de la implementación.
 */
package com.vigia.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        val LEFT = floatPreferencesKey("roi_left")
        val TOP = floatPreferencesKey("roi_top")
        val RIGHT = floatPreferencesKey("roi_right")
        val BOTTOM = floatPreferencesKey("roi_bottom")
    }

    override suspend fun saveRoi(roi: Roi) {
        context.roiDataStore.edit { preferences ->
            preferences[Keys.LEFT] = roi.left
            preferences[Keys.TOP] = roi.top
            preferences[Keys.RIGHT] = roi.right
            preferences[Keys.BOTTOM] = roi.bottom
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
                Roi(left, top, right, bottom)
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
        }
    }

    override suspend fun hasRoi(): Boolean {
        val preferences = context.roiDataStore.data.first()
        return preferences[Keys.LEFT] != null
    }
}