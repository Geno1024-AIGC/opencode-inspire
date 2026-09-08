package com.geno1024.ai.occ.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import com.geno1024.ai.occ.R
import com.geno1024.ai.occ.data.TokenDay
import com.geno1024.ai.occ.data.TokenFormat
import com.geno1024.ai.occ.data.TokenModelStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val chartBarHeight = 14.dp
private val chartGap = 6.dp

@Composable
fun ModelUsageChart(
    modelStats: Map<String, TokenModelStats>,
    format: TokenFormat,
) {
    val models = remember(modelStats) {
        modelStats.entries.map { (id, st) ->
            val day = st.history.values.fold(TokenDay()) { a, b -> a + b }
            Triple(id, day, day.fresh)
        }.sortedByDescending { it.third }
    }
    if (models.isEmpty()) return
    val maxFresh = max(models.maxOf { it.third }, 1L)
    val maxCache = max(models.maxOf { it.second.cacheRead }, 1L)
    val inColor = MaterialTheme.colorScheme.primary
    val outColor = MaterialTheme.colorScheme.secondary
    val reasoningColor = MaterialTheme.colorScheme.tertiary
    val cacheColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((id, day, fresh) in models) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        id,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        color = textColor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                    )
                    Text(
                        "${formatTokens(fresh, format)} · ${formatTokens(day.cacheRead, format)} crd",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = textColor,
                    )
                }
                Canvas(Modifier.fillMaxWidth().height(chartBarHeight)) {
                    var x = 0f
                    fun drawSegment(len: Long, color: Color) {
                        if (len <= 0L) return
                        val w = size.width * (len.toFloat() / maxFresh.toFloat())
                        drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                        x += w
                    }
                    drawSegment(day.input, inColor)
                    drawSegment(day.output, outColor)
                    drawSegment(day.reasoning, reasoningColor)
                    if (day.input + day.output + day.reasoning == 0L) {
                        drawRoundRect(
                            cacheColor.copy(alpha = 0.4f),
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    if (day.cacheRead > 0L) {
                        drawRoundRect(
                            cacheColor,
                            topLeft = androidx.compose.ui.geometry.Offset.Zero,
                            size = Size(size.width * (day.cacheRead.toFloat() / maxCache.toFloat()), size.height),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartLegend(inColor, stringResource(R.string.calendar_token_in))
            ChartLegend(outColor, stringResource(R.string.calendar_token_out))
            ChartLegend(reasoningColor, stringResource(R.string.calendar_token_infer))
            ChartLegend(cacheColor, stringResource(R.string.calendar_token_crd))
        }
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(8.dp).height(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DailyTrendChart(
    history: Map<String, TokenDay>,
    days: Int = 14,
    format: TokenFormat,
) {
    val measurer = rememberTextMeasurer()
    val today = LocalDate.now()
    val data = remember(history, days) {
        (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            Triple(date, history[date.toString()]?.fresh ?: 0L, history[date.toString()]?.msgs ?: 0L)
        }
    }
    val locale = java.util.Locale.getDefault()
    var showMsgs by remember { mutableStateOf(false) }
    val maxVal = max(data.maxOf { if (showMsgs) it.third else it.second }, 1L)
    val barColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily, fontSize = 9.sp)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (showMsgs) R.string.stats_tab_msgs else R.string.calendar_token_fresh),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = textColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showMsgs = !showMsgs }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                formatTokens(data.sumOf { if (showMsgs) it.third else it.second }, format) + " · " + formatTokens(maxVal, format) + " max",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = textColor,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val labelAreaH = 44.dp.toPx()
            val plotH = size.height - labelAreaH
            val step = plotH / 4f
            for (i in 0..4) {
                val y = plotH - i * step
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val barW = (size.width - 12f) / data.size
            val mmdd = DateTimeFormatter.ofPattern("MM-dd")
            val probe = measurer.measure("09-09", labelStyle)
            val labelW = probe.size.width.toFloat()
            val labelH = probe.size.height.toFloat()
            val labelPad = 4.dp.toPx()
            val dateTiers: List<Set<Int>?> = listOf(null, setOf(1, 8, 15, 22), setOf(1, 15), setOf(1))
            var shownLabels: List<Pair<Int, String>> = emptyList()
            var horizontal = false
            for (tier in dateTiers) {
                val shown = data.mapIndexedNotNull { index, item ->
                    if (tier == null || item.first.dayOfMonth in tier) index to item.first.format(mmdd) else null
                }
                if (shown.isEmpty()) continue
                val slotW = (size.width - 12f) / shown.size
                if (slotW >= labelW + labelPad) {
                    shownLabels = shown
                    horizontal = true
                    break
                } else if (slotW >= labelH * 0.85f) {
                    shownLabels = shown
                    horizontal = false
                    break
                }
            }
            if (shownLabels.isEmpty()) {
                shownLabels = data.mapIndexedNotNull { index, item ->
                    if (item.first.dayOfMonth == 1) index to item.first.format(mmdd) else null
                }
            }
            val labelTop = plotH + 4.dp.toPx()
            for ((index, item) in data.withIndex()) {
                val v = if (showMsgs) item.third else item.second
                if (v > 0L) {
                    val h = (plotH - 8f) / maxVal.toFloat() * v.toFloat()
                    val left = 6f + index * barW
                    drawRoundRect(
                        barColor,
                        topLeft = Offset(left, plotH - h),
                        size = Size(barW * 0.72f, h),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            for ((index, label) in shownLabels) {
                val layout = measurer.measure(label, labelStyle)
                val centerX = 6f + index * barW + barW / 2f
                if (horizontal) {
                    val lx = (centerX - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(lx, labelTop),
                    )
                } else {
                    val lx = (centerX - layout.size.height / 2f).coerceIn(0f, size.width - 1f)
                    rotate(degrees = -90f, pivot = Offset(lx, labelTop)) {
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(lx, labelTop),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartLegend(barColor, if (showMsgs) stringResource(R.string.calendar_msgs_total) else stringResource(R.string.calendar_token_fresh))
        }
    }
}