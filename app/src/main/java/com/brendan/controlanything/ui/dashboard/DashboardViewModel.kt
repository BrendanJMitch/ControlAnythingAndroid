package com.brendan.controlanything.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brendan.controlanything.data.device.DeviceRepository
import com.brendan.controlanything.data.layout.LayoutRepository
import com.brendan.controlanything.domain.grid.GridEngine
import com.brendan.controlanything.domain.grid.GridPosition
import com.brendan.controlanything.domain.grid.PlacedWidget
import com.brendan.controlanything.domain.model.ControlDef
import com.brendan.controlanything.domain.model.DashboardOrientation
import com.brendan.controlanything.domain.model.DeviceInfo
import com.brendan.controlanything.domain.model.OutputDef
import com.brendan.controlanything.domain.model.SliderOrientation
import com.brendan.controlanything.domain.model.TopicValue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal const val DEFAULT_COLUMN_COUNT = 4

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val layoutRepository: LayoutRepository,
) : ViewModel() {

    private val columnCount = MutableStateFlow(DEFAULT_COLUMN_COUNT)
    private val positions = MutableStateFlow<List<PlacedWidget>>(emptyList())
    private val outputValues = MutableStateFlow<Map<String, TopicValue>>(emptyMap())
    private val orientation = MutableStateFlow(DashboardOrientation.PORTRAIT)

    // kotlinx.coroutines' typed combine() only goes up to 5 flows - nest to stay type-safe at 6.
    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            deviceRepository.deviceInfo,
            columnCount,
            positions,
            outputValues,
            ::CoreState,
        ),
        deviceRepository.controlValues,
        orientation,
    ) { core, controlValues, orientation ->
        DashboardUiState(core.deviceInfo, core.columnCount, core.positions, core.outputValues, controlValues, orientation)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private var observeOutputsJob: Job? = null
    private var lastDeviceInfo: DeviceInfo? = null
    private var currentProjectId: String? = null

    init {
        viewModelScope.launch {
            deviceRepository.deviceInfo.filterNotNull().collect { deviceInfo ->
                if (deviceInfo != lastDeviceInfo) {
                    lastDeviceInfo = deviceInfo
                    currentProjectId = deviceInfo.projectId
                    reconcileLayout(deviceInfo)
                    observeOutputs(deviceInfo)
                }
            }
        }
    }

    /**
     * The `info` topic is the source of truth on every (re)connect. For each control/output it
     * declares, reuses that topic's saved position if one exists for this project; otherwise
     * places it fresh via [GridEngine.reconcile]. A topic that no longer appears here just
     * leaves its old saved row unused - no migration/remap step needed. Persists the reconciled
     * result immediately so a freshly-placed widget is remembered from this point on.
     */
    private fun reconcileLayout(deviceInfo: DeviceInfo) {
        viewModelScope.launch {
            val saved = layoutRepository.loadLayout(deviceInfo.projectId)
            val savedByKey = saved?.positions?.associateBy { it.key } ?: emptyMap()
            val newColumnCount = saved?.columnCount ?: DEFAULT_COLUMN_COUNT
            val newOrientation = saved?.orientation ?: DashboardOrientation.PORTRAIT

            val entries = deviceInfo.controls.map { it.topic to it.defaultSpan() } +
                deviceInfo.outputs.map { it.topic to it.defaultSpan() }
            val reconciled = GridEngine.reconcile(entries, savedByKey, newColumnCount)

            positions.value = reconciled
            columnCount.value = newColumnCount
            orientation.value = newOrientation

            layoutRepository.saveWidgetPositions(deviceInfo.projectId, reconciled)
            layoutRepository.saveSettings(deviceInfo.projectId, newColumnCount, newOrientation)
        }
    }

    private fun observeOutputs(deviceInfo: DeviceInfo) {
        observeOutputsJob?.cancel()
        outputValues.value = emptyMap()
        observeOutputsJob = viewModelScope.launch {
            deviceInfo.outputs.forEach { output ->
                launch {
                    deviceRepository.observeOutput(output.topic).collect { raw ->
                        val value = when (output) {
                            is OutputDef.NumericReadout -> raw.toFloatOrNull()?.let { TopicValue.Number(it) }
                            is OutputDef.LedIndicator -> raw.toBooleanStrictOrNull()?.let { TopicValue.Bool(it) }
                        }
                        if (value != null) {
                            outputValues.value = outputValues.value + (output.topic to value)
                        }
                    }
                }
            }
        }
    }

    /** [newPosition] is assumed already validated (WidgetFrame checks collisions before calling this). */
    fun onWidgetMoved(key: String, newPosition: GridPosition) {
        positions.value = positions.value.map { if (it.key == key) it.copy(position = newPosition) else it }
        val projectId = currentProjectId ?: return
        val widget = positions.value.firstOrNull { it.key == key } ?: return
        viewModelScope.launch { layoutRepository.saveWidgetPosition(projectId, widget) }
    }

    fun onColumnCountChanged(newColumnCount: Int) {
        positions.value = GridEngine.rescale(positions.value, columnCount.value, newColumnCount)
        columnCount.value = newColumnCount
        persistPositionsAndSettings()
    }

    fun onOrientationChanged(newOrientation: DashboardOrientation) {
        orientation.value = newOrientation
        persistSettings()
    }

    private fun persistSettings() {
        val projectId = currentProjectId ?: return
        viewModelScope.launch { layoutRepository.saveSettings(projectId, columnCount.value, orientation.value) }
    }

    private fun persistPositionsAndSettings() {
        val projectId = currentProjectId ?: return
        viewModelScope.launch {
            layoutRepository.saveWidgetPositions(projectId, positions.value)
            layoutRepository.saveSettings(projectId, columnCount.value, orientation.value)
        }
    }

    /** [topic] is a leaf name (e.g. a joystick axis). */
    fun onControlChanged(topic: String, value: TopicValue) {
        deviceRepository.setControl(topic, value)
    }
}

/** (colSpan, rowSpan) a freshly-placed widget starts at, before the user resizes it manually. */
private fun ControlDef.defaultSpan(): Pair<Int, Int> = when (this) {
    is ControlDef.Toggle -> 1 to 1
    is ControlDef.Button -> 2 to 1
    is ControlDef.Slider -> when (orientation) {
        SliderOrientation.HORIZONTAL -> 2 to 1
        SliderOrientation.VERTICAL -> 1 to 2
    }
    is ControlDef.Joystick -> 2 to 2
}

private fun OutputDef.defaultSpan(): Pair<Int, Int> = when (this) {
    is OutputDef.NumericReadout -> 2 to 1
    is OutputDef.LedIndicator -> 1 to 1
}

/** Intermediate holder so the 6-flow combine above can stay type-safe via nesting. */
private data class CoreState(
    val deviceInfo: DeviceInfo?,
    val columnCount: Int,
    val positions: List<PlacedWidget>,
    val outputValues: Map<String, TopicValue>,
)
