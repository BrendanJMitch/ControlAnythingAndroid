package com.brendan.controlanything.data.device

import com.brendan.controlanything.data.discovery.DeviceEndpoint
import com.brendan.controlanything.data.pubsub.ConnectionState
import com.brendan.controlanything.domain.model.DeviceInfo
import com.brendan.controlanything.domain.model.TopicValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The ControlAnything protocol on top of pub/sub: the retained `info` schema, `outputs/<leaf>`
 * from the device and `controls/<leaf>` to it. Callers deal only in leaf topic names.
 */
interface DeviceRepository {
    val connectionState: StateFlow<ConnectionState>

    /** Parsed `info` for the current connection; null until it arrives. */
    val deviceInfo: StateFlow<DeviceInfo?>

    /**
     * What the app last commanded for each control leaf topic. The app, not the device, is the
     * source of truth here, and every value is (re)sent whenever `info` arrives on a connection.
     */
    val controlValues: StateFlow<Map<String, TopicValue>>

    /**
     * One event per `info` payload that didn't parse cleanly. Hot and not replayed: a collector
     * only sees problems raised while it's collecting.
     */
    val infoProblems: Flow<InfoProblem>

    fun connect(endpoint: DeviceEndpoint)

    fun disconnect()

    fun observeOutput(topic: String): Flow<String>

    /** Records and sends a control value (never retained - controls are fire-and-forget). */
    fun setControl(topic: String, value: TopicValue)
}

/**
 * An `info` payload that didn't parse cleanly. [rejected] means nothing usable came of it (so no
 * dashboard); otherwise some entries were dropped. [raw] is kept so the payload can be logged.
 */
data class InfoProblem(
    val rejected: Boolean,
    val problems: List<String>,
    val raw: String,
)
