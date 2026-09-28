package com.brendan.controlanything.data.pubsub

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A single connection to a device over one protocol. Implementations own framing and liveness
 * (e.g. WebSocket ping/pong) but know nothing about topics beyond carrying them - routing to
 * subscribers is [PubSubClient]'s job. One instance per connection attempt; not reusable after
 * [close].
 */
interface Transport {
    val state: StateFlow<ConnectionState>

    /**
     * Every message received, in order. Buffered rather than dropped until collected, so nothing
     * sent in the burst right after connecting (the device's retained topics) is lost if the
     * collector starts a moment late. Intended for exactly one collector.
     */
    val incoming: Flow<TopicMessage>

    fun connect()

    /** Fire-and-forget; silently dropped if not connected. */
    fun send(message: TopicMessage)

    fun close()
}

/** Picks the protocol implementation for a discovered device. */
fun interface TransportFactory {
    fun create(endpoint: DeviceEndpoint): Transport
}
