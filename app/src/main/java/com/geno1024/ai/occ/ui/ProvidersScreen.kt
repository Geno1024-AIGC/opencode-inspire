package com.geno1024.ai.occ.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geno1024.ai.occ.R
import com.geno1024.ai.occ.data.IntegrationInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshText = stringResource(R.string.providers_refreshed)
    val connectFailText = stringResource(R.string.providers_connect_fail)
    val noKeyText = stringResource(R.string.providers_no_key)
    val integrations by viewModel.integrations.collectAsState()
    var connectTarget by remember { mutableStateOf<IntegrationInfo?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        refreshing = true
        viewModel.refreshIntegrations()
        refreshing = false
    }

    fun doRefresh() {
        viewModel.refreshIntegrations()
        scope.launch { snackbarHostState.showSnackbar(refreshText) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_providers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { doRefresh() }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.drawer_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                stringResource(R.string.providers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            if (integrations.isEmpty()) {
                if (refreshing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        stringResource(R.string.providers_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    items(integrations, key = { it.id }) { it ->
                        ProviderRow(
                            integration = it,
                            onClick = {
                                if (it.supportsKey) connectTarget = it
                                else scope.launch { snackbarHostState.showSnackbar(it.displayName + ": " + noKeyText) }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    connectTarget?.let { target ->
        ProviderKeyDialog(
            integration = target,
            onDismiss = { connectTarget = null },
            onConnect = { key, label ->
                viewModel.connectIntegration(
                    target.id, key, label,
                    onResult = { ok ->
                        if (ok) {
                            connectTarget = null
                            scope.launch { snackbarHostState.showSnackbar(target.displayName + " ✓") }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(connectFailText) }
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun ProviderRow(integration: IntegrationInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(integration.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                integration.id,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                methodHint(integration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (integration.connections.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    integration.connections.firstOrNull()?.label
                        ?: stringResource(R.string.providers_connected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun methodHint(integration: IntegrationInfo): String {
    val keyText = stringResource(R.string.providers_method_key)
    val oauthText = stringResource(R.string.providers_method_oauth)
    return integration.methods.joinToString(", ") { m ->
        when {
            m.type == "key" -> keyText
            m.type == "oauth" -> oauthText
            m.type == "env" -> m.names.joinToString("/")
            else -> m.type
        }
    }
}

@Composable
private fun ProviderKeyDialog(
    integration: IntegrationInfo,
    onDismiss: () -> Unit,
    onConnect: (key: String, label: String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(integration.displayName) },
        text = {
            Column {
                Text(
                    stringResource(R.string.providers_key_hint, integration.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.providers_key_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.providers_label_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(key.trim(), label.trim()) },
                enabled = key.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.providers_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}