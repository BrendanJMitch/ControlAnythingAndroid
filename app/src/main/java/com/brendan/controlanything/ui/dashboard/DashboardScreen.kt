package com.brendan.controlanything.ui.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brendan.controlanything.domain.grid.GridPosition
import com.brendan.controlanything.domain.grid.PlacedWidget
import com.brendan.controlanything.domain.model.ButtonMode
import com.brendan.controlanything.domain.model.ControlDef
import com.brendan.controlanything.domain.model.DashboardOrientation
import com.brendan.controlanything.domain.model.DeviceInfo
import com.brendan.controlanything.domain.model.LedColor
import com.brendan.controlanything.domain.model.OutputDef
import com.brendan.controlanything.domain.model.SliderOrientation
import com.brendan.controlanything.domain.model.TopicValue
import com.brendan.controlanything.ui.dashboard.grid.DashboardGrid
import com.brendan.controlanything.ui.dashboard.grid.gridPosition
import com.brendan.controlanything.ui.dashboard.widgets.ButtonWidget
import com.brendan.controlanything.ui.dashboard.widgets.JoystickWidget
import com.brendan.controlanything.ui.dashboard.widgets.LedIndicatorWidget
import com.brendan.controlanything.ui.dashboard.widgets.NumericReadoutWidget
import com.brendan.controlanything.ui.dashboard.widgets.SliderWidget
import com.brendan.controlanything.ui.dashboard.widgets.ToggleWidget
import com.brendan.controlanything.ui.dashboard.widgets.WidgetFrame
import com.brendan.controlanything.ui.theme.ControlAnythingTheme

private const val MIN_COLUMN_COUNT = 2
private const val MAX_COLUMN_COUNT = 12

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Auto-rotate doesn't work with a grid laid out for one screen dimension, so lock the
    // Activity to whichever orientation is chosen while this screen is on-screen, and free it
    // again on the way out rather than leaving it stuck on the last-locked orientation.
    DisposableEffect(uiState.orientation) {
        val activity = context.findActivity()
        activity?.requestedOrientation = when (uiState.orientation) {
            DashboardOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DashboardOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DashboardContent(
        uiState = uiState,
        onWidgetMoved = viewModel::onWidgetMoved,
        onColumnCountChanged = viewModel::onColumnCountChanged,
        onControlChanged = viewModel::onControlChanged,
        onOrientationChanged = viewModel::onOrientationChanged,
        modifier = modifier,
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onWidgetMoved: (String, GridPosition) -> Unit,
    onColumnCountChanged: (Int) -> Unit,
    onControlChanged: (String, TopicValue) -> Unit,
    onOrientationChanged: (DashboardOrientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditMode by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isResizeDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.deviceInfo?.deviceName ?: "Dashboard") },
                actions = {
                    IconButton(onClick = { isMenuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isEditMode) "Done editing" else "Edit layout") },
                            onClick = {
                                isEditMode = !isEditMode
                                isMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Resize grid") },
                            onClick = {
                                isResizeDialogOpen = true
                                isMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (uiState.orientation == DashboardOrientation.PORTRAIT) {
                                        "Switch to landscape"
                                    } else {
                                        "Switch to portrait"
                                    },
                                )
                            },
                            onClick = {
                                onOrientationChanged(
                                    if (uiState.orientation == DashboardOrientation.PORTRAIT) {
                                        DashboardOrientation.LANDSCAPE
                                    } else {
                                        DashboardOrientation.PORTRAIT
                                    },
                                )
                                isMenuExpanded = false
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            val cellSizePx = with(LocalDensity.current) { maxWidth.toPx() } / uiState.columnCount

            DashboardGrid(columnCount = uiState.columnCount, modifier = Modifier.fillMaxWidth()) {
                uiState.positions.forEach { widget ->
                    key(widget.key) {
                        val control = uiState.deviceInfo?.controls?.firstOrNull { it.topic == widget.key }
                        val output = uiState.deviceInfo?.outputs?.firstOrNull { it.topic == widget.key }

                        WidgetFrame(
                            isEditMode = isEditMode,
                            position = widget.position,
                            columnCount = uiState.columnCount,
                            cellSizePx = cellSizePx,
                            others = uiState.positions.filter { it.key != widget.key },
                            onPositionChange = { newPosition -> onWidgetMoved(widget.key, newPosition) },
                            modifier = Modifier
                                .gridPosition(widget.position)
                                .padding(4.dp),
                        ) {
                            when {
                                output is OutputDef.NumericReadout -> NumericReadoutWidget(
                                    definition = output,
                                    value = uiState.outputValues[output.topic] as? TopicValue.Number,
                                )
                                output is OutputDef.LedIndicator -> LedIndicatorWidget(
                                    definition = output,
                                    value = uiState.outputValues[output.topic] as? TopicValue.Bool,
                                )
                                control is ControlDef.Toggle -> ToggleWidget(
                                    definition = control,
                                    isOn = (uiState.controlValues[control.topic] as? TopicValue.Bool)?.value ?: false,
                                    onToggle = { onControlChanged(control.topic, TopicValue.Bool(it)) },
                                )
                                control is ControlDef.Button -> ButtonWidget(
                                    definition = control,
                                    onValueChange = { onControlChanged(control.topic, TopicValue.Bool(it)) },
                                )
                                control is ControlDef.Slider -> SliderWidget(
                                    definition = control,
                                    value = (uiState.controlValues[control.topic] as? TopicValue.Number)?.value
                                        ?: control.defaultValue,
                                    onValueChange = { onControlChanged(control.topic, TopicValue.Number(it)) },
                                )
                                control is ControlDef.Joystick -> JoystickWidget(
                                    definition = control,
                                    onValueChange = { x, y ->
                                        onControlChanged(control.topicX, TopicValue.Number(x))
                                        onControlChanged(control.topicY, TopicValue.Number(y))
                                    },
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    if (isResizeDialogOpen) {
        ResizeGridDialog(
            currentColumnCount = uiState.columnCount,
            onConfirm = { newColumnCount ->
                onColumnCountChanged(newColumnCount)
                isResizeDialogOpen = false
            },
            onDismiss = { isResizeDialogOpen = false },
        )
    }
}

@Composable
private fun ResizeGridDialog(
    currentColumnCount: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingColumnCount by remember { mutableIntStateOf(currentColumnCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resize grid") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Existing widgets are rescaled to fit - you may need to fix overlaps afterward.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { pendingColumnCount = (pendingColumnCount - 1).coerceAtLeast(MIN_COLUMN_COUNT) },
                        enabled = pendingColumnCount > MIN_COLUMN_COUNT,
                    ) {
                        Text("-", style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        text = "$pendingColumnCount columns",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(96.dp),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(
                        onClick = { pendingColumnCount = (pendingColumnCount + 1).coerceAtMost(MAX_COLUMN_COUNT) },
                        enabled = pendingColumnCount < MAX_COLUMN_COUNT,
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pendingColumnCount) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun fakeDeviceInfoForPreview() = DeviceInfo(
    deviceId = "preview-device",
    deviceName = "Preview Rover",
    projectId = "preview_project",
    schemaHash = "preview",
    controls = listOf(
        ControlDef.Slider("speed", "Speed", min = -1f, max = 1f, defaultValue = 0f),
        ControlDef.Toggle("headlights", "Headlights", defaultValue = true),
        ControlDef.Button("horn", "Horn", mode = ButtonMode.STATE),
        ControlDef.Joystick("drive_x", "drive_y", "Drive"),
        ControlDef.Slider(
            "tilt",
            "Tilt",
            min = 0f,
            max = 180f,
            defaultValue = 90f,
            orientation = SliderOrientation.VERTICAL,
        ),
    ),
    outputs = listOf(
        OutputDef.NumericReadout("battery", "Battery", suffix = "V"),
        OutputDef.LedIndicator("status", "Status", color = LedColor.CYAN),
    ),
)

private fun fakePositionsForPreview() = listOf(
    PlacedWidget("speed", GridPosition(col = 0, row = 0, colSpan = 2, rowSpan = 1)),
    PlacedWidget("headlights", GridPosition(col = 2, row = 0, colSpan = 1, rowSpan = 1)),
    PlacedWidget("horn", GridPosition(col = 3, row = 0, colSpan = 1, rowSpan = 1)),
    PlacedWidget("drive_x", GridPosition(col = 0, row = 1, colSpan = 2, rowSpan = 2)),
    PlacedWidget("battery", GridPosition(col = 2, row = 1, colSpan = 2, rowSpan = 1)),
    PlacedWidget("status", GridPosition(col = 2, row = 2, colSpan = 1, rowSpan = 1)),
    PlacedWidget("tilt", GridPosition(col = 3, row = 1, colSpan = 1, rowSpan = 2)),
)

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun DashboardScreenPreview() {
    ControlAnythingTheme {
        DashboardContent(
            uiState = DashboardUiState(
                deviceInfo = fakeDeviceInfoForPreview(),
                columnCount = 4,
                positions = fakePositionsForPreview(),
                outputValues = mapOf("battery" to TopicValue.Number(12.4f), "status" to TopicValue.Bool(true)),
                controlValues = mapOf(
                    "speed" to TopicValue.Number(0.2f),
                    "headlights" to TopicValue.Bool(true),
                    "tilt" to TopicValue.Number(90f),
                ),
            ),
            onWidgetMoved = { _, _ -> },
            onColumnCountChanged = {},
            onControlChanged = { _, _ -> },
            onOrientationChanged = {},
        )
    }
}
