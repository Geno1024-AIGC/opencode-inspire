package com.example.opencodeclient.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencodeclient.data.ServerProfile
import com.example.opencodeclient.data.Session
import kotlinx.coroutines.launch

sealed class Screen {
    data object Connect : Screen()
    data object Main : Screen()

    val key: String
        get() = when (this) {
            Connect -> "connect"
            Main -> "main"
        }

    companion object {
        fun fromKey(key: String): Screen = when (key) {
            "main" -> Main
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
            screenKey = Screen.Main.key
        }
    }

    when (screen) {
        is Screen.Connect -> ConnectScreen(
            viewModel = viewModel,
            onConnected = { screenKey = Screen.Main.key },
        )
        is Screen.Main -> MainScreen(
            viewModel = viewModel,
            onDisconnect = {
                viewModel.reset()
                screenKey = Screen.Connect.key
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    onDisconnect: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showServers by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                viewModel = viewModel,
                serverUrl = serverUrl,
                onClose = { scope.launch { drawerState.close() } },
                onSettings = { showSettings = true },
                onServers = { showServers = true },
                onDisconnect = onDisconnect,
            )
        },
    ) {
        ChatScreen(
            viewModel = viewModel,
            onMenu = { scope.launch { drawerState.open() } },
        )
    }

    if (showSettings) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettings = false },
        )
    }
    if (showServers) {
        ServersDialog(
            viewModel = viewModel,
            onDismiss = { showServers = false },
        )
    }
    LaunchedEffect(Unit) {
        viewModel.ensureLoaded()
    }
}

@Composable
private fun DrawerContent(
    viewModel: MainViewModel,
    serverUrl: String?,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onServers: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()

    ModalDrawerSheet {
        Column(
            modifier = Modifier.fillMaxWidth(1f),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("OpenCode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                serverUrl?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
        }
        HorizontalDivider()

        when (workspaceState) {
            is UiState.Loading -> Text(
                (workspaceState as UiState.Loading).message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            is UiState.Error -> Text(
                (workspaceState as UiState.Error).message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            else -> {}
        }

        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Projects", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                items(projects, key = { it.id }) { project ->
                    ExpandableProject(
                        project = project,
                        activeSessionId = activeSession?.id,
                        shortTokens = shortTokens,
                        onOpenSession = { viewModel.openSession(it) },
                        onNewSession = { viewModel.newSession(project.worktree) },
                    )
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSettings() }
                    .padding(16.dp),
            )
            Text(
                "Servers",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onServers() }
                    .padding(16.dp),
            )
        }
        }
    }
}

@Composable
private fun ExpandableProject(
    project: ProjectUi,
    activeSessionId: String?,
    shortTokens: Boolean = true,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
) {
    var expanded by rememberSaveable(project.id) { mutableStateOf(project.sessions.isEmpty()) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                project.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (expanded) {
            project.sessions.forEach { s ->
                SessionRow(
                    s = s,
                    isActive = s.id == activeSessionId,
                    shortTokens = shortTokens,
                    onClick = { onOpenSession(s.id) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNewSession)
                    .padding(horizontal = 40.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Add,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("New chat", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SessionRow(
    s: Session,
    isActive: Boolean,
    shortTokens: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 40.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Message,
            null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            s.title.ifBlank { "Untitled session" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        s.tokens?.let { t ->
            val total = t.input + t.output + t.reasoning
            if (total > 0) {
                Text(
                    formatTokens(total, shortTokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun formatTokens(count: Long, short: Boolean = true): String = when {
    !short -> count.toString()
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fk".format(count / 1_000.0)
    else -> count.toString()
}

@Composable
private fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val savedUser by viewModel.authUsername.collectAsStateWithLifecycle()
    val savedPass by viewModel.authPassword.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(serverUrl ?: "") }
    var username by rememberSaveable { mutableStateOf(savedUser ?: "") }
    var password by rememberSaveable { mutableStateOf(savedPass ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.connect(
                        url.trim(),
                        username = username.trim().takeIf { it.isNotEmpty() },
                        password = password.takeIf { it.isNotEmpty() },
                    )
                    onDismiss()
                },
            ) { Text("Save & Reconnect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ServersDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Servers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (servers.isEmpty()) {
                    Text("No saved servers yet.", style = MaterialTheme.typography.bodySmall)
                }
                servers.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            viewModel.connect(p.url, p.username, p.password)
                        }) {
                            Text(p.name.ifBlank { p.url }, fontWeight = FontWeight.Medium)
                            Text(p.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.url == serverUrl) {
                            Text("Connected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.removeServerProfile(p.url) }) {
                            Icon(Icons.Filled.Close, "Remove")
                        }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("New server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        viewModel.saveServerProfile(ServerProfile(url.trim(), username.takeIf { it.isNotBlank() }, password.takeIf { it.isNotBlank() }))
                        url = ""
                        username = ""
                        password = ""
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Save server") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
