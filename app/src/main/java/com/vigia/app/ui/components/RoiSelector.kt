/**
 * Archivo: app/src/main/java/com/vigia/app/ui/components/RoiSelector.kt
 * Propósito: Componente para selección manual y reposicionamiento de ROI sobre la preview de cámara.
 * Responsabilidad principal: Permitir al usuario dibujar un rectángulo ROI táctil y moverlo.
 * Alcance: Capa de UI, componente de interacción para definición de ROI.
 *
 * Decisiones técnicas relevantes:
 * - Basado en el patrón probado de MindaRoiOverlayView.kt (docs/legacy/)
 * - Manejo explícito de eventos táctiles: DOWN (inicio) → MOVE (arrastre) → UP (consolidación)
 * - Rectángulo temporal durante la creación (currentRect en píxeles)
 * - Conversión a coordenadas normalizadas (0.0-1.0) solo al soltar el dedo
 * - Dibujo separado: ROI temporal (amarillo) vs ROI ya guardado (verde)
 * - Modo CREACIÓN (nuevo ROI) vs modo MOVIMIENTO (reposicionar existente)
 * - Límites estrictos dentro de la vista (coerceIn)
 *
 * Limitaciones temporales del MVP:
 * - Solo un ROI rectangular
 * - Sin redimensionar por esquinas o bordes
 * - Sin rotación ni formas complejas
 *
 * Cambios recientes (2026-03-24):
 * - REESCRITURA COMPLETA basada en MindaRoiOverlayView.kt
 * - Separación clara entre rectángulo temporal (píxeles) y ROI consolidado (normalizado)
 * - Fix crítico: conversión correcta píxeles → normalizado al soltar
 * - Estados más simples y robustos: Idle, Drawing, Selected, Moving
 * - Hit test para detectar toque dentro de ROI existente
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Estado de la selección de ROI.
 * Diseño simplificado basado en MindaRoiOverlayView.
 */
sealed class RoiSelectionState {
    object Idle : RoiSelectionState()
    /** Dibujando nuevo ROI: almacena rectángulo en píxeles (no normalizado) */
    data class Drawing(val rect: Rect) : RoiSelectionState()
    /** ROI ya consolidado, almacenado en coordenadas normalizadas (0-1) */
    data class Selected(val normalizedRect: Rect) : RoiSelectionState()
    /** Moviendo ROI existente: almacena rectángulo en píxeles durante el arrastre */
    data class Moving(
        val normalizedRect: Rect,  // ROI original en normalizado
        val pixelRect: Rect,       // Posición actual en píxeles durante arrastre
        val dragOffset: Offset     // Offset del punto de agarre respecto a esquina sup-izq
    ) : RoiSelectionState()
}

/**
 * Componente para seleccionar y reposicionar un ROI rectangular sobre la cámara.
 * Basado en el patrón probado de MindaRoiOverlayView.kt.
 *
 * Flujo de creación:
 * 1. ACTION_DOWN (Press): Inicia rectángulo temporal en punto de toque
 * 2. ACTION_MOVE (Move): Actualiza esquina inferior-derecha del rectángulo temporal
 * 3. ACTION_UP (Release): Convierte a normalizado, valida tamaño mínimo, pasa a Selected
 *
 * Flujo de movimiento:
 * 1. ACTION_DOWN dentro de ROI existente: Inicia modo Moving
 * 2. ACTION_MOVE: Calcula nueva posición manteniendo el tamaño, con límites en vista
 * 3. ACTION_UP: Convierte nueva posición a normalizado, pasa a Selected
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
        // Capa táctil con manejo explícito de eventos al estilo MindaRoiOverlayView
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull() ?: continue
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()

                            when (event.type) {
                                PointerEventType.Press -> {
                                    val x = pointer.position.x.coerceIn(0f, width)
                                    val y = pointer.position.y.coerceIn(0f, height)

                                    when (val currentState = selectionState) {
                                        is RoiSelectionState.Idle -> {
                                            // Iniciar creación de nuevo ROI
                                            val initialRect = Rect(x, y, x, y)
                                            selectionState = RoiSelectionState.Drawing(initialRect)
                                        }
                                        is RoiSelectionState.Selected -> {
                                            // Verificar si el toque está dentro del ROI para moverlo
                                            val pixelRect = normalizedToPixelRect(currentState.normalizedRect, width, height)
                                            if (pixelRect.contains(Offset(x, y))) {
                                                // Iniciar movimiento: calcular offset del punto de agarre
                                                val dragOffset = Offset(x - pixelRect.left, y - pixelRect.top)
                                                selectionState = RoiSelectionState.Moving(
                                                    normalizedRect = currentState.normalizedRect,
                                                    pixelRect = pixelRect,
                                                    dragOffset = dragOffset
                                                )
                                            } else {
                                                // Tocar fuera reinicia la selección (nuevo ROI)
                                                val initialRect = Rect(x, y, x, y)
                                                selectionState = RoiSelectionState.Drawing(initialRect)
                                            }
                                        }
                                        is RoiSelectionState.Drawing,
                                        is RoiSelectionState.Moving -> {
                                            // Ignorar si ya estamos en medio de una operación
                                        }
                                    }
                                }

                                PointerEventType.Move -> {
                                    val x = pointer.position.x.coerceIn(0f, width)
                                    val y = pointer.position.y.coerceIn(0f, height)

                                    when (val currentState = selectionState) {
                                        is RoiSelectionState.Drawing -> {
                                            // Actualizar esquina inferior-derecha del rectángulo temporal
                                            val startLeft = currentState.rect.left
                                            val startTop = currentState.rect.top
                                            val newRect = Rect(
                                                left = min(startLeft, x),
                                                top = min(startTop, y),
                                                right = max(startLeft, x),
                                                bottom = max(startTop, y)
                                            )
                                            selectionState = RoiSelectionState.Drawing(newRect)
                                        }
                                        is RoiSelectionState.Moving -> {
                                            // Mover el ROI manteniendo su tamaño
                                            val originalNorm = currentState.normalizedRect
                                            val w = originalNorm.width * width
                                            val h = originalNorm.height * height

                                            // Nueva esquina superior-izquierda basada en posición del dedo menos offset
                                            val newLeft = (x - currentState.dragOffset.x).coerceIn(0f, width - w)
                                            val newTop = (y - currentState.dragOffset.y).coerceIn(0f, height - h)

                                            val newPixelRect = Rect(
                                                left = newLeft,
                                                top = newTop,
                                                right = newLeft + w,
                                                bottom = newTop + h
                                            )
                                            selectionState = currentState.copy(pixelRect = newPixelRect)
                                        }
                                        else -> {}
                                    }

                                    pointer.consume()
                                }

                                PointerEventType.Release -> {
                                    when (val currentState = selectionState) {
                                        is RoiSelectionState.Drawing -> {
                                            // Consolidar ROI: convertir a coordenadas normalizadas
                                            val pixelRect = currentState.rect
                                            val normalized = pixelsToNormalizedRect(pixelRect, width, height)

                                            // Validar tamaño mínimo (2% del área)
                                            if (normalized.width > 0.02f && normalized.height > 0.02f) {
                                                selectionState = RoiSelectionState.Selected(normalized)
                                            } else {
                                                // ROI demasiado pequeño, cancelar
                                                selectionState = RoiSelectionState.Idle
                                            }
                                        }
                                        is RoiSelectionState.Moving -> {
                                            // Finalizar movimiento: convertir posición actual a normalizado
                                            val normalized = pixelsToNormalizedRect(currentState.pixelRect, width, height)
                                            selectionState = RoiSelectionState.Selected(normalized)
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Dibujar según el estado (siguiendo patrón de MindaRoiOverlayView)
            when (val state = selectionState) {
                is RoiSelectionState.Drawing -> {
                    // Rectángulo temporal durante creación (amarillo, estilo provisional)
                    drawRoiRect(state.rect, isFinal = false, isMoving = false)
                }
                is RoiSelectionState.Moving -> {
                    // Rectángulo durante movimiento (amarillo con indicadores)
                    drawRoiRect(state.pixelRect, isFinal = false, isMoving = true)
                }
                is RoiSelectionState.Selected -> {
                    // ROI ya consolidado (verde, estilo final)
                    val pixelRect = normalizedToPixelRect(state.normalizedRect, size.width, size.height)
                    drawRoiRect(pixelRect, isFinal = true, isMoving = false)
                }
                else -> {} // Idle: no dibujar nada
            }
        }

        // Controles de acción y hints según el estado
        when (val state = selectionState) {
            is RoiSelectionState.Selected -> {
                SelectionControls(
                    onConfirm = {
                        val roi = rectToRoi(state.normalizedRect)
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
                Text(
                    text = "Moviendo ROI...",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            is RoiSelectionState.Drawing -> {
                Text(
                    text = "Suelta para fijar el área",
                    color = Color.Yellow,
                    fontSize = 14.sp,
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
                    fontSize = 14.sp,
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
 * Estilo basado en MindaRoiOverlayView.
 *
 * @param rect Rectángulo en coordenadas de píxeles
 * @param isFinal true = ROI ya consolidado (verde), false = ROI temporal (amarillo)
 * @param isMoving true = modo movimiento (indicadores adicionales)
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
    val fillAlpha = if (isMoving) 0.25f else if (isFinal) 0.15f else 0.2f

    // Relleno semi-transparente (como en MindaRoiOverlayView)
    drawRect(
        color = color.copy(alpha = fillAlpha),
        topLeft = rect.topLeft,
        size = rect.size
    )

    // Borde del rectángulo
    drawRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = strokeWidth)
    )

    // Esquinas marcadas con círculos
    val cornerRadius = if (isFinal) 6.dp.toPx() else 4.dp.toPx()
    drawCircle(color = color, radius = cornerRadius, center = rect.topLeft)
    drawCircle(color = color, radius = cornerRadius, center = rect.topRight)
    drawCircle(color = color, radius = cornerRadius, center = rect.bottomLeft)
    drawCircle(color = color, radius = cornerRadius, center = rect.bottomRight)

    // Indicador de movimiento (cruz en el centro)
    if (isMoving) {
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2
        val crossSize = 15.dp.toPx()
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
 * Convierte un rectángulo en píxeles a coordenadas normalizadas (0.0-1.0).
 * Corrige el bug anterior donde se pasaban píxeles a coerceIn sin dividir.
 */
private fun pixelsToNormalizedRect(pixelRect: Rect, width: Float, height: Float): Rect {
    return Rect(
        left = (min(pixelRect.left, pixelRect.right) / width).coerceIn(0f, 1f),
        top = (min(pixelRect.top, pixelRect.bottom) / height).coerceIn(0f, 1f),
        right = (max(pixelRect.left, pixelRect.right) / width).coerceIn(0f, 1f),
        bottom = (max(pixelRect.top, pixelRect.bottom) / height).coerceIn(0f, 1f)
    )
}

/**
 * Convierte un rectángulo normalizado (0.0-1.0) a coordenadas de píxeles.
 */
private fun normalizedToPixelRect(normalizedRect: Rect, width: Float, height: Float): Rect {
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