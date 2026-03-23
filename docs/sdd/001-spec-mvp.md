# VIGIA1 - Spec MVP

## Nombre del producto
VIGIA

## Objetivo
Crear una app Android en Kotlin capaz de monitorizar la imagen de cámara en tiempo real, analizar ROIs definidas y enviar alertas por Telegram cuando detecte un cambio específico de interés.

## Problema que resuelve
Permitir vigilancia automática desde un smartphone sin requerir supervisión constante del usuario, notificando por Telegram cuando ocurre un evento visual relevante.

## Flujo principal
1. El usuario abre la app.
2. Concede permisos de cámara e internet.
3. Ve una preview de la cámara.
4. Configura Telegram si aún no está configurado.
5. Activa la vigilancia.
6. La app analiza continuamente los ROIs.
7. Si detecta el cambio específico:
   - captura imagen completa,
   - envía mensaje "Minda Requiere Atención",
   - envía captura por Telegram,
   - programa segunda captura a los 3 minutos.
8. El usuario puede detener la vigilancia manualmente.

## Alcance MVP
- Android app nativa en Kotlin
- Cámara activa con preview
- Activar/desactivar monitorización
- Análisis básico de ROIs
- Detección de evento visual específico
- Envío de texto + imagen por Telegram
- Segunda imagen a los 3 minutos

## Fuera del MVP
- IA avanzada
- entrenamiento por plantillas
- múltiples modos industriales complejos
- dashboard remoto
- multiusuario
- histórico completo de eventos
- calibración avanzada

## Requisitos funcionales
## Requisitos funcionales
- RF-01: mostrar preview de cámara
- RF-02: permitir activar y desactivar monitorización
- RF-03: permitir definir manualmente un ROI rectangular sobre la imagen
- RF-04: guardar localmente el ROI definido
- RF-05: permitir redefinir el ROI desde una opción de ajustes o edición
- RF-06: analizar el ROI guardado
- RF-07: detectar un cambio específico configurable en lógica
- RF-08: capturar frame completo al detectar evento
- RF-09: enviar texto por Telegram
- RF-10: enviar imagen por Telegram
- RF-11: programar y enviar segunda imagen 3 minutos después
- RF-12: evitar disparos continuos descontrolados del mismo evento
## Requisitos no funcionales
- RNF-01: código modular
- RNF-02: base preparada para crecer
- RNF-03: permisos claros y controlados
- RNF-04: logs de debug suficientes
- RNF-05: comportamiento estable si Telegram falla
- RNF-06: interfaz simple y entendible

## Riesgos iniciales
- consumo de batería
- cámara en uso prolongado
- falsos positivos en detección
- restricciones Android en procesos largos
- problemas de red al enviar Telegram

## Definición de éxito del MVP
La app instalada en un smartphone puede vigilar con cámara, detectar el evento esperado en ROI, mandar mensaje y captura a Telegram y mandar una segunda captura 3 minutos después.

## En esta primera versión, el sistema no necesita resolver todavía toda la inteligencia final del cambio específico. Solo debe quedar preparada la arquitectura para analizar ROIs y disparar un evento cuando una regla provisional indique un cambio relevante. La regla final podrá refinarse después.