/**
 * Archivo: app/src/main/java/com/vigia/app/utils/PermissionsHelper.kt
 * Propósito: Utilidades para gestión de permisos de Android en VIGIA1.
 * Responsabilidad principal: Centralizar lógica de verificación y solicitud de permisos.
 * Alcance: Capa de utilidades, helpers para permisos.
 *
 * Decisiones técnicas relevantes:
 * - Funciones de extensión para Context para verificar permisos
 * - Centralización de constantes de permisos usados por la app
 *
 * Limitaciones temporales del MVP:
 * - Solo gestión de permiso de cámara
 * - Sin diálogos de explicación personalizados todavía
 *
 * Cambios recientes: Creación inicial del helper.
 */
package com.vigia.app.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Helper para gestión de permisos.
 */
object PermissionsHelper {

    /**
     * Verifica si el permiso de cámara está concedido.
     *
     * @param context Contexto de aplicación
     * @return true si el permiso está concedido
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}