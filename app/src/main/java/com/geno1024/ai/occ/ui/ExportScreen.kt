package com.geno1024.ai.occ.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.geno1024.ai.occ.R
import com.geno1024.ai.occ.data.PunchOrientation
import com.geno1024.ai.occ.data.ShareCardData
import com.geno1024.ai.occ.data.ShareCardModel
import com.geno1024.ai.occ.data.TokenDay
import com.geno1024.ai.occ.data.buildCalendarBitmap
import com.geno1024.ai.occ.data.buildPunchcardBitmap
import com.geno1024.ai.occ.data.buildShareCardBitmap
import com.geno1024.ai.occ.data.saveBitmapToDownloads
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: MainViewModel,
    startMonth: YearMonth,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val history by viewModel.tokenHistory.collectAsStateWithLifecycle()
    val hourByDay by viewModel.hourByDay.collectAsStateWithLifecycle()
    val tokenModelStats by viewModel.tokenModelStats.collectAsStateWithLifecycle()

    val locale = Locale.getDefault()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val ink = 0xFF161A1E.toInt()
    val muted = 0xFF6B7280.toInt()

    // ── share card options ──
    var cardTrend by remember { mutableStateOf(false) }
    var cardModels by remember { mutableStateOf(false) }
    var cardTransparent by remember { mutableStateOf(true) }
    val cardData = remember(history, tokenModelStats, cardTrend, cardModels, cardTransparent, startMonth) {
        val total = history.values.fold(TokenDay()) { a, b -> a + b }
        val today = LocalDate.now()
        val days = (13 downTo 0).map { off ->
            history[today.minusDays(off.toLong()).toString()]?.fresh ?: 0L
        }
        val palette = sharePalette(accent)
        val shares = tokenModelStats.mapNotNull { (id, st) ->
            val fresh = st.history.values.fold(TokenDay()) { a, b -> a + b }.fresh
            if (fresh <= 0L) null else id to fresh
        }.sortedByDescending { it.second }.take(8)
        val models = shares.mapIndexed { i, (id, fresh) ->
            ShareCardModel(
                name = id,
                share = if (total.fresh > 0) fresh.toDouble() / total.fresh.toDouble() else 0.0,
                color = palette[i % palette.size],
            )
        }
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
            days = days,
            models = models,
            accent = accent,
            ink = ink,
            muted = muted,
            footer = today.toString(),
            includeTrend = cardTrend,
            includeModelChart = cardModels,
            background = if (cardTransparent) null else 0xFFFAFBFC.toInt(),
        )
    }
    var cardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(cardData) {
        cardBitmap = withContext(Dispatchers.Default) { buildShareCardBitmap(cardData) }
    }

    // ── calendar options ──
    var calRange by remember { mutableIntStateOf(0) }
    var calTransparent by remember { mutableStateOf(true) }
    val monthCounts = listOf(1, 3, 6, Int.MAX_VALUE)
    val calMonths = remember(history, startMonth, calRange) {
        val count = monthCounts[calRange]
        val end = startMonth
        val all = if (count == Int.MAX_VALUE) {
            val keys = history.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            val min = keys.minOfOrNull { YearMonth.from(it) } ?: end
            generateSequence(end) { it.minusMonths(1) }.takeWhile { it >= min }.take(24).toList()
        } else {
            (count - 1 downTo 0).map { end.minusMonths(it.toLong()) }
        }
        all.sorted()
    }
    var calBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(history, calMonths, calTransparent) {
        calBitmap = withContext(Dispatchers.Default) {
            buildCalendarBitmap(
                history = history,
                months = calMonths,
                locale = locale,
                accent = accent,
                ink = ink,
                muted = muted,
                transparent = calTransparent,
                appName = context.getString(R.string.app_name),
            )
        }
    }

    // ── punchcard options ──
    var punchOrientation by remember { mutableStateOf(PunchOrientation.HORIZONTAL) }
    var punchTransparent by remember { mutableStateOf(true) }
    var punchBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(hourByDay, punchOrientation, punchTransparent) {
        punchBitmap = withContext(Dispatchers.Default) {
            buildPunchcardBitmap(
                hourByDay = hourByDay,
                orientation = punchOrientation,
                accent = accent,
                ink = ink,
                muted = muted,
                transparent = punchTransparent,
                locale = locale,
            )
        }
    }

    fun save(name: String, bmp: Bitmap?) {
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
            // Data
            Section(stringResource(R.string.export_section_data)) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val r = viewModel.exportUsage()
                            val msg = if (r != null) {
                                context.getString(R.string.export_saved, "${r.csvName}, ${r.jsonName}")
                            } else {
                                context.getString(R.string.export_failed)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_csv_json)) }
            }

            // Share card
            Section(stringResource(R.string.export_section_card)) {
                OptionSwitch(stringResource(R.string.export_opt_trend), cardTrend) { cardTrend = it }
                OptionSwitch(stringResource(R.string.export_opt_models), cardModels) { cardModels = it }
                OptionSwitch(stringResource(R.string.export_opt_transparent), cardTransparent) { cardTransparent = it }
                Preview(cardBitmap, cardTransparent, cardData.background)
                Button(onClick = { save("opencodeclient-usage-card.png", cardBitmap) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_save))
                }
            }

            // Calendar
            Section(stringResource(R.string.export_section_calendar)) {
                val ranges = listOf(
                    stringResource(R.string.export_range_1),
                    stringResource(R.string.export_range_3),
                    stringResource(R.string.export_range_6),
                    stringResource(R.string.export_range_all),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ranges.forEachIndexed { i, label ->
                        FilterChip(selected = calRange == i, onClick = { calRange = i }, label = { Text(label) })
                    }
                }
                OptionSwitch(stringResource(R.string.export_opt_transparent), calTransparent) { calTransparent = it }
                Preview(calBitmap, calTransparent, null)
                Button(onClick = { save("opencodeclient-calendar-${monthCounts[calRange].let { if (it == Int.MAX_VALUE) "all" else it } }m.png", calBitmap) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_save))
                }
            }

            // Punchcard
            Section(stringResource(R.string.export_section_punch)) {
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
                OptionSwitch(stringResource(R.string.export_opt_transparent), punchTransparent) { punchTransparent = it }
                Preview(
                    punchBitmap,
                    punchTransparent,
                    null,
                    horizontalLong = punchOrientation == PunchOrientation.HORIZONTAL,
                )
                Button(onClick = {
                    save(
                        "opencodeclient-punchcard-${if (punchOrientation == PunchOrientation.HORIZONTAL) "h" else "v"}.png",
                        punchBitmap,
                    )
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_save))
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

private fun sharePalette(accentArgb: Int): List<Int> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accentArgb, hsv)
    val h = hsv[0]
    val s = hsv[1].coerceAtLeast(0.35f)
    val v = hsv[2].coerceAtLeast(0.6f)
    return listOf(0, 60, 120, 180, 240, 300).map { deg ->
        android.graphics.Color.HSVToColor(floatArrayOf((h + deg) % 360f, s, v))
    }
}