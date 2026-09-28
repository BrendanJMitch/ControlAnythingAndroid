package com.brendan.controlanything.data.pubsub

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PubSubClientImplTest {

    private val factory = FakeTransportFactory()

    private fun TestScope.client() = PubSubClientImpl(factory, backgroundScope)

    private fun TestScope.collect(client: PubSubClient, topic: String): List<String> {
        val values = mutableListOf<String>()
        backgroundScope.launch { client.subscribe(topic).toList(values) }
        return values
    }

    @Test
    fun `delivers each message only to subscribers of its topic`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        val battery = collect(client, "outputs/battery")
        val status = collect(client, "outputs/status")

        client.connect(TEST_ENDPOINT)
        factory.latest.receive("outputs/battery", "7.2")
        factory.latest.receive("outputs/battery", "7.1")
        factory.latest.receive("outputs/unrelated", "x")
        runCurrent()

        assertEquals(listOf("7.2", "7.1"), battery)
        assertEquals(emptyList<String>(), status)
    }

    @Test
    fun `a late subscriber receives the last value seen on the connection`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        client.connect(TEST_ENDPOINT)
        factory.latest.receive("info", "first")
        factory.latest.receive("info", "second")
        runCurrent()

        assertEquals(listOf("second"), collect(client, "info"))
    }

    @Test
    fun `publish sends the full topic over the current transport`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        client.connect(TEST_ENDPOINT)

        client.publish("controls/red_led", "true")

        assertEquals(listOf(TopicMessage("controls/red_led", "true")), factory.latest.sent)
    }

    @Test
    fun `publish without a connection is a no-op`() = runTest(UnconfinedTestDispatcher()) {
        client().publish("controls/red_led", "true")
        assertTrue(factory.created.isEmpty())
    }

    @Test
    fun `reconnecting closes the old transport and clears last values but keeps subscribers`() =
        runTest(UnconfinedTestDispatcher()) {
            val client = client()
            val battery = collect(client, "outputs/battery")
            client.connect(TEST_ENDPOINT)
            val first = factory.latest
            first.receive("outputs/battery", "7.2")
            runCurrent()

            client.connect(TEST_ENDPOINT)
            runCurrent()

            assertTrue(first.isClosed)
            assertEquals(emptyList<String>(), collect(client, "outputs/battery"))

            factory.latest.receive("outputs/battery", "6.9")
            runCurrent()
            assertEquals(listOf("7.2", "6.9"), battery)
        }

    @Test
    fun `connection state mirrors the current transport`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        client.connect(TEST_ENDPOINT)
        assertEquals(ConnectionState.Connecting, client.connectionState.value)

        factory.latest.open()
        runCurrent()
        assertEquals(ConnectionState.Connected, client.connectionState.value)

        factory.latest.fail("boom")
        runCurrent()
        assertEquals(ConnectionState.Error("boom"), client.connectionState.value)
    }

    @Test
    fun `a replaced transport's later state changes are ignored`() = runTest(UnconfinedTestDispatcher()) {
        val client = client()
        client.connect(TEST_ENDPOINT)
        val first = factory.latest
        client.disconnect()
        runCurrent()

        first.fail("late failure")
        runCurrent()

        assertEquals(ConnectionState.Disconnected, client.connectionState.value)
    }
}
