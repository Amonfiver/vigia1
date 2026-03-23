# VIGIA1 - Estado actual del proyecto

## Resumen ejecutivo

VIGIA1 es una app Android en Kotlin llamada VIGIA orientada a monitorización visual con smartphone en entorno industrial.

El MVP actual busca:
- mostrar preview de cámara,
- permitir activar y detener vigilancia,
- permitir definir manualmente un ROI,
- guardar ese ROI localmente,
- analizar visualmente ese ROI con frames reales de cámara,
- configurar y probar Telegram manualmente,
- **capturar y enviar imagen manual a Telegram,**
- y más adelante enviar alertas automáticas por Telegram con captura y segunda confirmación a los 3 minutos.

## Estado actual

### Ya implementado
- estructura base del proyecto Android
- UI principal con Jetpack Compose
- preview real de cámara con CameraX
- gestión básica de permisos de cámara
- estado de monitorización activa/detenida
- selección manual de ROI sobre la preview
- confirmación/cancelación de selección
- reposicionamiento del ROI mediante pulsación y arrastre
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
- **captura real de imagen desde FrameProcessor**
- **envío manual de imagen a Telegram con multipart/form-data**
- **UI de captura con estados (Capturando, Enviando, Éxito, Error)**

### Implementado de forma provisional
- detector básico desacoplado (interfaz RoiDetector)
- algoritmo de detección simple basado en diferencia de luminancia
- conversión YUV a luminancia sin optimizaciones avanzadas
- análisis a 320x240 para rendimiento (downscaling)
- sin compensación de iluminación ni normalización avanzada
- prueba de Telegram es manual (no automática al detectar cambio)
- **captura usa último frame disponible, no frame exclusivo**

### Aún NO implementado o pendiente
- sensibilidad afinada / reducción de falsos positivos
- envío automático a Telegram al detectar cambio
- **captura de imagen automática al detectar cambio**
- envío de mensaje "Minda Requiere Atención" automático
- segunda captura 3 minutos después
- ajustes dedicados para redefinir/borrar ROI
- eliminación de ROI guardado
- múltiples ROIs

## Fase actual del MVP

Fase actual: captura y envío manual de imagen a Telegram implementado.

La app puede detectar cambios en el ROI, y el usuario puede capturar y enviar manualmente imágenes a Telegram como evidencia visual.

## Siguiente objetivo recomendado

Implementar el **envío automático de alertas a Telegram cuando el detector identifique un cambio significativo**, incluyendo:
- mensaje "Minda Requiere Atención"
- captura automática de imagen del momento del cambio
- programación de segunda captura a los 3 minutos

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- El ROI puede dibujarse y reposicionarse antes de confirmar.
- El ROI se guarda localmente y se recupera en futuras sesiones.
- El análisis usa frames reales de CameraX (ImageAnalysis).
- Telegram se configura manualmente y se prueba antes de usar.
- La captura de imagen reutiliza el último frame procesado.
- La arquitectura debe mantenerse modular y simple.
- Cada iteración debe ser pequeña, compilable y documentada.

## Riesgos y puntos de atención

- falsos positivos por iluminación o movimiento de cámara (mitigado parcialmente con ROI manual)
- dependencia de conexión a internet para Telegram
- tokens de bot expuestos en preferencias locales (sin cifrado en MVP)
- captura puede no ser exactamente del momento del cambio (usa último frame)
- sobrecargar demasiado pronto la arquitectura
- mezclar detección, UI y cámara en una sola capa (controlado por separación actual)

## Recordatorio de disciplina SDD

Antes de cambios grandes:
1. revisar spec y documentación viva
2. hacer commit checkpoint
3. lanzar tarea pequeña al agente
4. revisar respuesta y archivos tocados
5. actualizar documentación viva
6. hacer commit