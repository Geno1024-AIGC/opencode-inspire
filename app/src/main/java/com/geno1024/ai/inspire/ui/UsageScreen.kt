package com.geno1024.ai.inspire.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.geno1024.ai.inspire.data.UsageShareData
import com.geno1024.ai.inspire.data.UsageShareItem
import com.geno1024.ai.inspire.data.buildUsageShareBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geno1024.ai.inspire.R
import com.geno1024.ai.inspire.data.TokenDay
import com.geno1024.ai.inspire.data.TokenFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private data class UsageRow(val model: String, val day: TokenDay)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val tokenHistory by viewModel.tokenHistory.collectAsStateWithLifecycle()
    val tokenElapsed by viewModel.tokenElapsed.collectAsStateWithLifecycle()
    val tokenModelStats by viewModel.tokenModelStats.collectAsStateWithLifecycle()
    val tokenFormat by viewModel.tokenFormat.collectAsStateWithLifecycle()

    var periodMode by rememberSaveable { mutableStateOf("7") }
    var customStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var expandedModel by rememberSaveable { mutableStateOf<String?>(null) }
    val dayStartOffset by viewModel.dayStartOffset.collectAsStateWithLifecycle()
    val today = remember(dayStartOffset) { LocalDate.now(effectiveStatsZone(dayStartOffset)) }
    val context = LocalContext.current

    val usagePeriodAllRes = stringResource(R.string.usage_period_all)
    val usagePeriod7dRes = stringResource(R.string.usage_period_7d)
    val usagePeriod30dRes = stringResource(R.string.usage_period_30d)
    val usagePeriod90dRes = stringResource(R.string.usage_period_90d)
    val usageAppNameRes = stringResource(R.string.app_name)
    val usageTitleRes = stringResource(R.string.usage_leaderboard_title)
    val shareLabelRes = stringResource(R.string.chat_select_share)
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()

    val periodKeys = remember(tokenHistory, periodMode, customStart, customEnd, today) {
        when {
            periodMode == "all" -> tokenHistory.keys.toSet()
            periodMode == "custom" -> {
                val startLd = customStart?.toLocalDateUtc() ?: LocalDate.MIN
                val endLd = customEnd?.toLocalDateUtc() ?: today
                tokenHistory.keys.filter { k ->
                    runCatching {
                        val d = LocalDate.parse(k)
                        !d.isBefore(startLd) && !d.isAfter(endLd)
                    }.getOrDefault(false)
                }.toSet()
            }
            else -> {
                val span = periodMode.toIntOrNull() ?: 7
                val start = today.minusDays((span - 1).toLong())
                tokenHistory.keys.filter { k ->
                    runCatching {
                        val d = LocalDate.parse(k)
                        !d.isBefore(start) && !d.isAfter(today)
                    }.getOrDefault(false)
                }.toSet()
            }
        }
    }
    val periodTotal = remember(periodKeys, tokenHistory) {
        periodKeys.mapNotNull { tokenHistory[it] }.fold(TokenDay()) { a, b -> a + b }
    }
    val periodElapsed = remember(periodKeys, tokenElapsed) {
        periodKeys.sumOf { tokenElapsed[it] ?: 0L }
    }
    val rows = remember(periodKeys, tokenModelStats) {
        tokenModelStats.mapNotNull { (id, st) ->
            val day = periodKeys.mapNotNull { st.history[it] }.fold(TokenDay()) { a, b -> a + b }
            if (day.total <= 0L && day.cost <= 0.0) null else UsageRow(id, day)
        }.sortedByDescending { it.day.total }
    }
    val trendByModel = remember(periodKeys, tokenModelStats) {
        tokenModelStats.mapValues { (_, st) -> st.history.filterKeys { it in periodKeys } }
    }
    val periodDaySpan: Int = remember(periodKeys, periodMode, customStart, customEnd, today) {
        if (periodMode == "custom") {
            val startLd = customStart?.toLocalDateUtc()
            val endLd = customEnd?.toLocalDateUtc()
            when {
                startLd != null && endLd != null -> maxOf(1, ChronoUnit.DAYS.between(startLd, endLd).toInt() + 1)
                else -> {
                    val min = periodKeys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull()
                    min?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 } ?: 1
                }
            }
        } else if (periodMode == "all") {
            val min = periodKeys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull()
            min?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 } ?: 1
        } else periodMode.toIntOrNull() ?: 7
    }

    fun shareImage() {
        val periodLabel = when (periodMode) {
            "all" -> usagePeriodAllRes
            "custom" -> listOfNotNull(
                customStart?.toLocalDateUtc()?.toString(),
                customEnd?.toLocalDateUtc()?.toString(),
            ).joinToString(" ~ ")
            else -> when (periodMode) {
                "7" -> usagePeriod7dRes
                "30" -> usagePeriod30dRes
                else -> usagePeriod90dRes
            }
        }
        val data = UsageShareData(
            appName = usageAppNameRes,
            title = usageTitleRes,
            periodLabel = periodLabel,
            footer = LocalDate.now().toString(),
            totalTokens = periodTotal.total,
            input = periodTotal.input,
            output = periodTotal.output,
            reasoning = periodTotal.reasoning,
            cacheRead = periodTotal.cacheRead,
            cacheWrite = periodTotal.cacheWrite,
            messages = periodTotal.msgs,
            cost = periodTotal.cost,
            elapsed = formatElapsed(periodElapsed),
            items = rows.map { row ->
                val d = row.day
                UsageShareItem(
                    model = row.model,
                    total = d.total,
                    input = d.input,
                    output = d.output,
                    reasoning = d.reasoning,
                    cacheRead = d.cacheRead,
                    cacheWrite = d.cacheWrite,
                    cost = d.cost,
                    share = if (periodTotal.total > 0L) d.total.toFloat() / periodTotal.total.toFloat() else 0f,
                )
            },
            accent = accentColor,
        )
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { buildUsageShareBitmap(data) }
            val uri = withContext(Dispatchers.IO) {
                val file = java.io.File(context.cacheDir, "inspire-usage.png")
                file.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, shareLabelRes))
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.usage_leaderboard_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.drawer_close))
                    }
                },
                actions = {
                    IconButton(onClick = ::shareImage) {
                        Icon(Icons.Filled.Share, stringResource(R.string.chat_select_share))
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("7", "30", "90", "all", "custom").forEach { m ->
                    FilterChip(
                        selected = periodMode == m,
                        onClick = { periodMode = m },
                        label = {
                            Text(
                                when (m) {
                                    "all" -> stringResource(R.string.usage_period_all)
                                    "custom" -> stringResource(R.string.usage_period_custom)
                                    else -> stringResource(
                                        when (m) {
                                            "7" -> R.string.usage_period_7d
                                            "30" -> R.string.usage_period_30d
                                            else -> R.string.usage_period_90d
                                        }
                                    )
                                },
                                fontFamily = MonoFontFamily,
                            )
                        },
                    )
                }
            }

            if (periodMode == "custom") {
                CustomRangePickers(
                    start = customStart,
                    end = customEnd,
                    today = today,
                    onStartChange = { customStart = it },
                    onEndChange = { customEnd = it },
                )
            }

            if (periodTotal.total <= 0L && periodTotal.cost <= 0.0) {
                Text(
                    stringResource(R.string.usage_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                UsageSummaryCard(
                    total = periodTotal,
                    elapsed = periodElapsed,
                    format = tokenFormat,
                )
            }

            Text(
                stringResource(R.string.usage_model_section),
                style = MaterialTheme.typography.titleMedium,
            )

            rows.forEachIndexed { index, row ->
                ModelUsageRow(
                    rank = index + 1,
                    row = row,
                    total = periodTotal,
                    trendHistory = trendByModel[row.model] ?: emptyMap(),
                    trendDays = periodDaySpan,
                    expanded = expandedModel == row.model,
                    format = tokenFormat,
                    today = today,
                    onClick = { expandedModel = if (expandedModel == row.model) null else row.model },
                )
            }
        }
    }
}

@Composable
private fun UsageSummaryCard(total: TokenDay, elapsed: Long, format: TokenFormat) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.usage_period_tokens),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatTokens(total.total, format),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                )
            }
            SummaryDataRow(stringResource(R.string.usage_period_cost), formatCost(total.cost))
            SummaryDataRow(stringResource(R.string.usage_period_msgs), total.msgs.toString())
            SummaryDataRow(
                stringResource(R.string.usage_period_elapsed),
                formatElapsed(elapsed),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MetricRow(stringResource(R.string.calendar_token_in), formatTokens(total.input, format))
                MetricRow(stringResource(R.string.calendar_token_out), formatTokens(total.output, format))
                MetricRow(stringResource(R.string.calendar_token_reasoning), formatTokens(total.reasoning, format))
                MetricRow(stringResource(R.string.calendar_token_crd), formatTokens(total.cacheRead, format))
                MetricRow(stringResource(R.string.calendar_token_cwr), formatTokens(total.cacheWrite, format))
            }
        }
    }
}

@Composable
private fun SummaryDataRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
        )
    }
}

@Composable
private fun ModelUsageRow(
    rank: Int,
    row: UsageRow,
    total: TokenDay,
    trendHistory: Map<String, TokenDay>,
    trendDays: Int,
    expanded: Boolean,
    format: TokenFormat,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val day = row.day
    val share = if (total.total > 0L) day.total.toFloat() / total.total.toFloat() else 0f
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#$rank",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    row.model,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatTokens(day.total, format),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                )
            }
            LinearProgressIndicator(
                progress = { share },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MetricRow(stringResource(R.string.calendar_token_in), formatTokens(day.input, format))
                MetricRow(stringResource(R.string.calendar_token_out), formatTokens(day.output, format))
                MetricRow(stringResource(R.string.calendar_token_reasoning), formatTokens(day.reasoning, format))
                MetricRow(stringResource(R.string.calendar_token_crd), formatTokens(day.cacheRead, format))
                MetricRow(stringResource(R.string.calendar_token_cwr), formatTokens(day.cacheWrite, format))
            }
            Text(
                "${formatCost(day.cost)} · ${"%.1f".format(share * 100f)}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                DailyTrendChart(
                    history = trendHistory,
                    days = trendDays,
                    format = format,
                    today = today,
                )
                MetricRow(stringResource(R.string.stats_tab_msgs), day.msgs.toString())
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
        )
    }
}

private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toEpochMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangePickers(
    start: Long?,
    end: Long?,
    today: LocalDate,
    onStartChange: (Long?) -> Unit,
    onEndChange: (Long?) -> Unit,
) {
    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RangePickerRow(
                label = stringResource(R.string.usage_custom_start),
                value = start?.toLocalDateUtc(),
                onClick = { picker = "start" },
            )
            RangePickerRow(
                label = stringResource(R.string.usage_custom_end),
                value = end?.toLocalDateUtc(),
                onClick = { picker = "end" },
            )
        }
    }

    picker?.let { which ->
        val initial = (if (which == "start") start else end)?.toLocalDateUtc()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial?.toEpochMillisUtc() ?: today.toEpochMillisUtc(),
        )
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis
                    if (which == "start") onStartChange(picked) else onEndChange(picked)
                    picker = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerRow(label: String, value: LocalDate?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                value?.toString() ?: stringResource(R.string.usage_custom_unset),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
            )
        }
    }
}