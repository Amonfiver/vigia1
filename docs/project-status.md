# VIGIA1 - Estado actual del proyecto

## Resumen ejecutivo

VIGIA1 es una app Android en Kotlin llamada VIGIA orientada a monitorización visual con smartphone en entorno industrial.

El MVP actual busca:
- mostrar preview de cámara,
- permitir activar y detener vigilancia,
- permitir definir manualmente un ROI,
- guardar ese ROI localmente,
- analizar visualmente ese ROI,
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
- persistencia local del ROI
- recuperación automática del ROI al abrir la app
- visualización del ROI sobre la cámara
- capa provisional de detección separada por módulos
- estado visual en UI del resultado provisional de detección

### Implementado de forma provisional
- detector básico desacoplado
- flujo de análisis conectado a monitorización
- estado visual de cambio/no cambio

### Aún NO implementado o pendiente
- uso de frames reales de CameraX dentro del detector
- análisis real de píxeles del ROI
- sensibilidad afinada / reducción de falsos positivos
- configuración real y prueba de Telegram
- captura real para alertas
- envío de alerta con mensaje e imagen
- segunda captura 3 minutos después
- ajustes dedicados para redefinir/borrar ROI
- eliminación de ROI guardado
- múltiples ROIs

## Fase actual del MVP

Fase actual: transición entre ROI persistido y análisis real del ROI con frames de cámara.

## Siguiente objetivo recomendado

Conectar frames reales de CameraX al detector para sustituir la simulación actual y analizar de verdad el ROI guardado.

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- El ROI puede dibujarse y reposicionarse antes de confirmar.
- El ROI se guarda localmente y se recupera en futuras sesiones.
- La arquitectura debe mantenerse modular y simple.
- Cada iteración debe ser pequeña, compilable y documentada.

## Riesgos y puntos de atención

- falsos positivos por iluminación o movimiento de cámara
- sobrecargar demasiado pronto la arquitectura
- mezclar detección, UI y cámara en una sola capa
- dar por funcional una detección todavía simulada o provisional

## Recordatorio de disciplina SDD

Antes de cambios grandes:
1. revisar spec y documentación viva
2. hacer commit checkpoint
3. lanzar tarea pequeña al agente
4. revisar respuesta y archivos tocados
5. actualizar documentación viva
6. hacer commit