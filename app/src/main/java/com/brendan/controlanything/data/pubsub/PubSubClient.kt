package com.brendan.controlanything.data.pubsub

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Protocol-agnostic publish/subscribe over whichever [Transport] suits the connected device.
 * Topics are full topic strings; this layer has no notion of info/controls/outputs.
 *
 * Subscriptions are purely local - nothing is sent to the device when subscribing. They only
 * decide which collectors see an incoming message.
 */
interface PubSubClient {
    val connectionState: StateFlow<ConnectionState>

    /** Replaces any existing connection. */
    fun connect(endpoint: DeviceEndpoint)

    fun disconnect()

    /**
     * Values published to [topic] by the device. A new collector immediately receives the last
     * value seen on the current connection (if any), so subscribing after the device's retained
     * burst still catches it. Survives reconnects - collectors don't need to resubscribe.
     */
    fun subscribe(topic: String): Flow<String>

    /** Fire-and-forget; dropped if not connected. */
    fun publish(topic: String, value: String)
}
