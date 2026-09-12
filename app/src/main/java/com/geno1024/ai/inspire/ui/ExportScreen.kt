package com.geno1024.ai.inspire.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geno1024.ai.inspire.R
import com.geno1024.ai.inspire.data.CalendarMetric
import com.geno1024.ai.inspire.data.PunchMode
import com.geno1024.ai.inspire.data.PunchOrientation
import com.geno1024.ai.inspire.data.ShareCardData
import com.geno1024.ai.inspire.data.TokenDay
import com.geno1024.ai.inspire.data.buildCalendarBitmap
import com.geno1024.ai.inspire.data.buildPunchcardBitmap
import com.geno1024.ai.inspire.data.buildShareCardBitmap
import com.geno1024.ai.inspire.data.saveBitmapToDownloads
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ExportType { IMAGE, CSV, JSON }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: MainViewModel,
    startMonth: YearMonth,
    onBack: () -> Unit,
    initialProject: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val history by viewModel.tokenHistory.collectAsStateWithLifecycle()
    val hourByDay by viewModel.hourByDay.collectAsStateWithLifecycle()
    val tokenProjectStats by viewModel.tokenProjectStats.collectAsStateWithLifecycle()
    val tokenProjectDirs by viewModel.tokenProjectDirs.collectAsStateWithLifecycle()
    var projectFilter by rememberSaveable { mutableStateOf(initialProject.ifBlank { "all" }) }
    val projectIds = remember(tokenProjectStats) { tokenProjectStats.keys.sorted() }
    val activeProject = if (projectFilter in projectIds) projectFilter else "all"
    val projectNames = remember(tokenProjectDirs, projectIds) {
        projectIds.associateWith { tokenProjectDirs[it]?.substringAfterLast('/')?.ifBlank { it } ?: it.substringAfterLast('/').ifBlank { it } }
    }
    val effStats = if (activeProject == "all") null else tokenProjectStats[activeProject]
    val effHistory = effStats?.history ?: history
    val effHourByDay = effStats?.hourByDay ?: hourByDay
    val commonTransparent by viewModel.exportTransparent.collectAsStateWithLifecycle()
    val author by viewModel.exportAuthor.collectAsStateWithLifecycle()

    var authorDraft by rememberSaveable { mutableStateOf("") }
    var authorReady by remember { mutableStateOf(false) }
    LaunchedEffect(author) {
        if (!authorReady) {
            authorDraft = author
            authorReady = true
        }
    }
    fun persistAuthor() {
        if (authorDraft != author) viewModel.setExportAuthor(authorDraft.trim())
    }

    BackHandler(onBack = {
        persistAuthor()
        onBack()
    })

    val locale = Locale.getDefault()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val ink = 0xFF161A1E.toInt()
    val muted = 0xFF6B7280.toInt()

    // ── common image options ──
    var exportType by rememberSaveable { mutableStateOf(ExportType.IMAGE) }

    // ── share card options ──
    val cardData = remember(effHistory, commonTransparent, authorDraft, startMonth) {
        val total = effHistory.values.fold(TokenDay()) { a, b -> a + b }
        ShareCardData(
            appName = context.getString(R.string.app_name),
            monthLabel = startMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            sublabel = context.getString(R.string.share_card_subtitle),
            totalTokens = total.fresh,
            input = total.input,
            output = total.output,
            reasoning = total.reasoning,
            cacheRead = total.cacheRead,
            messages = total.msgs,
            cost = total.cost,
            accent = accent,
            ink = ink,
            muted = muted,
            footer = LocalDate.now().toString(),
            background = if (commonTransparent) null else 0xFFFAFBFC.toInt(),
            author = authorDraft.trim().ifBlank { null },
        )
    }
    var cardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(cardData) {
        cardBitmap = withContext(Dispatchers.Default) { buildShareCardBitmap(cardData) }
    }

    // ── calendar options ──
    var calRange by rememberSaveable { mutableIntStateOf(0) }
    var calContinuous by rememberSaveable { mutableStateOf(false) }
    var calMonthFormat by rememberSaveable { mutableStateOf(MonthFormats.default) }
    var calMetrics by rememberSaveable { mutableStateOf(listOf(CalendarMetric.FRESH)) }
    fun toggleMetric(m: CalendarMetric) {
        calMetrics = if (m in calMetrics) {
            if (calMetrics.size > 1) calMetrics - m else calMetrics
        } else {
            calMetrics + m
        }
    }
    var customStart by rememberSaveable { mutableStateOf(startMonth.minusMonths(2)) }
    var customEnd by rememberSaveable { mutableStateOf(startMonth) }
    val monthCounts = listOf(1, 3, 6, Int.MAX_VALUE)
    val allMonths = remember(effHistory, startMonth) {
        val end = startMonth
        val keys = effHistory.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        val min = keys.minOfOrNull { YearMonth.from(it) } ?: end.minusMonths(11)
        generateSequence(end) { it.minusMonths(1) }
            .takeWhile { it >= min }.toList().sorted()
    }
    val startChoices = remember(allMonths, customEnd) { allMonths.filter { it <= customEnd } }
    val endChoices = remember(allMonths, customStart) { allMonths.filter { it >= customStart } }
    val calMonths = remember(allMonths, startMonth, calRange, customStart, customEnd) {
        val sorted = when (calRange) {
            0 -> listOf(startMonth)
            1 -> (2 downTo 0).map { startMonth.minusMonths(it.toLong()) }
            2 -> (5 downTo 0).map { startMonth.minusMonths(it.toLong()) }
            3 -> allMonths
            else -> generateSequence(customEnd) { it.minusMonths(1) }.takeWhile { it >= customStart }.toList()
        }
        sorted.sorted()
    }
    var calBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(effHistory, calMonths, commonTransparent, authorDraft, calMonthFormat, calContinuous, calMetrics) {
        calBitmap = withContext(Dispatchers.Default) {
            buildCalendarBitmap(
                history = effHistory,
                months = calMonths,
                locale = locale,
                accent = accent,
                ink = ink,
                muted = muted,
                transparent = commonTransparent,
                appName = context.getString(R.string.app_name),
                author = authorDraft.trim().ifBlank { null },
                monthPattern = calMonthFormat,
                continuous = calContinuous,
                metrics = calMetrics,
            )
        }
    }

    // ── punchcard options ──
    var punchMode by remember { mutableStateOf(PunchMode.HOURLY) }
    var punchOrientation by remember { mutableStateOf(PunchOrientation.HORIZONTAL) }
    var punchBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(effHistory, effHourByDay, punchMode, punchOrientation, commonTransparent, authorDraft) {
        punchBitmap = withContext(Dispatchers.Default) {
            buildPunchcardBitmap(
                history = effHistory,
                hourByDay = effHourByDay,
                mode = punchMode,
                orientation = punchOrientation,
                accent = accent,
                ink = ink,
                muted = muted,
                transparent = commonTransparent,
                locale = locale,
                author = authorDraft.trim().ifBlank { null },
            )
        }
    }

    fun save(name: String, bmp: Bitmap?) {
        persistAuthor()
        scope.launch {
            val ok = bmp != null && withContext(Dispatchers.IO) {
                saveBitmapToDownloads(context, name, bmp!!)
            }
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.export_saved, name) else context.getString(R.string.export_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        persistAuthor()
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.drawer_close)) }
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
            // type selector
            Section(stringResource(R.string.export_section_type)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = exportType == ExportType.IMAGE,
                        onClick = { exportType = ExportType.IMAGE },
                        label = { Text(stringResource(R.string.export_type_image)) },
                    )
                    FilterChip(
                        selected = exportType == ExportType.CSV,
                        onClick = { exportType = ExportType.CSV },
                        label = { Text("CSV") },
                    )
                    FilterChip(
                        selected = exportType == ExportType.JSON,
                        onClick = { exportType = ExportType.JSON },
                        label = { Text("JSON") },
                    )
                }
            }

            AnimatedVisibility(visible = exportType == ExportType.IMAGE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Section(stringResource(R.string.export_section_common)) {
                        OptionSwitch(stringResource(R.string.export_opt_transparent), commonTransparent) { viewModel.setExportTransparent(it) }
                        OutlinedTextField(
                            value = authorDraft,
                            onValueChange = { authorDraft = it },
                            label = { Text(stringResource(R.string.export_opt_author)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Share card
                    Section(stringResource(R.string.export_section_card)) {
                        Preview(cardBitmap, commonTransparent, cardData.background)
                        Button(onClick = { save("inspire-usage-card.png", cardBitmap) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.export_save))
                        }
                    }

                    // Calendar
                    Section(stringResource(R.string.export_section_calendar)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.export_range_label), style = MaterialTheme.typography.bodyMedium)
                            DropdownSelect(
                                label = when (calRange) {
                                    0 -> stringResource(R.string.export_range_1)
                                    1 -> stringResource(R.string.export_range_3)
                                    2 -> stringResource(R.string.export_range_6)
                                    3 -> stringResource(R.string.export_range_all)
                                    else -> stringResource(R.string.export_range_custom)
                                },
                                options = listOf(
                                    stringResource(R.string.export_range_1),
                                    stringResource(R.string.export_range_3),
                                    stringResource(R.string.export_range_6),
                                    stringResource(R.string.export_range_all),
                                    stringResource(R.string.export_range_custom),
                                ),
                            ) { i -> calRange = i }
                        }
                        if (calRange == 4) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.export_range_from), style = MaterialTheme.typography.bodyMedium)
                                MonthSelect(
                                    label = customStart.format(DateTimeFormatter.ofPattern("yyyy-MM", locale)),
                                    months = startChoices,
                                    selected = customStart,
                                ) { m -> customStart = if (m <= customEnd) m else customEnd }
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.export_range_to), style = MaterialTheme.typography.bodyMedium)
                                MonthSelect(
                                    label = customEnd.format(DateTimeFormatter.ofPattern("yyyy-MM", locale)),
                                    months = endChoices,
                                    selected = customEnd,
                                ) { m -> customEnd = if (m >= customStart) m else customStart }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.export_layout_label), style = MaterialTheme.typography.bodyMedium)
                            FilterChip(selected = !calContinuous, onClick = { calContinuous = false }, label = { Text(stringResource(R.string.export_layout_paged)) })
                            FilterChip(selected = calContinuous, onClick = { calContinuous = true }, label = { Text(stringResource(R.string.export_layout_continuous)) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.export_month_fmt_label), style = MaterialTheme.typography.bodyMedium)
                            DropdownSelect(
                                label = monthFormatTitle(calMonthFormat, startMonth, locale),
                                options = CAL_MONTH_FORMATS + MonthFormats.default,
                            ) { i -> calMonthFormat = (CAL_MONTH_FORMATS + MonthFormats.default)[i] }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.export_metric_label), style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CalendarMetric.entries.forEach { m ->
                                    FilterChip(
                                        selected = m in calMetrics,
                                        onClick = { toggleMetric(m) },
                                        label = { Text(stringResource(metricLabelRes(m))) },
                                    )
                                }
                            }
                        }
                        Preview(calBitmap, commonTransparent, null)
                        Button(onClick = { save("inspire-calendar-${if (calRange == 4) "custom" else monthCounts[calRange].let { if (it == Int.MAX_VALUE) "all" else it } }m.png", calBitmap) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.export_save))
                        }
                    }

                    // Punchcard
                    Section(stringResource(R.string.export_section_punch)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = punchMode == PunchMode.HOURLY,
                                onClick = { punchMode = PunchMode.HOURLY },
                                label = { Text(stringResource(R.string.export_punch_hourly)) },
                            )
                            FilterChip(
                                selected = punchMode == PunchMode.DAILY,
                                onClick = { punchMode = PunchMode.DAILY },
                                label = { Text(stringResource(R.string.export_punch_daily)) },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = punchOrientation == PunchOrientation.HORIZONTAL,
                                onClick = { punchOrientation = PunchOrientation.HORIZONTAL },
                                label = { Text(stringResource(R.string.export_punch_h)) },
                            )
                            FilterChip(
                                selected = punchOrientation == PunchOrientation.VERTICAL,
                                onClick = { punchOrientation = PunchOrientation.VERTICAL },
                                label = { Text(stringResource(R.string.export_punch_v)) },
                            )
                        }
                        Preview(
                            punchBitmap,
                            commonTransparent,
                            null,
                            horizontalLong = punchMode == PunchMode.HOURLY && punchOrientation == PunchOrientation.HORIZONTAL ||
                                punchMode == PunchMode.DAILY && punchOrientation == PunchOrientation.VERTICAL,
                        )
                        Button(onClick = {
                            val modeName = if (punchMode == PunchMode.HOURLY) "hourly" else "daily"
                            val dirName = if (punchOrientation == PunchOrientation.HORIZONTAL) "h" else "v"
                            save("inspire-punchcard-$modeName-$dirName.png", punchBitmap)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.export_save))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = exportType == ExportType.CSV) {
                Section(stringResource(R.string.export_section_data)) {
                    Text(stringResource(R.string.export_data_desc), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            scope.launch {
                                val name = viewModel.exportUsageCsv(activeProject.takeIf { it != "all" })
                                val msg = if (name != null) {
                                    context.getString(R.string.export_saved, name)
                                } else {
                                    context.getString(R.string.export_failed)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("CSV") }
                }
            }

            AnimatedVisibility(visible = exportType == ExportType.JSON) {
                Section(stringResource(R.string.export_section_data)) {
                    Text(stringResource(R.string.export_data_desc), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            scope.launch {
                                val name = viewModel.exportUsageJson(activeProject.takeIf { it != "all" })
                                val msg = if (name != null) {
                                    context.getString(R.string.export_saved, name)
                                } else {
                                    context.getString(R.string.export_failed)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("JSON") }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Preview(
    bitmap: Bitmap?,
    transparent: Boolean,
    fallbackBg: Int?,
    horizontalLong: Boolean = false,
) {
    val bmp = bitmap ?: return
    val bg = if (transparent) fallbackBg else 0xFFF2F3F5.toInt()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (bg != null) Modifier.background(androidx.compose.ui.graphics.Color(bg)) else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (horizontalLong) 160.dp else 340.dp),
        )
    }
}

private fun metricLabelRes(m: CalendarMetric): Int = when (m) {
    CalendarMetric.FRESH -> R.string.export_metric_fresh
    CalendarMetric.TOTAL -> R.string.export_metric_total
    CalendarMetric.MSGS_USER -> R.string.export_metric_msgs_user
    CalendarMetric.MSGS_TOTAL -> R.string.export_metric_msgs_total
}

private object MonthFormats {
    const val default: String = "MMMM yyyy"
}

private val CAL_MONTH_FORMATS = listOf(
    "yyyy 年 M 月",
    "yyyy 年 MMMM",
    "yyyy-MM",
    "yyyy MMM",
    "MMM yyyy",
)

private fun monthFormatTitle(fmt: String, m: YearMonth, locale: Locale): String {
    val example = runCatching { m.format(DateTimeFormatter.ofPattern(fmt, locale)) }.getOrDefault(fmt)
    return "$fmt  →  $example"
}

@Composable
private fun DropdownSelect(label: String, options: List<String>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) })
            }
        }
    }
}

@Composable
private fun MonthSelect(label: String, months: List<YearMonth>, selected: YearMonth, onSelect: (YearMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            months.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.getDefault())), fontWeight = if (m == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onSelect(m) },
                )
            }
        }
    }
}