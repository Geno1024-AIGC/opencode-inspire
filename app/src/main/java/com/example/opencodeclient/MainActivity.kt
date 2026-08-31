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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencodeclient.ui.MainViewModel
import com.example.opencodeclient.ui.OpenCodeApp
import com.example.opencodeclient.ui.theme.OpenCodeTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
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
                OpenCodeTheme(darkTheme = darkTheme) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        OpenCodeApp(viewModel)

                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        updateInfo?.let { info ->
                            AlertDialog(
                                onDismissRequest = viewModel::dismissUpdate,
                                title = { Text(stringResource(R.string.update_available_title)) },
                                text = {
                                    Text(stringResource(R.string.update_available_body, info.version, BuildConfig.VERSION_NAME))
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.url))
                                        runCatching { ctx.startActivity(intent) }
                                        viewModel.dismissUpdate()
                                    }) { Text(stringResource(R.string.update_open)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = viewModel::dismissUpdate) { Text(stringResource(R.string.update_later)) }
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
