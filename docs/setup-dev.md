# Configuración de desarrollo - VIGIA1

## Objetivo

Dejar preparados los pasos mínimos para que cualquier desarrollador o agente pueda abrir, ejecutar y continuar el proyecto VIGIA1 con el menor número de dudas posible.

## Herramientas recomendadas

- Android Studio
- VSCode
- Git
- GitHub
- JDK 17
- Android SDK
- dispositivo Android físico para pruebas reales

## Lenguaje y stack

- Kotlin
- Jetpack Compose
- CameraX
- OkHttp

## Requisitos de entorno

### JDK
Se recomienda usar:

- JDK 17

### Android Studio
Usar una versión moderna compatible con:

- Kotlin actual
- Jetpack Compose
- CameraX

### SDK mínimo sugerido
Propuesta inicial:

- `minSdk = 26` o el valor que se confirme al crear el proyecto
- `targetSdk` actualizado según versión estable del entorno

## Permisos necesarios

La app necesitará como mínimo:

- permiso de cámara
- permiso de internet

Más adelante puede requerirse revisar permisos adicionales si se endurece la estrategia de ejecución prolongada.

## Cómo arrancar el proyecto

1. Abrir la carpeta del proyecto en Android Studio.
2. Sincronizar Gradle.
3. Revisar que el emulador o dispositivo esté disponible.
4. Ejecutar la app.
5. Conceder permisos de cámara al iniciar.

## Recomendación para pruebas

Aunque puede arrancarse en emulador, este proyecto se beneficia mucho más de pruebas en smartphone real porque:

- usa cámara,
- requiere estabilidad visual real,
- permitirá comprobar mejor el flujo de Telegram y de monitorización.

## Cómo ejecutar en móvil físico

1. Activar opciones de desarrollador en Android.
2. Activar depuración USB.
3. Conectar el móvil al equipo.
4. Autorizar el ordenador en el teléfono.
5. Ejecutar la app desde Android Studio seleccionando el dispositivo.

## Cómo conectar Telegram

Para usar Telegram Bot API hacen falta:

- un bot token
- un chat_id válido

Estos datos se introducirán en la app y se guardarán localmente.

Flujo recomendado:
1. Crear o usar un bot de Telegram.
2. Obtener su token.
3. Obtener el chat_id del destino.
4. Introducir ambos datos en la pantalla de configuración de VIGIA.
5. Usar una acción de prueba para verificar el envío.

## Estructura documental recomendada

Antes de implementar cambios, revisar:

- `README.md`
- `docs/vision.md`
- `docs/mvp-scope.md`
- `docs/architecture.md`
- `docs/telegram-flow.md`
- `docs/roi-strategy.md`
- `docs/sdd/`

## Forma de trabajo recomendada

1. Leer spec y contexto.
2. Hacer cambios pequeños.
3. Probar.
4. Revisar lo tocado.
5. Hacer commit checkpoint.
6. Continuar con la siguiente iteración.

## Nota importante

No implementar todavía complejidad innecesaria.

VIGIA1 debe crecer desde una base funcional mínima:
- cámara,
- ROI manual guardado,
- monitorización,
- Telegram,
- segunda confirmación.