package com.brendan.controlanything.data.pubsub.websocket

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import com.brendan.controlanything.data.pubsub.ConnectionState
import com.brendan.controlanything.data.pubsub.TopicMessage
import com.brendan.controlanything.data.pubsub.Transport
import com.brendan.controlanything.data.pubsub.WireCodec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * One text frame per message, in [WireCodec] format. Liveness comes from [client]'s ping
 * interval: OkHttp fails the socket (landing in [ConnectionState.Error]) when a pong is missed.
 */
class WebSocketTransport(
    private val client: OkHttpClient,
    private val endpoint: DeviceEndpoint.WebSocket,
) : Transport {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state = _state.asStateFlow()

    // Unlimited so OkHttp's reader thread never blocks or drops; the single collector drains it.
    private val messages = Channel<TopicMessage>(Channel.UNLIMITED)
    override val incoming = messages.receiveAsFlow()

    @Volatile
    private var webSocket: WebSocket? = null

    // Set by close() so callbacks racing the shutdown can't overwrite Disconnected with an error.
    @Volatile
    private var closedByUs = false

    override fun connect() {
        check(webSocket == null && !closedByUs) { "WebSocketTransport is single-use" }
        _state.value = ConnectionState.Connecting
        webSocket = client.newWebSocket(Request.Builder().url(endpoint.url()).build(), listener)
    }

    override fun send(message: TopicMessage) {
        if (_state.value != ConnectionState.Connected) return
        webSocket?.send(WireCodec.encode(message))
    }

    override fun close() {
        closedByUs = true
        webSocket?.close(NORMAL_CLOSURE, null)
        _state.value = ConnectionState.Disconnected
        messages.close()
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!closedByUs) _state.value = ConnectionState.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            WireCodec.decode(text)?.let { messages.trySend(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            end("${endpoint.name} closed the connection")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val wasConnected = _state.value == ConnectionState.Connected
            end(
                if (wasConnected) {
                    "Lost connection to ${endpoint.name}"
                } else {
                    "Couldn't connect to ${endpoint.name}: ${t.message ?: t.javaClass.simpleName}"
                },
            )
        }
    }

    private fun end(errorMessage: String) {
        if (!closedByUs) _state.value = ConnectionState.Error(errorMessage)
        messages.close()
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}

internal fun DeviceEndpoint.WebSocket.url(): String {
    // IPv6 literals need brackets in a URL authority.
    val authority = if (':' in host) "[$host]" else host
    return "ws://$authority:$port$path"
}
