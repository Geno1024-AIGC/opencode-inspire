package com.example.opencodeclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row

@Composable
fun ConnectScreen(
    viewModel: MainViewModel,
    onConnected: () -> Unit,
) {
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(serverUrl ?: "") }
    var visited by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(serverUrl) {
        if (serverUrl != null && !visited) {
            input = serverUrl ?: input
            visited = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("OpenCode Client", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect to an OpenCode server",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.10:4096") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is UiState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(s.message)
            }
            is UiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
            }
            else -> {}
        }

        if (state is UiState.Loading) {
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = { viewModel.connect(input.trim(), onSuccess = onConnected) },
            enabled = input.isNotBlank() && state !is UiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Connect")
        }

        if (serverUrl != null && state is UiState.Error) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Saved server: $serverUrl",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
