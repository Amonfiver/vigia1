# VIGIA1 - Estado actual del proyecto

## Resumen ejecutivo

VIGIA1 es una app Android en Kotlin llamada VIGIA orientada a monitorización visual con smartphone en entorno industrial.

El MVP actual busca:
- mostrar preview de cámara,
- permitir activar y detener vigilancia,
- permitir definir manualmente un ROI,
- guardar ese ROI localmente,
- analizar visualmente ese ROI con frames reales de cámara,
- y más adelante enviar alertas por Telegram con captura y segunda confirmación a los 3 minutos.

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
- **frames reales de CameraX conectados al detector**
- análisis de luminancia real del ROI
- estado visual en UI del resultado de detección

### Implementado de forma provisional
- detector básico desacoplado (interfaz RoiDetector)
- algoritmo de detección simple basado en diferencia de luminancia
- conversión YUV a luminancia sin optimizaciones avanzadas
- análisis a 320x240 para rendimiento (downscaling)
- sin compensación de iluminación ni normalización avanzada

### Aún NO implementado o pendiente
- sensibilidad afinada / reducción de falsos positivos
- configuración real y prueba de Telegram
- captura real para alertas
- envío de alerta con mensaje e imagen
- segunda captura 3 minutos después
- ajustes dedicados para redefinir/borrar ROI
- eliminación de ROI guardado
- múltiples ROIs

## Fase actual del MVP

Fase actual: análisis visual real del ROI con frames de cámara funcionando.

La detección ya no usa datos simulados. Ahora procesa luminancia real del ROI usando CameraX ImageAnalysis.

## Siguiente objetivo recomendado

Implementar la configuración y prueba de Telegram, seguido de la captura y envío de alertas cuando se detecte un cambio.

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- El ROI puede dibujarse y reposicionarse antes de confirmar.
- El ROI se guarda localmente y se recupera en futuras sesiones.
- El análisis usa frames reales de CameraX (ImageAnalysis).
- La arquitectura debe mantenerse modular y simple.
- Cada iteración debe ser pequeña, compilable y documentada.

## Riesgos y puntos de atención

- falsos positivos por iluminación o movimiento de cámara (mitigado parcialmente con ROI manual)
- sobrecargar demasiado pronto la arquitectura
- mezclar detección, UI y cámara en una sola capa (controlado por separación actual)
- consumo de batería por análisis continuo de frames

## Recordatorio de disciplina SDD

Antes de cambios grandes:
1. revisar spec y documentación viva
2. hacer commit checkpoint
3. lanzar tarea pequeña al agente
4. revisar respuesta y archivos tocados
5. actualizar documentación viva
6. hacer commit