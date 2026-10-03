package com.brendan.controlanything.data.device

import com.brendan.controlanything.domain.model.ButtonMode
import com.brendan.controlanything.domain.model.ControlDef
import com.brendan.controlanything.domain.model.DeviceInfo
import com.brendan.controlanything.domain.model.LedColor
import com.brendan.controlanything.domain.model.OutputDef
import com.brendan.controlanything.domain.model.SliderOrientation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

// Unknown keys are ignored so the firmware can add schema fields ahead of the app understanding them.
private val infoJson = Json { ignoreUnknownKeys = true }

/**
 * [info] is null if the payload was unusable as a whole. [problems] describes everything that
 * didn't parse cleanly - including entries dropped from an otherwise usable schema - so a
 * firmware/app mismatch can be reported instead of silently producing a blank or partial dashboard.
 */
data class InfoParseResult(
    val info: DeviceInfo?,
    val problems: List<String>,
)

/** Parses a raw `info` payload. Never throws. */
fun parseInfo(raw: String): InfoParseResult {
    val message = try {
        infoJson.decodeFromString<InfoMessage>(raw)
    } catch (e: IllegalArgumentException) { // SerializationException is a subclass.
        return InfoParseResult(info = null, problems = listOf(e.message ?: e.javaClass.simpleName))
    }
    val problems = mutableListOf<String>()
    val info = DeviceInfo(
        deviceId = message.device_id,
        deviceName = message.device_name,
        projectId = message.project_id,
        schemaHash = message.schema_hash,
        controls = message.controls.mapEntries("controls", problems) { it.toControlDef() },
        outputs = message.outputs.mapEntries("outputs", problems) { it.toOutputDef() },
    )
    return InfoParseResult(info, problems)
}

/** A mapped widget, or why it was dropped. */
private sealed interface Mapped<out T> {
    data class Ok<T>(val value: T) : Mapped<T>
    data class Dropped(val reason: String) : Mapped<Nothing>
}

/**
 * Unrecognized widget types (or a topic list too short for what the widget needs) are dropped
 * rather than failing the whole parse, but each one is recorded in [problems].
 */
private fun <T> List<WidgetSpecJson>.mapEntries(
    section: String,
    problems: MutableList<String>,
    map: (WidgetSpecJson) -> Mapped<T>,
): List<T> = mapIndexedNotNull { index, spec ->
    when (val mapped = map(spec)) {
        is Mapped.Ok -> mapped.value
        is Mapped.Dropped -> {
            problems += "$section[$index] \"${spec.display_name}\" dropped: ${mapped.reason}"
            null
        }
    }
}

private fun <T> WidgetSpecJson.withTopics(count: Int, build: (List<String>) -> T): Mapped<T> =
    if (topics.size >= count) {
        Mapped.Ok(build(topics))
    } else {
        Mapped.Dropped("${widget.type} needs $count topic(s), got ${topics.size}")
    }

private fun WidgetSpecJson.unknownType(): Mapped<Nothing> = Mapped.Dropped("unknown widget type \"${widget.type}\"")

private fun WidgetSpecJson.toControlDef(): Mapped<ControlDef> = when (widget.type) {
    "toggle" -> withTopics(1) {
        ControlDef.Toggle(
            topic = it[0],
            displayName = display_name,
            defaultValue = widget.default_value?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }
    "button" -> withTopics(1) {
        ControlDef.Button(
            topic = it[0],
            displayName = display_name,
            mode = widget.mode.toButtonMode(),
        )
    }
    "slider" -> withTopics(1) {
        val min = (widget.min ?: 0.0).toFloat()
        val max = (widget.max ?: 1.0).toFloat()
        val defaultValue = (widget.default_value?.jsonPrimitive?.floatOrNull ?: min).coerceIn(min, max)
        ControlDef.Slider(
            topic = it[0],
            displayName = display_name,
            min = min,
            max = max,
            defaultValue = defaultValue,
            orientation = widget.orientation.toSliderOrientation(),
        )
    }
    "joystick" -> withTopics(2) { ControlDef.Joystick(it[0], it[1], display_name) }
    else -> unknownType()
}

private fun WidgetSpecJson.toOutputDef(): Mapped<OutputDef> = when (widget.type) {
    "numeric_readout" -> withTopics(1) {
        OutputDef.NumericReadout(topic = it[0], displayName = display_name, suffix = widget.suffix ?: "")
    }
    "led_indicator" -> withTopics(1) {
        OutputDef.LedIndicator(topic = it[0], displayName = display_name, color = widget.color.toLedColor())
    }
    else -> unknownType()
}

private fun String?.toButtonMode(): ButtonMode =
    ButtonMode.entries.firstOrNull { it.name.equals(this, ignoreCase = true) } ?: ButtonMode.STATE

private fun String?.toSliderOrientation(): SliderOrientation =
    SliderOrientation.entries.firstOrNull { it.name.equals(this, ignoreCase = true) } ?: SliderOrientation.HORIZONTAL

private fun String?.toLedColor(): LedColor =
    LedColor.entries.firstOrNull { it.name.equals(this, ignoreCase = true) } ?: LedColor.GREEN
