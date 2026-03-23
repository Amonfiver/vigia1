# VIGIA1 - Tareas iniciales

## Fase 0 - Bootstrap
- Crear proyecto Android en Kotlin
- Configurar Gradle
- Añadir permisos de internet y cámara
- Crear estructura base de paquetes
- Crear README y docs

## Fase 1 - UI mínima
- Pantalla principal
- Preview de cámara
- Botón iniciar vigilancia
- Botón detener vigilancia
- Estado visual actual

## Fase 2 - ROI manual
- Crear modo de definición manual de ROI
- Permitir marcar ROI rectangular sobre preview
- Guardar ROI localmente
- Recuperar ROI guardado
- Añadir opción para redefinir ROI desde ajustes

## Fase 3 - Configuración Telegram
- Inputs para bot token y chat id
- Guardado local
- Validación básica
- Servicio de envío de mensaje de prueba

## Fase 4 - Captura y envío
- Capturar frame actual
- Enviar mensaje Telegram
- Enviar imagen Telegram
- Manejo de errores

## Fase 5 - Monitorización base
- Definir clase ROI
- Leer frame
- Aplicar análisis simple sobre ROI guardado
- Detectar cambio específico según regla inicial
- Disparar alerta una sola vez por evento

## Fase 6 - Confirmación diferida
- Programar segunda captura a los 3 minutos
- Enviar segunda captura por Telegram
- Añadir logs de trazabilidad