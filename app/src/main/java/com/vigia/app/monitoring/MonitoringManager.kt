/**
 * Archivo: app/src/main/java/com/vigia/app/monitoring/MonitoringManager.kt
 * Propósito: Gestor del estado de monitorización de VIGIA1.
 * Responsabilidad principal: Coordinar el inicio y parada de la vigilancia.
 * Alcance: Capa de monitorización, control del estado de vigilancia.
 *
 * Decisiones técnicas relevantes:
 * - Singleton/Manager pattern para centralizar estado de vigilancia
 * - StateFlow para observabilidad reactiva del estado
 *
 * Limitaciones temporales del MVP:
 * - Solo gestión de estado, sin análisis de frames todavía
 * - Sin lógica de detección de eventos
 *
 * Cambios recientes: Creación inicial del manager.
 */
package com.vigia.app.monitoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor del estado de monitorización.
 * Controla si la vigilancia está activa o detenida.
 */
class MonitoringManager {

    private val _isMonitoring = MutableStateFlow(false)
    
    /**
     * Estado actual de la monitorización.
     */
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    /**
     * Inicia la monitorización.
     * En el MVP solo cambia el estado, sin activar análisis real.
     */
    fun startMonitoring() {
        _isMonitoring.value = true
        // TODO: Implementar inicio de análisis de frames en fase posterior
    }

    /**
     * Detiene la monitorización.
     */
    fun stopMonitoring() {
        _isMonitoring.value = false
        // TODO: Implementar parada de análisis en fase posterior
    }
}