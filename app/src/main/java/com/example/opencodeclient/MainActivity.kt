package com.example.opencodeclient

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.example.opencodeclient.ui.MonoFontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencodeclient.ui.MainViewModel
import com.example.opencodeclient.ui.OpenCodeApp
import com.example.opencodeclient.ui.formatBytes
import com.example.opencodeclient.ui.formatEta
import com.example.opencodeclient.ui.formatSpeed
import com.example.opencodeclient.ui.theme.OpenCodeTheme
import com.example.opencodeclient.ui.theme.ThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_download", false)) {
            viewModel.showDownloadDialog()
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        if (intent.getBooleanExtra("open_download", false)) {
            viewModel.showDownloadDialog()
        }
        setContent {
            var permissionPromptDismissed by rememberSaveable { mutableStateOf(false) }
            val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

            if (needsNotificationPermission && !permissionPromptDismissed) {
                AlertDialog(
                    onDismissRequest = { permissionPromptDismissed = true },
                    title = { Text(stringResource(R.string.permission_notif_title)) },
                    text = { Text(stringResource(R.string.permission_notif_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            permissionPromptDismissed = true
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }) {
                            Text(stringResource(R.string.permission_notif_allow))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { permissionPromptDismissed = true }) {
                            Text(stringResource(R.string.permission_notif_later))
                        }
                    },
                )
            }

            val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
            val updateMessage by viewModel.updateMessage.collectAsStateWithLifecycle()
            val themePref by viewModel.theme.collectAsStateWithLifecycle()
            val themePresetPref by viewModel.themePreset.collectAsStateWithLifecycle()
            val customThemeColorsPref by viewModel.customThemeColors.collectAsStateWithLifecycle()
            val langPref by viewModel.language.collectAsStateWithLifecycle()
            val darkTheme = when (themePref) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            val baseConfig = LocalConfiguration.current
            val appConfig = Configuration(baseConfig)
            val locale = when (langPref) {
                "en" -> Locale.ENGLISH
                "zh" -> Locale.SIMPLIFIED_CHINESE
                else -> Locale.getDefault()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appConfig.setLocales(android.os.LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                appConfig.locale = locale
            }

            CompositionLocalProvider(LocalConfiguration provides appConfig) {
                val preset = try {
                    ThemePreset.entries.first { it.id == themePresetPref }
                } catch (_: Exception) { ThemePreset.DEFAULT }
                val customColors = try {
                    runCatching {
                        kotlinx.serialization.json.Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonPrimitive>>(customThemeColorsPref)
                            .mapValues { it.value.content.toLongOrNull() ?: 0L }
                    }.getOrNull() ?: emptyMap()
                } catch (_: Exception) { emptyMap() }
                OpenCodeTheme(darkTheme = darkTheme, preset = preset, customColors = customColors) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        OpenCodeApp(viewModel)

                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val scope = rememberCoroutineScope()
                        var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                        val downloadPercent by viewModel.downloadPercent.collectAsStateWithLifecycle()
                        val downloadDone by viewModel.downloadDone.collectAsStateWithLifecycle()
                        val downloadTotal by viewModel.downloadTotal.collectAsStateWithLifecycle()
                        val downloadSpeed by viewModel.downloadSpeed.collectAsStateWithLifecycle()
                        val downloadDialogVisible by viewModel.downloadDialogVisible.collectAsStateWithLifecycle()
                        updateInfo?.let { info ->
                            AlertDialog(
                                onDismissRequest = viewModel::dismissUpdate,
                                title = { Text(stringResource(R.string.update_available_title)) },
                                text = {
                                    Text(stringResource(R.string.update_available_body, info.version, BuildConfig.VERSION_NAME))
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.showDownloadDialog()
                                        downloadJob = scope.launch {
                                            val startTime = System.currentTimeMillis()
                                            val apk = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    val file = File(ctx.cacheDir, "update_${System.currentTimeMillis()}.apk")
                                                    val conn = (java.net.URL(info.url).openConnection() as java.net.HttpURLConnection).apply {
                                                        instanceFollowRedirects = true
                                                        connectTimeout = 15000
                                                        readTimeout = 15000
                                                        addRequestProperty("User-Agent", "opencode-inspire")
                                                    }
                                                    val total = conn.contentLength.toLong().coerceAtLeast(0L)
                                                    var downloaded = 0L
                                                    val buf = ByteArray(8192)
                                                    conn.inputStream.use { input ->
                                                        file.outputStream().use { output ->
                                                            var n: Int
                                                            while (input.read(buf).also { n = it } != -1) {
                                                                coroutineContext.ensureActive()
                                                                output.write(buf, 0, n)
                                                                downloaded += n
                                                                val elapsed = System.currentTimeMillis() - startTime
                                                                val speed = if (elapsed > 0L) downloaded * 1000L / elapsed else 0L
                                                                viewModel.showDownloadProgress(downloaded, total, speed)
                                                            }
                                                        }
                                                    }
                                                    conn.disconnect()
                                                    file
                                                }
                                            }.getOrNull()
                                            if (!coroutineContext.isActive) return@launch
                                            viewModel.dismissUpdate()
                                            viewModel.dismissDownload()
                                            if (apk != null && apk.exists() && apk.length() > 0L) {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                runCatching { ctx.startActivity(intent) }
                                            } else {
                                                viewModel.showUpdateMessage(ctx.getString(R.string.update_download_failed))
                                            }
                                        }
                                    }) { Text(stringResource(R.string.update_download)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = viewModel::dismissUpdate) { Text(stringResource(R.string.update_later)) }
                                },
                            )
                        }
                        if (downloadDialogVisible) {
                            AlertDialog(
                                onDismissRequest = viewModel::dismissDownloadDialog,
                                title = { Text(stringResource(R.string.download_title)) },
                                text = {
                                    val pct = downloadPercent
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (pct >= 0) {
                                            Text(
                                                stringResource(R.string.download_percent, pct),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontFamily = MonoFontFamily,
                                            )
                                        }
                                        val detail = if (downloadTotal > 0L) {
                                            stringResource(R.string.download_progress_detail,
                                                formatBytes(downloadDone),
                                                formatBytes(downloadTotal))
                                        } else {
                                            stringResource(R.string.download_progress_unknown,
                                                formatBytes(downloadDone))
                                        }
                                        Text(
                                            detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = MonoFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            formatSpeed(downloadSpeed),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = MonoFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (downloadTotal > 0L && downloadSpeed > 0L && downloadDone < downloadTotal) {
                                            val eta = (downloadTotal - downloadDone) / downloadSpeed
                                            Text(
                                                stringResource(R.string.download_eta, formatEta(eta)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = MonoFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (pct >= 0) {
                                            LinearProgressIndicator(
                                                progress = { pct / 100f },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = {
                                        downloadJob?.cancel()
                                        viewModel.dismissDownload()
                                    }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                },
                            )
                        }
                        updateMessage?.let { message ->
                            AlertDialog(
                                onDismissRequest = viewModel::dismissUpdateMessage,
                                title = { Text(stringResource(R.string.update_check_title)) },
                                text = { Text(message) },
                                confirmButton = {
                                    TextButton(onClick = viewModel::dismissUpdateMessage) { Text(stringResource(R.string.ok)) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
