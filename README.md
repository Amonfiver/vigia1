# VIGIA1

VIGIA1 es el nuevo proyecto base de VIGIA, una app Android desarrollada en Kotlin para usar un smartphone como sistema de vigilancia visual continua en entorno industrial.

La app utiliza la cámara del teléfono para observar una escena en tiempo real. Cuando la monitorización está activa, analiza una o varias zonas de interés (ROIs) definidas manualmente por el usuario/desarrollador. Si detecta un cambio visual específico dentro de esas zonas, envía una alerta a Telegram con un mensaje y una captura de la imagen. Tres minutos después, envía una segunda captura de confirmación.

## Objetivo del MVP

Construir una primera versión funcional que permita:

- abrir la cámara del smartphone,
- activar o detener la monitorización manualmente,
- definir manualmente un ROI y guardarlo,
- analizar ese ROI en tiempo real,
- detectar un evento visual específico con lógica inicial simple,
- enviar por Telegram:
  - el mensaje `Minda Requiere Atención`,
  - una captura completa,
  - y una segunda captura de confirmación a los 3 minutos.

## Filosofía del proyecto

VIGIA1 nace con un enfoque realista y controlado. Se prioriza:

- fiabilidad antes que automatismos complejos,
- selección manual del ROI en lugar de detección automática temprana,
- arquitectura modular,
- iteraciones pequeñas en modo SDD,
- base sólida antes de añadir inteligencia avanzada.

## Stack inicial previsto

- Kotlin
- Android Studio
- Jetpack Compose
- CameraX
- OkHttp
- SharedPreferences o DataStore para ajustes locales
- Git + GitHub para control de versiones

## Estado actual

Fase inicial de arranque.

En esta etapa se está definiendo:

- la spec del MVP,
- la arquitectura base,
- el flujo de trabajo SDD,
- los prompts para Cline,
- y la estructura del proyecto.

## Cómo arrancar el proyecto

1. Abrir la carpeta `Vigia1` en Android Studio o VSCode.
2. Revisar la documentación dentro de `docs/`.
3. Seguir el flujo SDD descrito en `docs/sdd/`.
4. Inicializar o revisar Git.
5. Ejecutar las tareas paso a paso con Cline usando los prompts de `prompts/`.

## Flujo de trabajo acordado

1. Definir cambios en documentación/spec.
2. Hacer commit checkpoint.
3. Dar una tarea pequeña y concreta a Cline.
4. Revisar qué hizo y qué archivos tocó.
5. Si está correcto, hacer commit.
6. Si falla, rollback y rehacer.

## Próximas fases

1. Bootstrap del proyecto Android.
2. Preview de cámara.
3. Activar/desactivar monitorización.
4. Definición manual y guardado del ROI.
5. Configuración de Telegram.
6. Detección inicial sobre ROI.
7. Captura y alerta por Telegram.
8. Segunda captura a los 3 minutos.
9. Mejoras de estabilidad y reducción de falsos positivos.

## Nota importante sobre ROI

En VIGIA1 el ROI no se detecta automáticamente.

El ROI será definido manualmente por el usuario/desarrollador sobre la imagen de cámara, guardado localmente y reutilizado en monitorización hasta que se cambie desde una opción de ajustes o redefinición.