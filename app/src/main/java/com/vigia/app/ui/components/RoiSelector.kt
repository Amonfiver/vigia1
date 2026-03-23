/**
 * Archivo: app/src/main/java/com/vigia/app/ui/components/RoiSelector.kt
 * Propósito: Componente para selección manual de ROI sobre la preview de cámara.
 * Responsabilidad principal: Permitir al usuario dibujar un rectángulo ROI táctil.
 * Alcance: Capa de UI, componente de interacción para definición de ROI.
 *
 * Decisiones técnicas relevantes:
 * - Modifier.pointerInput para detectar gestos táctiles (toque y arrastre)
 * - Canvas para dibujar el rectángulo seleccionado
 * - Coordenadas normalizadas (0.0-1.0) independientes del tamaño de pantalla
 *
 * Limitaciones temporales del MVP:
 * - Solo un ROI rectangular
 * - Sin redimensionar después de crear (se redefine desde cero)
 * - Sin rotación ni formas complejas
 *
 * Cambios recientes: Creación inicial del selector de ROI.
 */
package com.vigia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vigia.app.domain.model.Roi

/**
 * Estado de la selección de ROI.
 */
sealed class RoiSelectionState {
    object Idle : RoiSelectionState()
    data class Selecting(val start: Offset, val current: Offset) : RoiSelectionState()
    data class Selected(val rect: Rect) : RoiSelectionState()
}

/**
 * Componente para seleccionar un ROI rectangular sobre la cámara.
 *
 * @param onRoiSelected Callback cuando se confirma un ROI (coordenadas normalizadas 0.0-1.0)
 * @param onCancel Callback cuando se cancela la selección
 * @param modifier Modificador para el layout
 */
@Composable
fun RoiSelector(
    onRoiSelected: (Roi) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectionState by remember { mutableStateOf<RoiSelectionState>(RoiSelectionState.Idle) }

    Box(modifier = modifier.fillMaxSize()) {
        // Capa táctil para detectar gestos
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            selectionState = RoiSelectionState.Selecting(
                                start = offset,
                                current = offset
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val currentState = selectionState
                            if (currentState is RoiSelectionState.Selecting) {
                                selectionState = currentState.copy(current = change.position)
                            }
                        },
                        onDragEnd = {
                            val currentState = selectionState
                            if (currentState is RoiSelectionState.Selecting) {
                                val rect = createNormalizedRect(currentState.start, currentState.current, size.width.toFloat(), size.height.toFloat())
                                if (rect.width > 0.05f && rect.height > 0.05f) { // Mínimo 5% del área
                                    selectionState = RoiSelectionState.Selected(rect)
                                } else {
                                    selectionState = RoiSelectionState.Idle
                                }
                            }
                        }
                    )
                }
        ) {
            // Dibujar rectángulo según el estado
            when (val state = selectionState) {
                is RoiSelectionState.Selecting -> {
                    val rect = createRect(state.start, state.current)
                    drawRoiRect(rect, isFinal = false)
                }
                is RoiSelectionState.Selected -> {
                    drawRoiRect(state.rect, isFinal = true)
                }
                else -> {}
            }
        }

        // Controles de acción (Confirmar/Cancelar)
        when (selectionState) {
            is RoiSelectionState.Selected -> {
                SelectionControls(
                    onConfirm = {
                        val state = selectionState as RoiSelectionState.Selected
                        val roi = rectToRoi(state.rect)
                        onRoiSelected(roi)
                    },
                    onCancel = {
                        selectionState = RoiSelectionState.Idle
                        onCancel()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            is RoiSelectionState.Selecting -> {
                // Mostrar hint mientras se selecciona
                Text(
                    text = "Arrastra para definir el área",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            else -> {
                // Hint inicial
                Text(
                    text = "Toca y arrastra para definir el ROI",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Dibuja el rectángulo del ROI en el Canvas.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoiRect(rect: Rect, isFinal: Boolean) {
    val color = if (isFinal) Color.Green else Color.Yellow
    val strokeWidth = if (isFinal) 4.dp.toPx() else 2.dp.toPx()

    // Rectángulo principal
    drawRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = strokeWidth)
    )

    // Esquinas marcadas
    val cornerSize = 12.dp.toPx()
    drawCircle(
        color = color,
        radius = 6.dp.toPx(),
        center = rect.topLeft
    )
    drawCircle(
        color = color,
        radius = 6.dp.toPx(),
        center = rect.topRight
    )
    drawCircle(
        color = color,
        radius = 6.dp.toPx(),
        center = rect.bottomLeft
    )
    drawCircle(
        color = color,
        radius = 6.dp.toPx(),
        center = rect.bottomRight
    )

    // Relleno semi-transparente
    drawRect(
        color = color.copy(alpha = 0.15f),
        topLeft = rect.topLeft,
        size = rect.size
    )
}

/**
 * Controles para confirmar o cancelar la selección.
 */
@Composable
private fun SelectionControls(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(bottom = 24.dp)
            .background(Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("Confirmar ROI")
        }

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Cancelar")
        }
    }
}

/**
 * Crea un Rect desde dos puntos (sin normalizar).
 */
private fun createRect(start: Offset, end: Offset): Rect {
    val left = minOf(start.x, end.x)
    val top = minOf(start.y, end.y)
    val right = maxOf(start.x, end.x)
    val bottom = maxOf(start.y, end.y)
    return Rect(left, top, right, bottom)
}

/**
 * Crea un Rect normalizado (0.0-1.0) desde dos puntos en coordenadas de pantalla.
 */
private fun createNormalizedRect(start: Offset, end: Offset, width: Float, height: Float): Rect {
    val left = (minOf(start.x, end.x) / width).coerceIn(0f, 1f)
    val top = (minOf(start.y, end.y) / height).coerceIn(0f, 1f)
    val right = (maxOf(start.x, end.x) / width).coerceIn(0f, 1f)
    val bottom = (maxOf(start.y, end.y) / height).coerceIn(0f, 1f)
    return Rect(left, top, right, bottom)
}

/**
 * Convierte un Rect normalizado a objeto Roi del dominio.
 */
private fun rectToRoi(rect: Rect): Roi {
    return Roi(
        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom
    )
}