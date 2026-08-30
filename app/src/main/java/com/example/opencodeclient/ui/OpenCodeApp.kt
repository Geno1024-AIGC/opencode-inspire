package com.example.opencodeclient.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

sealed class Screen {
    data object Connect : Screen()
    data object Project : Screen()
    data object Chat : Screen()

    val key: String
        get() = when (this) {
            Connect -> "connect"
            Project -> "project"
            Chat -> "chat"
        }

    companion object {
        fun fromKey(key: String): Screen = when (key) {
            "project" -> Project
            "chat" -> Chat
            else -> Connect
        }
    }
}

@Composable
fun OpenCodeApp(viewModel: MainViewModel) {
    var screenKey by rememberSaveable { mutableStateOf(Screen.Connect.key) }
    val screen = Screen.fromKey(screenKey)
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()

    LaunchedEffect(serverUrl) {
        if (serverUrl != null && screen is Screen.Connect) {
            screenKey = Screen.Project.key
        }
    }

    when (screen) {
        is Screen.Connect -> ConnectScreen(
            viewModel = viewModel,
            onConnected = { screenKey = Screen.Project.key },
        )
        is Screen.Project -> ProjectBrowserScreen(
            viewModel = viewModel,
            onStartChat = { screenKey = Screen.Chat.key },
            onDisconnect = {
                viewModel.reset()
                screenKey = Screen.Connect.key
            },
        )
        is Screen.Chat -> ChatScreen(
            viewModel = viewModel,
            onBack = { screenKey = Screen.Project.key },
        )
    }
}
