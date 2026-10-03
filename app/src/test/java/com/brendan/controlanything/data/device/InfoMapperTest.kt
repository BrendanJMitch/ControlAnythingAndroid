package com.brendan.controlanything.data.device

import com.brendan.controlanything.domain.model.ButtonMode
import com.brendan.controlanything.domain.model.ControlDef
import com.brendan.controlanything.domain.model.LedColor
import com.brendan.controlanything.domain.model.OutputDef
import com.brendan.controlanything.domain.model.SliderOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoMapperTest {

    private fun parse(json: String) = requireNotNull(parseInfo(json).info)

    @Test
    fun `device_id and schema_hash are optional`() {
        val info = parse("""{"device_name": "Test Rover", "project_id": "test_project"}""")
        assertEquals("", info.deviceId)
        assertEquals("", info.schemaHash)
        assertTrue(info.controls.isEmpty())
    }

    @Test
    fun `unknown keys are ignored`() {
        val info = parse(
            """
            {
              "device_name": "Test Rover",
              "project_id": "test_project",
              "firmware_version": "1.2.3",
              "controls": [{"topics": ["lights"], "display_name": "Lights", "type": "bool", "icon": "bulb", "widget": {"type": "toggle", "haptics": true}}]
            }
            """.trimIndent(),
        )
        assertEquals(listOf(ControlDef.Toggle("lights", "Lights")), info.controls)
    }

    @Test
    fun `malformed or incomplete payloads yield null info and a problem`() {
        for (raw in listOf("not json", """{"device_name": "Missing project id"}""")) {
            val result = parseInfo(raw)
            assertNull(result.info)
            assertEquals(1, result.problems.size)
        }
    }

    @Test
    fun `an entry using the singular topic key is rejected, naming the missing field`() {
        val result = parseInfo(
            controlsPayload("""{"topic": ["lights"], "display_name": "Lights", "type": "bool", "widget": {"type": "toggle"}}"""),
        )
        assertNull(result.info)
        assertTrue(result.problems.single(), "topics" in result.problems.single())
    }

    @Test
    fun `a clean payload reports no problems`() {
        val result = parseInfo(
            controlsPayload("""{"topics": ["lights"], "display_name": "Lights", "type": "bool", "widget": {"type": "toggle"}}"""),
        )
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun `every dropped entry is reported with its section and index`() {
        val result = parseInfo(
            """
            {
              "device_name": "Test Rover",
              "project_id": "test_project",
              "controls": [
                {"topics": ["lights"], "display_name": "Lights", "type": "bool", "widget": {"type": "toggle"}},
                {"topics": ["drive_x"], "display_name": "Drive", "type": "float", "widget": {"type": "joystick"}}
              ],
              "outputs": [{"topics": ["mystery"], "display_name": "Mystery", "type": "float", "widget": {"type": "dial"}}]
            }
            """.trimIndent(),
        )
        assertEquals(listOf(ControlDef.Toggle("lights", "Lights")), result.info?.controls)
        assertEquals(
            listOf(
                "controls[1] \"Drive\" dropped: joystick needs 2 topic(s), got 1",
                "outputs[0] \"Mystery\" dropped: unknown widget type \"dial\"",
            ),
            result.problems,
        )
    }

    @Test
    fun `parses device metadata`() {
        val info = parse(
            """
            {
              "device_id": "esp32-01",
              "device_name": "Test Rover",
              "project_id": "test_project",
              "schema_hash": "abc123",
              "controls": [],
              "outputs": []
            }
            """.trimIndent(),
        )
        assertEquals("esp32-01", info.deviceId)
        assertEquals("Test Rover", info.deviceName)
        assertEquals("test_project", info.projectId)
        assertEquals("abc123", info.schemaHash)
    }

    @Test
    fun `toggle defaults to false when default_value is omitted`() {
        val info = parse(controlsPayload("""{"topics": ["lights"], "display_name": "Lights", "type": "bool", "widget": {"type": "toggle"}}"""))
        assertEquals(listOf(ControlDef.Toggle("lights", "Lights", defaultValue = false)), info.controls)
    }

    @Test
    fun `toggle honors an explicit default_value`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["lights"], "display_name": "Lights", "type": "bool", "widget": {"type": "toggle", "default_value": true}}""",
            ),
        )
        assertEquals(listOf(ControlDef.Toggle("lights", "Lights", defaultValue = true)), info.controls)
    }

    @Test
    fun `button defaults to STATE mode when mode is omitted`() {
        val info = parse(controlsPayload("""{"topics": ["horn"], "display_name": "Horn", "type": "bool", "widget": {"type": "button"}}"""))
        assertEquals(listOf(ControlDef.Button("horn", "Horn", mode = ButtonMode.STATE)), info.controls)
    }

    @Test
    fun `button honors an explicit mode, case-insensitively`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["horn"], "display_name": "Horn", "type": "bool", "widget": {"type": "button", "mode": "Rising"}}""",
            ),
        )
        assertEquals(listOf(ControlDef.Button("horn", "Horn", mode = ButtonMode.RISING)), info.controls)
    }

    @Test
    fun `button falls back to STATE mode for an unrecognized mode string`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["horn"], "display_name": "Horn", "type": "bool", "widget": {"type": "button", "mode": "sideways"}}""",
            ),
        )
        assertEquals(listOf(ControlDef.Button("horn", "Horn", mode = ButtonMode.STATE)), info.controls)
    }

    @Test
    fun `parses a slider control with explicit min and max`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["speed"], "display_name": "Speed", "type": "float", "widget": {"type": "slider", "min": -2.0, "max": 2.0}}""",
            ),
        )
        assertEquals(listOf(ControlDef.Slider("speed", "Speed", -2f, 2f, defaultValue = -2f)), info.controls)
    }

    @Test
    fun `slider falls back to 0 to 1 when min and max are omitted`() {
        val info = parse(controlsPayload("""{"topics": ["speed"], "display_name": "Speed", "type": "float", "widget": {"type": "slider"}}"""))
        assertEquals(listOf(ControlDef.Slider("speed", "Speed", 0f, 1f, defaultValue = 0f)), info.controls)
    }

    @Test
    fun `slider honors an explicit default_value`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["speed"], "display_name": "Speed", "type": "float", "widget": {"type": "slider", "min": -2.0, "max": 2.0, "default_value": 0.5}}""",
            ),
        )
        assertEquals(0.5f, (info.controls.single() as ControlDef.Slider).defaultValue)
    }

    @Test
    fun `slider default_value outside min-max is clamped`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["speed"], "display_name": "Speed", "type": "float", "widget": {"type": "slider", "min": 0.0, "max": 1.0, "default_value": 5.0}}""",
            ),
        )
        assertEquals(1f, (info.controls.single() as ControlDef.Slider).defaultValue)
    }

    @Test
    fun `slider defaults to horizontal orientation`() {
        val info = parse(controlsPayload("""{"topics": ["speed"], "display_name": "Speed", "type": "float", "widget": {"type": "slider"}}"""))
        assertEquals(SliderOrientation.HORIZONTAL, (info.controls.single() as ControlDef.Slider).orientation)
    }

    @Test
    fun `slider honors an explicit vertical orientation`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["speed"], "display_name": "Speed", "type": "float", "widget": {"type": "slider", "orientation": "vertical"}}""",
            ),
        )
        assertEquals(SliderOrientation.VERTICAL, (info.controls.single() as ControlDef.Slider).orientation)
    }

    @Test
    fun `parses a joystick's two topics by position`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["drive_x", "drive_y"], "display_name": "Drive", "type": "float", "widget": {"type": "joystick"}}""",
            ),
        )
        assertEquals(listOf(ControlDef.Joystick("drive_x", "drive_y", "Drive")), info.controls)
    }

    @Test
    fun `joystick with only one topic is dropped`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["drive_x"], "display_name": "Drive", "type": "float", "widget": {"type": "joystick"}}""",
            ),
        )
        assertTrue(info.controls.isEmpty())
    }

    @Test
    fun `unknown widget type is dropped instead of failing the parse`() {
        val info = parse(
            controlsPayload(
                """{"topics": ["mystery"], "display_name": "Mystery", "type": "float", "widget": {"type": "dial"}}""",
            ),
        )
        assertTrue(info.controls.isEmpty())
    }

    @Test
    fun `numeric readout defaults to an empty suffix`() {
        val info = parse(
            outputsPayload("""{"topics": ["battery"], "display_name": "Battery", "type": "float", "widget": {"type": "numeric_readout"}}"""),
        )
        assertEquals(listOf(OutputDef.NumericReadout("battery", "Battery", suffix = "")), info.outputs)
    }

    @Test
    fun `numeric readout honors an explicit suffix`() {
        val info = parse(
            outputsPayload(
                """{"topics": ["battery"], "display_name": "Battery", "type": "float", "widget": {"type": "numeric_readout", "suffix": "V"}}""",
            ),
        )
        assertEquals(listOf(OutputDef.NumericReadout("battery", "Battery", suffix = "V")), info.outputs)
    }

    @Test
    fun `led indicator defaults to green`() {
        val info = parse(
            outputsPayload("""{"topics": ["status"], "display_name": "Status", "type": "bool", "widget": {"type": "led_indicator"}}"""),
        )
        assertEquals(listOf(OutputDef.LedIndicator("status", "Status", color = LedColor.GREEN)), info.outputs)
    }

    @Test
    fun `led indicator honors an explicit color, case-insensitively`() {
        val info = parse(
            outputsPayload(
                """{"topics": ["status"], "display_name": "Status", "type": "bool", "widget": {"type": "led_indicator", "color": "cyan"}}""",
            ),
        )
        assertEquals(listOf(OutputDef.LedIndicator("status", "Status", color = LedColor.CYAN)), info.outputs)
    }

    @Test
    fun `led indicator falls back to green for an unrecognized color`() {
        val info = parse(
            outputsPayload(
                """{"topics": ["status"], "display_name": "Status", "type": "bool", "widget": {"type": "led_indicator", "color": "mauve"}}""",
            ),
        )
        assertEquals(listOf(OutputDef.LedIndicator("status", "Status", color = LedColor.GREEN)), info.outputs)
    }

    @Test
    fun `entry with an empty topic list is dropped`() {
        val info = parse(controlsPayload("""{"topics": [], "display_name": "Lights", "type": "bool", "widget": {"type": "toggle"}}"""))
        assertTrue(info.controls.isEmpty())
        assertNull(info.controls.firstOrNull())
    }

    private fun controlsPayload(vararg controls: String) = """
        {
          "device_id": "esp32-01",
          "device_name": "Test Rover",
          "project_id": "test_project",
          "schema_hash": "abc123",
          "controls": [${controls.joinToString(",")}],
          "outputs": []
        }
    """.trimIndent()

    private fun outputsPayload(vararg outputs: String) = """
        {
          "device_id": "esp32-01",
          "device_name": "Test Rover",
          "project_id": "test_project",
          "schema_hash": "abc123",
          "controls": [],
          "outputs": [${outputs.joinToString(",")}]
        }
    """.trimIndent()
}
