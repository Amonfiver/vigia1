/**
 * Archivo: build.gradle.kts (project level)
 * Propósito: Configuración de nivel de proyecto para VIGIA1.
 * Responsabilidad principal: Definir plugins y repositorios globales del proyecto.
 * Alcance: Configuración base del proyecto Android completo.
 * 
 * Decisiones técnicas relevantes:
 * - Usar plugins de Android Gradle Plugin 8.2.x
 * - Repositorios Google y Maven Central para dependencias
 * 
 * Limitaciones temporales del MVP:
 * - Configuración mínima sin plugins adicionales de análisis o métricas
 * 
 * Cambios recientes: Creación inicial del archivo de build del proyecto.
 */

// Top-level build file
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}