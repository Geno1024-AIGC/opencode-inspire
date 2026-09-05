package com.example.opencodeclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencodeclient.R
import com.example.opencodeclient.data.ServerProfile

@Composable
fun ConnectScreen(
    viewModel: MainViewModel,
    onConnected: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("4096") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.connect_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        if (servers.isEmpty()) {
            ServerGuide()
            Spacer(Modifier.height(16.dp))
        }

        if (adding) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.host_label)) },
                    placeholder = { Text(stringResource(R.string.host_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.port_label)) },
                    placeholder = { Text(stringResource(R.string.port_hint_default)) },
                    modifier = Modifier.width(132.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username_label_optional)) },
                placeholder = if (password.isNotEmpty()) {
                    { Text(stringResource(R.string.username_hint_default)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_label_optional)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
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
                    Spacer(Modifier.height(12.dp))
                }
                else -> {}
            }
            Button(
                onClick = {
                    val hostTrim = host.trim()
                    val portTrim = port.trim().ifBlank { "4096" }
                    val base = if (
                        hostTrim.startsWith("http://") || hostTrim.startsWith("https://")
                    ) hostTrim else "http://$hostTrim"
                    val url = "$base:$portTrim"
                    val user = username.trim().takeIf { it.isNotEmpty() }
                        ?: if (password.isNotEmpty()) "opencode" else null
                    viewModel.connect(
                        url,
                        username = user,
                        password = password.takeIf { it.isNotEmpty() },
                        onSuccess = onConnected,
                    )
                },
                enabled = host.isNotBlank() && state !is UiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.connect_save))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    adding = false
                    host = ""
                    port = "4096"
                    username = ""
                    password = ""
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.connect_cancel))
            }
        } else {
            Button(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.connect_add))
            }
        }

        if (!adding) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            servers.sortedBy { it.url != serverUrl }.forEach { p ->
                ServerRow(
                    profile = p,
                    isCurrent = p.url == serverUrl,
                    loading = state is UiState.Loading,
                    onConnect = {
                        viewModel.connect(
                            p.url,
                            p.username?.takeIf { it.isNotEmpty() },
                            p.password?.takeIf { it.isNotEmpty() },
                            onSuccess = onConnected,
                        )
                    },
                    onDelete = { viewModel.removeServerProfile(p.url) },
                )
            }
        }
    }
}

@Composable
private fun ServerGuide() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.connect_guide_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.connect_guide_step1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.connect_guide_cmd),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = MonoFontFamily,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            R.string.connect_guide_point1,
            R.string.connect_guide_point2,
            R.string.connect_guide_point3,
            R.string.connect_guide_point4,
        ).forEach { res ->
            Text(
                "• ${stringResource(res)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ServerRow(
    profile: ServerProfile,
    isCurrent: Boolean,
    loading: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onConnect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                profile.name.ifBlank { profile.url },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (profile.url != profile.name) {
                Text(
                    profile.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) {
                Text(
                    stringResource(R.string.connect_connected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.connect_remove))
        }
    }
}