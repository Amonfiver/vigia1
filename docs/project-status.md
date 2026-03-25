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

---

## Estado actual

### Fase CERRADA: Clasificación automática basada en dataset implementada y usable

**Fecha de cierre**: 2026-03-25

La fase de clasificación automática basada en dataset está **completamente implementada y usable en dispositivo real**. 

#### Características implementadas

**1. UI de clasificación en tiempo real** ✅
- Visualización explícita de clase estimada (OK/OBSTACULO/FALLO) con colores coherentes:
  - Verde (#4CAF50) para OK ✓
  - Naranja (#FF9800) para OBSTÁCULO ⚠️
  - Rojo (#F44336) para FALLO 🚨
- Barra de confianza visual (0-100%)
- Scores por clase con barras de progreso
- Contadores de muestras por clase disponibles en caché
- Indicador de estado de sincronización (SYNCED/SYNCING/STALE/EMPTY)

**2. Caché de features del dataset** ✅
- **Precálculo de features en sincronización**: Las features HSV se extraen una sola vez al sincronizar
- **Caché en memoria**: Map<sampleId, CachedSampleFeatures> mantiene features precalculadas
- **Sin re-decodificación**: Durante clasificación online, solo se comparan features (sin tocar JPEGs)
- **Rendimiento**: Clasificación O(n) sin operaciones costosas de decodificación

**3. Flujo de resincronización explícita** ✅
- Estado de sincronización visible: SYNCED (✓), SYNCING (↻), STALE (⚠), EMPTY (✗)
- Sincronización automática al iniciar vigilancia
- Botón "Resincronizar dataset" cuando está STALE
- Método `forceResyncDataset()` para resincronización manual
- Invalidación automática al capturar/borrar muestras

**4. Integración completa** ✅
- Clasificación visible solo durante vigilancia activa
- Actualización en tiempo real (cada 500ms)
- No bloquea la UI ni el flujo de vigilancia
- Compatible con modo de entrenamiento

---

## Decisiones clave vigentes

- El ROI NO se detecta automáticamente.
- El ROI lo define manualmente el usuario/desarrollador.
- **El análisis usa información cromática HSV, no grayscale como señal principal**
- **Dentro del ROI global, se analiza una subregión activa (60% centro)**
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

### Archivos modificados
- `classification/DatasetClassifier.kt` - **REESCRITO**: Caché de features, sincronización explícita, estados
- `monitoring/MonitoringManager.kt` - Estado de sincronización, resincronización, integración con caché
- `ui/MainViewModel.kt` - Estado de sincronización, métodos de resincronización, observadores
- `MainActivity.kt` - UI completa de clasificación (ClassificationSection)

## Riesgos y puntos de atención

- **Caché en memoria**: Se pierde al cerrar la app (se regenera al iniciar vigilancia)
- **Dataset recomendado**: Mínimo 5-10 muestras por clase para clasificación usable
- **Sin persistencia de features**: No se guardan las features precalculadas en disco
- **Límite de escalabilidad**: ~150 muestras totales por la complejidad O(n) de k-NN
- **Sin ajuste dinámico de pesos**: Los pesos de features son fijos

## Estado de infraestructura

- **Gradle Wrapper**: 8.5 (estable)
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Estado de build**: ✅ COMPILA CORRECTAMENTE
- **Warnings actuales**: 3 menores (deprecated durante transición, parámetro no usado)

## Fases CERRADAS

- ✅ Corrección pipeline cromático (cerrada)
- ✅ Baseline manual OK (cerrada - legacy)
- ✅ Evidencias visuales separadas (cerrada)
- ✅ Modo entrenamiento supervisado (cerrada)
- ✅ **Clasificación automática basada en dataset (CERRADA - usable)**

## Siguiente paso técnico recomendado

**Fase CERRADA** - La clasificación automática está completa y usable.

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