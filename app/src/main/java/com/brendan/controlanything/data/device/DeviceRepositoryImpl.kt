package com.brendan.controlanything.data.device

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import com.brendan.controlanything.data.pubsub.PubSubClient
import com.brendan.controlanything.di.ApplicationScope
import com.brendan.controlanything.domain.model.ControlDef
import com.brendan.controlanything.domain.model.DeviceInfo
import com.brendan.controlanything.domain.model.TopicValue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val pubSubClient: PubSubClient,
    @ApplicationScope scope: CoroutineScope,
) : DeviceRepository {

    override val connectionState = pubSubClient.connectionState

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    override val deviceInfo = _deviceInfo.asStateFlow()

    private val _controlValues = MutableStateFlow<Map<String, TopicValue>>(emptyMap())
    override val controlValues = _controlValues.asStateFlow()

    // Buffered so emitting from the info collector never suspends waiting on a slow UI collector.
    private val _infoProblems = MutableSharedFlow<InfoProblem>(extraBufferCapacity = PROBLEM_BUFFER)
    override val infoProblems = _infoProblems.asSharedFlow()

    // The last schema seen, kept across connections (unlike deviceInfo) so a reconnect to the
    // same project can tell which control values are still valid to carry over.
    private var lastInfo: DeviceInfo? = null

    init {
        scope.launch {
            pubSubClient.subscribe(INFO_TOPIC).collect { raw ->
                val result = parseInfo(raw)
                if (result.problems.isNotEmpty()) {
                    _infoProblems.tryEmit(InfoProblem(rejected = result.info == null, result.problems, raw))
                }
                result.info?.let(::onInfoReceived)
            }
        }
    }

    override fun connect(endpoint: DeviceEndpoint) {
        _deviceInfo.value = null
        pubSubClient.connect(endpoint)
    }

    override fun disconnect() {
        pubSubClient.disconnect()
        _deviceInfo.value = null
    }

    override fun observeOutput(topic: String): Flow<String> = pubSubClient.subscribe("$OUTPUTS_PREFIX$topic")

    override fun setControl(topic: String, value: TopicValue) {
        _controlValues.update { it + (topic to value) }
        pubSubClient.publish("$CONTROLS_PREFIX$topic", value.toWire())
    }

    /**
     * Arrives once per connection (it's retained) and again if the firmware republishes it. A
     * control keeps its last commanded value only if it's the same project and the control's
     * definition is unchanged; anything new or changed starts from its default. Joysticks always
     * restart centered, since they spring back when released.
     */
    private fun onInfoReceived(info: DeviceInfo) {
        val previous = lastInfo?.takeIf { it.projectId == info.projectId }
        val previousControls = previous?.controls.orEmpty()
        val current = _controlValues.value

        val values = buildMap {
            info.controls.forEach { control ->
                val carryOver = control in previousControls && control !is ControlDef.Joystick
                control.seedValues().forEach { (topic, default) ->
                    put(topic, current[topic]?.takeIf { carryOver } ?: default)
                }
            }
        }

        lastInfo = info
        _controlValues.value = values
        _deviceInfo.value = info
        values.forEach { (topic, value) -> pubSubClient.publish("$CONTROLS_PREFIX$topic", value.toWire()) }
    }

    private companion object {
        const val INFO_TOPIC = "info"
        const val CONTROLS_PREFIX = "controls/"
        const val OUTPUTS_PREFIX = "outputs/"
        const val PROBLEM_BUFFER = 8
    }
}

/** Default value(s) per wire topic. Buttons are momentary and have no resting value to send. */
private fun ControlDef.seedValues(): Map<String, TopicValue> = when (this) {
    is ControlDef.Toggle -> mapOf(topic to TopicValue.Bool(defaultValue))
    is ControlDef.Slider -> mapOf(topic to TopicValue.Number(defaultValue))
    is ControlDef.Joystick -> mapOf(topicX to TopicValue.Number(0f), topicY to TopicValue.Number(0f))
    is ControlDef.Button -> emptyMap()
}
