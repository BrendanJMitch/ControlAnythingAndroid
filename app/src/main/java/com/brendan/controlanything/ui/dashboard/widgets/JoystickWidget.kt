package com.brendan.controlanything.ui.dashboard.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.brendan.controlanything.domain.model.ControlDef
import kotlin.math.roundToInt

/** Joystick axes are always normalized to [-1, 1]; the schema doesn't carry a min/max for them. */
private const val AXIS_RANGE = 1f

/** Thumb diameter as a fraction of the track's radius, so it scales with the widget's own resize. */
private const val THUMB_TO_RADIUS_RATIO = 0.55f

@Composable
fun JoystickWidget(
    definition: ControlDef.Joystick,
    onValueChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var thumbOffset by remember(definition.topicX, definition.topicY) { mutableStateOf(Offset.Zero) }
    var touchOffset by remember(definition.topicX, definition.topicY) { mutableStateOf(Offset.Zero) }
    var radiusPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = with(LocalDensity.current) { (radiusPx * THUMB_TO_RADIUS_RATIO).toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .onSizeChanged { size -> radiusPx = minOf(size.width, size.height) / 2f }
            .pointerInput(definition.topicX, definition.topicY) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (radiusPx <= 0f) return@detectDragGestures
                        touchOffset = touchOffset + dragAmount
                        val distance = touchOffset.getDistance()
                        thumbOffset = if (distance > radiusPx) touchOffset * (radiusPx / distance) else touchOffset
                        // Screen y grows downward; up should mean "forward" (positive y) for driving.
                        val normalizedX = (thumbOffset.x / radiusPx).coerceIn(-AXIS_RANGE, AXIS_RANGE)
                        val normalizedY = (-thumbOffset.y / radiusPx).coerceIn(-AXIS_RANGE, AXIS_RANGE)
                        onValueChange(normalizedX, normalizedY)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        touchOffset = Offset.Zero
                        onValueChange(0f, 0f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = definition.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        )
        Box(
            modifier = Modifier
                .size(thumbSizeDp)
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}
