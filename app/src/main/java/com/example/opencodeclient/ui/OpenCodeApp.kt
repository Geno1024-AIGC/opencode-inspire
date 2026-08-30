package com.example.opencodeclient.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

sealed class Screen {
    data object Connect : Screen()
    data object Project : Screen()
    data object Chat : Screen()
}

@Composable
fun OpenCodeApp(viewModel: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf<Screen>(Screen.Connect) }
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()

    LaunchedEffect(serverUrl) {
        if (serverUrl != null && screen is Screen.Connect) {
            screen = Screen.Project
        }
    }

    when (screen) {
        is Screen.Connect -> ConnectScreen(
            viewModel = viewModel,
            onConnected = { screen = Screen.Project },
        )
        is Screen.Project -> ProjectBrowserScreen(
            viewModel = viewModel,
            onStartChat = { screen = Screen.Chat },
            onDisconnect = {
                viewModel.reset()
                screen = Screen.Connect
            },
        )
        is Screen.Chat -> ChatScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Project },
        )
    }
}
