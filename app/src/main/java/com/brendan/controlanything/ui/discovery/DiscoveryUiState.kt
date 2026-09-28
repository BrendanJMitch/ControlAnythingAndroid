package com.brendan.controlanything.ui.discovery

sealed interface DiscoveryUiState {
    data object Searching : DiscoveryUiState

    /** [transport] is a user-facing name for how we're connecting, e.g. "Wi-Fi". */
    data class Connecting(val label: String? = null, val transport: String? = null) : DiscoveryUiState
    data class Error(val message: String) : DiscoveryUiState
}
