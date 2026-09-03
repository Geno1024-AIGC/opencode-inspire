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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencodeclient.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenCalendarScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val history by viewModel.tokenHistory.collectAsStateWithLifecycle()
    val elapsed by viewModel.tokenElapsed.collectAsStateWithLifecycle()
    val loading by viewModel.tokenHistoryLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadTokenHistory()
    }

    val totalTokens = history.values.sum()
    val totalElapsed = elapsed.values.sum()
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    var shownMonth by remember { mutableStateOf(YearMonth.now()) }
    var detailDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.drawer_close)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                stringResource(R.string.calendar_total, totalTokens) + " · " + stringResource(R.string.calendar_total_elapsed, formatSeconds(totalElapsed), formatClock(totalElapsed)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { shownMonth = shownMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Prev", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    shownMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { shownMonth = shownMonth.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            CalendarGrid(
                history = history,
                elapsed = elapsed,
                shownMonth = shownMonth,
                locale = locale,
                onSelect = { detailDate = it },
            )
        }
    }

    detailDate?.let { date ->
        val day = date.toString()
        val tokens = history[day] ?: 0L
        val dur = elapsed[day] ?: 0L
        AlertDialog(
            onDismissRequest = { detailDate = null },
            title = {
                Text(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (EEE)", locale)), fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(stringResource(R.string.calendar_day_tokens, tokens))
                    if (dur > 0L) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.calendar_day_elapsed_full, formatSeconds(dur), formatClock(dur)))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailDate = null }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

@Composable
private fun CalendarGrid(
    history: Map<String, Long>,
    elapsed: Map<String, Long>,
    shownMonth: YearMonth,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek.value
    val first = shownMonth.atDay(1)
    val leading = (first.dayOfWeek.value - firstDayOfWeek + 7) % 7
    val gridStart = first.minusDays(leading.toLong())

    val monthTokens = history
        .filterKeys { runCatching { LocalDate.parse(it).let { d -> d.year == shownMonth.year && d.month == shownMonth.month } }.getOrDefault(false) }
        .values
    val monthMax = (monthTokens.maxOrNull() ?: 0L).coerceAtLeast(1L)

    val startDow = DayOfWeek.of(firstDayOfWeek)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { i ->
                Text(
                    startDow.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }
        for (r in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val date = gridStart.plusDays((r * 7 + c).toLong())
                    val inMonth = date.month == first.month && date.year == first.year
                    val tokens = history[date.toString()] ?: 0L
                    val dayElapsed = elapsed[date.toString()] ?: 0L
                    CalendarDayCell(
                        date = date,
                        inMonth = inMonth,
                        tokens = tokens,
                        dayElapsed = dayElapsed,
                        isToday = date == today,
                        intensity = if (tokens > 0L) tokens.toDouble() / monthMax else 0.0,
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    inMonth: Boolean,
    tokens: Long,
    dayElapsed: Long,
    isToday: Boolean,
    intensity: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isToday -> MaterialTheme.colorScheme.primary
        inMonth && tokens > 0L -> MaterialTheme.colorScheme.primary.copy(alpha = (0.12f + 0.55f * intensity).toFloat().coerceIn(0.12f, 0.67f))
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .aspectRatio(0.9f)
            .padding(2.dp)
            .background(
                if (inMonth || isToday) bg else Color.Transparent,
                CircleShape,
            )
            .clickable(enabled = inMonth, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                isToday -> MaterialTheme.colorScheme.onPrimary
                tokens > 0L -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isToday || tokens > 0L) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        if (inMonth && tokens > 0L) {
            Text(
                formatTokensCompact(tokens),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (isToday) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        if (inMonth && dayElapsed > 0L) {
            Text(
                formatClock(dayElapsed),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                color = if (isToday) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private fun formatTokensCompact(n: Long): String = when {
    n >= 1_000_000_000_000L -> trim1(n / 1_000_000_000_000f) + "T"
    n >= 1_000_000_000L -> trim1(n / 1_000_000_000f) + "G"
    n >= 1_000_000L -> trim1(n / 1_000_000f) + "M"
    n >= 1_000L -> trim1(n / 1_000f) + "k"
    else -> n.toString()
}

private fun trim1(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(Locale.ROOT, v)

private fun formatSeconds(ms: Long): String =
    "%.1f".format(Locale.ROOT, ms / 1000.0) + " s"

private fun formatClock(ms: Long): String {
    val totalTenths = ms / 100
    val h = totalTenths / 36000
    val m = (totalTenths % 36000) / 600
    val s = (totalTenths % 600) / 10
    val d = totalTenths % 10
    return "%d:%02d:%02d.%d".format(Locale.ROOT, h, m, s, d)
}
