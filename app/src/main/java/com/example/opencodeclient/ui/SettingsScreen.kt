package com.example.opencodeclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val presetColors = listOf(
    0xFFFB8C00L, 0xFFF44A6AL, 0xFFEC407AL, 0xFFE91E63L,
    0xFFAB47BFL, 0xFF7E57C2L, 0xFF5C6BC0L, 0xFF42A5F5L,
    0xFF29B6F6L, 0xFF26C6DAL, 0xFF26A69AL, 0xFF66BB6AL,
    0xFF9CCC65L, 0xFFD4E157L, 0xFFFFEE58L, 0xFFFFCA28L,
    0xFFFF7043L, 0xFF8D6E63L, 0xFF78909CL, 0xFF37474FL,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val userBubbleColor by viewModel.userBubbleColor.collectAsStateWithLifecycle()
    val assistantBubbleColor by viewModel.assistantBubbleColor.collectAsStateWithLifecycle()

    val sections = listOf("General", "Appearance")
    var section by remember { mutableStateOf("General") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                sections.forEach { s ->
                    val selected = section == s
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { section = s },
                    ) {
                        Text(
                            s,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (section) {
                    "General" -> GeneralTab(shortTokens = shortTokens, onShortTokens = viewModel::setShortTokens)
                    else -> AppearanceTab(
                        theme = theme,
                        onTheme = viewModel::setTheme,
                        userColor = userBubbleColor,
                        assistantColor = assistantBubbleColor,
                        onUserColor = viewModel::setUserBubbleColor,
                        onAssistantColor = viewModel::setAssistantBubbleColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralTab(
    shortTokens: Boolean,
    onShortTokens: (Boolean) -> Unit,
) {
    SettingSwitchRow(
        title = "Compact token counts",
        subtitle = "Show token numbers as 2.0M / 12.5k instead of full digits",
        checked = shortTokens,
        onCheckedChange = onShortTokens,
    )
}

@Composable
private fun AppearanceTab(
    theme: String,
    onTheme: (String) -> Unit,
    userColor: Long,
    assistantColor: Long,
    onUserColor: (Long) -> Unit,
    onAssistantColor: (Long) -> Unit,
) {
    Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    listOf(
        "system" to "Follow system",
        "light" to "Light",
        "dark" to "Dark",
    ).forEach { (value, label) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTheme(value) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = theme == value, onClick = { onTheme(value) })
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }

    HorizontalDivider()

    Text("Chat bubbles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    var picking by remember { mutableStateOf<String?>(null) }
    ColorPreviewRow(
        title = "Your messages",
        color = userColor,
        onClick = { picking = "user" },
    )
    ColorPreviewRow(
        title = "Assistant messages",
        color = assistantColor,
        onClick = { picking = "assistant" },
    )

    val target = picking
    if (target != null) {
        ColorPickerDialog(
            title = if (target == "user") "Your messages color" else "Assistant messages color",
            initial = if (target == "user") userColor else assistantColor,
            onPick = { c ->
                if (target == "user") onUserColor(c) else onAssistantColor(c)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ColorPreviewRow(
    title: String,
    color: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (color >= 0) Color(color.toInt()) else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color < 0) {
                Text("D", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialColor = Color(if (initial >= 0) initial.toInt() else android.graphics.Color.WHITE)
    val initialHsv = remember {
        val hsv = floatArrayOf(0f, 0f, 0f)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }
    var currentColor by remember { mutableStateOf(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    fun hsvToColor(): Color {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        return Color(c)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Presets", style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(80.dp),
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { onPick(-1L) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("D", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    items(presetColors) { c ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(Color(c.toInt()), CircleShape)
                                .clickable {
                                    currentColor = Color(c.toInt())
                                    val hsv = floatArrayOf(0f, 0f, 0f)
                                    android.graphics.Color.colorToHSV(c.toInt(), hsv)
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                },
                        )
                    }
                }

                HorizontalDivider()

                Text("Customize", style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(currentColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#%06X".format((currentColor.toArgb() and 0xFFFFFF)), color = contrastColor(currentColor))
                }
                SliderRow("Hue", hue, { hue = it })
                SliderRow("Saturation", sat, { sat = it })
                SliderRow("Brightness", value, { value = it })
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val argb = hsvToColor().toArgb() and 0xFFFFFF
                    onPick(0xFF000000L or argb.toLong())
                },
            ) { Text("Apply") }
        },
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.size(80.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun contrastColor(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}