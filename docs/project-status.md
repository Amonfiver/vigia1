# VIGIA1 - Estado actual del proyecto

## Resumen ejecutivo

VIGIA1 es una app Android en Kotlin llamada VIGIA orientada a monitorización visual con smartphone en entorno industrial.

El MVP actual tiene:
- preview de cámara, vigilancia activa/detenida, ROI manual
- **alertas automáticas a Telegram cuando se detecta cambio relevante**
- mensaje "Minda Requiere Atención" + imagen automáticos
- protección anti-spam básica (cooldown 60 segundos)
- **confirmación diferida a los 3 minutos con imagen nueva**
- **estabilización de detección: requiere cambios consecutivos antes de alertar**
- **infraestructura de build completa (Gradle Wrapper 8.5 estable)**
- feedback visual del estado de alertas y confirmaciones en la UI
- captura y envío manual de imagen como respaldo
- **orientación forzada a landscape para uso industrial**
- **selector de ROI reescrito basado en patrón probado de MindaVigilante**
- **ANÁLISIS CROMÁTICO HSV: migración completada de grayscale a detección por color**
- **subregión activa dentro del ROI global (60% centro con sesgo vertical)**
- **base preparada para detección de naranja (obstáculo) y rojo (fallo)**

---

## Estado actual

### Fase ACTIVA: Refactorización a análisis cromático

**Fecha de inicio**: 2026-03-25

Se ha completado la refactorización de la base de análisis visual para que deje de depender de grayscale como señal principal y pase a usar información cromática.

#### Cambios implementados en esta iteración

| Cambio | Descripción | Estado |
|--------|-------------|--------|
| **ColorFrameData** | Nueva estructura con píxeles HSV | ✅ Implementado |
| **HSV vs RGB** | Decisión técnica: HSV separa tono de intensidad | ✅ Documentado |
| **FrameProcessor** | Genera dual: ColorFrameData + FrameData legacy | ✅ Implementado |
| **ColorBasedDetector** | Detector nuevo basado en color, no luminancia | ✅ Implementado |
| **SubRegion** | Heurística espacial: 60% centro con sesgo 0.4 | ✅ Implementado |
| **MonitoringManager** | Soporte dual: análisis cromático + legacy | ✅ Implementado |
| **Integración UI** | CameraPreview, MainActivity, ViewModel actualizados | ✅ Implementado |
| **Compilación** | Build exitoso con warnings menores (deprecated) | ✅ Verificado |

#### Representación de color elegida: HSV

**¿Por qué HSV en lugar de RGB?**
- **Separación clara**: HSV separa el tono (color) de la saturación e intensidad
- **Robustez**: El canal H (Hue) es más invariante a cambios de iluminación que RGB
- **Detección de naranja/rojo**: En HSV es directo (rangos de hue), en RGB es complejo
- **Filtro de grises**: La saturación permite descartar fácilmente blancos/negros/grises

**Rangos HSV usados:**
- Naranja: Hue 15-35 (aprox 20°-50°)
- Rojo: Hue 0-15 o 230-255 (aprox 0°-20° o 320°-360°)
- Saturación mínima: 60 (evita grises)

#### Heurística espacial (subregión activa)

**Problema**: El ROI global cubre toda la vía incluyendo:
- Fondo blanco de la vía
- Dos líneas negras paralelas
- Cuerpo del transfer (zona informativa real)

**Solución implementada**:
```
Subregión activa = 60% del área central del ROI
Sesgo vertical = 0.4 (ligeramente hacia arriba)
Descripción: "Centro 60% del ROI (cuerpo del transfer)"
```

**Coordenadas relativas al ROI**:
- left/right: 0.20 - 0.80 (recorta 20% de cada lado)
- top: 0.28 (sesgo hacia arriba)
- bottom: 0.72

#### Clasificación por señales cromáticas

**Señal A: Naranja** (umbral provisional 5%)
- Interpretación: candidato a obstáculo / atención inmediata
- Emoji: 🟠
- Implementado en `ColorStats.hasSignificantOrange()`

**Señal B: Rojo** (umbral provisional 5%)
- Interpretación: candidato a fallo confirmado
- Emoji: 🔴
- Implementado en `ColorStats.hasSignificantRed()`

**Nota**: La lógica temporal de persistencia (20 segundos) se afinará en iteración posterior.

#### Observabilidad implementada

**Crops de diagnóstico** (métodos en FrameProcessor):
- `getLastFrameBitmap()`: Frame completo en color
- `getRegionCrop(left, top, right, bottom)`: Crop arbitrario
- `getRoiCrop(roi)`: Crop del ROI global

**Uso**: Permite verificar visualmente qué región analiza el sistema y qué colores detecta.

---

### Fase PREVIA CERRADA: Corrección de bugs críticos

**Fecha de cierre**: 2026-03-24

Bugs corregidos:
- Crash al guardar Telegram (companion object lazy)
- Crash potencial en ROI (UNDEFINED lazy)
- Orientación forzada landscape
- ROI no quedaba fijado al soltar (reescritura completa basada en MindaRoiOverlayView)

---

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- **El análisis usa información cromática HSV, no grayscale como señal principal**
- **Dentro del ROI global, se analiza una subregión activa (60% centro)**
- **Naranja = obstáculo/atención, Rojo = fallo confirmado** (umbrales provisionales)
- El selector de ROI usa el patrón probado de MindaVigilante.
- Telegram se configura manualmente y se prueba antes de usar.
- Las alertas automáticas tienen cooldown de 60 segundos.
- Tras alerta exitosa, se programa confirmación a 3 minutos.
- Un cambio debe mantenerse durante 1-1.5 segundos (3 detecciones) para ser válido.

## Archivos nuevos/modificados en esta iteración

### Nuevos archivos
- `detection/ColorFrameData.kt` - Estructura de datos cromáticos HSV
- `detection/ColorBasedDetector.kt` - Detector basado en color

### Archivos modificados
- `camera/FrameProcessor.kt` - Generación dual HSV + luminancia
- `monitoring/MonitoringManager.kt` - Soporte cromático + subregión activa
- `camera/CameraPreview.kt` - Callback onCameraReadyColor
- `ui/MainViewModel.kt` - Conexión de flujos cromáticos
- `MainActivity.kt` - Integración UI

## Riesgos y puntos de atención

- **Migración en progreso**: FrameData legacy sigue disponible para compatibilidad
- **Umbrales provisionales**: 5% de píxeles para naranja/rojo pueden necesitar ajuste
- **Sin lógica temporal compleja**: Persistencia de 20 segundos no implementada aún
- **Rendimiento**: Conversión YUV→RGB→HSV en cada frame (optimizable con LUT)
- falsos positivos parcialmente mitigados por estabilización
- dependencia de conexión a internet para Telegram

## Estado de infraestructura

- **Gradle Wrapper**: 8.5 (estable)
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Estado de build**: ✅ COMPILA CORRECTAMENTE
- **Warnings actuales**: 4 menores (deprecated durante transición)

## Siguiente paso técnico recomendado

**Opción A: Detección de naranja**
- Afinar umbrales con datos reales
- Implementar histéresis para evitar oscilaciones
- Añadir indicador visual de % naranja detectado

**Opción B: Detección de rojo persistente**
- Implementar lógica temporal (persistencia 20 segundos)
- Distinguir rojo temporal vs rojo sostenido
- Preparar para estado "fallo confirmado"

**Opción C: Mejora de observabilidad**
- Añadir UI de diagnóstico con crop en tiempo real
- Mostrar estadísticas HSV en vivo
- Visualizar subregión activa sobre el ROI

## Recordatorio de disciplina SDD

Antes de cambios grandes:
1. revisar spec y documentación viva
2. hacer commit checkpoint
3. lanzar tarea pequeña al agente
4. revisar respuesta y archivos tocados
5. actualizar documentación viva
6. hacer commit