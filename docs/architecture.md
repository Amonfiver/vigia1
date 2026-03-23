# Arquitectura inicial - VIGIA1

## Objetivo de la arquitectura

La arquitectura de VIGIA1 debe ser simple, modular y fácil de ampliar. El MVP no necesita complejidad excesiva, pero sí una base ordenada para que futuras versiones puedan crecer sin rehacer todo.

## Módulos principales

### 1. UI principal
Responsable de mostrar:

- preview de cámara,
- estado de monitorización,
- botones de iniciar y detener,
- campos de configuración de Telegram,
- acceso a ajustes,
- modo de definición o redefinición del ROI.

La UI no debe contener lógica pesada de análisis ni de red.

### 2. Cámara / Preview
Responsable de:

- abrir la cámara,
- mostrar la vista previa,
- proporcionar frames al sistema de análisis,
- permitir capturar un frame completo cuando haga falta enviar evidencia.

Se recomienda usar CameraX.

### 3. Motor de monitorización
Responsable de coordinar el flujo de vigilancia:

- saber si la vigilancia está activa o detenida,
- decidir cuándo se analizan frames,
- recibir resultados del detector,
- disparar alertas cuando corresponda,
- evitar bucles de alertas descontroladas.

### 4. Gestión de ROIs
Responsable de:

- representar el ROI definido por el usuario,
- guardar y recuperar el ROI localmente,
- permitir redefinirlo,
- entregarlo al analizador para limitar la zona de observación.

En esta fase el ROI será rectangular y definido manualmente.

### 5. Detector de cambio
Responsable de:

- recibir datos visuales del ROI,
- aplicar la lógica inicial de detección,
- decidir si hay o no un evento relevante,
- devolver un resultado claro al motor de monitorización.

La lógica final podrá cambiar en futuras fases sin romper el resto del sistema.

### 6. Servicio Telegram
Responsable de:

- enviar mensajes de texto,
- enviar imágenes,
- manejar errores de red básicos,
- trabajar con bot token y chat_id configurados por el usuario.

### 7. Capturas
Responsable de:

- obtener la imagen completa del frame actual,
- preparar esa imagen para enviarla por Telegram,
- reutilizar la misma mecánica tanto en alerta inicial como en confirmación.

### 8. Temporizador de segunda confirmación
Responsable de:

- programar el segundo envío 3 minutos después de la alerta inicial,
- solicitar una nueva captura en ese momento,
- disparar el segundo envío sin bloquear la UI.

### 9. Almacenamiento de configuración
Responsable de guardar y recuperar:

- bot token,
- chat_id,
- ROI,
- estados o preferencias simples del MVP.

## Relación general entre módulos

1. La UI muestra la cámara y controles.
2. El usuario define y guarda el ROI.
3. El usuario activa la monitorización.
4. El módulo de cámara entrega frames.
5. El motor de monitorización solo analiza si está activo.
6. El detector revisa el ROI.
7. Si detecta evento, el motor pide captura.
8. El servicio Telegram envía mensaje e imagen.
9. El temporizador programa la segunda captura a los 3 minutos.

## Estructura orientativa de paquetes

```text
com.vigia.app
├─ ui/
├─ camera/
├─ monitoring/
├─ telegram/
├─ data/
├─ domain/
└─ utils/

## Principios importantes
no mezclar lógica de UI con lógica de negocio,
no meter Telegram dentro de la Activity principal,
mantener el detector separado del módulo de cámara,
tratar el ROI como dato persistente configurable,
preparar el sistema para futuras mejoras sin meterlas todavía.


# `docs/telegram-flow.md`

```md
# Flujo de Telegram - VIGIA1

## Objetivo

Definir claramente cómo se enviarán las alertas por Telegram en el MVP de VIGIA1.

## Datos necesarios

Para enviar mensajes mediante Telegram Bot API, el sistema necesita:

- `bot_token`
- `chat_id`

Estos datos serán configurados por el usuario y guardados localmente.

## Mensaje exacto del MVP

Cuando se detecte el evento relevante, el mensaje base será:

`Minda Requiere Atención`

## Orden de envío esperado

### Alerta inicial
1. Detectar evento en el ROI.
2. Capturar frame completo actual.
3. Enviar mensaje de texto:
   - `Minda Requiere Atención`
4. Enviar captura completa asociada al evento.

### Confirmación diferida
5. Programar una espera de 3 minutos.
6. Tomar una segunda captura completa.
7. Enviar segunda captura de confirmación.

## Consideraciones del flujo

- El mensaje de texto debe enviarse antes que la primera imagen.
- La primera imagen debe corresponder al momento del disparo.
- La segunda imagen debe tomarse de nuevo a los 3 minutos, no reutilizar la primera.
- La segunda captura se envía como confirmación visual del estado posterior.

## Manejo de errores

### Errores posibles
- bot token inválido
- chat_id inválido
- sin conexión a internet
- timeout de red
- error al subir imagen
- error al capturar frame

### Comportamiento esperado en MVP
- registrar el error en logs
- no crashear la app
- informar en UI si el envío de prueba falla
- permitir volver a intentar manualmente
- no bloquear toda la monitorización por un fallo puntual de red

## Reintentos básicos

En el MVP, los reintentos deben ser simples.

Propuesta inicial:
- 1 intento principal
- 1 reintento corto si falla por error temporal de red

No se implementará aún una cola compleja de reenvíos persistentes.

## Prueba manual recomendada

Debe existir una acción de prueba para verificar que:

- el bot token es válido,
- el chat_id es correcto,
- el mensaje llega al Telegram esperado.

## Nota sobre destino

El `chat_id` puede corresponder al número o chat del usuario según cómo esté configurado el bot y el chat en Telegram, pero a nivel técnico la app trabajará con el `chat_id` exacto proporcionado por el usuario.