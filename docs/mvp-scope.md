# Alcance del MVP - VIGIA1

## Incluido en el MVP

### Base de aplicación
- App Android nativa en Kotlin
- Interfaz simple y funcional
- Uso de cámara del smartphone
- Permisos de cámara e internet

### Monitorización
- Vista previa de cámara
- Activación manual de monitorización
- Desactivación manual de monitorización
- Estado visual claro de si la vigilancia está activa o detenida

### Gestión de ROI
- Definición manual del ROI sobre la imagen
- ROI rectangular en esta primera fase
- Guardado local del ROI definido
- Reutilización del ROI guardado en futuras sesiones
- Opción de redefinir el ROI desde ajustes o modo de edición

### Detección
- Análisis visual del ROI
- Lógica inicial de detección de cambio específico
- Base preparada para refinar la detección más adelante
- Control para evitar alertas continuas descontroladas

### Telegram
- Configuración de bot token
- Configuración de chat_id o destino
- Envío de mensaje de alerta
- Envío de captura completa al detectar evento
- Envío de segunda captura de confirmación a los 3 minutos

### Persistencia y ajustes
- Guardado local de configuración básica
- Guardado local del ROI
- Posibilidad de volver a definir el ROI cuando el usuario lo necesite

## No incluido todavía

### Inteligencia avanzada
- Entrenamiento avanzado por plantillas
- Aprendizaje automático completo
- Clasificación sofisticada de múltiples estados
- Detección automática de ROI

### Complejidad industrial ampliada
- Multi-transfer complejo si aún no está bien definido
- Múltiples perfiles de vigilancia avanzados
- Varias escenas complejas con lógica distinta
- Gestión avanzada de zonas y subzonas

### Infraestructura extra
- Backend externo
- Panel web
- Cuenta multiusuario
- Histórico remoto de eventos
- Sincronización en la nube

### Otras mejoras futuras
- Ajuste fino de sensibilidad desde UI
- Servicio foreground endurecido
- Optimización avanzada de batería
- Reintentos avanzados y cola robusta de envíos

## Resumen operativo del MVP

El MVP debe ser capaz de hacer esto de principio a fin:

1. El usuario abre la app.
2. Ve la cámara.
3. Define manualmente un ROI y lo guarda.
4. Configura Telegram.
5. Activa la vigilancia.
6. La app analiza ese ROI.
7. Si detecta el cambio relevante:
   - envía `Minda Requiere Atención`,
   - envía una captura completa,
   - y 3 minutos después envía una segunda captura.