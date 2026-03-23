# VIGIA1 - Criterios de aceptación MVP

## CA-01
La app compila y abre en Android sin crashear.

## CA-02
Se muestra preview de cámara correctamente tras conceder permisos.

## CA-03
El usuario puede iniciar y detener la vigilancia con un control visible.

## CA-04
El usuario puede configurar bot token y chat id de Telegram.

## CA-05
La app puede enviar un mensaje de prueba a Telegram.

## CA-06
Al detectar el evento definido, la app envía:
- mensaje "Minda Requiere Atención"
- una captura completa

## CA-07
Tres minutos después del evento, la app envía una segunda captura.

## CA-08
La app no entra en bucle de alertas continuas por un mismo evento.

## CA-09
La lógica está separada en módulos entendibles y ampliables.

## CA-10
Los logs permiten entender qué ha ocurrido en cada fase del flujo.