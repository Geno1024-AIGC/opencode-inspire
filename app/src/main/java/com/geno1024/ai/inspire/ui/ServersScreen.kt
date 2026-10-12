package com.geno1024.ai.inspire.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geno1024.ai.inspire.R
import com.geno1024.ai.inspire.data.ServerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var renameUrl by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var mergeMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }

    val linked = servers.filter { it.sid.isNotBlank() }
    val groups: List<Pair<String, List<ServerProfile>>> =
        linked.map { it.sid }.distinct().map { sid -> sid to linked.filter { it.sid == sid } }
    val standalone = servers.filter { it.sid.isBlank() }
    val mergeable = linked.size >= 2

    BackHandler {
        if (mergeMode) {
            mergeMode = false
            selected = emptySet()
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.servers_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (mergeMode) {
                            mergeMode = false
                            selected = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (mergeMode) {
                        TextButton(onClick = { mergeMode = false; selected = emptySet() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        if (mergeable) {
                            TextButton(onClick = { mergeMode = true; selected = emptySet() }) {
                                Text(stringResource(R.string.server_merge_action))
                            }
                        }
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Filled.Add, stringResource(R.string.server_add))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (mergeMode) {
                Text(
                    stringResource(R.string.server_merge_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (servers.isEmpty()) {
                    Text(
                        stringResource(R.string.servers_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (mergeMode) {
                    linked.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (p.url in selected) selected - p.url else selected + p.url
                                }
                                .padding(vertical = 2.dp),
                        ) {
                            Checkbox(
                                checked = p.url in selected,
                                onCheckedChange = { c -> selected = if (c) selected + p.url else selected - p.url },
                            )
                            Text(p.name.ifBlank { p.url }, modifier = Modifier.weight(1f))
                            if (p.url == serverUrl) {
                                Text(
                                    stringResource(R.string.connect_connected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                } else {
                    groups.forEach { (_, members) ->
                        if (members.size > 1) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.server_group_bound, members.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    members.forEach { p ->
                                        ServerListRow(
                                            p = p,
                                            serverUrl = serverUrl,
                                            viewModel = viewModel,
                                            onRename = {
                                                renameUrl = p.url
                                                renameText = p.name
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            val p = members.first()
                            ServerListRow(p, serverUrl, viewModel) {
                                renameUrl = p.url
                                renameText = p.name
                            }
                        }
                    }
                    standalone.forEach { p ->
                        ServerListRow(p, serverUrl, viewModel) {
                            renameUrl = p.url
                            renameText = p.name
                        }
                    }
                }
            }
            if (mergeMode) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.server_merge_selected, selected.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected.size >= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = selected.size >= 2,
                        onClick = {
                            val chosen = servers.filter { it.url in selected }
                            val targetSid = chosen.firstOrNull { it.url == serverUrl }?.sid
                                ?: chosen.firstOrNull()?.sid ?: return@TextButton
                            val sources = chosen.map { it.sid }.filter { it != targetSid }.distinct()
                            viewModel.mergeServers(sources, targetSid)
                            mergeMode = false
                            selected = emptySet()
                        },
                    ) { Text(stringResource(R.string.server_merge_action)) }
                }
            }
        }
    }

    if (showAdd) {
        AddServerDialog(
            onDismiss = { showAdd = false },
            onSave = {
                viewModel.saveServerProfile(it)
                showAdd = false
            },
        )
    }

    val renameTarget = renameUrl
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameUrl = null },
            title = { Text(stringResource(R.string.server_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.server_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameServerProfile(renameTarget, renameText.trim())
                    renameUrl = null
                }) { Text(stringResource(R.string.server_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameUrl = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ServerListRow(
    p: ServerProfile,
    serverUrl: String?,
    viewModel: MainViewModel,
    onRename: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val connected = p.url == serverUrl
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).clickable {
                viewModel.connect(p.url, p.type, p.username, p.password)
            },
        ) {
            Text(p.name.ifBlank { p.url }, fontWeight = FontWeight.Medium)
            Text(p.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (connected) {
            Text(stringResource(R.string.connect_connected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.servers_title))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.server_rename)) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
            }
        }
        IconButton(onClick = { viewModel.removeServerProfile(p.url) }) {
            Icon(Icons.Filled.Close, stringResource(R.string.connect_remove))
        }
    }
}