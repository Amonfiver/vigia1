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
- **CLASIFICACIÓN AUTOMÁTICA BASADA EN DATASET: UI completa, caché de features, resincronización explícita**
- **SUBROI DEL TRANSFER: clasificación ahora usa solo el cuerpo del transfer, no toda la vía**
- **DETECTOR TransferSubRoiDetector: heurística de color para ignorar fondo de la vía**

---

## Estado actual

### Fase CERRADA: Clasificación refinada con subROI del transfer

**Fecha de cierre**: 2026-03-27

La clasificación automática ahora opera principalmente sobre la **subROI del transfer** en lugar del ROI completo de la vía, mejorando significativamente la discriminación entre estados.

#### Características implementadas en esta iteración

**1. Detector de subROI del transfer (TransferSubRoiDetector)** ✅
- Heurística basada en análisis de color para detectar el cuerpo del transfer
- Excluye píxeles blancos (fondo) y negros (líneas de la vía)
- Calcula bounding box de la región con contenido cromático significativo
- Fallback a heurística centrada (60%) si la detección por color falla
- Métodos: `COLOR_MASK` (preferido), `CENTERED_HEURISTIC` (fallback), `FULL_ROI` (último recurso)

**2. Clasificación usando solo subROI** ✅
- La extracción de features y clasificación operan sobre la subROI detectada
- Ya no compara contra el ROI completo (que incluía fondo estructural no informativo)
- Pipeline: Frame → Detectar subROI → Extraer sub-frame → Extraer features → Clasificar

**3. Observabilidad mejorada** ✅
- Estado `transferSubRoiResult` expone: subregión detectada, método usado, confianza, estadísticas
- Estado `topMatchInfo` expone: muestra entrenada más cercana (sampleId, label, similitud)
- Integración en MonitoringManager con flujos reactivos

**4. Compilación verificada** ✅
- Build exitoso sin errores
- Solo warnings menores (parámetros no usados, APIs deprecated)

### Estructura de la subROI

```
ROI global (definido por usuario)
├── Fondo blanco de la vía (excluido)
├── Líneas negras paralelas (excluidas)  
└── subROI del transfer (usado para clasificación)
    ├── Detectado por: color/estructura distinta
    └── Fallback: 60% centro del ROI
```

---

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- **El análisis usa información cromática HSV, no grayscale como señal principal**
- **Dentro del ROI global, se detecta automáticamente una subROI del transfer para clasificación**
- **La clasificación compara SOLO la subROI del transfer, no toda la vía**
- **Heurística de detección: excluir fondo blanco y líneas negras, quedarse con región colorida**
- **Naranja = obstáculo/atención, Rojo = fallo confirmado** (umbrales provisionales)
- **Las capturas de evidencia ahora usan pipeline de color corregido (sin artefactos)**
- **Modo entrenamiento supervisado disponible para construir dataset etiquetado**
- **Clasificación automática con caché de features y resincronización explícita**
- El selector de ROI usa el patrón probado de MindaVigilante.
- Telegram se configura manualmente y se prueba antes de usar.
- Las alertas automáticas tienen cooldown de 60 segundos.
- Tras alerta exitosa, se programa confirmación a 3 minutos.
- Un cambio debe mantenerse durante 1-1.5 segundos (3 detecciones) para ser válido.

## Archivos nuevos/modificados en esta iteración

### Archivos creados
- `detection/TransferSubRoiDetector.kt` - **NUEVO**: Detector de subROI del transfer basado en color

### Archivos modificados
- `classification/DatasetClassifier.kt` - Añadido `TopMatchInfo` para comparación visual
- `monitoring/MonitoringManager.kt` - **Integración completa**: detector de subROI, clasificación con subROI, observabilidad
- `ui/MainViewModel.kt` - Estados para subROI y top match

## Riesgos y puntos de atención

- **Caché en memoria**: Se pierde al cerrar la app (se regenera al iniciar vigilancia)
- **Dataset recomendado**: Mínimo 5-10 muestras por clase para clasificación usable
- **Sin persistencia de features**: No se guardan las features precalculadas en disco
- **Límite de escalabilidad**: ~150 muestras totales por la complejidad O(n) de k-NN
- **Sin ajuste dinámico de pesos**: Los pesos de features son fijos
- **Detección de subROI basada en color**: Puede fallar si el transfer no tiene color distintivo del fondo
- **Parámetros de subROI fijos**: Umbrales de colorfulness (40), tamaño mínimo (30%), no adaptativos

## Estado de infraestructura

- **Gradle Wrapper**: 8.5 (estable)
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Estado de build**: ✅ COMPILA CORRECTAMENTE
- **Warnings actuales**: 6 menores (deprecated, parámetros no usados)

## Fases CERRADAS

- ✅ Corrección pipeline cromático (cerrada)
- ✅ Baseline manual OK (cerrada - legacy)
- ✅ Evidencias visuales separadas (cerrada)
- ✅ Modo entrenamiento supervisado (cerrada)
- ✅ Clasificación automática basada en dataset (CERRADA - usable)
- ✅ **Refinamiento de clasificación con subROI del transfer (CERRADA)**

## Fase CERRADA: Observabilidad visual completa en UI

**Fecha de cierre**: 2026-03-27

La observabilidad visual de la clasificación está completamente implementada y usable en dispositivo móvil.

### Características implementadas en esta iteración

**1. Sección de inspección visual (VisualInspectionSection)** ✅
- Tarjeta desplegable en UI durante vigilancia activa
- Tres botones toggle para mostrar/ocultar vistas:
  - **ROI Global**: Crop completo del área definida por el usuario
  - **SubROI**: Crop de la subregión del transfer detectada
  - **Top Match**: Comparación visual contra la muestra más similar del dataset

**2. Visualización de ROI global** ✅
- Muestra el crop completo del ROI definido por el usuario
- Imagen en color, actualizada en tiempo real
- Etiqueta clara: "ROI Global (definido por usuario)"

**3. Visualización de subROI efectiva** ✅
- Muestra el crop de la subregión usada realmente para clasificación
- Incluye información del método de detección (COLOR_MASK, CENTERED_HEURISTIC, FULL_ROI)
- Muestra descripción de la subregión detectada
- Esta es la imagen que se usa para extraer features y clasificar

**4. Comparación visual top-1 del dataset** ✅
- Vista lado a lado: imagen actual vs muestra entrenada más similar
- Muestra etiqueta de clase del top match (con color: verde/naranja/rojo)
- Muestra score de similitud (0-100%)
- Permite verificar visualmente si la comparación tiene sentido

**5. Estados de UI implementados** ✅
- `showRoiInspection`: Controla visibilidad del ROI global
- `showSubRoiInspection`: Controla visibilidad de la subROI
- `showTopMatchComparison`: Controla visibilidad de la comparación
- Estados persistentes durante la sesión de vigilancia

**6. Métodos del ViewModel** ✅
- `toggleRoiInspection()`, `toggleSubRoiInspection()`, `toggleTopMatchComparison()`
- `getGlobalRoiCrop()`, `getSubRoiCrop()`, `getClassificationCrop()`, `getTopMatchImage()`
- Todos integrados con FrameProcessor para obtener crops en tiempo real

### Cómo verificar manualmente en dispositivo

1. **Preparar dataset**:
   - Entrar en modo entrenamiento
   - Capturar 5-10+ muestras de cada clase (OK, OBSTACULO, FALLO)
   - Volver al modo normal

2. **Iniciar vigilancia**:
   - Definir ROI sobre el transfer
   - Pulsar "Iniciar vigilancia"
   - Verificar que aparece sección "🔍 Inspección Visual"

3. **Verificar ROI global**:
   - Pulsar botón "ROI Global" (se marca con ✓)
   - Verificar que aparece imagen del ROI completo
   - Confirmar que muestra la región correcta de la vía

4. **Verificar subROI**:
   - Pulsar botón "SubROI"
   - Verificar que aparece imagen más pequeña (solo el transfer)
   - Confirmar que excluye fondo blanco y líneas negras de la vía
   - Verificar texto con método usado (COLOR_MASK o CENTERED_HEURISTIC)

5. **Verificar top match**:
   - Pulsar botón "Top Match"
   - Verificar que aparecen dos imágenes lado a lado
   - Izquierda: crop actual; Derecha: muestra del dataset más similar
   - Verificar que la etiqueta y similitud tienen sentido

6. **Verificar en diferentes condiciones**:
   - Probar con transfer en estado OK, OBSTACULO, FALLO
   - Confirmar que las imágenes de inspección cambian correctamente
   - Verificar que el top match cambia según el estado visual

### Estructura de la inspección visual

```
┌─────────────────────────────────────┐
│ 🔍 Inspección Visual                │
├─────────────────────────────────────┤
│ [✓ ROI Global] [✓ SubROI] [Top Match] │
├─────────────────────────────────────┤
│ 1. ROI Global                       │
│    [imagen completa del ROI]        │
│                                     │
│ 2. SubROI Efectiva + Crop Clasificado│
│    COLOR_MASK | Transfer detectado  │
│    [imagen del transfer solo]       │
│                                     │
│ 3. Comparación Top-1                │
│    Top match: OK | Similitud: 87%   │
│    [Actual]        [Top-1 Dataset]  │
│                                     │
│ SubROI: COLOR_MASK (conf: 85%)      │
└─────────────────────────────────────┘
```

## Siguiente paso técnico recomendado

**Fase CERRADA** - La observabilidad visual está completa y verificable en dispositivo.

Posibles mejoras futuras (no prioritarias):
- Persistir features precalculadas en disco para arranque más rápido
- Ajuste dinámico de pesos de features basado en precisión observada
- Umbral mínimo de confianza configurable para predicción válida
- Historial de clasificaciones para detectar patrones temporales

## Recordatorio de disciplina SDD

Antes de cambios grandes:
1. revisar spec y documentación viva
2. hacer commit checkpoint
3. lanzar tarea pequeña al agente
4. revisar respuesta y archivos tocados
5. actualizar documentación viva
6. hacer commit