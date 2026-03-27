/**
 * Archivo: app/src/main/java/com/vigia/app/ui/components/RoiSelector.kt
 * Propósito: Componente para selección manual dual de ROI Global y SubROI del transfer.
 * Responsabilidad principal: Permitir al usuario definir primero el ROI global (vía completa)
 * y luego la SubROI (solo el cuerpo del transfer) sobre la preview de cámara.
 * Alcance: Capa de UI, componente de interacción para definición precisa de regiones.
 *
 * Decisiones técnicas relevantes:
 * - Flujo en dos pasos: 1) ROI Global (azul), 2) SubROI Transfer (naranja)
 * - Cada región se puede dibujar y mover independientemente
 * - Coordenadas normalizadas (0.0-1.0) para independencia de resolución
 * - SubROI se almacena relativa al ROI global (0.0-1.0 dentro del ROI)
 * - Manejo explícito de eventos táctiles: DOWN → MOVE → UP
 * - Dibujo separado: temporal (semi-transparente) vs final (sólido)
 *
 * Limitaciones temporales del MVP:
 * - Solo un ROI global y una SubROI por sesión
 * - Sin redimensionar por esquinas o bordes
 * - Sin rotación ni formas complejas
 * - No se puede editar SubROI sin redefinir desde cero
 *
 * Cambios recientes (2026-03-27):
 * - REESCRITURA para soporte de SubROI manual del transfer
 * - Dos fases de selección: Global → Transfer
 * - Colores diferenciados: Azul (global), Naranja (transfer)
 * - SubROI almacenada como coordenadas relativas al ROI
 * - Validación de que SubROI está contenida dentro del ROI global
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
import com.vigia.app.domain.model.RelativeSubRoi
import com.vigia.app.domain.model.Roi
import kotlin.math.max
import kotlin.math.min

/**
 * Fase actual de la selección de ROI.
 */
enum class SelectionPhase {
    GLOBAL,     // Definiendo ROI global (vía completa)
    SUBROI,     // Definiendo SubROI del transfer (dentro del global)
    CONFIRM     // Ambos definidos, listo para confirmar
}

/**
 * Estado de una región individual (global o subROI).
 */
sealed class RegionState {
    object Undefined : RegionState()
    /** Dibujando nueva región: almacena rectángulo en píxeles */
    data class Drawing(val rect: Rect) : RegionState()
    /** Región ya consolidada, almacenada en coordenadas normalizadas */
    data class Defined(val normalizedRect: Rect) : RegionState()
    /** Moviendo región existente: almacena rectángulo en píxeles durante arrastre */
    data class Moving(
        val normalizedRect: Rect,
        val pixelRect: Rect,
        val dragOffset: Offset
    ) : RegionState()
}

/**
 * Componente para seleccionar ROI Global y SubROI del transfer en dos fases.
 *
 * Flujo:
 * 1. Fase GLOBAL: Dibuja ROI azul sobre toda la vía
 * 2. Fase SUBROI: Dibuja ROI naranja solo sobre el cuerpo del transfer (dentro del azul)
 * 3. Fase CONFIRM: Revisa ambas regiones y confirma
 *
 * @param onRoiSelected Callback cuando se confirman ambas regiones (ROI con SubROI relativa)
 * @param onCancel Callback cuando se cancela la selección
 * @param modifier Modificador para el layout
 */
@Composable
fun RoiSelector(
    onRoiSelected: (Roi) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf(SelectionPhase.GLOBAL) }
    var globalState by remember { mutableStateOf<RegionState>(RegionState.Undefined) }
    var subRoiState by remember { mutableStateOf<RegionState>(RegionState.Undefined) }

    Box(modifier = modifier.fillMaxSize()) {
        // Capa táctil
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(phase) {
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

                                    when (phase) {
                                        SelectionPhase.GLOBAL -> handleGlobalPress(x, y, width, height, globalState) { globalState = it }
                                        SelectionPhase.SUBROI -> handleSubRoiPress(x, y, width, height, globalState, subRoiState) { subRoiState = it }
                                        SelectionPhase.CONFIRM -> handleConfirmPress(x, y, width, height, globalState, subRoiState, 
                                            onGlobalChange = { globalState = it },
                                            onSubRoiChange = { subRoiState = it }
                                        )
                                    }
                                }

                                PointerEventType.Move -> {
                                    val x = pointer.position.x.coerceIn(0f, width)
                                    val y = pointer.position.y.coerceIn(0f, height)

                                    when (phase) {
                                        SelectionPhase.GLOBAL -> handleGlobalMove(x, y, width, height, globalState) { globalState = it }
                                        SelectionPhase.SUBROI -> handleSubRoiMove(x, y, width, height, globalState, subRoiState) { subRoiState = it }
                                        SelectionPhase.CONFIRM -> handleConfirmMove(x, y, width, height, globalState, subRoiState,
                                            onGlobalChange = { globalState = it },
                                            onSubRoiChange = { subRoiState = it }
                                        )
                                    }
                                    pointer.consume()
                                }

                                PointerEventType.Release -> {
                                    when (phase) {
                                        SelectionPhase.GLOBAL -> {
                                            val newState = finalizeGlobal(globalState, width, height)
                                            globalState = newState
                                            if (newState is RegionState.Defined) {
                                                phase = SelectionPhase.SUBROI
                                            }
                                        }
                                        SelectionPhase.SUBROI -> {
                                            val newState = finalizeSubRoi(subRoiState, width, height)
                                            subRoiState = newState
                                            if (newState is RegionState.Defined) {
                                                phase = SelectionPhase.CONFIRM
                                            }
                                        }
                                        SelectionPhase.CONFIRM -> {
                                            globalState = finalizeAny(globalState, width, height)
                                            subRoiState = finalizeAny(subRoiState, width, height)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Dibujar ROI Global (siempre visible una vez definido)
            val globalRect = when (val state = globalState) {
                is RegionState.Drawing -> state.rect
                is RegionState.Defined -> normalizedToPixelRect(state.normalizedRect, size.width, size.height)
                is RegionState.Moving -> state.pixelRect
                else -> null
            }
            globalRect?.let {
                val isFinal = globalState is RegionState.Defined || phase == SelectionPhase.CONFIRM
                drawRoiRect(it, color = Color(0xFF2196F3), isFinal = isFinal, label = "VÍA")
            }

            // Dibujar SubROI (visible en fase SUBROI y CONFIRM)
            if (phase != SelectionPhase.GLOBAL) {
                val subRect = when (val state = subRoiState) {
                    is RegionState.Drawing -> state.rect
                    is RegionState.Defined -> normalizedToPixelRect(state.normalizedRect, size.width, size.height)
                    is RegionState.Moving -> state.pixelRect
                    else -> null
                }
                subRect?.let {
                    val isFinal = subRoiState is RegionState.Defined || phase == SelectionPhase.CONFIRM
                    drawRoiRect(it, color = Color(0xFFFF9800), isFinal = isFinal, label = "TRANSFER")
                }
            }
        }

        // UI de instrucciones y controles según la fase
        SelectionUi(
            phase = phase,
            globalState = globalState,
            subRoiState = subRoiState,
            onNextPhase = {
                when (phase) {
                    SelectionPhase.GLOBAL -> if (globalState is RegionState.Defined) phase = SelectionPhase.SUBROI
                    SelectionPhase.SUBROI -> if (subRoiState is RegionState.Defined) phase = SelectionPhase.CONFIRM
                    else -> {}
                }
            },
            onConfirm = {
                val roi = createRoiWithSubRoi(globalState, subRoiState)
                if (roi != null) onRoiSelected(roi)
            },
            onCancel = onCancel,
            onResetSubRoi = {
                subRoiState = RegionState.Undefined
                phase = SelectionPhase.SUBROI
            },
            onResetAll = {
                globalState = RegionState.Undefined
                subRoiState = RegionState.Undefined
                phase = SelectionPhase.GLOBAL
            }
        )
    }
}

// === Handlers de eventos táctiles ===

private fun handleGlobalPress(
    x: Float, y: Float, width: Float, height: Float,
    currentState: RegionState,
    update: (RegionState) -> Unit
) {
    when (currentState) {
        is RegionState.Undefined -> {
            update(RegionState.Drawing(Rect(x, y, x, y)))
        }
        is RegionState.Defined -> {
            val pixelRect = normalizedToPixelRect(currentState.normalizedRect, width, height)
            if (pixelRect.contains(Offset(x, y))) {
                val dragOffset = Offset(x - pixelRect.left, y - pixelRect.top)
                update(RegionState.Moving(currentState.normalizedRect, pixelRect, dragOffset))
            } else {
                update(RegionState.Drawing(Rect(x, y, x, y)))
            }
        }
        else -> {}
    }
}

private fun handleGlobalMove(
    x: Float, y: Float, width: Float, height: Float,
    currentState: RegionState,
    update: (RegionState) -> Unit
) {
    when (currentState) {
        is RegionState.Drawing -> {
            val startLeft = currentState.rect.left
            val startTop = currentState.rect.top
            update(RegionState.Drawing(Rect(
                left = min(startLeft, x),
                top = min(startTop, y),
                right = max(startLeft, x),
                bottom = max(startTop, y)
            )))
        }
        is RegionState.Moving -> {
            val w = currentState.normalizedRect.width * width
            val h = currentState.normalizedRect.height * height
            val newLeft = (x - currentState.dragOffset.x).coerceIn(0f, width - w)
            val newTop = (y - currentState.dragOffset.y).coerceIn(0f, height - h)
            update(currentState.copy(pixelRect = Rect(
                left = newLeft, top = newTop,
                right = newLeft + w, bottom = newTop + h
            )))
        }
        else -> {}
    }
}

private fun handleSubRoiPress(
    x: Float, y: Float, width: Float, height: Float,
    globalState: RegionState,
    subRoiState: RegionState,
    update: (RegionState) -> Unit
) {
    // Verificar que el toque está dentro del ROI global
    val globalRect = when (globalState) {
        is RegionState.Defined -> normalizedToPixelRect(globalState.normalizedRect, width, height)
        is RegionState.Moving -> globalState.pixelRect
        else -> return
    }
    
    if (!globalRect.contains(Offset(x, y))) return // Ignorar toques fuera del global

    when (subRoiState) {
        is RegionState.Undefined -> {
            update(RegionState.Drawing(Rect(x, y, x, y)))
        }
        is RegionState.Defined -> {
            val pixelRect = normalizedToPixelRect(subRoiState.normalizedRect, width, height)
            if (pixelRect.contains(Offset(x, y))) {
                val dragOffset = Offset(x - pixelRect.left, y - pixelRect.top)
                update(RegionState.Moving(subRoiState.normalizedRect, pixelRect, dragOffset))
            } else {
                update(RegionState.Drawing(Rect(x, y, x, y)))
            }
        }
        else -> {}
    }
}

private fun handleSubRoiMove(
    x: Float, y: Float, width: Float, height: Float,
    globalState: RegionState,
    subRoiState: RegionState,
    update: (RegionState) -> Unit
) {
    // Obtener límites del ROI global
    val globalRect = when (globalState) {
        is RegionState.Defined -> normalizedToPixelRect(globalState.normalizedRect, width, height)
        is RegionState.Moving -> globalState.pixelRect
        else -> return
    }

    when (subRoiState) {
        is RegionState.Drawing -> {
            val startLeft = subRoiState.rect.left
            val startTop = subRoiState.rect.top
            // Limitar al ROI global
            val newLeft = min(startLeft, x).coerceIn(globalRect.left, globalRect.right)
            val newTop = min(startTop, y).coerceIn(globalRect.top, globalRect.bottom)
            val newRight = max(startLeft, x).coerceIn(globalRect.left, globalRect.right)
            val newBottom = max(startTop, y).coerceIn(globalRect.top, globalRect.bottom)
            update(RegionState.Drawing(Rect(newLeft, newTop, newRight, newBottom)))
        }
        is RegionState.Moving -> {
            val w = subRoiState.normalizedRect.width * width
            val h = subRoiState.normalizedRect.height * height
            val newLeft = (x - subRoiState.dragOffset.x).coerceIn(globalRect.left, globalRect.right - w)
            val newTop = (y - subRoiState.dragOffset.y).coerceIn(globalRect.top, globalRect.bottom - h)
            update(subRoiState.copy(pixelRect = Rect(
                left = newLeft, top = newTop,
                right = newLeft + w, bottom = newTop + h
            )))
        }
        else -> {}
    }
}

private fun handleConfirmPress(
    x: Float, y: Float, width: Float, height: Float,
    globalState: RegionState, subRoiState: RegionState,
    onGlobalChange: (RegionState) -> Unit,
    onSubRoiChange: (RegionState) -> Unit
) {
    // Verificar primero SubROI (está encima visualmente)
    when (subRoiState) {
        is RegionState.Defined -> {
            val pixelRect = normalizedToPixelRect(subRoiState.normalizedRect, width, height)
            if (pixelRect.contains(Offset(x, y))) {
                val dragOffset = Offset(x - pixelRect.left, y - pixelRect.top)
                onSubRoiChange(RegionState.Moving(subRoiState.normalizedRect, pixelRect, dragOffset))
                return
            }
        }
        else -> {}
    }
    
    // Luego verificar ROI global
    when (globalState) {
        is RegionState.Defined -> {
            val pixelRect = normalizedToPixelRect(globalState.normalizedRect, width, height)
            if (pixelRect.contains(Offset(x, y))) {
                val dragOffset = Offset(x - pixelRect.left, y - pixelRect.top)
                onGlobalChange(RegionState.Moving(globalState.normalizedRect, pixelRect, dragOffset))
            }
        }
        else -> {}
    }
}

private fun handleConfirmMove(
    x: Float, y: Float, width: Float, height: Float,
    globalState: RegionState, subRoiState: RegionState,
    onGlobalChange: (RegionState) -> Unit,
    onSubRoiChange: (RegionState) -> Unit
) {
    // Mover SubROI si está en modo moving
    when (subRoiState) {
        is RegionState.Moving -> {
            val globalRect = when (globalState) {
                is RegionState.Defined -> normalizedToPixelRect(globalState.normalizedRect, width, height)
                else -> return
            }
            val w = subRoiState.normalizedRect.width * width
            val h = subRoiState.normalizedRect.height * height
            val newLeft = (x - subRoiState.dragOffset.x).coerceIn(globalRect.left, globalRect.right - w)
            val newTop = (y - subRoiState.dragOffset.y).coerceIn(globalRect.top, globalRect.bottom - h)
            onSubRoiChange(subRoiState.copy(pixelRect = Rect(
                left = newLeft, top = newTop,
                right = newLeft + w, bottom = newTop + h
            )))
            return
        }
        else -> {}
    }
    
    // Mover ROI global si está en modo moving
    when (globalState) {
        is RegionState.Moving -> {
            val w = globalState.normalizedRect.width * width
            val h = globalState.normalizedRect.height * height
            val newLeft = (x - globalState.dragOffset.x).coerceIn(0f, width - w)
            val newTop = (y - globalState.dragOffset.y).coerceIn(0f, height - h)
            onGlobalChange(globalState.copy(pixelRect = Rect(
                left = newLeft, top = newTop,
                right = newLeft + w, bottom = newTop + h
            )))
        }
        else -> {}
    }
}

// === Finalización de regiones ===

private fun finalizeGlobal(state: RegionState, width: Float, height: Float): RegionState {
    return when (state) {
        is RegionState.Drawing -> {
            val normalized = pixelsToNormalizedRect(state.rect, width, height)
            if (normalized.width > 0.05f && normalized.height > 0.05f) {
                RegionState.Defined(normalized)
            } else RegionState.Undefined
        }
        is RegionState.Moving -> {
            RegionState.Defined(pixelsToNormalizedRect(state.pixelRect, width, height))
        }
        else -> state
    }
}

private fun finalizeSubRoi(state: RegionState, width: Float, height: Float): RegionState {
    return finalizeGlobal(state, width, height) // Misma lógica
}

private fun finalizeAny(state: RegionState, width: Float, height: Float): RegionState {
    return when (state) {
        is RegionState.Moving -> RegionState.Defined(pixelsToNormalizedRect(state.pixelRect, width, height))
        else -> state
    }
}

// === UI ===

@Composable
private fun SelectionUi(
    phase: SelectionPhase,
    globalState: RegionState,
    subRoiState: RegionState,
    onNextPhase: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onResetSubRoi: () -> Unit,
    onResetAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Instrucciones superiores
        Card(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (phase) {
                    SelectionPhase.GLOBAL -> {
                        Text(
                            "Paso 1: Define el ROI Global",
                            color = Color(0xFF2196F3),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Dibuja un rectángulo azul que cubra toda la vía del transfer",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    SelectionPhase.SUBROI -> {
                        Text(
                            "Paso 2: Define la SubROI del Transfer",
                            color = Color(0xFFFF9800),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Dibuja un rectángulo naranja solo sobre el cuerpo del transfer",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "(Dentro del área azul)",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    SelectionPhase.CONFIRM -> {
                        Text(
                            "Revisa las regiones",
                            color = Color.Green,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Azul = Vía completa | Naranja = Transfer",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Mantén pulsado para mover cualquier región",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Controles inferiores
        Card(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (phase) {
                    SelectionPhase.GLOBAL -> {
                        if (globalState is RegionState.Defined) {
                            Button(onClick = onNextPhase) {
                                Text("Continuar →")
                            }
                        }
                    }
                    SelectionPhase.SUBROI -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onResetSubRoi) {
                                Text("Repetir")
                            }
                            if (subRoiState is RegionState.Defined) {
                                Button(onClick = onNextPhase) {
                                    Text("Continuar →")
                                }
                            }
                        }
                    }
                    SelectionPhase.CONFIRM -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onResetAll) {
                                Text("Rehacer todo")
                            }
                            OutlinedButton(onClick = onCancel, colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )) {
                                Text("Cancelar")
                            }
                            Button(
                                onClick = onConfirm,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("✓ Confirmar")
                            }
                        }
                    }
                }
            }
        }
    }
}

// === Funciones de dibujo y utilidades ===

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoiRect(
    rect: Rect,
    color: Color,
    isFinal: Boolean,
    label: String
) {
    val strokeWidth = if (isFinal) 4.dp.toPx() else 2.dp.toPx()
    val fillAlpha = if (isFinal) 0.2f else 0.3f

    // Relleno
    drawRect(
        color = color.copy(alpha = fillAlpha),
        topLeft = rect.topLeft,
        size = rect.size
    )

    // Borde
    drawRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = strokeWidth)
    )

    // Esquinas
    val cornerRadius = if (isFinal) 6.dp.toPx() else 4.dp.toPx()
    drawCircle(color = color, radius = cornerRadius, center = rect.topLeft)
    drawCircle(color = color, radius = cornerRadius, center = rect.topRight)
    drawCircle(color = color, radius = cornerRadius, center = rect.bottomLeft)
    drawCircle(color = color, radius = cornerRadius, center = rect.bottomRight)

    // Etiqueta
    if (isFinal) {
        drawRect(
            color = color,
            topLeft = Offset(rect.left, rect.top - 24.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(80.dp.toPx(), 24.dp.toPx())
        )
    }
}

private fun pixelsToNormalizedRect(pixelRect: Rect, width: Float, height: Float): Rect {
    return Rect(
        left = (min(pixelRect.left, pixelRect.right) / width).coerceIn(0f, 1f),
        top = (min(pixelRect.top, pixelRect.bottom) / height).coerceIn(0f, 1f),
        right = (max(pixelRect.left, pixelRect.right) / width).coerceIn(0f, 1f),
        bottom = (max(pixelRect.top, pixelRect.bottom) / height).coerceIn(0f, 1f)
    )
}

private fun normalizedToPixelRect(normalizedRect: Rect, width: Float, height: Float): Rect {
    return Rect(
        left = normalizedRect.left * width,
        top = normalizedRect.top * height,
        right = normalizedRect.right * width,
        bottom = normalizedRect.bottom * height
    )
}

private fun createRoiWithSubRoi(globalState: RegionState, subRoiState: RegionState): Roi? {
    val globalRect = when (globalState) {
        is RegionState.Defined -> globalState.normalizedRect
        else -> return null
    }
    val subRect = when (subRoiState) {
        is RegionState.Defined -> subRoiState.normalizedRect
        else -> return null
    }

    // Convertir SubROI de coordenadas absolutas a relativas respecto al ROI global
    val globalWidth = globalRect.width
    val globalHeight = globalRect.height
    
    // Evitar división por cero
    if (globalWidth <= 0 || globalHeight <= 0) return null
    
    val relativeSubRoi = RelativeSubRoi(
        left = ((subRect.left - globalRect.left) / globalWidth).coerceIn(0f, 1f),
        top = ((subRect.top - globalRect.top) / globalHeight).coerceIn(0f, 1f),
        right = ((subRect.right - globalRect.left) / globalWidth).coerceIn(0f, 1f),
        bottom = ((subRect.bottom - globalRect.top) / globalHeight).coerceIn(0f, 1f)
    )

    return Roi(
        left = globalRect.left,
        top = globalRect.top,
        right = globalRect.right,
        bottom = globalRect.bottom,
        relativeSubRoi = relativeSubRoi
    )
}