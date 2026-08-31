package com.example.opencodeclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val colorCandidates = listOf(
    0xFFFFFFFFL,
    0xFFE3F2FDL,
    0xFFBBDEFBL,
    0xFFD1C4E9L,
    0xFFC8E6C9L,
    0xFFFFF9C4L,
    0xFFFFE0B2L,
    0xFFFFCDD2L,
    0xFF0D1117L,
    0xFF161B22L,
    0xFF2D333BL,
    0xFF1F6FEBL,
    0xFF7AA2F7L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val userBubbleColor by viewModel.userBubbleColor.collectAsStateWithLifecycle()
    val assistantBubbleColor by viewModel.assistantBubbleColor.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Use compact token counts (e.g. 2.0M)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = shortTokens,
                    onCheckedChange = { viewModel.setShortTokens(it) },
                )
            }
            HorizontalDivider()

            Text(
                "Theme",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setTheme(value) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = theme == value,
                        onClick = { viewModel.setTheme(value) },
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()

            Text(
                "Bubble colors",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ColorRow("Your messages", userBubbleColor, viewModel::setUserBubbleColor)
            ColorRow("Assistant messages", assistantBubbleColor, viewModel::setAssistantBubbleColor)
        }
    }
}

@Composable
private fun ColorRow(
    label: String,
    current: Long,
    onPick: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val defaultBox = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clickable { onPick(-1L) }
            Row(
                modifier = defaultBox,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "D",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            colorCandidates.forEach { c ->
                val selected = current == c
                val box = Modifier
                    .size(32.dp)
                    .background(Color(c.toInt()), RoundedCornerShape(8.dp))
                    .clickable { onPick(c) }
                Row(
                    modifier = box,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (selected) {
                        Text("✓", color = contrastColor(c), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun contrastColor(color: Long): Color {
    val c = Color(color.toInt())
    val luminance = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}