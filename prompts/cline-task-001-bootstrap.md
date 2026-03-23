Contexto:
Estamos creando VIGIA1, una app Android en Kotlin llamada VIGIA.
El MVP consiste en usar la cámara del smartphone para monitorizar una escena, analizar ROIs y, al detectar un cambio específico, enviar un mensaje y captura por Telegram, además de una segunda captura 3 minutos después.

Quiero que en esta iteración solo hagas el bootstrap inicial del proyecto, sin intentar implementar toda la lógica final.

Objetivo de esta tarea:
1. Crear la base del proyecto Android en Kotlin.
2. Usar Jetpack Compose para la UI.
3. Preparar permisos de cámara e internet.
4. Añadir dependencias iniciales necesarias para:
   - CameraX
   - OkHttp
   - navegación básica si hace falta
5. Crear estructura de paquetes limpia para:
   - ui
   - camera
   - monitoring
   - telegram
   - data
   - domain
   - utils
6. Crear una pantalla principal mínima con:
   - título VIGIA
   - texto de estado "Monitorización detenida"
   - botón "Iniciar vigilancia"
   - botón "Detener vigilancia"
   - placeholders para configuración de Telegram
7. Añadir cabecera documental breve al inicio de cada archivo creado o modificado.
8. No implementar aún:
   - detección real
   - envío real a Telegram
   - captura real
   - lógica avanzada de ROIs
9. Dejar TODO compilable y ordenado.

Al terminar:
- explica qué hiciste,
- lista archivos creados/modificados,
- indica siguientes pasos recomendados.