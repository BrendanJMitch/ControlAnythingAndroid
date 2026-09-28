package com.brendan.controlanything.data.pubsub

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import com.brendan.controlanything.di.ApplicationScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class PubSubClientImpl @Inject constructor(
    private val transportFactory: TransportFactory,
    @ApplicationScope private val scope: CoroutineScope,
) : PubSubClient {

    private val lock = Any()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()

    // One flow per topic ever seen or subscribed to. replay = 1 is the "last value" cache; it's
    // reset (not the flow replaced) on each connection so existing collectors carry over.
    private val topics = ConcurrentHashMap<String, MutableSharedFlow<String>>()

    @Volatile
    private var transport: Transport? = null
    private var connectionJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class) // resetReplayCache
    override fun connect(endpoint: DeviceEndpoint) {
        val newTransport = transportFactory.create(endpoint)
        synchronized(lock) {
            closeCurrent()
            topics.values.forEach { it.resetReplayCache() }
            transport = newTransport
            // Started before collection so the mirrored state never shows the transport's idle
            // pre-connect Disconnected; anything received meanwhile waits in its buffer.
            newTransport.connect()
            connectionJob = scope.launch {
                launch { newTransport.state.collect { _connectionState.value = it } }
                newTransport.incoming.collect { dispatch(newTransport, it) }
            }
        }
    }

    override fun disconnect() {
        synchronized(lock) {
            closeCurrent()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /** Caller holds [lock]. Cancels collection first so the old transport's final state can't leak out. */
    private fun closeCurrent() {
        connectionJob?.cancel()
        connectionJob = null
        transport?.close()
        transport = null
    }

    private fun dispatch(source: Transport, message: TopicMessage) {
        synchronized(lock) {
            // A message still in flight from a replaced connection must not repopulate the cache.
            if (source !== transport) return
            flowFor(message.topic).tryEmit(message.value)
        }
    }

    override fun subscribe(topic: String): Flow<String> = flowFor(topic).asSharedFlow()

    override fun publish(topic: String, value: String) {
        transport?.send(TopicMessage(topic, value))
    }

    private fun flowFor(topic: String): MutableSharedFlow<String> = topics.computeIfAbsent(topic) {
        MutableSharedFlow(
            replay = 1,
            extraBufferCapacity = SUBSCRIBER_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }

    private companion object {
        /** Headroom for a briefly slow collector before its oldest undelivered values are dropped. */
        const val SUBSCRIBER_BUFFER = 64
    }
}
