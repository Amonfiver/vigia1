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

## Siguiente paso técnico recomendado

**Fase CERRADA** - La clasificación refinada con subROI está completa.

Posibles mejoras futuras (no prioritarias):
- **UI de inspección visual**: Botones para ver ROI, subROI, crop actual, comparación top-1
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