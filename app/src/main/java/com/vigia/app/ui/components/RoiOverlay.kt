/**
 * Archivo: app/src/main/java/com/vigia/app/ui/components/RoiOverlay.kt
 * Propósito: Componente para visualizar el ROI definido sobre la preview de cámara.
 * Responsabilidad principal: Dibujar el rectángulo del ROI actual sobre la imagen.
 * Alcance: Capa de UI, componente de visualización de ROI.
 *
 * Decisiones técnicas relevantes:
 * - Canvas para dibujar el rectángulo sobre la imagen
 * - Coordenadas normalizadas convertidas a coordenadas de pantalla
 * - Estilo visual diferente al selector (más sutil, indicando área vigilada)
 *
 * Limitaciones temporales del MVP:
 * - Solo visualización estática, sin interacción
 * - Solo un ROI
 *
 * Cambios recientes: Creación inicial del overlay de ROI.
 */
package com.vigia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.vigia.app.domain.model.Roi

/**
 * Overlay que dibuja el ROI actual sobre la preview de cámara.
 *
 * @param roi ROI a visualizar (coordenadas normalizadas 0.0-1.0)
 * @param isActive Indica si la vigilancia está activa (cambia el color)
 * @param modifier Modificador para el layout
 */
@Composable
fun RoiOverlay(
    roi: Roi,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Convertir coordenadas normalizadas a coordenadas de pantalla
        val rect = Rect(
            left = roi.left * width,
            top = roi.top * height,
            right = roi.right * width,
            bottom = roi.bottom * height
        )

        // Color según estado: verde si está vigilando, azul si está definido pero no vigilando
        val color = if (isActive) Color(0xFF4CAF50) else Color(0xFF2196F3)
        val strokeWidth = 3.dp.toPx()

        // Borde del rectángulo
        drawRect(
            color = color,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = strokeWidth)
        )

        // Esquinas marcadas
        val cornerRadius = 8.dp.toPx()
        drawCircle(
            color = color,
            radius = cornerRadius,
            center = rect.topLeft
        )
        drawCircle(
            color = color,
            radius = cornerRadius,
            center = rect.topRight
        )
        drawCircle(
            color = color,
            radius = cornerRadius,
            center = rect.bottomLeft
        )
        drawCircle(
            color = color,
            radius = cornerRadius,
            center = rect.bottomRight
        )

        // Relleno semi-transparente
        drawRect(
            color = color.copy(alpha = 0.1f),
            topLeft = rect.topLeft,
            size = rect.size
        )

        // Línea diagonal indicando el área
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = rect.topLeft,
            end = rect.bottomRight,
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = rect.topRight,
            end = rect.bottomLeft,
            strokeWidth = 1.dp.toPx()
        )
    }
}