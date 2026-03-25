/**
 * Archivo: app/src/main/java/com/vigia/app/data/local/FileOkBaselineRepository.kt
 * Propósito: Implementación del repositorio de baseline OK usando almacenamiento en archivos.
 * Responsabilidad principal: Persistir muestras de estado OK como archivos JPEG en almacenamiento privado.
 * Alcance: Capa de datos, implementación de persistencia local.
 *
 * Decisiones técnicas relevantes:
 * - Almacenamiento de imágenes JPEG en directorio privado de la app (filesDir/ok_baseline)
 * - Metadatos (ROI, timestamp, índice) en formato de texto simple (no JSON para evitar dependencias extra)
 * - Operaciones con coroutines y Dispatchers.IO para no bloquear UI
 *
 * Limitaciones temporales del MVP:
 * - Sin cifrado de imágenes (almacenamiento privado de la app es suficiente por ahora)
 * - Sin compresión adicional (JPEG ya comprime)
 * - Sin límite de tamaño total (solo límite de 10 muestras)
 *
 * Cambios recientes:
 * - Creación inicial para persistencia de baseline manual
 * - Simplificado para evitar dependencia de kotlinx.serialization
 */
package com.vigia.app.data.local

import android.content.Context
import com.vigia.app.domain.model.OkBaselineSample
import com.vigia.app.domain.model.OkBaselineState
import com.vigia.app.domain.model.Roi
import com.vigia.app.domain.repository.OkBaselineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementación de OkBaselineRepository usando almacenamiento en archivos.
 *
 * @param context Contexto de aplicación para acceder a directorios privados
 */
class FileOkBaselineRepository(
    private val context: Context
) : OkBaselineRepository {

    private val baselineDir: File by lazy {
        File(context.filesDir, BASELINE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    override suspend fun saveSample(sample: OkBaselineSample): Boolean = withContext(Dispatchers.IO) {
        try {
            // Guardar imagen JPEG
            val imageFile = File(baselineDir, "sample_${sample.index}.jpg")
            imageFile.writeBytes(sample.imageData)

            // Guardar metadatos en formato simple (no JSON)
            val metadataLines = buildList {
                add("index=${sample.index}")
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
            
            val metadataFile = File(baselineDir, "sample_${sample.index}.meta")
            metadataFile.writeText(metadataLines.joinToString("\n"))

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getAllSamples(): List<OkBaselineSample> = withContext(Dispatchers.IO) {
        try {
            val samples = mutableListOf<OkBaselineSample>()
            
            // Buscar todos los archivos de metadatos
            val metadataFiles = baselineDir.listFiles { file ->
                file.name.startsWith("sample_") && file.name.endsWith(".meta")
            } ?: return@withContext emptyList()

            for (metadataFile in metadataFiles.sortedBy { it.name }) {
                try {
                    val metadata = parseMetadata(metadataFile.readText())
                    val imageFile = File(baselineDir, "sample_${metadata.index}.jpg")
                    
                    if (imageFile.exists()) {
                        val imageData = imageFile.readBytes()
                        val sample = OkBaselineSample(
                            index = metadata.index,
                            timestamp = metadata.timestamp,
                            imageData = imageData,
                            roi = metadata.roi,
                            subRegion = metadata.subRegion
                        )
                        samples.add(sample)
                    }
                } catch (e: Exception) {
                    // Ignorar muestras corruptas individuales
                    e.printStackTrace()
                }
            }

            samples.sortedBy { it.index }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Parsea metadatos desde formato de texto simple.
     */
    private fun parseMetadata(content: String): SampleMetadata {
        val lines = content.lines()
        val map = lines.associate { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }

        val index = map["index"]?.toIntOrNull() ?: 0
        val timestamp = map["timestamp"]?.toLongOrNull() ?: 0L
        
        val roi = Roi(
            left = map["roi_left"]?.toFloatOrNull() ?: 0f,
            top = map["roi_top"]?.toFloatOrNull() ?: 0f,
            right = map["roi_right"]?.toFloatOrNull() ?: 1f,
            bottom = map["roi_bottom"]?.toFloatOrNull() ?: 1f
        )

        val subRegion = if (map.containsKey("sub_left")) {
            OkBaselineSample.SubRegionInfo(
                left = map["sub_left"]?.toFloatOrNull() ?: 0f,
                top = map["sub_top"]?.toFloatOrNull() ?: 0f,
                right = map["sub_right"]?.toFloatOrNull() ?: 1f,
                bottom = map["sub_bottom"]?.toFloatOrNull() ?: 1f,
                description = map["sub_desc"] ?: "Subregión"
            )
        } else null

        return SampleMetadata(index, timestamp, roi, subRegion)
    }

    override suspend fun getBaselineState(): OkBaselineState = withContext(Dispatchers.IO) {
        OkBaselineState(samples = getAllSamples())
    }

    override suspend fun deleteSample(index: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val imageFile = File(baselineDir, "sample_$index.jpg")
            val metadataFile = File(baselineDir, "sample_$index.meta")
            
            val imageDeleted = if (imageFile.exists()) imageFile.delete() else true
            val metadataDeleted = if (metadataFile.exists()) metadataFile.delete() else true
            
            imageDeleted && metadataDeleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun clearAllSamples(): Boolean = withContext(Dispatchers.IO) {
        try {
            val files = baselineDir.listFiles() ?: return@withContext true
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

    override suspend fun hasSamples(): Boolean = withContext(Dispatchers.IO) {
        getSampleCount() > 0
    }

    override suspend fun getSampleCount(): Int = withContext(Dispatchers.IO) {
        try {
            baselineDir.listFiles { file ->
                file.name.startsWith("sample_") && file.name.endsWith(".jpg")
            }?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Obtiene la ruta del directorio de baseline (para debug/info).
     */
    fun getBaselineDirectory(): String = baselineDir.absolutePath

    companion object {
        private const val BASELINE_DIR_NAME = "ok_baseline"
    }
}

/**
 * Datos de metadatos parseados (clase interna, no serializable).
 */
private data class SampleMetadata(
    val index: Int,
    val timestamp: Long,
    val roi: Roi,
    val subRegion: OkBaselineSample.SubRegionInfo? = null
)