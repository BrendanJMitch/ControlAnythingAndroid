package com.brendan.controlanything.data.device

import com.brendan.controlanything.data.pubsub.FakeTransportFactory
import com.brendan.controlanything.data.pubsub.PubSubClientImpl
import com.brendan.controlanything.data.pubsub.TEST_ENDPOINT
import com.brendan.controlanything.data.pubsub.TopicMessage
import com.brendan.controlanything.domain.model.TopicValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepositoryImplTest {

    private val factory = FakeTransportFactory()

    private fun TestScope.repository() = DeviceRepositoryImpl(PubSubClientImpl(factory, backgroundScope), backgroundScope)

    private fun TestScope.connectAndReceiveInfo(repository: DeviceRepository, info: String) {
        repository.connect(TEST_ENDPOINT)
        factory.latest.open()
        factory.latest.receive("info", info)
        runCurrent()
    }

    @Test
    fun `parses info and sends every control's default on arrival`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        connectAndReceiveInfo(repository, info(LIGHTS, SPEED, HORN, DRIVE))

        assertEquals("Test Rover", repository.deviceInfo.value?.deviceName)
        val expected = mapOf(
            "lights" to TopicValue.Bool(true),
            "speed" to TopicValue.Number(1.5f),
            "drive_x" to TopicValue.Number(0f),
            "drive_y" to TopicValue.Number(0f),
        )
        assertEquals(expected, repository.controlValues.value)
        assertEquals(
            setOf(
                TopicMessage("controls/lights", "true"),
                TopicMessage("controls/speed", "1.5"),
                TopicMessage("controls/drive_x", "0.0"),
                TopicMessage("controls/drive_y", "0.0"),
            ),
            factory.latest.sent.toSet(),
        )
    }

    @Test
    fun `invalid info is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        connectAndReceiveInfo(repository, "{not json")

        assertNull(repository.deviceInfo.value)
        assertEquals(emptyList<TopicMessage>(), factory.latest.sent)
    }

    @Test
    fun `info problems are reported, and a clean info reports none`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        val problems = mutableListOf<InfoProblem>()
        backgroundScope.launch { repository.infoProblems.toList(problems) }

        connectAndReceiveInfo(repository, info(LIGHTS))
        connectAndReceiveInfo(repository, "{not json")
        val unknown = """{"topics": ["x"], "display_name": "Dial", "type": "float", "widget": {"type": "dial"}}"""
        connectAndReceiveInfo(repository, info(LIGHTS, unknown))

        assertEquals(listOf(true, false), problems.map { it.rejected })
        assertEquals("{not json", problems[0].raw)
        assertNotNull(repository.deviceInfo.value) // The partial schema still produces a dashboard.
    }

    @Test
    fun `setControl records the value and publishes it under controls`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        connectAndReceiveInfo(repository, info(LIGHTS))
        factory.latest.sent.clear()

        repository.setControl("lights", TopicValue.Bool(false))

        assertEquals(TopicValue.Bool(false), repository.controlValues.value["lights"])
        assertEquals(listOf(TopicMessage("controls/lights", "false")), factory.latest.sent)
    }

    @Test
    fun `observeOutput receives values from the outputs topic`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        val values = mutableListOf<String>()
        backgroundScope.launch { repository.observeOutput("battery").toList(values) }
        repository.connect(TEST_ENDPOINT)

        factory.latest.receive("outputs/battery", "7.21")
        factory.latest.receive("battery", "ignored - no prefix")
        runCurrent()

        assertEquals(listOf("7.21"), values)
    }

    @Test
    fun `connecting clears the previous device's info`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        connectAndReceiveInfo(repository, info(LIGHTS))
        assertNotNull(repository.deviceInfo.value)

        repository.connect(TEST_ENDPOINT)

        assertNull(repository.deviceInfo.value)
    }

    @Test
    fun `reconnecting to the same project resends last values for unchanged controls`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        connectAndReceiveInfo(repository, info(LIGHTS, SPEED, DRIVE))
        repository.setControl("lights", TopicValue.Bool(false))
        repository.setControl("speed", TopicValue.Number(3f))
        repository.setControl("drive_x", TopicValue.Number(0.5f))

        // Same project, but the speed slider's range changed in the new firmware.
        val speedWithNewRange = SPEED.replace("\"max\": 4.0", "\"max\": 10.0")
        connectAndReceiveInfo(repository, info(LIGHTS, speedWithNewRange, DRIVE))

        assertEquals(TopicValue.Bool(false), repository.controlValues.value["lights"])
        assertEquals(TopicValue.Number(1.5f), repository.controlValues.value["speed"])
        assertEquals(TopicValue.Number(0f), repository.controlValues.value["drive_x"])
        assertEquals(
            setOf(
                TopicMessage("controls/lights", "false"),
                TopicMessage("controls/speed", "1.5"),
                TopicMessage("controls/drive_x", "0.0"),
                TopicMessage("controls/drive_y", "0.0"),
            ),
            factory.latest.sent.toSet(),
        )
    }

    @Test
    fun `a different project starts from defaults`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        connectAndReceiveInfo(repository, info(LIGHTS))
        repository.setControl("lights", TopicValue.Bool(false))

        connectAndReceiveInfo(repository, info(LIGHTS, projectId = "other_project"))

        assertEquals(TopicValue.Bool(true), repository.controlValues.value["lights"])
    }

    @Test
    fun `controls no longer in the schema are dropped`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        connectAndReceiveInfo(repository, info(LIGHTS, SPEED))

        connectAndReceiveInfo(repository, info(LIGHTS))

        assertEquals(setOf("lights"), repository.controlValues.value.keys)
    }

    private fun info(vararg controls: String, projectId: String = "test_project") = """
        {"device_name": "Test Rover", "project_id": "$projectId", "controls": [${controls.joinToString(",")}]}
    """.trimIndent()

    private companion object {
        const val LIGHTS =
            """{"topics": ["lights"], "display_name": "Lights", "type": "bool", "widget": {"type": "toggle", "default_value": true}}"""
        const val SPEED =
            """{"topics": ["speed"], "display_name": "Speed", "type": "float", "widget": {"type": "slider", "min": -1.0, "max": 4.0, "default_value": 1.5}}"""
        const val HORN =
            """{"topics": ["horn"], "display_name": "Horn", "type": "bool", "widget": {"type": "button"}}"""
        const val DRIVE =
            """{"topics": ["drive_x", "drive_y"], "display_name": "Drive", "type": "float", "widget": {"type": "joystick"}}"""
    }
}
