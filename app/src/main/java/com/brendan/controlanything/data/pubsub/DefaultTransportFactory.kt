package com.brendan.controlanything.data.pubsub

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import com.brendan.controlanything.data.pubsub.websocket.WebSocketTransport
import javax.inject.Inject
import okhttp3.OkHttpClient

class DefaultTransportFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : TransportFactory {
    override fun create(endpoint: DeviceEndpoint): Transport = when (endpoint) {
        is DeviceEndpoint.WebSocket -> WebSocketTransport(okHttpClient, endpoint)
    }
}
