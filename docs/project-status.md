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
- **CORREGIDO: Pipeline de captura en color sin artefactos verde/magenta**
- **BASELINE MANUAL OK: flujo para capturar 5-10 muestras de estado OK del transfer**
- **EVIDENCIAS VISUALES SEPARADAS: referencia actual, último evento, última confirmación**
- **MODO ENTRENAMIENTO SUPERVISADO: captura de muestras etiquetadas OK/OBSTACULO/FALLO**

---

## Estado actual

### Fase ACTIVA: Modo de entrenamiento supervisado implementado

**Fecha de inicio**: 2026-03-25

Se ha implementado un **modo de entrenamiento supervisado manual** para capturar y almacenar muestras etiquetadas del transfer. Esto permite construir un dataset local que servirá de base para clasificación en iteraciones futuras.

#### Dataset de entrenamiento implementado

**Tres clases de referencia**:

1. **`OK`** - Estado normal / sano / sin incidencia
   - Color: Verde (#4CAF50)
   - Icono: ✓

2. **`OBSTACULO`** - Presencia de señal visual naranja / atención inmediata
   - Color: Naranja (#FF9800)
   - Icono: ⚠️

3. **`FALLO`** - Presencia de señal visual roja / franjas rojas / estado anómalo
   - Color: Rojo (#F44336)
   - Icono: 🚨

**Estructura de almacenamiento**:
- Directorio base: `filesDir/training/`
- Subdirectorios por clase: `OK/`, `OBSTACULO/`, `FALLO/`
- Formato: JPEG para imagen, `.meta` para metadatos (texto simple)
- Límite: 50 muestras por clase (para no saturar almacenamiento)
- Mínimo recomendado: 5 muestras por clase para entrenamiento básico

**Flujo de uso**:
1. Usuario define el ROI del transfer
2. Entra en modo "🎓 Modo Entrenamiento" (desde vista normal)
3. Selecciona la clase a capturar (OK, OBSTACULO, FALLO)
4. Captura 5-10+ muestras de esa clase
5. Cambia de clase y repite el proceso
6. Visualiza contadores por clase en tiempo real
7. Puede reiniciar clase específica o todo el dataset

**UI de entrenamiento**:
- Selector de clase con botones visuales (color + emoji)
- Contadores por clase (X/50 muestras)
- Botón de captura con feedback de progreso
- Mensajes de éxito/error temporales (3 segundos)
- Botones de gestión: reiniciar clase, reiniciar todo, volver

**Componentes creados**:
- `domain/model/TrainingSample.kt`: Modelo de datos con ClassLabel enum
- `domain/repository/TrainingDatasetRepository.kt`: Interfaz de persistencia
- `data/local/FileTrainingDatasetRepository.kt`: Implementación con archivos
- `ui/MainViewModel.kt`: Lógica de captura, conteo, gestión
- `MainActivity.kt`: UI completa del modo entrenamiento

#### Nota sobre baseline OK previo
El baseline manual de estado OK (implementado en iteración anterior) sigue disponible pero está **marcado como legacy**. El nuevo modo de entrenamiento supervisado es el enfoque preferido para construir referencias visuales, ya que permite:
- Múltiples clases (no solo "OK")
- Mayor cantidad de muestras por clase (50 vs 10)
- Organización por carpetas más clara
- Preparación directa para clasificación supervisada

---

### Fases PREVIAS CERRADAS

- Corrección pipeline cromático (cerrada)
- Baseline manual OK (cerrada - legacy)
- Evidencias visuales separadas (cerrada)

---

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- **El análisis usa información cromática HSV, no grayscale como señal principal**
- **Dentro del ROI global, se analiza una subregión activa (60% centro)**
- **Naranja = obstáculo/atención, Rojo = fallo confirmado** (umbrales provisionales)
- **Las capturas de evidencia ahora usan pipeline de color corregido (sin artefactos)**
- **Modo entrenamiento supervisado disponible para construir dataset etiquetado**
- El selector de ROI usa el patrón probado de MindaVigilante.
- Telegram se configura manualmente y se prueba antes de usar.
- Las alertas automáticas tienen cooldown de 60 segundos.
- Tras alerta exitosa, se programa confirmación a 3 minutos.
- Un cambio debe mantenerse durante 1-1.5 segundos (3 detecciones) para ser válido.

## Archivos nuevos/modificados en esta iteración

### Nuevos archivos
- `domain/model/TrainingSample.kt` - Modelo de muestras de entrenamiento (3 clases)
- `domain/repository/TrainingDatasetRepository.kt` - Interfaz de persistencia dataset
- `data/local/FileTrainingDatasetRepository.kt` - Implementación persistencia por carpetas

### Archivos modificados
- `ui/MainViewModel.kt` - Añadido modo entrenamiento completo
- `MainActivity.kt` - UI de entrenamiento con 3 clases, contadores, gestión
- `ui/ScreenMode.kt` (implícito) - Añadido modo TRAINING

## Riesgos y puntos de atención

- **Dataset de entrenamiento**: Solo almacena, sin clasificación automática todavía
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
- **Warnings actuales**: 3 menores (deprecated durante transición, parámetro no usado)

## Fase ACTIVA: Clasificación automática basada en dataset

**Estado**: ✅ IMPLEMENTADA - Primera versión funcional

Se ha implementado una primera versión de clasificación automática basada en el dataset etiquetado:

### Representación de features elegida
- **Histograma HSV**: 16 bins para el canal H (Hue), normalizado
- **Estadísticas S/V**: Media y desviación estándar de Saturación y Value
- **Features cromáticas**: Porcentaje de píxeles naranja, rojo y con color
- **Vector de features**: Completamente explicable y comparable

### Algoritmo de comparación
- **k-NN (k=3)**: Vecinos más cercanos con votación ponderada por similitud
- **Distancia ponderada**: Combina distancia de histograma (chi-cuadrado), estadísticas S/V y porcentajes cromáticos
- **Similitud**: Transformación exponencial de la distancia (0.0-1.0)
- **Confianza**: Basada en diferencia entre score de clase ganadora y segunda clase

### Archivos creados/modificados
- **Nuevos**:
  - `classification/ColorFeatures.kt`: Modelo de features HSV
  - `classification/DatasetClassifier.kt`: Clasificador k-NN
- **Modificados**:
  - `monitoring/MonitoringManager.kt`: Integración de clasificación en análisis
  - `ui/MainViewModel.kt`: Exposición de estado de clasificación

### Integración en flujo actual
- Clasificación se ejecuta en cada análisis periódico (500ms) si hay dataset
- Resultado disponible vía StateFlow para observación en UI
- Dataset se sincroniza automáticamente desde TrainingDatasetRepository

### Observabilidad en UI (pendiente de integración visual)
- Clase estimada actual: `classificationResult.predictedClass`
- Score/confianza: `classificationResult.confidence`
- Muestras por clase usadas: `classificationResult.samplesUsed`
- Top matches: `classificationResult.topMatches`
- Features del frame actual: `classificationResult.featuresSummary`
- Estadísticas del dataset: `datasetStats.summary()`

### Limitaciones temporales de esta iteración
- Sin UI visual de clasificación (datos disponibles en estado)
- Comparación fuerza bruta O(n) - escalable hasta ~150 muestras
- Sin ajuste dinámico de pesos de features
- Sin persistencia de resultados de clasificación

## Siguiente paso técnico recomendado

**Opción A (SIGUIENTE): UI de clasificación en tiempo real**
- Mostrar clase estimada actual con color (verde/naranja/rojo)
- Barra de confianza visual
- Indicador de muestras por clase disponibles
- Botón para forzar sincronización dataset → clasificador

**Opción B: Mejoras de clasificación**
- Ajustar pesos de features con datos reales
- Implementar caché de features de muestras (evitar re-decodificar)
- Añadir umbral mínimo de confianza para predicción válida

**Opción C: Reglas de alerta basadas en clasificación**
- Sustituir alertas basadas solo en umbrales por alertas basadas en clase detectada
- Política: OBSTACULO → alerta temprana, FALLO → alerta crítica
- Historial de clasificaciones para detectar patrones

## Recordatorio de disciplina SDD

Antes de cambios grandes:
1. revisar spec y documentación viva
2. hacer commit checkpoint
3. lanzar tarea pequeña al agente
4. revisar respuesta y archivos tocados
5. actualizar documentación viva
6. hacer commit