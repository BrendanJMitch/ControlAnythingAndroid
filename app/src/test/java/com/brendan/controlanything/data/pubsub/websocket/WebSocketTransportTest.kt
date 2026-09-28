package com.brendan.controlanything.data.pubsub.websocket

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import com.brendan.controlanything.data.pubsub.ConnectionState
import com.brendan.controlanything.data.pubsub.TopicMessage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs against a real (local) WebSocket server standing in for the microcontroller. */
class WebSocketTransportTest {

    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().pingInterval(100, TimeUnit.MILLISECONDS).build()
    private val framesFromApp = LinkedBlockingQueue<String>()
    private var deviceSocket: WebSocket? = null
    private var onDeviceOpen: (WebSocket) -> Unit = {}

    private val device = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            deviceSocket = webSocket
            onDeviceOpen(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            framesFromApp += text
        }

        // Complete the close handshake like a real server would, or the server can't shut down.
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }
    }

    @Before
    fun setUp() {
        server.enqueue(MockResponse.Builder().webSocketUpgrade(device).build())
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
        client.dispatcher.executorService.shutdown()
    }

    private fun transport() = WebSocketTransport(
        client,
        DeviceEndpoint.WebSocket(name = "Test Rover", host = server.hostName, port = server.port),
    )

    private fun awaitState(transport: WebSocketTransport, predicate: (ConnectionState) -> Boolean) = runBlocking {
        withTimeout(TIMEOUT_MS) { transport.state.first(predicate) }
    }

    @Test
    fun `connects and decodes frames the device sends on connect`() {
        onDeviceOpen = { socket ->
            socket.send("""info:{"device_name":"Rover"}""")
            socket.send("garbage without delimiter")
            socket.send("outputs/battery_voltage:7.21")
        }
        val transport = transport()
        transport.connect()

        awaitState(transport) { it == ConnectionState.Connected }
        val received = runBlocking { withTimeout(TIMEOUT_MS) { transport.incoming.take(2).toList() } }

        assertEquals(
            listOf(
                TopicMessage("info", """{"device_name":"Rover"}"""),
                TopicMessage("outputs/battery_voltage", "7.21"),
            ),
            received,
        )
        transport.close()
    }

    @Test
    fun `sends messages as topic-colon-value text frames`() {
        val transport = transport()
        transport.connect()
        awaitState(transport) { it == ConnectionState.Connected }

        transport.send(TopicMessage("controls/red_led", "true"))

        assertEquals("controls/red_led:true", framesFromApp.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        transport.close()
    }

    @Test
    fun `device closing the socket surfaces as an error`() {
        val transport = transport()
        transport.connect()
        awaitState(transport) { it == ConnectionState.Connected }

        deviceSocket!!.close(1000, null)

        awaitState(transport) { it is ConnectionState.Error }
    }

    @Test
    fun `closing from the app ends in Disconnected, not an error`() {
        val transport = transport()
        transport.connect()
        awaitState(transport) { it == ConnectionState.Connected }

        transport.close()

        assertEquals(ConnectionState.Disconnected, transport.state.value)
        Thread.sleep(200) // Let the close handshake's callbacks land.
        assertEquals(ConnectionState.Disconnected, transport.state.value)
    }

    @Test
    fun `an unreachable device surfaces as an error`() {
        server.close()
        val transport = transport()
        transport.connect()

        awaitState(transport) { it is ConnectionState.Error }
        assertTrue((transport.state.value as ConnectionState.Error).message.startsWith("Couldn't connect to Test Rover"))
    }

    @Test
    fun `url brackets IPv6 hosts and includes port and path`() {
        assertEquals("ws://192.168.4.1:81/", DeviceEndpoint.WebSocket("d", "192.168.4.1", 81).url())
        assertEquals("ws://[fe80::1]:81/ws", DeviceEndpoint.WebSocket("d", "fe80::1", 81, "/ws").url())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
