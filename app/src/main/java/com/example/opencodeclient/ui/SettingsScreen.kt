package com.example.opencodeclient.ui

import com.example.opencodeclient.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencodeclient.R
import com.example.opencodeclient.ui.theme.ThemePreset
import com.example.opencodeclient.ui.theme.PresetDarkSchemes
import kotlinx.serialization.builtins.serializer

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
    onOpenCalendar: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    val shortTokens by viewModel.shortTokens.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val customThemeColorsJson by viewModel.customThemeColors.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val mirror by viewModel.mirror.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val checkingUpdate by viewModel.checkingUpdate.collectAsStateWithLifecycle()
    val updateMessage by viewModel.updateMessage.collectAsStateWithLifecycle()
    val userBubbleColor by viewModel.userBubbleColor.collectAsStateWithLifecycle()
    val assistantBubbleColor by viewModel.assistantBubbleColor.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.drawer_close)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── General ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(R.string.settings_general)
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_compact_tokens),
                        subtitle = stringResource(R.string.settings_compact_tokens_sub),
                        checked = shortTokens,
                        onCheckedChange = viewModel::setShortTokens,
                    )
                    SectionLabel(R.string.settings_language, small = true)
                    DropdownSetting(
                        listOf(
                            "system" to stringResource(R.string.lang_system),
                            "en" to stringResource(R.string.lang_english),
                            "zh" to stringResource(R.string.lang_chinese),
                        ),
                        selected = language,
                        onSelect = viewModel::setLanguage,
                    )
                }
            }

            // ── Appearance ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(R.string.settings_appearance)
                    SectionLabel(R.string.settings_theme, small = true)
                    DropdownSetting(
                        listOf(
                            "system" to stringResource(R.string.theme_system),
                            "light" to stringResource(R.string.theme_light),
                            "dark" to stringResource(R.string.theme_dark),
                        ),
                        selected = theme,
                        onSelect = viewModel::setTheme,
                    )

                    SectionLabel(R.string.settings_theme_preset, small = true)
                    val presetOptions = listOf(
                        "default" to stringResource(R.string.theme_preset_default),
                        "github" to stringResource(R.string.theme_preset_github),
                        "tokyo_night" to stringResource(R.string.theme_preset_tokyo_night),
                        "dracula" to stringResource(R.string.theme_preset_dracula),
                        "nord" to stringResource(R.string.theme_preset_nord),
                        "one_dark" to stringResource(R.string.theme_preset_one_dark),
                        "solarized" to stringResource(R.string.theme_preset_solarized),
                        "gruvbox" to stringResource(R.string.theme_preset_gruvbox),
                        "catppuccin" to stringResource(R.string.theme_preset_catppuccin),
                        "china_red" to stringResource(R.string.theme_preset_china_red),
                        "persimmon" to stringResource(R.string.theme_preset_persimmon),
                        "lemon" to stringResource(R.string.theme_preset_lemon),
                        "forest" to stringResource(R.string.theme_preset_forest),
                        "mint" to stringResource(R.string.theme_preset_mint),
                        "sky_blue" to stringResource(R.string.theme_preset_sky_blue),
                        "grape" to stringResource(R.string.theme_preset_grape),
                        "custom" to stringResource(R.string.theme_preset_custom),
                    )
                    DropdownSetting(
                        presetOptions,
                        selected = themePreset,
                        onSelect = viewModel::setThemePreset,
                    )

                    if (themePreset == "custom") {
                        val customColors = try {
                            runCatching {
                                kotlinx.serialization.json.Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonPrimitive>>(customThemeColorsJson)
                                    .mapValues { it.value.content.toLongOrNull() ?: 0L }
                            }.getOrNull() ?: emptyMap()
                        } catch (_: Exception) { emptyMap() }

                        fun updateCustomColor(key: String, value: Long) {
                            val updated = customColors.toMutableMap()
                            updated[key] = value
                            val json = kotlinx.serialization.json.Json.encodeToString(
                                kotlinx.serialization.builtins.MapSerializer(
                                    String.serializer(),
                                    Long.serializer(),
                                ),
                                updated,
                            )
                            viewModel.setCustomThemeColors(json)
                        }

                        val isDark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
                        val colorDefaults = mapOf(
                            "primary" to (if (isDark) 0xFF7AA2F7L else 0xFF1F6FEBL),
                            "background" to (if (isDark) 0xFF0D1117L else 0xFFFFFFFFL),
                            "surface" to (if (isDark) 0xFF161B22L else 0xFFF6F8FAL),
                            "onBackground" to (if (isDark) 0xFFC9D1D9L else 0xFF1F2328L),
                            "onSurface" to (if (isDark) 0xFFC9D1D9L else 0xFF1F2328L),
                            "surfaceVariant" to (if (isDark) 0xFF21262DL else 0xFFE8ECEFL),
                            "onSurfaceVariant" to (if (isDark) 0xFF8B949EL else 0xFF636C76L),
                        )
                        val colorKeys = listOf(
                            "primary" to R.string.theme_custom_primary,
                            "background" to R.string.theme_custom_background,
                            "surface" to R.string.theme_custom_surface,
                            "onBackground" to R.string.theme_custom_on_background,
                            "onSurface" to R.string.theme_custom_on_surface,
                            "surfaceVariant" to R.string.theme_custom_surface_variant,
                            "onSurfaceVariant" to R.string.theme_custom_on_surface_variant,
                        )
                        var customPicking by remember { mutableStateOf<String?>(null) }
                        colorKeys.forEach { (key, resId) ->
                            val displayColor = customColors[key] ?: colorDefaults[key] ?: -1L
                            ColorPreviewRow(
                                title = stringResource(resId),
                                color = displayColor,
                                onClick = { customPicking = key },
                            )
                        }
                        if (customPicking != null) {
                            val key = customPicking!!
                            val defaultColor = colorDefaults[key] ?: 0xFFFFFFFFL
                            ColorPickerDialog(
                                title = stringResource(
                                    when (key) {
                                        "primary" -> R.string.theme_custom_primary
                                        "background" -> R.string.theme_custom_background
                                        "surface" -> R.string.theme_custom_surface
                                        "onBackground" -> R.string.theme_custom_on_background
                                        "onSurface" -> R.string.theme_custom_on_surface
                                        "surfaceVariant" -> R.string.theme_custom_surface_variant
                                        "onSurfaceVariant" -> R.string.theme_custom_on_surface_variant
                                        else -> R.string.theme_custom_primary
                                    }
                                ),
                                initial = customColors[key] ?: defaultColor,
                                onPick = { c ->
                                    updateCustomColor(key, c)
                                    customPicking = null
                                },
                                onDismiss = { customPicking = null },
                            )
                        }
                    }

                    SectionLabel(R.string.settings_bubbles, small = true)
                    var picking by remember { mutableStateOf<String?>(null) }
                    ColorPreviewRow(
                        title = stringResource(R.string.settings_my_bubbles),
                        color = userBubbleColor,
                        onClick = { picking = "user" },
                    )
                    ColorPreviewRow(
                        title = stringResource(R.string.settings_assistant_bubbles),
                        color = assistantBubbleColor,
                        onClick = { picking = "assistant" },
                    )

                    val target = picking
                    if (target != null) {
                        ColorPickerDialog(
                            title = stringResource(if (target == "user") R.string.color_your_title else R.string.color_assistant_title),
                            initial = if (target == "user") userBubbleColor else assistantBubbleColor,
                            onPick = { c ->
                                if (target == "user") viewModel.setUserBubbleColor(c) else viewModel.setAssistantBubbleColor(c)
                                picking = null
                            },
                            onDismiss = { picking = null },
                        )
                    }
                }
            }

            // ── Updates ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(R.string.settings_updates)
                    DropdownSetting(
                        listOf(
                            "release" to stringResource(R.string.channel_release),
                            "canary" to stringResource(R.string.channel_canary),
                        ),
                        selected = channel,
                        onSelect = viewModel::setChannel,
                        title = stringResource(R.string.settings_update_channel),
                    )
                    DropdownSetting(
                        listOf(
                            "github" to stringResource(R.string.source_github),
                            "ghproxy" to stringResource(R.string.source_ghproxy),
                        ),
                        selected = if (mirror) "ghproxy" else "github",
                        onSelect = { viewModel.setMirror(it != "github") },
                        title = stringResource(R.string.settings_update_source),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.check_updates), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.check_updates_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { viewModel.checkForUpdates() },
                            enabled = !checkingUpdate,
                        ) {
                            Text(if (checkingUpdate) stringResource(R.string.checking_updates) else stringResource(R.string.check_now))
                        }
                    }
                    val msg = updateMessage
                    if (!msg.isNullOrBlank()) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateInfo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFontFamily,
                    )
                }
            }

            // ── Token History ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel(R.string.settings_token_history)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenCalendar)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_token_history), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.settings_token_history_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.color_edit),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(resId: Int, small: Boolean = false) {
    Text(
        stringResource(resId),
        style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = if (small) 4.dp else 8.dp),
    )
}

@Composable
private fun RadioSetting(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    options.forEach { (value, label) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(value) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == value, onClick = { onSelect(value) })
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    title: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selected }?.second
        ?: options.first().second
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title.isNotEmpty()) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        }
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
                Text(stringResource(R.string.color_default), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.color_edit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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
                Text(stringResource(R.string.color_presets), style = MaterialTheme.typography.labelLarge)
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
                            Text(stringResource(R.string.color_default), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
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

                Text(stringResource(R.string.color_customize), style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(currentColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#%06X".format((currentColor.toArgb() and 0xFFFFFF)), color = contrastColor(currentColor), fontFamily = MonoFontFamily)
                }
                SliderRow(stringResource(R.string.color_hue), hue, { hue = it })
                SliderRow(stringResource(R.string.color_saturation), sat, { sat = it })
                SliderRow(stringResource(R.string.color_brightness), value, { value = it })
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.color_cancel)) }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val argb = hsvToColor().toArgb() and 0xFFFFFF
                    onPick(0xFF000000L or argb.toLong())
                },
            ) { Text(stringResource(R.string.color_apply)) }
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