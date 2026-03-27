/**
 * Archivo: app/src/main/java/com/vigia/app/classification/DatasetClassifier.kt
 * Propósito: Clasificador automático basado en comparación con dataset etiquetado.
 * Responsabilidad principal: Comparar features de imagen actual contra muestras almacenadas y estimar clase.
 * Alcance: Capa de clasificación, reemplazo parcial de umbrales genéricos por clasificación basada en datos.
 *
 * Decisiones técnicas relevantes:
 * - Algoritmo k-NN (k-Nearest Neighbors) simple con k=3 por defecto
 * - Estrategia de votación ponderada por similitud (no solo conteo)
 * - Features: ColorFeatures con histograma HSV + estadísticas + porcentajes cromáticos
 * - Comparación directa contra todas las muestras del dataset (fuerza bruta, sin índices)
 * - Sin dependencias de ML externo, completamente explicable
 * - CACHE DE FEATURES: Las features del dataset se precalculan y cachean en memoria para evitar
 *   re-decodificar JPEGs en cada ciclo de clasificación. La caché se invalida cuando cambia el dataset.
 *
 * Estrategia de clasificación:
 * 1. Extraer features de la imagen actual
 * 2. Calcular similitud contra todas las muestras del dataset (usando caché de features)
 * 3. Seleccionar top-k muestras más similares
 * 4. Agrupar por clase y calcular score ponderado
 * 5. Decidir clase con mayor score
 * 6. Calcular confianza basada en diferencia entre top clases
 *
 * Estrategia de caché:
 * - La caché es un Map<sampleId, CachedSampleFeatures> que se mantiene en memoria
 * - Se recalcula solo cuando: (a) se actualiza el dataset, o (b) se fuerza resincronización
 * - Cada entrada cacheada contiene: ColorFeatures precalculada + metadatos de la muestra
 * - Durante clasificación online, se compara contra la caché (O(n)) sin decodificación
 *
 * Limitaciones temporales del MVP:
 * - Comparación fuerza bruta O(n) contra todas las muestras (escalable hasta ~150 muestras)
 * - Sin indexación espacial ni aproximación de búsqueda
 * - k fijo en 3, no adaptable
 * - Sin validación cruzada ni métricas de calidad del dataset
 * - Sin manejo de clases desbalanceadas más allá de ponderación
 * - La caché es en memoria (se pierde al cerrar la app)
 *
 * Cambios recientes:
 * - AÑADIDO: Caché de features del dataset para evitar re-decodificación en cada ciclo
 * - AÑADIDO: Estado de sincronización del dataset (SYNCED, STALE, EMPTY)
 * - AÑADIDO: Método explícito para forzar resincronización
 * - Creación inicial para clasificación automática basada en dataset etiquetado
 */
package com.vigia.app.classification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vigia.app.detection.ColorFrameData
import com.vigia.app.detection.TransferSubRoiDetector
import com.vigia.app.detection.TransferSubRoiResult
import com.vigia.app.domain.model.ClassLabel
import com.vigia.app.domain.model.Roi
import com.vigia.app.domain.model.TrainingSample

/**
 * Resultado de la clasificación automática.
 *
 * @property predictedClass Clase estimada (OK, OBSTACULO, FALLO)
 * @property confidence Confianza en la predicción (0.0-1.0)
 * @property classScores Scores por clase (para observabilidad)
 * @property samplesUsed Número de muestras usadas en la clasificación
 * @property topMatches Top-k matches con sus similitudes (para debug)
 * @property featuresSummary Resumen de features de la imagen actual
 */
data class ClassificationResult(
    val predictedClass: ClassLabel,
    val confidence: Float,
    val classScores: Map<ClassLabel, Float>,
    val samplesUsed: Int,
    val topMatches: List<ClassMatch>,
    val featuresSummary: String
)

/**
 * Match individual con una muestra del dataset.
 *
 * @property label Clase de la muestra
 * @property sampleId ID de la muestra
 * @property similarity Similitud (0.0-1.0)
 */
data class ClassMatch(
    val label: ClassLabel,
    val sampleId: String,
    val similarity: Float
)

/**
 * Features de una muestra cacheadas en memoria.
 * Evita re-decodificar el JPEG en cada ciclo de clasificación.
 *
 * @property sampleId ID único de la muestra
 * @property label Clase de la muestra
 * @property features ColorFeatures precalculadas
 * @property cachedAt Timestamp de cuando se cacheó
 */
data class CachedSampleFeatures(
    val sampleId: String,
    val label: ClassLabel,
    val features: ColorFeatures,
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Estado de sincronización del dataset.
 */
enum class DatasetSyncStatus {
    EMPTY,      // No hay muestras en el dataset
    SYNCED,     // Dataset cargado y cacheado, listo para clasificar
    STALE       // Dataset cambió desde última sincronización, necesita recache
}

/**
 * Clasificador basado en dataset etiquetado usando k-NN.
 *
 * @param k Número de vecinos a considerar (default 3)
 * @param minConfidenceThreshold Umbral mínimo de confianza para predicción válida (default 0.3)
 */
class DatasetClassifier(
    private val k: Int = 3,
    private val minConfidenceThreshold: Float = 0.3f
) {
    // Caché de features del dataset: sampleId -> CachedSampleFeatures
    private val featuresCache = mutableMapOf<String, CachedSampleFeatures>()
    
    // Estado actual de sincronización
    private var syncStatus: DatasetSyncStatus = DatasetSyncStatus.EMPTY
    
    // Referencia al dataset original para estadísticas
    private var lastDataset: List<TrainingSample> = emptyList()

    /**
     * Sincroniza el dataset y precalcula las features en caché.
     * DEBE llamarse cuando:
     * - Se inicia la vigilancia y hay un dataset disponible
     * - Se capturan nuevas muestras de entrenamiento
     * - Se eliminan muestras del dataset
     * - El usuario fuerza resincronización manual
     *
     * @param dataset Dataset completo de muestras etiquetadas
     * @return Resultado de la sincronización (éxito y estadísticas)
     */
    fun syncDataset(dataset: List<TrainingSample>): SyncResult {
        if (dataset.isEmpty()) {
            featuresCache.clear()
            lastDataset = emptyList()
            syncStatus = DatasetSyncStatus.EMPTY
            return SyncResult(success = true, cachedCount = 0, errors = 0, status = syncStatus)
        }

        var cachedCount = 0
        var errorCount = 0
        val newCache = mutableMapOf<String, CachedSampleFeatures>()

        for (sample in dataset) {
            try {
                // Intentar reutilizar de la caché existente si la muestra no cambió
                val existing = featuresCache[sample.id]
                if (existing != null) {
                    newCache[sample.id] = existing
                    cachedCount++
                    continue
                }

                // Extraer features de la muestra (decodificando JPEG)
                val features = extractFeaturesFromSample(sample)
                
                newCache[sample.id] = CachedSampleFeatures(
                    sampleId = sample.id,
                    label = sample.label,
                    features = features
                )
                cachedCount++
            } catch (e: Exception) {
                // Ignorar muestras que no se puedan procesar
                errorCount++
            }
        }

        // Reemplazar caché antigua por la nueva
        featuresCache.clear()
        featuresCache.putAll(newCache)
        lastDataset = dataset
        syncStatus = if (cachedCount > 0) DatasetSyncStatus.SYNCED else DatasetSyncStatus.EMPTY

        return SyncResult(
            success = cachedCount > 0,
            cachedCount = cachedCount,
            errors = errorCount,
            status = syncStatus
        )
    }

    /**
     * Clasifica una imagen actual comparándola contra el dataset cacheado.
     * Esta operación es O(n) pero SOLO compara features (sin decodificación JPEG).
     *
     * @param currentFrame Frame actual con información HSV
     * @return Resultado de la clasificación
     */
    fun classify(currentFrame: ColorFrameData): ClassificationResult {
        // Extraer features de la imagen actual (una sola vez por frame)
        val currentFeatures = ColorFeatures.fromColorFrameData(currentFrame)

        // Si no hay caché, no se puede clasificar
        if (featuresCache.isEmpty()) {
            return ClassificationResult(
                predictedClass = ClassLabel.OK, // Default conservador
                confidence = 0f,
                classScores = emptyMap(),
                samplesUsed = 0,
                topMatches = emptyList(),
                featuresSummary = currentFeatures.summary() + " | Sin dataset cacheado"
            )
        }

        // Calcular similitud contra TODAS las muestras cacheadas (sin decodificación)
        val matches = featuresCache.values.mapNotNull { cached ->
            try {
                val similarity = currentFeatures.similarityTo(cached.features)
                ClassMatch(
                    label = cached.label,
                    sampleId = cached.sampleId,
                    similarity = similarity
                )
            } catch (e: Exception) {
                null
            }
        }

        // Seleccionar top-k
        val topK = matches.sortedByDescending { it.similarity }.take(k.coerceAtLeast(1))

        // Calcular scores por clase (ponderado por similitud)
        val classScores = calculateWeightedScores(topK)

        // Determinar clase ganadora
        val predictedClass = classScores.maxByOrNull { it.value }?.key ?: ClassLabel.OK

        // Calcular confianza basada en diferencia entre top 2 clases
        val confidence = calculateConfidence(classScores, predictedClass)

        return ClassificationResult(
            predictedClass = predictedClass,
            confidence = confidence,
            classScores = classScores,
            samplesUsed = featuresCache.size,
            topMatches = topK,
            featuresSummary = currentFeatures.summary()
        )
    }

    /**
     * Clasifica usando features ya extraídas (optimización para casos especiales).
     */
    fun classifyWithFeatures(currentFeatures: ColorFeatures): ClassificationResult {
        if (featuresCache.isEmpty()) {
            return ClassificationResult(
                predictedClass = ClassLabel.OK,
                confidence = 0f,
                classScores = emptyMap(),
                samplesUsed = 0,
                topMatches = emptyList(),
                featuresSummary = currentFeatures.summary() + " | Sin dataset cacheado"
            )
        }

        val matches = featuresCache.values.mapNotNull { cached ->
            try {
                val similarity = currentFeatures.similarityTo(cached.features)
                ClassMatch(
                    label = cached.label,
                    sampleId = cached.sampleId,
                    similarity = similarity
                )
            } catch (e: Exception) {
                null
            }
        }

        val topK = matches.sortedByDescending { it.similarity }.take(k.coerceAtLeast(1))
        val classScores = calculateWeightedScores(topK)

        val predictedClass = classScores.maxByOrNull { it.value }?.key ?: ClassLabel.OK

        val confidence = calculateConfidence(classScores, predictedClass)

        return ClassificationResult(
            predictedClass = predictedClass,
            confidence = confidence,
            classScores = classScores,
            samplesUsed = featuresCache.size,
            topMatches = topK,
            featuresSummary = currentFeatures.summary()
        )
    }

    /**
     * Extrae features de una muestra de entrenamiento (decodificando JPEG).
     * Esta operación es costosa y solo debe hacerse durante sincronización, no en cada clasificación.
     */
    private fun extractFeaturesFromSample(sample: TrainingSample): ColorFeatures {
        // Decodificar JPEG a Bitmap
        val bitmap = BitmapFactory.decodeByteArray(
            sample.imageData,
            0,
            sample.imageData.size
        ) ?: throw IllegalArgumentException("No se pudo decodificar imagen de muestra ${sample.id}")

        // Convertir Bitmap a ColorFrameData (simplificado)
        val width = bitmap.width
        val height = bitmap.height
        val hsvArray = Array(width * height) { i ->
            val x = i % width
            val y = i / width
            val pixel = bitmap.getPixel(x, y)

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            com.vigia.app.detection.HsvPixel.fromRgb(r, g, b)
        }

        val frameData = ColorFrameData(
            timestamp = sample.timestamp,
            width = width,
            height = height,
            hsvArray = hsvArray
        )

        return ColorFeatures.fromColorFrameData(frameData)
    }

    /**
     * Calcula scores ponderados por similitud para cada clase.
     * Fórmula: score_clase = Σ(similitud_i) para todas las muestras de esa clase en top-k
     */
    private fun calculateWeightedScores(topMatches: List<ClassMatch>): Map<ClassLabel, Float> {
        val scores = mutableMapOf<ClassLabel, Float>()

        for (match in topMatches) {
            val currentScore = scores.getOrDefault(match.label, 0f)
            scores[match.label] = currentScore + match.similarity
        }

        // Normalizar para que sumen 1.0
        val totalScore = scores.values.sum().coerceAtLeast(1e-6f)
        return scores.mapValues { it.value / totalScore }
    }

    /**
     * Calcula confianza basada en diferencia entre clase ganadora y segunda clase.
     * Confianza alta = diferencia grande entre top 1 y top 2.
     */
    private fun calculateConfidence(
        classScores: Map<ClassLabel, Float>,
        predictedClass: ClassLabel
    ): Float {
        if (classScores.size < 2) {
            return classScores[predictedClass] ?: 0f
        }

        val sortedScores = classScores.values.sortedDescending()
        val topScore = sortedScores[0]
        val secondScore = sortedScores[1]

        // Confianza = diferencia normalizada
        val rawConfidence = (topScore - secondScore).coerceAtLeast(0f)
        return (rawConfidence * 2).coerceIn(0f, 1f) // Escala para que 0.5 diff = 1.0 confianza
    }

    /**
     * Obtiene el estado actual de sincronización.
     */
    fun getSyncStatus(): DatasetSyncStatus = syncStatus

    /**
     * Obtiene estadísticas de la caché actual.
     */
    fun getCacheStats(): CacheStats {
        val byClass = featuresCache.values.groupingBy { it.label }.eachCount()
        return CacheStats(
            totalCached = featuresCache.size,
            okCached = byClass[ClassLabel.OK] ?: 0,
            obstaculoCached = byClass[ClassLabel.OBSTACULO] ?: 0,
            falloCached = byClass[ClassLabel.FALLO] ?: 0,
            syncStatus = syncStatus,
            lastSyncTimestamp = if (featuresCache.isNotEmpty()) {
                featuresCache.values.maxOf { it.cachedAt }
            } else 0
        )
    }

    /**
     * Verifica si el dataset tiene suficientes muestras para clasificación confiable.
     * Requiere al menos 2 clases con 2+ muestras cada una.
     */
    fun isDatasetReady(): Boolean {
        if (featuresCache.isEmpty()) return false

        val counts = featuresCache.values.groupingBy { it.label }.eachCount()
        return counts.size >= 2 && counts.values.all { it >= 2 }
    }

    /**
     * Obtiene estadísticas del dataset para observabilidad.
     */
    fun getDatasetStats(dataset: List<TrainingSample>): DatasetStats {
        val counts = dataset.groupingBy { it.label }.eachCount()
        return DatasetStats(
            totalSamples = dataset.size,
            okCount = counts[ClassLabel.OK] ?: 0,
            obstaculoCount = counts[ClassLabel.OBSTACULO] ?: 0,
            falloCount = counts[ClassLabel.FALLO] ?: 0,
            isReady = isDatasetReady()
        )
    }

    /**
     * Invalida la caché forzando una resincronización en la próxima clasificación.
     */
    fun invalidateCache() {
        syncStatus = DatasetSyncStatus.STALE
    }

    /**
     * Limpia completamente la caché.
     */
    fun clearCache() {
        featuresCache.clear()
        lastDataset = emptyList()
        syncStatus = DatasetSyncStatus.EMPTY
    }
}

/**
 * Resultado de una operación de sincronización.
 */
data class SyncResult(
    val success: Boolean,
    val cachedCount: Int,
    val errors: Int,
    val status: DatasetSyncStatus
)

/**
 * Estadísticas de la caché de features.
 */
data class CacheStats(
    val totalCached: Int,
    val okCached: Int,
    val obstaculoCached: Int,
    val falloCached: Int,
    val syncStatus: DatasetSyncStatus,
    val lastSyncTimestamp: Long
) {
    fun summary(): String {
        return "Cache: $totalCached muestras (OK:$okCached, OBST:$obstaculoCached, FALL:$falloCached) - ${when(syncStatus) {
            DatasetSyncStatus.SYNCED -> "✓ Sincronizado"
            DatasetSyncStatus.STALE -> "⚠ Desactualizado"
            DatasetSyncStatus.EMPTY -> "✗ Vacío"
        }}"
    }
}

/**
 * Estadísticas del dataset para observabilidad.
 */
data class DatasetStats(
    val totalSamples: Int,
    val okCount: Int,
    val obstaculoCount: Int,
    val falloCount: Int,
    val isReady: Boolean
) {
    fun summary(): String {
        return "Dataset: $totalSamples muestras (OK:$okCount, OBST:$obstaculoCount, FALL:$falloCount) - ${if (isReady) "✓ Listo" else "⚠ Insuficiente"}"
    }
}

/**
 * Información del match más cercano para comparación visual.
 * Usado en la UI para mostrar contra qué muestra entrenada se compara.
 *
 * @property sampleId ID de la muestra más cercana
 * @property label Clase de la muestra
 * @property similarity Similitud (0.0-1.0)
 * @property sampleImage Bitmap de la muestra entrenada (nullable para lazy loading)
 */
data class TopMatchInfo(
    val sampleId: String,
    val label: ClassLabel,
    val similarity: Float,
    val sampleImage: android.graphics.Bitmap? = null
)
