# Archivo: app/proguard-rules.pro
# Propósito: Reglas de ProGuard para el módulo app de VIGIA1.
# Responsabilidad principal: Configurar ofuscación y optimización del código.
# Alcance: Configuración de ProGuard para release builds.
#
# Decisiones técnicas relevantes:
# - Configuración mínima para MVP sin ofuscación agresiva
# - Conservación de clases de modelo de dominio
#
# Limitaciones temporales del MVP:
# - minifyEnabled está desactivado en build.gradle.kts
# - Este archivo es placeholder para futura activación
#
# Cambios recientes: Creación inicial del archivo.

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep model classes for serialization
-keep class com.vigia.app.domain.model.** { *; }