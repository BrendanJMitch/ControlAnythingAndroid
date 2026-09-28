package com.brendan.controlanything.data.discovery

/**
 * Where and how to reach a discovered device. Each case corresponds to exactly one transport -
 * a device only ever advertises over one - so exhaustive `when`s over this type are where a new
 * transport (e.g. a future `Ble` case) gets wired in.
 */
sealed interface DeviceEndpoint {
    /** Human-readable name the device advertised itself under. */
    val name: String

    data class WebSocket(
        override val name: String,
        val host: String,
        val port: Int,
        val path: String = DEFAULT_PATH,
    ) : DeviceEndpoint {
        companion object {
            const val DEFAULT_PORT = 81
            const val DEFAULT_PATH = "/"
        }
    }
}
