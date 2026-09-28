package com.brendan.controlanything.ui.dashboard.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.brendan.controlanything.domain.grid.GridEngine
import com.brendan.controlanything.domain.grid.GridPosition
import com.brendan.controlanything.domain.grid.PlacedWidget
import kotlin.math.roundToInt

/**
 * Shared chrome for a dashboard widget: in edit mode, overlays move/resize handles that
 * intercept touches before they reach [content], so individual widgets (slider, joystick, etc.)
 * never need to know edit mode exists. Outside edit mode, [content] gets all touches unimpeded.
 */
@Composable
fun WidgetFrame(
    isEditMode: Boolean,
    position: GridPosition,
    columnCount: Int,
    cellSizePx: Float,
    others: List<PlacedWidget>,
    onPositionChange: (GridPosition) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var dragOffset by remember(position) { mutableStateOf(Offset.Zero) }
    var resizeOffset by remember(position) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .border(
                width = if (isEditMode) 2.dp else 0.dp,
                color = if (isEditMode) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        content()

        if (isEditMode && cellSizePx > 0f) {

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(position, others, columnCount, cellSizePx) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                            },
                            onDragEnd = {
                                val colDelta = (dragOffset.x / cellSizePx).roundToInt()
                                val rowDelta = (dragOffset.y / cellSizePx).roundToInt()
                                val candidate = GridEngine.clampToGrid(
                                    position.copy(col = position.col + colDelta, row = position.row + rowDelta),
                                    columnCount,
                                )
                                if (GridEngine.canPlace(candidate, others)) {
                                    onPositionChange(candidate)
                                }
                                dragOffset = Offset.Zero
                            },
                        )
                    },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(resizeOffset.x.roundToInt(), resizeOffset.y.roundToInt()) }
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
                    .pointerInput(position, others, columnCount, cellSizePx) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeOffset += dragAmount
                            },
                            onDragEnd = {
                                val colSpanDelta = (resizeOffset.x / cellSizePx).roundToInt()
                                val rowSpanDelta = (resizeOffset.y / cellSizePx).roundToInt()
                                val candidate = GridEngine.clampToGrid(
                                    position.copy(
                                        colSpan = (position.colSpan + colSpanDelta).coerceAtLeast(1),
                                        rowSpan = (position.rowSpan + rowSpanDelta).coerceAtLeast(1),
                                    ),
                                    columnCount,
                                )
                                if (GridEngine.canPlace(candidate, others)) {
                                    onPositionChange(candidate)
                                }
                                resizeOffset = Offset.Zero
                            },
                        )
                    },
            )
        }
    }
}
