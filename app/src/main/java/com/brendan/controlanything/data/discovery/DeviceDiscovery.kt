package com.brendan.controlanything.data.discovery

import kotlinx.coroutines.flow.Flow

/**
 * Finds devices reachable over one transport. Every implementation is bound into a set and run in
 * parallel; the app connects to whichever device turns up first.
 */
interface DeviceDiscovery {
    /** Runtime permissions [discover] needs; granted by the UI before discovery starts. */
    val requiredPermissions: List<String>

    /** Emits devices as they're found; searching continues until collection stops. */
    fun discover(): Flow<DeviceEndpoint>
}
