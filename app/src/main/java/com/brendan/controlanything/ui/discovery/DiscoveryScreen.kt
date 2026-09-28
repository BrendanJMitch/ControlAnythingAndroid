package com.brendan.controlanything.ui.discovery

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brendan.controlanything.ui.theme.ControlAnythingTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DiscoveryScreen(
    onDeviceReady: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoveryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val permissions = viewModel.requiredPermissions
    var hasPermissions by remember {
        mutableStateOf(
            permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
        )
    }

    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> hasPermissions = permissions.all { results[it] == true } }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            requestPermissions.launch(permissions.toTypedArray())
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            viewModel.startDiscovery()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateToDashboard.collectLatest { onDeviceReady() }
    }

    if (hasPermissions) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        DiscoveryContent(state = state, modifier = modifier.fillMaxSize())
    } else {
        PermissionRequiredContent(
            onRequestPermission = { requestPermissions.launch(permissions.toTypedArray()) },
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PermissionRequiredContent(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Permission needed", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "ControlAnything needs access to nearby devices to find and connect to your project.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant access")
        }
    }
}

@Composable
private fun DiscoveryContent(
    state: DiscoveryUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            is DiscoveryUiState.Searching -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(24.dp))
                Text("Searching for your device...", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Make sure your device is powered on and your phone is on the same network.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            is DiscoveryUiState.Connecting -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(24.dp))
                Text(
                    text = buildString {
                        append("Connecting")
                        state.label?.let { append(" to $it") }
                        state.transport?.let { append(" over $it") }
                        append("...")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }

            is DiscoveryUiState.Error -> {
                Text("Couldn't connect", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionRequiredContentPreview() {
    ControlAnythingTheme {
        PermissionRequiredContent(onRequestPermission = {}, modifier = Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveryContentSearchingPreview() {
    ControlAnythingTheme {
        DiscoveryContent(state = DiscoveryUiState.Searching, modifier = Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveryContentConnectingPreview() {
    ControlAnythingTheme {
        DiscoveryContent(state = DiscoveryUiState.Connecting(label = "Rover 2", transport = "Wi-Fi"), modifier = Modifier.fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveryContentErrorPreview() {
    ControlAnythingTheme {
        DiscoveryContent(
            state = DiscoveryUiState.Error("Lost connection to Rover 2"),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
