/**
 * Archivo: app/src/main/java/com/vigia/app/ui/components/RoiSelector.kt
 * Propósito: Componente para selección manual y reposicionamiento de ROI sobre la preview de cámara.
 * Responsabilidad principal: Permitir al usuario dibujar un rectángulo ROI táctil y moverlo.
 * Alcance: Capa de UI, componente de interacción para definición de ROI.
 *
 * Decisiones técnicas relevantes:
 * - Modifier.pointerInput para detectar gestos táctiles (toque, arrastre, reposicionamiento)
 * - Canvas para dibujar el rectángulo seleccionado
 * - Coordenadas normalizadas (0.0-1.0) independientes del tamaño de pantalla
 * - Estado ADDING para crear ROI nuevo, MOVING para reposicionar ROI existente
 *
 * Limitaciones temporales del MVP:
 * - Solo un ROI rectangular
 * - Sin redimensionar por esquinas o bordes
 * - Sin rotación ni formas complejas
 *
 * Cambios recientes:
 * - Añadido modo de reposicionamiento (mover ROI completo)
 * - Mejorada gestión de estados de selección
 * - Validación de límites al mover
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
    data class Adding(val start: Offset, val current: Offset) : RoiSelectionState()
    data class Moving(val rect: Rect, val dragStart: Offset) : RoiSelectionState()
    data class Selected(val rect: Rect) : RoiSelectionState()
}

/**
 * Componente para seleccionar y reposicionar un ROI rectangular sobre la cámara.
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
                            val currentState = selectionState
                            when (currentState) {
                                is RoiSelectionState.Idle -> {
                                    // Iniciar creación de nuevo ROI
                                    selectionState = RoiSelectionState.Adding(
                                        start = offset,
                                        current = offset
                                    )
                                }
                                is RoiSelectionState.Selected -> {
                                    // Verificar si el toque está dentro del ROI para moverlo
                                    val rect = createPixelRect(currentState.rect, size.width.toFloat(), size.height.toFloat())
                                    if (rect.contains(offset)) {
                                        selectionState = RoiSelectionState.Moving(
                                            rect = currentState.rect,
                                            dragStart = offset
                                        )
                                    } else {
                                        // Tocar fuera reinicia la selección
                                        selectionState = RoiSelectionState.Adding(
                                            start = offset,
                                            current = offset
                                        )
                                    }
                                }
                                else -> {}
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val currentState = selectionState
                            when (currentState) {
                                is RoiSelectionState.Adding -> {
                                    selectionState = currentState.copy(current = change.position)
                                }
                                is RoiSelectionState.Moving -> {
                                    // Calcular desplazamiento en coordenadas normalizadas
                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()
                                    val dxNorm = dragAmount.x / width
                                    val dyNorm = dragAmount.y / height

                                    // Nuevo rectángulo desplazado, validando límites
                                    val newRect = Rect(
                                        left = (currentState.rect.left + dxNorm).coerceIn(0f, 1f - currentState.rect.width),
                                        top = (currentState.rect.top + dyNorm).coerceIn(0f, 1f - currentState.rect.height),
                                        right = (currentState.rect.right + dxNorm).coerceIn(currentState.rect.width, 1f),
                                        bottom = (currentState.rect.bottom + dyNorm).coerceIn(currentState.rect.height, 1f)
                                    )
                                    selectionState = RoiSelectionState.Moving(
                                        rect = newRect,
                                        dragStart = currentState.dragStart
                                    )
                                }
                                else -> {}
                            }
                        },
                        onDragEnd = {
                            val currentState = selectionState
                            when (currentState) {
                                is RoiSelectionState.Adding -> {
                                    val rect = createNormalizedRect(currentState.start, currentState.current)
                                    if (rect.width > 0.05f && rect.height > 0.05f) { // Mínimo 5% del área
                                        selectionState = RoiSelectionState.Selected(rect)
                                    } else {
                                        selectionState = RoiSelectionState.Idle
                                    }
                                }
                                is RoiSelectionState.Moving -> {
                                    // Finalizar reposicionamiento, mantener el ROI seleccionado
                                    selectionState = RoiSelectionState.Selected(currentState.rect)
                                }
                                else -> {}
                            }
                        }
                    )
                }
        ) {
            // Dibujar rectángulo según el estado
            when (val state = selectionState) {
                is RoiSelectionState.Adding -> {
                    val rect = createRect(state.start, state.current)
                    drawRoiRect(rect, isFinal = false)
                }
                is RoiSelectionState.Moving -> {
                    val rect = createPixelRect(state.rect, size.width, size.height)
                    drawRoiRect(rect, isFinal = true, isMoving = true)
                }
                is RoiSelectionState.Selected -> {
                    val rect = createPixelRect(state.rect, size.width, size.height)
                    drawRoiRect(rect, isFinal = true)
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
                    showHint = true,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            is RoiSelectionState.Moving -> {
                // Hint mientras se mueve
                Text(
                    text = "Moviendo ROI...",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            is RoiSelectionState.Adding -> {
                Text(
                    text = "Arrastra para crear el área",
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
                    text = "Toca y arrastra para crear ROI\nMantén pulsado dentro para mover",
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoiRect(
    rect: Rect,
    isFinal: Boolean,
    isMoving: Boolean = false
) {
    val color = when {
        isMoving -> Color.Yellow
        isFinal -> Color.Green
        else -> Color.Yellow
    }
    val strokeWidth = if (isFinal) 4.dp.toPx() else 2.dp.toPx()

    // Rectángulo principal
    drawRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = strokeWidth)
    )

    // Esquinas marcadas
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
        color = color.copy(alpha = if (isMoving) 0.25f else 0.15f),
        topLeft = rect.topLeft,
        size = rect.size
    )

    // Indicador de movimiento (cruz en el centro)
    if (isMoving) {
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2
        val crossSize = 20.dp.toPx()
        drawLine(
            color = color,
            start = Offset(centerX - crossSize, centerY),
            end = Offset(centerX + crossSize, centerY),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = color,
            start = Offset(centerX, centerY - crossSize),
            end = Offset(centerX, centerY + crossSize),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/**
 * Controles para confirmar o cancelar la selección.
 */
@Composable
private fun SelectionControls(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    showHint: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(bottom = 24.dp)
            .background(Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showHint) {
            Text(
                text = "Mantén pulsado dentro del área para mover",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
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
}

/**
 * Crea un Rect desde dos puntos en píxeles (sin normalizar).
 */
private fun createRect(start: Offset, end: Offset): Rect {
    val left = minOf(start.x, end.x)
    val top = minOf(start.y, end.y)
    val right = maxOf(start.x, end.x)
    val bottom = maxOf(start.y, end.y)
    return Rect(left, top, right, bottom)
}

/**
 * Crea un Rect normalizado (0.0-1.0) desde dos puntos.
 */
private fun createNormalizedRect(start: Offset, end: Offset): Rect {
    val left = minOf(start.x, end.x).coerceIn(0f, 1f)
    val top = minOf(start.y, end.y).coerceIn(0f, 1f)
    val right = maxOf(start.x, end.x).coerceIn(0f, 1f)
    val bottom = maxOf(start.y, end.y).coerceIn(0f, 1f)
    return Rect(left, top, right, bottom)
}

/**
 * Convierte un Rect normalizado a coordenadas de píxeles.
 */
private fun createPixelRect(normalizedRect: Rect, width: Float, height: Float): Rect {
    return Rect(
        left = normalizedRect.left * width,
        top = normalizedRect.top * height,
        right = normalizedRect.right * width,
        bottom = normalizedRect.bottom * height
    )
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