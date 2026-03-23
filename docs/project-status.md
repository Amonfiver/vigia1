# VIGIA1 - Estado actual del proyecto

## Resumen ejecutivo

VIGIA1 es una app Android en Kotlin llamada VIGIA orientada a monitorización visual con smartphone en entorno industrial.

El MVP actual tiene:
- preview de cámara, vigilancia activa/detenida, ROI manual
- **alertas automáticas a Telegram cuando se detecta cambio relevante**
- mensaje "Minda Requiere Atención" + imagen automáticos
- protección anti-spam básica (cooldown 60 segundos)
- feedback visual del estado de alertas en la UI
- captura y envío manual de imagen como respaldo

La segunda captura a los 3 minutos está pendiente para próxima iteración.

## Estado actual

### Ya implementado
- estructura base del proyecto Android
- UI principal con Jetpack Compose
- preview real de cámara con CameraX
- gestión básica de permisos de cámara
- estado de monitorización activa/detenida
- selección manual de ROI sobre la preview
- confirmación/cancelación de selección
- persistencia local del ROI (DataStore)
- recuperación automática del ROI al abrir la app
- visualización del ROI sobre la cámara
- capa de detección separada por módulos
- frames reales de CameraX conectados al detector
- análisis de luminancia real del ROI
- estado visual en UI del resultado de detección
- configuración funcional de Telegram (bot token + chat_id)
- persistencia de configuración Telegram
- prueba manual de envío de mensaje a Telegram con feedback visual
- captura real de imagen desde FrameProcessor
- envío manual de imagen a Telegram con multipart/form-data
- UI de captura con estados (Capturando, Enviando, Éxito, Error)
- **AlertManager: gestor de alertas automáticas**
- **Disparo automático cuando hasChange == true**
- **Envío automático: mensaje "Minda Requiere Atención" + imagen**
- **Protección anti-spam: cooldown de 60 segundos entre alertas**
- **UI de alertas automáticas con estados (Enviando, Éxito, Error, Cooldown)**

### Implementado de forma provisional
- detector básico desacoplado (interfaz RoiDetector)
- algoritmo de detección simple basado en diferencia de luminancia
- conversión YUV a luminancia sin optimizaciones avanzadas
- análisis a 320x240 para rendimiento (downscaling)
- sin compensación de iluminación ni normalización avanzada
- captura usa último frame disponible, no frame exclusivo
- cooldown fijo de 60 segundos (no configurable aún)
- sin cola de alertas pendientes (si falla, se pierde)

### Aún NO implementado o pendiente
- **segunda captura 3 minutos después** (próximo objetivo)
- sensibilidad afinada / reducción de falsos positivos
- ajustes dedicados para redefinir/borrar ROI
- eliminación de ROI guardado
- múltiples ROIs

## Fase actual del MVP

Fase actual: **alertas automáticas a Telegram implementadas**.

La app detecta cambios en el ROI y envía automáticamente mensaje + imagen a Telegram cuando `hasChange == true`. Tiene protección anti-spam de 60 segundos para evitar envíos continuos.

## Siguiente objetivo recomendado

Implementar la **segunda captura a los 3 minutos** después de la alerta inicial:
- programar captura diferida 3 minutos después de la primera alerta
- enviar segunda imagen como confirmación del estado
- mantener la arquitectura simple sin WorkManager si es posible
- reutilizar el sistema de captura existente

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- El ROI puede dibujarse y reposicionarse antes de confirmar.
- El ROI se guarda localmente y se recupera en futuras sesiones.
- El análisis usa frames reales de CameraX (ImageAnalysis).
- Telegram se configura manualmente y se prueba antes de usar.
- La captura de imagen reutiliza el último frame procesado.
- **Las alertas automáticas tienen cooldown de 60 segundos para evitar spam.**
- **La arquitectura mantiene separación: detección → monitorización → alerta → Telegram.**
- Cada iteración debe ser pequeña, compilable y documentada.

## Riesgos y puntos de atención

- falsos positivos por iluminación o movimiento de cámara (mitigado parcialmente con ROI manual)
- dependencia de conexión a internet para Telegram
- tokens de bot expuestos en preferencias locales (sin cifrado en MVP)
- captura puede no ser exactamente del momento del cambio (usa último frame)
- cooldown de 60s puede ser muy largo o muy corto según el caso de uso
- sin cola de alertas: si falla el envío, se pierde la alerta
- segunda captura a 3 minutos no implementada todavía

## Recordatorio de disciplina SDD

Antes de cambios grandes:
1. revisar spec y documentación viva
2. hacer commit checkpoint
3. lanzar tarea pequeña al agente
4. revisar respuesta y archivos tocados
5. actualizar documentación viva
6. hacer commit