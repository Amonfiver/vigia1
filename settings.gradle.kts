/**
 * Archivo: settings.gradle.kts
 * Propósito: Configuración de settings de Gradle para VIGIA1.
 * Responsabilidad principal: Definir nombre del proyecto, repositorios y estructura de módulos.
 * Alcance: Configuración inicial de Gradle para el proyecto Android.
 * 
 * Decisiones técnicas relevantes:
 * - Repositorios pluginManagement para resolver plugins de Android y Kotlin
 * - dependencyResolutionManagement para repositorios de dependencias
 * 
 * Limitaciones temporales del MVP:
 * - Solo un módulo app, sin módulos adicionales de librería
 * 
 * Cambios recientes: Creación inicial del archivo de settings.
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Vigia1"
include(":app")