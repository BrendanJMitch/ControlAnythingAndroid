package com.brendan.controlanything.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brendan.controlanything.data.device.DeviceRepository
import com.brendan.controlanything.data.discovery.DeviceDiscovery
import com.brendan.controlanything.data.discovery.DeviceEndpoint
import com.brendan.controlanything.data.pubsub.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveries: Set<@JvmSuppressWildcards DeviceDiscovery>,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    /** Everything any transport's discovery needs, requested together before searching starts. */
    val requiredPermissions: List<String> = discoveries.flatMap { it.requiredPermissions }.distinct()

    private val _uiState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Searching)
    val uiState = _uiState.asStateFlow()

    private val navigateToDashboardChannel = Channel<Unit>(Channel.CONFLATED)
    val navigateToDashboard: Flow<Unit> = navigateToDashboardChannel.receiveAsFlow()

    private var connectAttempted = false
    private var discoveryStarted = false

    init {
        viewModelScope.launch {
            deviceRepository.connectionState.collect { state ->
                if (state is ConnectionState.Error) {
                    _uiState.value = DiscoveryUiState.Error(state.message)
                }
            }
        }
        viewModelScope.launch {
            deviceRepository.deviceInfo.filterNotNull().collect {
                navigateToDashboardChannel.trySend(Unit)
            }
        }
    }

    /** Must not be called until [requiredPermissions] have been granted. */
    fun startDiscovery() {
        if (discoveryStarted) return
        discoveryStarted = true
        viewModelScope.launch {
            // Keeps collecting after the first device rather than taking first(): stopping
            // discovery also releases transport-specific setup such as the Wi-Fi process binding,
            // which must outlive the connection attempt.
            merge(*discoveries.map { it.discover() }.toTypedArray()).collect { endpoint ->
                if (connectAttempted) return@collect
                connectAttempted = true
                _uiState.value = DiscoveryUiState.Connecting(endpoint.name, endpoint.transportLabel())
                deviceRepository.connect(endpoint)
            }
        }
    }
}

private fun DeviceEndpoint.transportLabel(): String = when (this) {
    is DeviceEndpoint.WebSocket -> "Wi-Fi"
}
