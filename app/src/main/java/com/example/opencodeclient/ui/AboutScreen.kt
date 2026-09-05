package com.example.opencodeclient.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.opencodeclient.BuildConfig
import com.example.opencodeclient.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.about_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "BUILD ${BuildConfig.BUILD} · PACK ${BuildConfig.PACK} · ${BuildConfig.GIT_COMMIT.take(7)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonoFontFamily,
            )
            Text(
                stringResource(R.string.about_tokens_used),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            TokenDetailRow(stringResource(R.string.about_token_in), formatCount(BuildConfig.TOKENS_INPUT))
            TokenDetailRow(stringResource(R.string.about_token_out), formatCount(BuildConfig.TOKENS_OUTPUT))
            TokenDetailRow(stringResource(R.string.about_token_reasoning), formatCount(BuildConfig.TOKENS_REASONING))
            TokenDetailRow(stringResource(R.string.about_token_cache_read), formatCount(BuildConfig.TOKENS_CACHE_READ))
            TokenDetailRow(stringResource(R.string.about_token_cache_write), formatCount(BuildConfig.TOKENS_CACHE_WRITE))
            Text(
                formatCount(BuildConfig.TOKENS_TOTAL),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = MonoFontFamily,
            )
            Text(
                stringResource(R.string.about_tokens_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.about_tokens_by_model),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            rememberModels().forEach { m ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(m.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "in ${formatCount(m.input)} · out ${formatCount(m.output)} · rea ${formatCount(m.reasoning)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFontFamily,
                    )
                    Text(
                        "cr ${formatCount(m.cacheRead)} · cw ${formatCount(m.cacheWrite)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFontFamily,
                    )
                }
            }
            HorizontalDivider()
            AboutRow(
                title = stringResource(R.string.about_repo),
                url = "https://github.com/Geno1024-AIGC/opencode-inspire",
                context = context,
            )
            AboutRow(
                title = stringResource(R.string.about_issue),
                url = "https://github.com/Geno1024-AIGC/opencode-inspire/issues",
                context = context,
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.about_acknowledgments),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.about_ack_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.about_license),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.about_license_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCount(value: Long): String = String.format(Locale.US, "%,d", value)

private data class ModelTokens(
    val name: String,
    val input: Long,
    val output: Long,
    val reasoning: Long,
    val cacheRead: Long,
    val cacheWrite: Long,
)

private fun parseModels(raw: String): List<ModelTokens> =
    raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.split(":")
        if (parts.size < 6) return@mapNotNull null
        ModelTokens(
            name = parts[0],
            input = parts[1].toLongOrNull() ?: 0L,
            output = parts[2].toLongOrNull() ?: 0L,
            reasoning = parts[3].toLongOrNull() ?: 0L,
            cacheRead = parts[4].toLongOrNull() ?: 0L,
            cacheWrite = parts[5].toLongOrNull() ?: 0L,
        )
    }

@Composable
private fun rememberModels(): List<ModelTokens> =
    remember(BuildConfig.TOKENS_MODELS) { parseModels(BuildConfig.TOKENS_MODELS) }

@Composable
private fun TokenDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = MonoFontFamily,
        )
    }
}

@Composable
private fun AboutRow(
    title: String,
    url: String,
    context: Context,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}