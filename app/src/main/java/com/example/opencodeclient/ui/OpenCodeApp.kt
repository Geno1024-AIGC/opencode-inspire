package com.example.opencodeclient.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencodeclient.R
import com.example.opencodeclient.data.CapabilityState
import com.example.opencodeclient.data.FeatureGroup
import com.example.opencodeclient.data.FeatureStatus
import com.example.opencodeclient.data.ServerProfile
import com.example.opencodeclient.data.Session
import com.example.opencodeclient.data.StoredHistoryStats
import kotlinx.coroutines.launch

sealed class Screen {
    data object Connect : Screen()
    data object Main : Screen()
    data object Settings : Screen()
    data object Calendar : Screen()

    val key: String
        get() = when (this) {
            Connect -> "connect"
            Main -> "main"
            Settings -> "settings"
            Calendar -> "calendar"
        }

    companion object {
        fun fromKey(key: String): Screen = when (key) {
            "main" -> Main
            "settings" -> Settings
            "calendar" -> Calendar
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

    val saveableStateHolder = rememberSaveableStateHolder()

    when (screen) {
        is Screen.Connect -> saveableStateHolder.SaveableStateProvider(Screen.Connect.key) {
            ConnectScreen(
                viewModel = viewModel,
                onConnected = { screenKey = Screen.Main.key },
            )
        }
        is Screen.Main -> saveableStateHolder.SaveableStateProvider(Screen.Main.key) {
            MainScreen(
                viewModel = viewModel,
                onDisconnect = {
                    viewModel.reset()
                    screenKey = Screen.Connect.key
                },
                onOpenSettings = { screenKey = Screen.Settings.key },
            )
        }
        is Screen.Settings -> saveableStateHolder.SaveableStateProvider(Screen.Settings.key) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { screenKey = Screen.Main.key },
                onOpenCalendar = { screenKey = Screen.Calendar.key },
            )
        }
        is Screen.Calendar -> saveableStateHolder.SaveableStateProvider(Screen.Calendar.key) {
            TokenCalendarScreen(
                viewModel = viewModel,
                onBack = { screenKey = Screen.Settings.key },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var showServers by remember { mutableStateOf(false) }
    var showAddProject by remember { mutableStateOf(false) }
    var showCapabilities by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                viewModel = viewModel,
                serverUrl = serverUrl,
                onClose = { scope.launch { drawerState.close() } },
                onSettings = onOpenSettings,
                onServers = { showServers = true },
                onAddProject = { showAddProject = true },
                onCapabilities = { showCapabilities = true },
                onDisconnect = onDisconnect,
            )
        },
    ) {
        ChatScreen(
            viewModel = viewModel,
            onMenu = { scope.launch { drawerState.open() } },
        )
    }

    if (showServers) {
        ServersDialog(
            viewModel = viewModel,
            onDismiss = { showServers = false },
        )
    }
    if (showAddProject) {
        AddProjectDialog(
            viewModel = viewModel,
            onDismiss = { showAddProject = false },
        )
    }
    if (showCapabilities) {
        CapabilitiesDialog(
            viewModel = viewModel,
            onDismiss = { showCapabilities = false },
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
    onAddProject: () -> Unit,
    onCapabilities: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activeSessionTotalElapsed by viewModel.sessionTotalElapsed.collectAsStateWithLifecycle()
    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()
    val storedStats by viewModel.storedStats.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    ModalDrawerSheet {
        Column(
            modifier = Modifier.fillMaxWidth(1f),
        ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onServers() },
                        onLongClick = { menuExpanded = true },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                serverUrl?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.drawer_close)) }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.drawer_capabilities)) },
                    onClick = {
                        menuExpanded = false
                        onCapabilities()
                    },
                )
            }
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
                Text(stringResource(R.string.drawer_projects), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onAddProject) { Icon(Icons.Filled.Add, stringResource(R.string.drawer_add_project)) }
                IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.drawer_refresh)) }
            }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                items(projects, key = { it.id }) { project ->
                    ExpandableProject(
                        project = project,
                        activeSessionId = activeSession?.id,
                        activeSessionTotalElapsed = activeSessionTotalElapsed,
                        storedStats = storedStats,
                        shortTokens = shortTokens,
                        favorites = favorites,
                        onOpenSession = { viewModel.openSession(it) },
                        onNewSession = { viewModel.newSession(project.worktree) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                    )
                }
            }
        }

        HorizontalDivider()
        Text(
            stringResource(R.string.drawer_settings),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSettings() }
                .padding(16.dp),
        )
        }
    }
}

@Composable
private fun ExpandableProject(
    project: ProjectUi,
    activeSessionId: String?,
    activeSessionTotalElapsed: Long? = null,
    storedStats: Map<String, StoredHistoryStats> = emptyMap(),
    shortTokens: Boolean = true,
    favorites: Set<String> = emptySet(),
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    var expanded by rememberSaveable(project.id) { mutableStateOf(project.sessions.isEmpty()) }
    val orderedSessions = remember(project.sessions, favorites) {
        project.sessions.sortedByDescending { it.id in favorites }
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    project.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (project.worktree.isNotBlank() && project.worktree != project.name) {
                    Text(
                        project.worktree,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFontFamily,
                    )
                }
            }
            Icon(if (expanded) painterResource(R.drawable.ic_expand_less) else painterResource(R.drawable.ic_expand_more), null)
        }
        if (expanded) {
            orderedSessions.forEach { s ->
                val storedElapsed = storedStats[s.id]?.totalElapsed ?: 0L
                SessionRow(
                    s = s,
                    isActive = s.id == activeSessionId,
                    isFavorite = s.id in favorites,
                    totalElapsed = if (storedElapsed > 0L) storedElapsed else null,
                    shortTokens = shortTokens,
                    onClick = { onOpenSession(s.id) },
                    onToggleFavorite = { onToggleFavorite(s.id) },
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
                Text(stringResource(R.string.new_chat), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SessionRow(
    s: Session,
    isActive: Boolean,
    isFavorite: Boolean = false,
    totalElapsed: Long? = null,
    shortTokens: Boolean = true,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 40.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_message),
            null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                s.title.ifBlank { stringResource(R.string.untitled_session) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                s.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonoFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        totalElapsed?.let { elapsed ->
            if (elapsed > 0) {
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    formatElapsed(elapsed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Star,
                stringResource(R.string.favorite_toggle),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

fun formatElapsed(ms: Long): String = when {
    ms <= 0L -> ""
    ms < 60_000L -> "%.1fs".format(ms / 1000.0)
    ms < 3_600_000L -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
    else -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
}

fun formatTokens(count: Long, short: Boolean = true): String = when {
    !short -> count.toString()
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fk".format(count / 1_000.0)
    else -> count.toString()
}

fun formatBytes(bytes: Long): String = bytes.toString()

fun formatSpeed(bytesPerSec: Long): String = formatBytes(bytesPerSec) + "/s"

fun formatEta(seconds: Long): String = when {
    seconds < 0 -> ""
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
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
    var adding by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.servers_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (servers.isEmpty()) {
                    Text(stringResource(R.string.servers_empty), style = MaterialTheme.typography.bodySmall)
                }
                servers.sortedBy { it.url != serverUrl }.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            viewModel.connect(p.url, p.username, p.password)
                        }) {
                            Text(p.name.ifBlank { p.url }, fontWeight = FontWeight.Medium)
                            Text(p.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.url == serverUrl) {
                            Text(stringResource(R.string.connect_connected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.removeServerProfile(p.url) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.connect_remove))
                        }
                    }
                }
                if (adding) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.server_new_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.server_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.server_password)) },
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
                            adding = false
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.server_save)) }
                } else {
                    TextButton(
                        onClick = { adding = true },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("+  ${stringResource(R.string.server_add)}") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
private fun CapabilitiesDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val report by viewModel.capabilities.collectAsStateWithLifecycle()
    val statuses by viewModel.featureStatus.collectAsStateWithLifecycle()
    val detecting by viewModel.capabilitiesDetecting.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capabilities_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.capabilities_version) + ": " +
                        (report?.version ?: stringResource(R.string.capabilities_unknown)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.capabilities_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                val r = report
                if (r != null) {
                    val fsEndpoint = if (r.fsListV2) "v2 /api/fs/list" else "v1 /file"
                    Text(
                        stringResource(R.string.capabilities_active_fs) + ": " + fsEndpoint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!r.fsListV2 && r.fileListV1) {
                        Text(stringResource(R.string.capabilities_degraded), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                val grouped = statuses.groupBy { it.spec.group }
                FeatureGroup.values().forEach { group ->
                    val items = grouped[group].orEmpty()
                    if (items.isEmpty()) return@forEach
                    Text(
                        stringResource(group.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    items.forEach { CapabilityRow(it) }
                }
                TextButton(
                    onClick = { viewModel.refreshCapabilities() },
                    enabled = !detecting,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (detecting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(if (detecting) R.string.capabilities_detecting else R.string.capabilities_detect),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
private fun CapabilityRow(status: FeatureStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status.state) {
            CapabilityState.VERIFIED_SUPPORTED -> CapabilityIcon(Icons.Filled.Check, MaterialTheme.colorScheme.primary)
            CapabilityState.VERIFIED_UNSUPPORTED -> CapabilityIcon(Icons.Filled.Close, MaterialTheme.colorScheme.outline)
            CapabilityState.ESTIMATED_SUPPORTED ->
                CapabilityIcon(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.tertiary)
            CapabilityState.ESTIMATED_UNSUPPORTED ->
                CapabilityIcon(Icons.Filled.Close, MaterialTheme.colorScheme.tertiary)
            CapabilityState.BUILTIN -> CapabilityIcon(Icons.Filled.Info, MaterialTheme.colorScheme.secondary)
            CapabilityState.UNKNOWN -> CapabilityIcon(Icons.Filled.Info, MaterialTheme.colorScheme.outline)
        }
        Text(
            stringResource(status.spec.labelRes),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(status.state.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun CapabilityIcon(icon: ImageVector, tint: Color) {
    Icon(icon, null, tint = tint, modifier = Modifier.padding(end = 8.dp))
}

@Composable
private fun AddProjectDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    var directory by rememberSaveable { mutableStateOf("") }
    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    var showBrowser by rememberSaveable { mutableStateOf(false) }

    if (showBrowser) {
        ServerFolderBrowser(
            viewModel = viewModel,
            onPick = { path ->
                directory = path
                showBrowser = false
            },
            onDismiss = { showBrowser = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_project_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.add_project_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = { Text(stringResource(R.string.directory_label)) },
                    placeholder = { Text(stringResource(R.string.directory_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showBrowser = true }) {
                    Icon(painterResource(R.drawable.ic_folder), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.add_project_browse))
                }
                if (workspaceState is UiState.Loading) {
                    Text(
                        (workspaceState as UiState.Loading).message,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (workspaceState is UiState.Error) {
                    Text(
                        (workspaceState as UiState.Error).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = directory.isNotBlank() && workspaceState !is UiState.Loading,
                onClick = {
                    viewModel.newSession(directory.trim(), onDone = onDismiss)
                },
            ) { Text(stringResource(R.string.open)) }
        },
        dismissButton = {
            TextButton(
                enabled = workspaceState !is UiState.Loading,
                onClick = onDismiss,
            ) { Text(stringResource(R.string.connect_cancel)) }
        },
    )
}

@Composable
private fun ServerFolderBrowser(
    viewModel: MainViewModel,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var baseDir by remember { mutableStateOf<String?>(null) }
    var currentPath by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<com.example.opencodeclient.data.FileNode>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        baseDir = viewModel.currentWorkingDir()
        loading = false
    }

    LaunchedEffect(baseDir, currentPath) {
        if (baseDir == null) return@LaunchedEffect
        loading = true
        entries = viewModel.listServerFiles(baseDir, currentPath)
        loading = false
    }

    val dirs = entries.filter { it.type == "directory" || it.children != null }
    val absBase = baseDir
    val displayPath = buildString {
        if (!absBase.isNullOrBlank()) append(absBase.trimEnd('/'))
        if (currentPath.isNotEmpty() && currentPath != "/") append("/").append(currentPath.trimStart('/'))
        if (isEmpty()) append("/")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                displayPath,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = MonoFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            when {
                loading -> Text(stringResource(R.string.connecting), style = MaterialTheme.typography.bodySmall)
                baseDir == null -> Text(stringResource(R.string.add_project_browse_empty), style = MaterialTheme.typography.bodySmall)
                dirs.isEmpty() -> Text(stringResource(R.string.add_project_browse_empty), style = MaterialTheme.typography.bodySmall)
                else -> {
                    LazyColumn {
                        if (currentPath.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentPath = parentOfRelative(currentPath) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("..", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        items(dirs) { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentPath = node.path }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text(node.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = baseDir != null,
                onClick = {
                    val abs = buildString {
                        append(baseDir?.trimEnd('/') ?: "")
                        if (currentPath.isNotEmpty() && currentPath != "/") append("/").append(currentPath.trimStart('/'))
                    }
                    onPick(abs)
                },
            ) { Text(stringResource(R.string.open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.connect_cancel))
            }
        },
    )
}

private fun parentOfRelative(path: String): String {
    val p = path.trimEnd('/')
    val idx = p.lastIndexOf('/')
    if (idx <= 0) return ""
    return p.substring(0, idx)
}
