package com.geno1024.ai.occ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geno1024.ai.occ.R
import com.geno1024.ai.occ.data.CapabilityState
import com.geno1024.ai.occ.data.FeatureGroup
import com.geno1024.ai.occ.data.FeatureStatus
import com.geno1024.ai.occ.data.ServerProfile
import com.geno1024.ai.occ.data.Session
import com.geno1024.ai.occ.data.StoredHistoryStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

sealed class Screen {
    data object Connect : Screen()
    data object Main : Screen()
    data object Settings : Screen()
    data object Calendar : Screen()
    data object Export : Screen()
    data object About : Screen()

    val key: String
        get() = when (this) {
            Connect -> "connect"
            Main -> "main"
            Settings -> "settings"
            Calendar -> "calendar"
            Export -> "export"
            About -> "about"
        }

    companion object {
        fun fromKey(key: String): Screen = when (key) {
            "main" -> Main
            "settings" -> Settings
            "calendar" -> Calendar
            "export" -> Export
            "about" -> About
            else -> Connect
        }
    }
}

@Composable
fun OpenCodeApp(viewModel: MainViewModel) {
    var screenKey by rememberSaveable { mutableStateOf(Screen.Connect.key) }
    val screen = Screen.fromKey(screenKey)
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var exportStartMonth by remember { mutableStateOf(java.time.YearMonth.now()) }

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
                onOpenCalendar = { screenKey = Screen.Calendar.key },
                onOpenAbout = { screenKey = Screen.About.key },
            )
        }
        is Screen.Settings -> saveableStateHolder.SaveableStateProvider(Screen.Settings.key) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { screenKey = Screen.Main.key },
                onOpenCalendar = { screenKey = Screen.Calendar.key },
                onOpenAbout = { screenKey = Screen.About.key },
            )
        }
        is Screen.Calendar -> saveableStateHolder.SaveableStateProvider(Screen.Calendar.key) {
            TokenCalendarScreen(
                viewModel = viewModel,
                onBack = { screenKey = Screen.Settings.key },
                onOpenExport = { m ->
                    exportStartMonth = m
                    screenKey = Screen.Export.key
                },
            )
        }
        is Screen.Export -> saveableStateHolder.SaveableStateProvider(Screen.Export.key) {
            ExportScreen(
                viewModel = viewModel,
                startMonth = exportStartMonth,
                onBack = { screenKey = Screen.Calendar.key },
            )
        }
        is Screen.About -> saveableStateHolder.SaveableStateProvider(Screen.About.key) {
            AboutScreen(
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
    onOpenCalendar: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var showServers by remember { mutableStateOf(false) }
    var showAddProject by remember { mutableStateOf(false) }
    var showCapabilities by remember { mutableStateOf(false) }
    var showQuickCommand by remember { mutableStateOf(false) }

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
                onQuickCommand = { showQuickCommand = true },
                onDisconnect = onDisconnect,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                        (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
                        keyEvent.key == androidx.compose.ui.input.key.Key.K
                    ) {
                        showQuickCommand = true
                        true
                    } else {
                        false
                    }
                },
        ) {
            ChatScreen(
                viewModel = viewModel,
                onMenu = { scope.launch { drawerState.open() } },
            )
        }
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
    if (showQuickCommand) {
        QuickCommandDialog(
            viewModel = viewModel,
            onDismiss = { showQuickCommand = false },
            onAddProject = { showQuickCommand = false; showAddProject = true },
            onServers = { showQuickCommand = false; showServers = true },
            onCapabilities = { showQuickCommand = false; showCapabilities = true },
            onSettings = { showQuickCommand = false; onOpenSettings() },
            onCalendar = { showQuickCommand = false; onOpenCalendar() },
            onAbout = { showQuickCommand = false; onOpenAbout() },
        )
    }
    LaunchedEffect(Unit) {
        viewModel.ensureLoaded()
    }
}

private data class QuickAction(
    val icon: ImageVector,
    val label: String,
    val subtitle: String,
    val run: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCommandDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onAddProject: () -> Unit,
    onServers: () -> Unit,
    onCapabilities: () -> Unit,
    onSettings: () -> Unit,
    onCalendar: () -> Unit,
    onAbout: () -> Unit,
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val sessionRefs = remember(projects) { projects.flatMap { p -> p.sessions.map { it to p.worktree } } }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val cmds = stringResource(R.string.quick_title)
    val cmdAdd = stringResource(R.string.drawer_add_project)
    val cmdServers = stringResource(R.string.servers_title)
    val cmdCapabilities = stringResource(R.string.capabilities_title)
    val cmdSettings = stringResource(R.string.settings_title)
    val cmdCalendar = stringResource(R.string.calendar_title)
    val cmdAbout = stringResource(R.string.settings_about)

    val actions = listOf(
        QuickAction(Icons.Filled.Add, cmdAdd, "", onAddProject),
        QuickAction(Icons.Filled.List, cmdServers, "", onServers),
        QuickAction(Icons.Filled.Refresh, cmdCapabilities, "", onCapabilities),
        QuickAction(Icons.Filled.Settings, cmdSettings, "", onSettings),
        QuickAction(Icons.Filled.Home, cmdCalendar, "", onCalendar),
        QuickAction(Icons.Filled.Info, cmdAbout, "", onAbout),
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Search, stringResource(R.string.quick_search_hint)) }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.quick_search_hint),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.drawer_close)) }
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    val filteredActions = if (q.isEmpty()) actions else actions.filter {
                        it.label.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
                    }
                    val filteredSessions = if (q.isEmpty()) sessionRefs else sessionRefs.filter { (s, path) ->
                        s.title.contains(q, ignoreCase = true) || path.contains(q, ignoreCase = true)
                    }
                    if (filteredActions.isNotEmpty()) {
                        item { QuickListHeader(cmds) }
                        items(filteredActions.size) { i ->
                            val a = filteredActions[i]
                            QuickActionRow(a) {
                                a.run()
                            }
                        }
                    }
                    if (filteredSessions.isNotEmpty()) {
                        item { QuickListHeader(stringResource(R.string.quick_sessions)) }
                        items(filteredSessions, key = { it.first.id }) { (s, path) ->
                            QuickSessionRow(
                                title = s.title.ifBlank { stringResource(R.string.untitled_session) },
                                subtitle = path,
                                onClick = {
                                    onDismiss()
                                    viewModel.openSession(s.id)
                                },
                            )
                        }
                    }
                    if (filteredActions.isEmpty() && filteredSessions.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.quick_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickListHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun QuickActionRow(action: QuickAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(action.label, style = MaterialTheme.typography.bodyLarge)
            if (action.subtitle.isNotBlank()) {
                Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickSessionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
    onQuickCommand: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activeSessionTotalElapsed by viewModel.sessionTotalElapsed.collectAsStateWithLifecycle()
    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()
    val storedStats by viewModel.storedStats.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val sessionModelTokens by viewModel.sessionModelTokens.collectAsStateWithLifecycle()
    val sessionCosts = remember(sessionModelTokens) {
        sessionModelTokens.mapValues { (_, models) -> models.values.sumOf { it.cost } }
    }
    val projectSummaries = remember(sessionModelTokens, projects) {
        projects.associate { p ->
            val fresh = p.sessions.sumOf { s ->
                sessionModelTokens[s.id]?.values?.sumOf { it.input + it.output + it.reasoning } ?: 0L
            }
            val msgs = p.sessions.sumOf { s ->
                sessionModelTokens[s.id]?.values?.sumOf { it.msgs } ?: 0L
            }
            val cost = p.sessions.sumOf { s ->
                sessionModelTokens[s.id]?.values?.sumOf { it.cost } ?: 0.0
            }
            p.id to ProjectSummary(fresh = fresh, msgs = msgs, cost = cost)
        }
    }
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
                Row {
                    IconButton(onClick = onQuickCommand) { Icon(Icons.Filled.Search, stringResource(R.string.quick_title)) }
                    IconButton(onClick = onAddProject) { Icon(Icons.Filled.Add, stringResource(R.string.drawer_add_project)) }
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.drawer_refresh)) }
                }
            }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                item {
                    PinnedSection(
                        sessions = remember(projects, favorites) {
                            projects.flatMap { it.sessions }.filter { it.id in favorites }
                        },
                        activeSessionId = activeSession?.id,
                        activeSessionTotalElapsed = activeSessionTotalElapsed,
                        storedStats = storedStats,
                        shortTokens = shortTokens,
                        archived = archived,
                        sessionCosts = sessionCosts,
                        onOpenSession = { viewModel.openSession(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onToggleArchived = { viewModel.toggleArchived(it) },
                    )
                }
                items(projects, key = { it.id }) { project ->
                    ExpandableProject(
                        project = project,
                        activeSessionId = activeSession?.id,
                        activeSessionTotalElapsed = activeSessionTotalElapsed,
                        storedStats = storedStats,
                        shortTokens = shortTokens,
                        favorites = favorites,
                        archived = archived,
                        sessionCosts = sessionCosts,
                        summary = projectSummaries[project.id],
                        onOpenSession = { viewModel.openSession(it) },
                        onNewSession = { viewModel.newSession(project.worktree) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onToggleArchived = { viewModel.toggleArchived(it) },
                    )
                }
            }
            ArchivedSection(
                sessions = remember(projects, archived) {
                    projects.flatMap { it.sessions }.filter { it.id in archived }
                },
                activeSessionId = activeSession?.id,
                activeSessionTotalElapsed = activeSessionTotalElapsed,
                storedStats = storedStats,
                shortTokens = shortTokens,
                sessionCosts = sessionCosts,
                onOpenSession = { viewModel.openSession(it) },
                onToggleArchived = { viewModel.toggleArchived(it) },
            )
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
    archived: Set<String> = emptySet(),
    sessionCosts: Map<String, Double> = emptyMap(),
    summary: ProjectSummary? = null,
    onOpenSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleArchived: (String) -> Unit,
) {
    var expanded by rememberSaveable(project.id) { mutableStateOf(project.sessions.isEmpty()) }
    val orderedSessions = remember(project.sessions, favorites, archived) {
        project.sessions.filter { it.id !in archived && it.id !in favorites }.sortedByDescending { it.id in favorites }
    }
    val groupedSessions = remember(orderedSessions) { groupSessionsByDay(orderedSessions) }
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
                if (summary != null && (summary.fresh + summary.msgs > 0L || summary.cost > 0.0)) {
                    Text(
                        "${formatTokens(summary.fresh, shortTokens)}, ${formatTokens(summary.msgs, shortTokens)}, ${formatCost(summary.cost)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontFamily = MonoFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(if (expanded) painterResource(R.drawable.ic_expand_less) else painterResource(R.drawable.ic_expand_more), null)
        }
        if (expanded) {
            groupedSessions.forEach { (key, sessions) ->
                Text(
                    stringResource(
                        when (key) {
                            "today" -> R.string.session_group_today
                            "yesterday" -> R.string.session_group_yesterday
                            else -> R.string.session_group_earlier
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp, end = 16.dp, top = 6.dp),
                )
                sessions.forEach { s ->
                    SessionRow(
                        s = s,
                        isActive = s.id == activeSessionId,
                        isFavorite = s.id in favorites,
                        isArchived = s.id in archived,
                        totalElapsed = storedStats[s.id]?.totalElapsed,
                        cost = sessionCosts[s.id] ?: 0.0,
                        shortTokens = shortTokens,
                        onClick = { onOpenSession(s.id) },
                        onToggleFavorite = { onToggleFavorite(s.id) },
                        onToggleArchived = { onToggleArchived(s.id) },
                    )
                }
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
    isArchived: Boolean = false,
    totalElapsed: Long? = null,
    cost: Double = 0.0,
    shortTokens: Boolean = true,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onToggleArchived: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 40.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
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
            val fresh = s.tokens?.let { it.input + it.output + it.reasoning } ?: 0L
            val elapsedStr = totalElapsed?.let { formatElapsed(it).ifBlank { "0s" } } ?: "-"
            Text(
                "${formatTokens(fresh, shortTokens)}, $elapsedStr, ${formatCost(cost)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                fontFamily = MonoFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        var rowMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { rowMenu = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    stringResource(R.string.session_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (isArchived) R.string.session_unarchive else R.string.session_archive)) },
                    onClick = {
                        rowMenu = false
                        onToggleArchived()
                    },
                )
            }
        }
    }
}

private fun sessionCreatedMillis(s: Session): Long {
    val v = s.time?.created ?: 0L
    return when {
        v <= 0L -> 0L
        v < 10_000_000_000L -> v * 1000L
        else -> v
    }
}

private fun groupSessionsByDay(sessions: List<Session>): List<Pair<String, List<Session>>> {
    val now = LocalDate.now()
    val yesterday = now.minusDays(1)
    val groups = HashMap<String, MutableList<Session>>()
    for (s in sessions) {
        val millis = sessionCreatedMillis(s)
        val key = if (millis <= 0L) "earlier" else {
            val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            when (d) {
                now -> "today"
                yesterday -> "yesterday"
                else -> "earlier"
            }
        }
        groups.getOrPut(key) { mutableListOf() }.add(s)
    }
    return listOf("today", "yesterday", "earlier").mapNotNull { groups[it]?.let { g -> it to g } }
}

@Composable
private fun PinnedSection(
    sessions: List<Session>,
    activeSessionId: String?,
    activeSessionTotalElapsed: Long? = null,
    storedStats: Map<String, StoredHistoryStats> = emptyMap(),
    shortTokens: Boolean = true,
    archived: Set<String> = emptySet(),
    sessionCosts: Map<String, Double> = emptyMap(),
    onOpenSession: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleArchived: (String) -> Unit,
) {
    if (sessions.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                stringResource(R.string.drawer_pinned, sessions.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) painterResource(R.drawable.ic_expand_less) else painterResource(R.drawable.ic_expand_more), null)
        }
        if (expanded) {
            sessions.forEach { s ->
                SessionRow(
                    s = s,
                    isActive = s.id == activeSessionId,
                    isFavorite = true,
                    isArchived = s.id in archived,
                    totalElapsed = storedStats[s.id]?.totalElapsed,
                    cost = sessionCosts[s.id] ?: 0.0,
                    shortTokens = shortTokens,
                    onClick = { onOpenSession(s.id) },
                    onToggleFavorite = { onToggleFavorite(s.id) },
                    onToggleArchived = { onToggleArchived(s.id) },
                )
            }
        }
    }
}

@Composable
private fun ArchivedSection(
    sessions: List<Session>,
    activeSessionId: String?,
    activeSessionTotalElapsed: Long? = null,
    storedStats: Map<String, StoredHistoryStats> = emptyMap(),
    shortTokens: Boolean = true,
    sessionCosts: Map<String, Double> = emptyMap(),
    onOpenSession: (String) -> Unit,
    onToggleArchived: (String) -> Unit,
) {
    if (sessions.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.drawer_archived, sessions.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) painterResource(R.drawable.ic_expand_less) else painterResource(R.drawable.ic_expand_more), null)
        }
        if (expanded) {
            sessions.forEach { s ->
                SessionRow(
                    s = s,
                    isActive = s.id == activeSessionId,
                    isFavorite = false,
                    isArchived = true,
                    totalElapsed = storedStats[s.id]?.totalElapsed,
                    cost = sessionCosts[s.id] ?: 0.0,
                    shortTokens = shortTokens,
                    onClick = { onOpenSession(s.id) },
                    onToggleArchived = { onToggleArchived(s.id) },
                )
            }
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

fun formatCost(cost: Double): String = if (cost >= 1.0)
    "$%.2f".format(cost) else "$%.4f".format(cost)

fun formatBytes(bytes: Long): String = bytes.toString()

fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1L shl 30 -> "%.2f GiB/s".format(bytesPerSec.toDouble() / (1L shl 30))
    bytesPerSec >= 1L shl 20 -> "%.2f MiB/s".format(bytesPerSec.toDouble() / (1L shl 20))
    bytesPerSec >= 1L shl 10 -> "%.2f kiB/s".format(bytesPerSec.toDouble() / (1L shl 10))
    else -> "$bytesPerSec B/s"
}

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
    var gitUrl by rememberSaveable { mutableStateOf("") }
    var cloneMode by rememberSaveable { mutableStateOf(false) }
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

    val targetDir = if (cloneMode && gitUrl.isNotBlank() && directory.isNotBlank()) {
        "${directory.trimEnd('/')}/${viewModel.repoNameFromUrl(gitUrl)}"
    } else {
        directory
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_project_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !cloneMode,
                        onClick = { cloneMode = false },
                        label = { Text(stringResource(R.string.add_project_mode_existing)) },
                    )
                    FilterChip(
                        selected = cloneMode,
                        onClick = { cloneMode = true },
                        label = { Text(stringResource(R.string.add_project_mode_clone)) },
                    )
                }
                if (cloneMode) {
                    OutlinedTextField(
                        value = gitUrl,
                        onValueChange = { gitUrl = it },
                        label = { Text(stringResource(R.string.git_url_label)) },
                        placeholder = { Text(stringResource(R.string.git_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (cloneMode) {
                    Text(
                        stringResource(R.string.add_project_clone_into),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        stringResource(R.string.add_project_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = {
                        Text(if (cloneMode) stringResource(R.string.add_project_clone_into) else stringResource(R.string.directory_label))
                    },
                    placeholder = { Text(stringResource(R.string.directory_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showBrowser = true }) {
                    Icon(painterResource(R.drawable.ic_folder), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.add_project_browse))
                }
                if (cloneMode) {
                    Text(
                        targetDir,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                enabled = workspaceState !is UiState.Loading &&
                    targetDir.isNotBlank() && if (cloneMode) gitUrl.isNotBlank() else true,
                onClick = {
                    if (cloneMode) {
                        viewModel.cloneProject(gitUrl.trim(), directory, onDone = onDismiss)
                    } else {
                        viewModel.newSession(directory.trim(), onDone = onDismiss)
                    }
                },
            ) { Text(stringResource(if (cloneMode) R.string.add_project_clone else R.string.open)) }
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
    var entries by remember { mutableStateOf(emptyList<com.geno1024.ai.occ.data.FileNode>()) }
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

data class ProjectSummary(
    val fresh: Long = 0L,
    val msgs: Long = 0L,
    val cost: Double = 0.0,
)
