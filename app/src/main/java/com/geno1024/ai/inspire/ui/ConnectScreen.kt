package com.geno1024.ai.inspire.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geno1024.ai.inspire.R
import com.geno1024.ai.inspire.data.AgentClientFactory
import com.geno1024.ai.inspire.data.AgentClientRegistry
import com.geno1024.ai.inspire.data.ServerField
import com.geno1024.ai.inspire.data.ServerGuide
import com.geno1024.ai.inspire.data.ServerProfile

@Composable
fun ConnectScreen(
    viewModel: MainViewModel,
    onConnected: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    val factories = remember { AgentClientRegistry.all() }
    var typeId by rememberSaveable { mutableStateOf(AUTO_TYPE) }
    var fieldValues by remember(typeId) { mutableStateOf(emptyMap<String, String>()) }
    val hostValue = fieldValues["host"]?.trim().orEmpty()
    val detectedFactory = if (typeId == AUTO_TYPE) {
        factories.firstOrNull { it.supports(hostValue) }
    } else {
        null
    }
    val factory = detectedFactory
        ?: factories.firstOrNull { it.id == typeId }
        ?: factories.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .then(if (Build.VERSION.SDK_INT >= 35) Modifier.imePadding() else Modifier)
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
            Text(
                stringResource(R.string.connect_welcome),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        if (adding) {
            TypeSelector(
                label = stringResource(R.string.server_type_label),
                options = listOf(AUTO_TYPE to stringResource(R.string.server_type_auto)) + factories.map { it.id to it.label },
                selected = if (typeId == AUTO_TYPE) AUTO_TYPE else factory.id,
                onSelect = {
                    typeId = it
                    fieldValues = emptyMap()
                },
            )
            if (typeId == AUTO_TYPE) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (detectedFactory != null) {
                        stringResource(R.string.server_type_detected, detectedFactory.label)
                    } else {
                        stringResource(R.string.server_type_detect_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            if (typeId != AUTO_TYPE || detectedFactory != null) {
                factory.guide?.let {
                    ServerGuideBlock(it)
                    Spacer(Modifier.height(14.dp))
                }
            }
            val displayedFields = factory.fields.map { f ->
                if (typeId != "opencode" && f.key == "port") {
                    f.copy(hintRes = R.string.port_hint_generic)
                } else {
                    f
                }
            }
            ServerFields(
                fields = displayedFields,
                values = fieldValues,
                onValueChange = { key, value -> fieldValues = fieldValues + (key to value) },
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
                    val config = factory.buildConfig(fieldValues)
                    viewModel.connect(
                        config.serverUrl,
                        type = factory.id,
                        username = config.username,
                        password = config.password,
                        onSuccess = onConnected,
                    )
                },
                enabled = factory.fields.filter { it.required }.all { fieldValues[it.key].isNullOrBlank().not() }
                    && state !is UiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.connect_save))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    adding = false
                    typeId = AUTO_TYPE
                    fieldValues = emptyMap()
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
                            type = p.type,
                            username = p.username?.takeIf { it.isNotEmpty() },
                            password = p.password?.takeIf { it.isNotEmpty() },
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
internal fun TypeSelector(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Box {
            Text(
                options.firstOrNull { it.first == selected }?.second ?: selected,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, itemLabel) ->
                    DropdownMenuItem(
                        text = { Text(itemLabel) },
                        onClick = { onSelect(id); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerFields(
    fields: List<ServerField>,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
) {
    var i = 0
    while (i < fields.size) {
        val field = fields[i]
        val next = fields.getOrNull(i + 1)
        val showHint = { f: ServerField ->
            f.hintRes != null && (f.key != "username" || values["password"].isNullOrEmpty().not())
        }
        if (next != null && next.widthDp != null) {
            if (field.labelRes != null) {
                FieldLabel(field.labelRes)
                Spacer(Modifier.height(6.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FieldInput(
                    field,
                    value = values[field.key].orEmpty(),
                    onValueChange = { onValueChange(field.key, it) },
                    modifier = Modifier.weight(1f),
                )
                FieldInput(
                    next,
                    value = values[next.key].orEmpty(),
                    onValueChange = { onValueChange(next.key, it) },
                    modifier = Modifier.width(next.widthDp!!.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            i += 2
        } else {
            if (field.labelRes != null) {
                FieldLabel(field.labelRes)
                Spacer(Modifier.height(6.dp))
            }
            FieldInput(
                field,
                value = values[field.key].orEmpty(),
                onValueChange = { onValueChange(field.key, it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            i += 1
        }
    }
}

@Composable
private fun FieldLabel(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FieldInput(
    field: ServerField,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = field.hintRes?.let { { Text(stringResource(it)) } },
        modifier = modifier,
        singleLine = true,
        visualTransformation = if (field.password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (field.numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
    )
}

@Composable
private fun ServerGuideBlock(guide: ServerGuide) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(guide.titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(guide.step1Res),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            guide.command,
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
        guide.pointsRes.forEach { res ->
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
    val typeLabel = remember(profile.type) { AgentClientRegistry.byId(profile.type)?.label }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onConnect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.name.ifBlank { profile.url },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (typeLabel != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
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

internal const val AUTO_TYPE = "auto"