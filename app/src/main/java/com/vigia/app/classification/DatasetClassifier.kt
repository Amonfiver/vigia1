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
 *
 * Estrategia de clasificación:
 * 1. Extraer features de la imagen actual
 * 2. Calcular similitud contra todas las muestras del dataset
 * 3. Seleccionar top-k muestras más similares
 * 4. Agrupar por clase y calcular score ponderado
 * 5. Decidir clase con mayor score
 * 6. Calcular confianza basada en diferencia entre top clases
 *
 * Limitaciones temporales del MVP:
 * - Comparación fuerza bruta O(n) contra todas las muestras (escalable hasta ~150 muestras)
 * - Sin indexación espacial ni aproximación de búsqueda
 * - k fijo en 3, no adaptable
 * - Sin validación cruzada ni métricas de calidad del dataset
 * - Sin manejo de clases desbalanceadas más allá de ponderación
 *
 * Cambios recientes:
 * - Creación inicial para clasificación automática basada en dataset etiquetado
 */
package com.vigia.app.classification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vigia.app.detection.ColorFrameData
import com.vigia.app.domain.model.ClassLabel
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
 * Clasificador basado en dataset etiquetado usando k-NN.
 *
 * @param k Número de vecinos a considerar (default 3)
 * @param minConfidenceThreshold Umbral mínimo de confianza para predicción válida (default 0.3)
 */
class DatasetClassifier(
    private val k: Int = 3,
    private val minConfidenceThreshold: Float = 0.3f
) {
    /**
     * Clasifica una imagen actual comparándola contra el dataset.
     *
     * @param currentFrame Frame actual con información HSV
     * @param dataset Muestras etiquetadas del dataset
     * @return Resultado de la clasificación
     */
    fun classify(
        currentFrame: ColorFrameData,
        dataset: List<TrainingSample>
    ): ClassificationResult {
        // Extraer features de la imagen actual
        val currentFeatures = ColorFeatures.fromColorFrameData(currentFrame)

        // Si no hay muestras en el dataset, retornar resultado de "desconocido"
        if (dataset.isEmpty()) {
            return ClassificationResult(
                predictedClass = ClassLabel.OK, // Default conservador
                confidence = 0f,
                classScores = emptyMap(),
                samplesUsed = 0,
                topMatches = emptyList(),
                featuresSummary = currentFeatures.summary() + " | Sin muestras en dataset"
            )
        }

        // Calcular similitud contra todas las muestras
        val matches = dataset.mapNotNull { sample ->
            try {
                // Convertir imagen de muestra a ColorFrameData
                val sampleFeatures = extractFeaturesFromSample(sample)
                val similarity = currentFeatures.similarityTo(sampleFeatures)

                ClassMatch(
                    label = sample.label,
                    sampleId = sample.id,
                    similarity = similarity
                )
            } catch (e: Exception) {
                // Ignorar muestras que no se puedan procesar
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
            samplesUsed = dataset.size,
            topMatches = topK,
            featuresSummary = currentFeatures.summary()
        )
    }

    /**
     * Clasifica usando features ya extraídas (optimización).
     */
    fun classifyWithFeatures(
        currentFeatures: ColorFeatures,
        dataset: List<TrainingSample>
    ): ClassificationResult {
        if (dataset.isEmpty()) {
            return ClassificationResult(
                predictedClass = ClassLabel.OK,
                confidence = 0f,
                classScores = emptyMap(),
                samplesUsed = 0,
                topMatches = emptyList(),
                featuresSummary = currentFeatures.summary() + " | Sin muestras en dataset"
            )
        }

        val matches = dataset.mapNotNull { sample ->
            try {
                val sampleFeatures = extractFeaturesFromSample(sample)
                val similarity = currentFeatures.similarityTo(sampleFeatures)

                ClassMatch(
                    label = sample.label,
                    sampleId = sample.id,
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
            samplesUsed = dataset.size,
            topMatches = topK,
            featuresSummary = currentFeatures.summary()
        )
    }

    /**
     * Extrae features de una muestra de entrenamiento (decodificando JPEG).
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
     * Verifica si el dataset tiene suficientes muestras para clasificación confiable.
     */
    fun isDatasetReady(dataset: List<TrainingSample>): Boolean {
        if (dataset.isEmpty()) return false

        val counts = dataset.groupingBy { it.label }.eachCount()
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
            isReady = isDatasetReady(dataset)
        )
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