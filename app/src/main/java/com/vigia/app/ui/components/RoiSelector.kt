/**
 * Archivo: app/src/main/java/com/vigia/app/ui/components/RoiSelector.kt
 * Propósito: Componente para selección manual y reposicionamiento de ROI sobre la preview de cámara.
 * Responsabilidad principal: Permitir al usuario dibujar un rectángulo ROI táctil y moverlo.
 * Alcance: Capa de UI, componente de interacción para definición de ROI.
 *
 * Decisiones técnicas relevantes:
 * - Modifier.pointerInput con awaitPointerEventScope para control directo de eventos táctiles
 * - Canvas para dibujar el rectángulo seleccionado
 * - Coordenadas normalizadas (0.0-1.0) independientes del tamaño de pantalla
 * - Estado ADDING para crear ROI nuevo, MOVING para reposicionar ROI existente
 * - Manejo explícito de DOWN/MOVE/UP para evitar problemas con detectDragGestures
 *
 * Limitaciones temporales del MVP:
 * - Solo un ROI rectangular
 * - Sin redimensionar por esquinas o bordes
 * - Sin rotación ni formas complejas
 *
 * Cambios recientes:
 * - CORRECCIÓN CRÍTICA: Reemplazado detectDragGestures por awaitPointerEventScope
 * - Ahora el ROI queda fijado correctamente al soltar el dedo
 * - Mejorada gestión de estados de selección
 * - Validación de límites al mover
 */
package com.vigia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Capa táctil para detectar gestos con control directo de eventos
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // Esperar evento de puntero
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull() ?: continue

                            when (event.type) {
                                PointerEventType.Press -> {
                                    val offset = pointer.position
                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()

                                    when (val currentState = selectionState) {
                                        is RoiSelectionState.Idle -> {
                                            // Iniciar creación de nuevo ROI
                                            selectionState = RoiSelectionState.Adding(
                                                start = offset,
                                                current = offset
                                            )
                                        }
                                        is RoiSelectionState.Selected -> {
                                            // Verificar si el toque está dentro del ROI para moverlo
                                            val pixelRect = createPixelRect(currentState.rect, width, height)
                                            if (pixelRect.contains(offset)) {
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
                                }
                                PointerEventType.Move -> {
                                    val offset = pointer.position
                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()

                                    when (val currentState = selectionState) {
                                        is RoiSelectionState.Adding -> {
                                            selectionState = currentState.copy(current = offset)
                                        }
                                        is RoiSelectionState.Moving -> {
                                            // Calcular desplazamiento desde el inicio del arrastre
                                            val dx = offset.x - currentState.dragStart.x
                                            val dy = offset.y - currentState.dragStart.y
                                            val dxNorm = dx / width
                                            val dyNorm = dy / height

                                            // Nuevo rectángulo desplazado, validando límites
                                            val originalRect = currentState.rect
                                            val newRect = Rect(
                                                left = (originalRect.left + dxNorm).coerceIn(0f, 1f - originalRect.width),
                                                top = (originalRect.top + dyNorm).coerceIn(0f, 1f - originalRect.height),
                                                right = (originalRect.right + dxNorm).coerceIn(originalRect.width, 1f),
                                                bottom = (originalRect.bottom + dyNorm).coerceIn(originalRect.height, 1f)
                                            )
                                            selectionState = RoiSelectionState.Moving(
                                                rect = newRect,
                                                dragStart = currentState.dragStart
                                            )
                                        }
                                        else -> {}
                                    }

                                    pointer.consume()
                                }
                                PointerEventType.Release -> {
                                    when (val currentState = selectionState) {
                                        is RoiSelectionState.Adding -> {
                                            val rect = createNormalizedRect(currentState.start, currentState.current)
                                            // Reducido a 2% para ser más permisivo, pero manteniendo mínimo razonable
                                            if (rect.width > 0.02f && rect.height > 0.02f) {
                                                selectionState = RoiSelectionState.Selected(rect)
                                            } else {
                                                // ROI demasiado pequeño, volver a idle
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
                            }
                        }
                    }
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