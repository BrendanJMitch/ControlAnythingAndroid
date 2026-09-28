package com.brendan.controlanything.data.pubsub

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** In-memory [Transport]: tests drive the device side via [open], [receive] and [fail]. */
class FakeTransport : Transport {
    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private val channel = Channel<TopicMessage>(Channel.UNLIMITED)
    override val incoming = channel.receiveAsFlow()

    val sent = mutableListOf<TopicMessage>()
    var isClosed = false
        private set

    override fun connect() {
        state.value = ConnectionState.Connecting
    }

    override fun send(message: TopicMessage) {
        sent += message
    }

    override fun close() {
        isClosed = true
        state.value = ConnectionState.Disconnected
        channel.close()
    }

    fun open() {
        state.value = ConnectionState.Connected
    }

    fun receive(topic: String, value: String) {
        channel.trySend(TopicMessage(topic, value))
    }

    fun fail(message: String) {
        state.value = ConnectionState.Error(message)
    }
}

class FakeTransportFactory : TransportFactory {
    val created = mutableListOf<FakeTransport>()
    val latest: FakeTransport get() = created.last()

    override fun create(endpoint: DeviceEndpoint): Transport = FakeTransport().also { created += it }
}

val TEST_ENDPOINT = DeviceEndpoint.WebSocket(name = "Test Rover", host = "192.168.4.1", port = 81)
