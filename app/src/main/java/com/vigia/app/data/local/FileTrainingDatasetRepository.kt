/**
 * Archivo: app/src/main/java/com/vigia/app/data/local/FileTrainingDatasetRepository.kt
 * Propósito: Implementación del repositorio de dataset de entrenamiento usando almacenamiento en archivos.
 * Responsabilidad principal: Persistir muestras etiquetadas como archivos JPEG organizados por clase.
 * Alcance: Capa de datos, implementación de persistencia local.
 *
 * Decisiones técnicas relevantes:
 * - Almacenamiento organizado por carpetas: filesDir/training/OK/, OBSTACULO/, FALLO/
 * - Imágenes JPEG con prefijo de timestamp para orden cronológico
 * - Metadatos en archivo .meta con formato texto simple (key=value)
 * - IDs únicos generados con timestamp + UUID corto
 *
 * Limitaciones temporales del MVP:
 * - Sin cifrado de imágenes (almacenamiento privado de la app es suficiente por ahora)
 * - Sin compresión adicional (JPEG ya comprime)
 * - Sin sincronización con cloud
 *
 * Cambios recientes:
 * - Creación inicial para modo de entrenamiento supervisado manual
 */
package com.vigia.app.data.local

import android.content.Context
import com.vigia.app.domain.model.ClassLabel
import com.vigia.app.domain.model.Roi
import com.vigia.app.domain.model.TrainingDatasetState
import com.vigia.app.domain.model.TrainingSample
import com.vigia.app.domain.repository.TrainingDatasetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Implementación de TrainingDatasetRepository usando almacenamiento en archivos.
 * Estructura de directorios:
 * - filesDir/training/OK/      -> muestras clase OK
 * - filesDir/training/OBSTACULO/ -> muestras clase OBSTACULO
 * - filesDir/training/FALLO/     -> muestras clase FALLO
 *
 * @param context Contexto de aplicación para acceder a directorios privados
 */
class FileTrainingDatasetRepository(
    private val context: Context
) : TrainingDatasetRepository {

    private val baseTrainingDir: File by lazy {
        File(context.filesDir, BASE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Obtiene el directorio específico para una clase.
     */
    private fun getClassDirectory(label: ClassLabel): File {
        return File(baseTrainingDir, label.name).apply {
            if (!exists()) mkdirs()
        }
    }

    override fun generateSampleId(label: ClassLabel): String {
        val timestamp = System.currentTimeMillis()
        val shortUuid = UUID.randomUUID().toString().take(8)
        return "${label.name}_${timestamp}_$shortUuid"
    }

    override suspend fun saveSample(sample: TrainingSample): Boolean = withContext(Dispatchers.IO) {
        try {
            val classDir = getClassDirectory(sample.label)
            
            // Guardar imagen JPEG
            val imageFile = File(classDir, "${sample.id}.jpg")
            imageFile.writeBytes(sample.imageData)

            // Guardar metadatos
            val metadataLines = buildList {
                add("id=${sample.id}")
                add("label=${sample.label.name}")
                add("timestamp=${sample.timestamp}")
                add("roi_left=${sample.roi.left}")
                add("roi_top=${sample.roi.top}")
                add("roi_right=${sample.roi.right}")
                add("roi_bottom=${sample.roi.bottom}")
                sample.subRegion?.let { sr ->
                    add("sub_left=${sr.left}")
                    add("sub_top=${sr.top}")
                    add("sub_right=${sr.right}")
                    add("sub_bottom=${sr.bottom}")
                    add("sub_desc=${sr.description}")
                }
            }
            
            val metadataFile = File(classDir, "${sample.id}.meta")
            metadataFile.writeText(metadataLines.joinToString("\n"))

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getSamplesByLabel(label: ClassLabel): List<TrainingSample> = withContext(Dispatchers.IO) {
        try {
            val classDir = getClassDirectory(label)
            val samples = mutableListOf<TrainingSample>()
            
            // Buscar todos los archivos de metadatos de esta clase
            val metadataFiles = classDir.listFiles { file ->
                file.name.endsWith(".meta")
            } ?: return@withContext emptyList()

            for (metadataFile in metadataFiles.sortedBy { it.name }) {
                try {
                    val sample = parseSampleFromFile(metadataFile, label)
                    if (sample != null) {
                        samples.add(sample)
                    }
                } catch (e: Exception) {
                    // Ignorar muestras corruptas individuales
                    e.printStackTrace()
                }
            }

            samples.sortedBy { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Parsea una muestra desde archivo de metadatos y su imagen asociada.
     */
    private fun parseSampleFromFile(metadataFile: File, expectedLabel: ClassLabel): TrainingSample? {
        val content = metadataFile.readText()
        val lines = content.lines()
        val map = lines.associate { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }

        val id = map["id"] ?: return null
        val timestamp = map["timestamp"]?.toLongOrNull() ?: return null
        
        // Verificar que la etiqueta coincida
        val labelName = map["label"]
        if (labelName != expectedLabel.name) return null

        val roi = Roi(
            left = map["roi_left"]?.toFloatOrNull() ?: 0f,
            top = map["roi_top"]?.toFloatOrNull() ?: 0f,
            right = map["roi_right"]?.toFloatOrNull() ?: 1f,
            bottom = map["roi_bottom"]?.toFloatOrNull() ?: 1f
        )

        val subRegion = if (map.containsKey("sub_left")) {
            TrainingSample.SubRegionInfo(
                left = map["sub_left"]?.toFloatOrNull() ?: 0f,
                top = map["sub_top"]?.toFloatOrNull() ?: 0f,
                right = map["sub_right"]?.toFloatOrNull() ?: 1f,
                bottom = map["sub_bottom"]?.toFloatOrNull() ?: 1f,
                description = map["sub_desc"] ?: "Subregión"
            )
        } else null

        // Cargar imagen asociada
        val imageFile = File(metadataFile.parent, "$id.jpg")
        if (!imageFile.exists()) return null
        
        val imageData = imageFile.readBytes()

        return TrainingSample(
            id = id,
            label = expectedLabel,
            timestamp = timestamp,
            imageData = imageData,
            roi = roi,
            subRegion = subRegion
        )
    }

    override suspend fun getDatasetState(): TrainingDatasetState = withContext(Dispatchers.IO) {
        TrainingDatasetState(
            okSamples = getSamplesByLabel(ClassLabel.OK),
            obstaculoSamples = getSamplesByLabel(ClassLabel.OBSTACULO),
            falloSamples = getSamplesByLabel(ClassLabel.FALLO)
        )
    }

    override suspend fun getSampleCount(label: ClassLabel): Int = withContext(Dispatchers.IO) {
        try {
            val classDir = getClassDirectory(label)
            classDir.listFiles { file -> file.name.endsWith(".jpg") }?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun deleteSample(sampleId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Buscar en todas las clases
            var deleted = false
            ClassLabel.entries.forEach { label ->
                val classDir = getClassDirectory(label)
                val imageFile = File(classDir, "$sampleId.jpg")
                val metaFile = File(classDir, "$sampleId.meta")
                
                if (imageFile.exists()) {
                    imageFile.delete()
                    deleted = true
                }
                if (metaFile.exists()) {
                    metaFile.delete()
                }
            }
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun clearSamplesByLabel(label: ClassLabel): Boolean = withContext(Dispatchers.IO) {
        try {
            val classDir = getClassDirectory(label)
            val files = classDir.listFiles() ?: return@withContext true
            
            var allDeleted = true
            for (file in files) {
                if (!file.delete()) {
                    allDeleted = false
                }
            }
            allDeleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun clearAllSamples(): Boolean = withContext(Dispatchers.IO) {
        try {
            var allDeleted = true
            
            ClassLabel.entries.forEach { label ->
                if (!clearSamplesByLabel(label)) {
                    allDeleted = false
                }
            }
            
            allDeleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun hasSamples(label: ClassLabel): Boolean = withContext(Dispatchers.IO) {
        getSampleCount(label) > 0
    }

    /**
     * Obtiene la ruta base del directorio de entrenamiento (para debug/info).
     */
    fun getTrainingDirectory(): String = baseTrainingDir.absolutePath

    /**
     * Obtiene la ruta de una clase específica (para debug/info).
     */
    fun getClassDirectoryPath(label: ClassLabel): String = getClassDirectory(label).absolutePath

    companion object {
        private const val BASE_DIR_NAME = "training"
    }
}