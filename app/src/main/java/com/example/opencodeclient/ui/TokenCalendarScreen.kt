package com.example.opencodeclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    val hourByMonth by viewModel.hourByMonth.collectAsStateWithLifecycle()
    val hourByWeek by viewModel.hourByWeek.collectAsStateWithLifecycle()
    val loading by viewModel.tokenHistoryLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (history.isEmpty() && elapsed.isEmpty()) viewModel.loadTokenHistory()
    }

    val totalTokens = history.values.sum()
    val totalElapsed = elapsed.values.sum()
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    var shownMonth by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(today) }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.drawer_close)) }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadTokenHistory() }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.drawer_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            val monthToks = history.filterKeys { isInMonth(it, shownMonth) }.values.sum()
            val monthElapsed = elapsed.filterKeys { isInMonth(it, shownMonth) }.values.sum()
            SummaryTable(
                totalTokens = totalTokens,
                monthTokens = monthToks,
                totalElapsed = totalElapsed,
                monthElapsed = monthElapsed,
            )
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.stats_tab_daily)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.stats_tab_monthly)) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.stats_tab_weekly)) })
            }
            when (tab) {
                0 -> DailyCalendarTab(
                    history = history,
                    elapsed = elapsed,
                    shownMonth = shownMonth,
                    selected = selected,
                    locale = locale,
                    onPrev = { shownMonth = shownMonth.minusMonths(1) },
                    onNext = { shownMonth = shownMonth.plusMonths(1) },
                    onSelect = { selected = it },
                )
                1 -> MonthColumnCard(buckets = hourByMonth, locale = locale)
                2 -> WeekColumnCard(buckets = hourByWeek, locale = locale)
            }
        }
    }
}

@Composable
private fun DailyCalendarTab(
    history: Map<String, Long>,
    elapsed: Map<String, Long>,
    shownMonth: YearMonth,
    selected: LocalDate?,
    locale: Locale,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Prev", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                shownMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CalendarGrid(
            history = history,
            elapsed = elapsed,
            shownMonth = shownMonth,
            selected = selected,
            locale = locale,
            onSelect = onSelect,
        )
        HorizontalDivider()
        val selDate = selected
        if (selDate == null) {
            Text(
                stringResource(R.string.calendar_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            val d = selDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (EEE)", locale))
            val selTokens = history[selDate.toString()] ?: 0L
            val selElapsed = elapsed[selDate.toString()] ?: 0L
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    d,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.calendar_day_detail_tokens, selTokens),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = MonoFontFamily,
                )
                Text(
                    stringResource(R.string.calendar_day_detail_elapsed, formatSeconds(selElapsed), formatClock(selElapsed)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = MonoFontFamily,
                )
            }
        }
    }
}

@Composable
private fun MonthColumnCard(buckets: Map<String, Map<Int, Long>>, locale: Locale) {
    val sorted = buckets.toSortedMap()
    val periods = sorted.keys.toList()
    val labels = periods.map { key ->
        runCatching { YearMonth.parse(key).format(DateTimeFormatter.ofPattern("yyyy-MM", locale)) }
            .getOrDefault(key)
    }
    PeriodColumns(
        periods = sorted.map { it.key to it.value },
        labels = labels,
        circle = 24.dp,
        gap = 2.dp,
    )
}

@Composable
private fun WeekColumnCard(buckets: Map<String, Map<Int, Long>>, locale: Locale) {
    val sorted = buckets.toSortedMap()
    val periods = sorted.keys.toList()
    val labels = periods.mapIndexed { i, key ->
        val date = runCatching { LocalDate.parse(key) }.getOrNull()
        if (date == null) {
            key
        } else {
            val prev = if (i > 0) runCatching { LocalDate.parse(periods[i - 1]) }.getOrNull() else null
            if (prev == null || date.year != prev.year) {
                date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", locale))
            } else {
                date.format(DateTimeFormatter.ofPattern("MM-dd", locale))
            }
        }
    }
    PeriodColumns(
        periods = sorted.map { it.key to it.value },
        labels = labels,
        circle = 24.dp,
        gap = 2.dp,
    )
}

private val PeriodHeaderH = 14.dp

@Composable
private fun PeriodColumns(
    periods: List<Pair<String, Map<Int, Long>>>,
    labels: List<String>,
    circle: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
) {
    val mono = MonoFontFamily
    val primary = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val totals = LongArray(24)
    var max = 0L
    for ((_, m) in periods) {
        for (h in 0 until 24) {
            val v = m[h] ?: 0L
            totals[h] += v
            if (v > max) max = v
        }
    }
    for (h in 0 until 24) if (totals[h] > max) max = totals[h]
    max = max.coerceAtLeast(1L)

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(
            Modifier.width(24.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Box(Modifier.height(PeriodHeaderH)) {}
            for (hour in 0 until 24) {
                Box(Modifier.height(circle), contentAlignment = Alignment.CenterStart) {
                    Text(
                        "%02d".format(Locale.ROOT, hour),
                        fontSize = 8.sp,
                        fontFamily = mono,
                        color = labelColor,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.Top) {
                periods.forEachIndexed { index, (_, m) ->
                    Column(
                        Modifier.padding(end = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            labels.getOrElse(index) { "" },
                            fontSize = 9.sp,
                            color = labelColor,
                            maxLines = 1,
                            modifier = Modifier.height(PeriodHeaderH),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            for (hour in 0 until 24) {
                                HourCircle(value = m[hour] ?: 0L, max = max, size = circle, gap = gap, mono = mono, primary = primary)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.stats_total),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(PeriodHeaderH),
            )
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (hour in 0 until 24) {
                    HourCircle(value = totals[hour], max = max, size = circle, gap = gap, mono = mono, primary = primary)
                }
            }
        }
    }
}

@Composable
private fun HourCircle(
    value: Long,
    max: Long,
    size: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
    mono: androidx.compose.ui.text.font.FontFamily,
    primary: Color,
) {
    val text = if (value > 0L) formatTokensCompact(value) else ""
    val bg = if (value > 0L) {
        val frac = (value.toDouble() / max.toDouble()).toFloat().coerceIn(0f, 1f)
        primary.copy(alpha = (0.18f + 0.72f * frac).coerceIn(0.18f, 0.9f))
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        Modifier.size(size).padding(gap / 2).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotEmpty()) {
            Text(
                text,
                fontSize = if (text.length <= 3) 9.sp else 7.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HourCircle(
    value: Long,
    max: Long,
    size: androidx.compose.ui.unit.Dp,
    mono: androidx.compose.ui.text.font.FontFamily,
    primary: Color,
) {
    val text = if (value > 0L) formatTokensCompact(value) else ""
    val bg = if (value > 0L) {
        val frac = (value.toDouble() / max.toDouble()).toFloat().coerceIn(0f, 1f)
        primary.copy(alpha = (0.18f + 0.72f * frac).coerceIn(0.18f, 0.9f))
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        Modifier.size(size).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotEmpty()) {
            Text(
                text,
                fontSize = if (text.length <= 3) 11.sp else 8.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
@Composable
private fun SummaryTable(totalTokens: Long, monthTokens: Long, totalElapsed: Long, monthElapsed: Long) {
    val mono = MonoFontFamily
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("", modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.calendar_table_total),
                fontWeight = FontWeight.Bold,
                color = onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.calendar_table_month),
                fontWeight = FontWeight.Bold,
                color = onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                stringResource(R.string.calendar_table_token),
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            Text(totalTokens.toString(), fontFamily = mono, modifier = Modifier.weight(1f))
            Text(monthTokens.toString(), fontFamily = mono, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                stringResource(R.string.calendar_table_time),
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            Text(formatClock(totalElapsed), fontFamily = mono, modifier = Modifier.weight(1f))
            Text(formatClock(monthElapsed), fontFamily = mono, modifier = Modifier.weight(1f))
        }
    }
}

private fun isInMonth(day: String, month: YearMonth): Boolean =
    runCatching {
        val d = LocalDate.parse(day)
        d.year == month.year && d.month == month.month
    }.getOrDefault(false)

@Composable
private fun CalendarGrid(
    history: Map<String, Long>,
    elapsed: Map<String, Long>,
    shownMonth: YearMonth,
    selected: LocalDate?,
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
                        selected = date == selected,
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
    selected: Boolean,
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
            .aspectRatio(1f)
            .padding(1.dp)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
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
                tokens > 0L || selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isToday || tokens > 0L || selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            fontSize = 15.sp,
            lineHeight = 16.sp,
        )
        if (inMonth && tokens > 0L) {
            Text(
                formatTokensCompact(tokens),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 9.sp),
                color = if (isToday) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        if (inMonth && dayElapsed > 0L) {
            Text(
                formatClock(dayElapsed),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, lineHeight = 8.sp),
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
