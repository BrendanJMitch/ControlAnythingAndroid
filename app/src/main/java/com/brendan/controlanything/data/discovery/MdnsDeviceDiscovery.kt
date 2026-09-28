package com.brendan.controlanything.data.discovery

import android.Manifest
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Finds WebSocket devices on the current Wi-Fi network via mDNS/DNS-SD. The port comes from the
 * service's SRV record; the WebSocket path from an optional `path` TXT entry.
 */
class MdnsDeviceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiBindingHelper: WifiBindingHelper,
) : DeviceDiscovery {

    // ACCESS_LOCAL_NETWORK only exists (and gates NSD/local sockets) from API 37 - requesting an
    // undefined permission on an older release would just come back denied.
    override val requiredPermissions: List<String> =
        if (Build.VERSION.SDK_INT >= 37) listOf(Manifest.permission.ACCESS_LOCAL_NETWORK) else emptyList()

    /** Restarts the search whenever a different Wi-Fi network becomes the bound one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun discover(): Flow<DeviceEndpoint> =
        wifiBindingHelper.bindToWifiNetwork().flatMapLatest { discoverOnCurrentNetwork() }

    private fun discoverOnCurrentNetwork(): Flow<DeviceEndpoint> = callbackFlow {
        val nsdManager = context.getSystemService(NsdManager::class.java)
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        // NSD relies on multicast; some OEM Wi-Fi stacks drop mDNS packets without this.
        val multicastLock = wifiManager?.createMulticastLock("controlanything-mdns")?.apply {
            setReferenceCounted(true)
        }
        multicastLock?.acquire()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION") // registerServiceInfoCallback replaces this only from API 34.
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        serviceInfo.toEndpoint()?.let { trySend(it) }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            multicastLock?.release()
            nsdManager.stopServiceDiscovery(discoveryListener)
        }
    }

    private fun NsdServiceInfo.toEndpoint(): DeviceEndpoint.WebSocket? {
        val host = preferredHostAddress() ?: return null
        val path = attributes[TXT_PATH]
            ?.decodeToString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("/")) it else "/$it" }
            ?: DeviceEndpoint.WebSocket.DEFAULT_PATH
        return DeviceEndpoint.WebSocket(
            name = serviceName,
            host = host,
            port = port.takeIf { it > 0 } ?: DeviceEndpoint.WebSocket.DEFAULT_PORT,
            path = path,
        )
    }

    /** IPv4 when available - an IPv6 link-local address carries a scope ID that URLs can't express. */
    private fun NsdServiceInfo.preferredHostAddress(): String? {
        val addresses = if (Build.VERSION.SDK_INT >= 34) hostAddresses else listOfNotNull(@Suppress("DEPRECATION") host)
        val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
        return address?.hostAddress
    }

    private companion object {
        const val SERVICE_TYPE = "_controlanything._tcp."
        const val TXT_PATH = "path"
    }
}
