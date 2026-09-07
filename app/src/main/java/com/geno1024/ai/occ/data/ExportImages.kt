package com.geno1024.ai.occ.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import com.geno1024.ai.occ.ui.monoAndroidTypeface

private const val W = 1080
private const val MR = 44

private fun compactTokens(n: Long): String = when {
    n >= 1_000_000_000_000L -> (n / 1_000_000_000_000f).trim1() + "T"
    n >= 1_000_000_000L -> (n / 1_000_000_000f).trim1() + "G"
    n >= 1_000_000L -> (n / 1_000_000f).trim1() + "M"
    n >= 1_000L -> (n / 1_000f).trim1() + "k"
    else -> n.toString()
}

private fun Float.trim1(): String =
    if (this % 1f == 0f) toInt().toString() else "%.1f".format(Locale.ROOT, this)

private fun darkened(argb: Int, factor: Float): Int {
    val r = (Color.red(argb) * factor).toInt()
    val g = (Color.green(argb) * factor).toInt()
    val b = (Color.blue(argb) * factor).toInt()
    return Color.rgb(r, g, b)
}

enum class CalendarMetric { FRESH, TOTAL, MSGS_USER, MSGS_TOTAL }

fun TokenDay.metric(m: CalendarMetric): Long = when (m) {
    CalendarMetric.FRESH -> fresh
    CalendarMetric.TOTAL -> total
    CalendarMetric.MSGS_USER -> msgsSent
    CalendarMetric.MSGS_TOTAL -> msgs
}

private val ZH_DOWS = listOf("一", "二", "三", "四", "五", "六", "日")

private fun dowLabel(dow: DayOfWeek, locale: Locale): String =
    if (locale.language.startsWith("zh")) ZH_DOWS[(dow.value - 1) % 7] else dow.getDisplayName(TextStyle.SHORT, locale)

fun buildCalendarBitmap(
    history: Map<String, TokenDay>,
    months: List<YearMonth>,
    locale: Locale,
    accent: Int,
    ink: Int,
    muted: Int,
    transparent: Boolean,
    appName: String,
    author: String? = null,
    monthPattern: String = "MMMM yyyy",
    continuous: Boolean = false,
    metrics: List<CalendarMetric> = listOf(CalendarMetric.FRESH),
): Bitmap {
    if (continuous) {
        return buildCalendarContinuous(history, months, locale, accent, ink, muted, transparent, appName, author, monthPattern, metrics)
    }
    val contentW = W - MR * 2
    val cellGap = 10f
    val cell = (contentW - cellGap * 6) / 7f
    val blockH = 68f + 40f + 6f * cell + 5f * cellGap + 60f
    val pageH = 96f
    val blockGap = 44f
    val monthsCapped = months.take(24)
    val footerH = if (!author.isNullOrBlank()) 52f else 0f

    val h = (pageH + monthsCapped.size * blockH + (monthsCapped.size - 1).coerceAtLeast(0) * blockGap + footerH + 40f).toInt()
    val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (!transparent) c.drawColor(0xFFF6F8FB.toInt())

    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (transparent) 0xFFFFFFF.toInt() else Color.WHITE
        style = Paint.Style.FILL
    }
    val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (ink and 0x00FFFFFF) or 0x18000000
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 32f
        typeface = monoAndroidTypeface(bold = true)
        letterSpacing = 0.12f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 46f
        typeface = monoAndroidTypeface(bold = true)
        textAlign = Paint.Align.CENTER
    }
    val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 32f
        textAlign = Paint.Align.RIGHT
        typeface = monoAndroidTypeface(bold = true)
    }
    val dayTokensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = darkened(accent, 0.55f)
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = monoAndroidTypeface()
    }
    val weekHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = monoAndroidTypeface(bold = true)
        letterSpacing = 0.06f
    }
    val mutedOut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (muted and 0x00FFFFFF) or 0x55000000
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 26f
        textAlign = Paint.Align.RIGHT
    }

    val nf = NumberFormat.getIntegerInstance()
    val startDow = DayOfWeek.of(WeekFields.of(locale).firstDayOfWeek.value)

    var y = 0f
    c.drawRoundRect(RectF(48f, 66f, 72f, 90f), 8f, 8f, accentPaint)
    c.drawText("${appName.uppercase()}  ·  USAGE CALENDAR", 96f, 88f, appPaint)
    y = 96f

    val today = LocalDate.now()
    monthsCapped.forEach { month ->
        c.drawRoundRect(RectF(28f, y, W - 28f, y + blockH), 40f, 40f, card)
        c.drawRoundRect(RectF(28f, y, W - 28f, y + blockH), 40f, 40f, cardBorder)
        val cardTop = y + 30f
        c.drawText(month.format(DateTimeFormatter.ofPattern(monthPattern, locale)), W / 2f, cardTop + 52f, titlePaint)

        val monthDates = history.keys.mapNotNull { k ->
            runCatching { LocalDate.parse(k) }.getOrNull()
        }.filter { it.year == month.year && it.month == month.month }
        val primary = metrics.firstOrNull() ?: CalendarMetric.FRESH
        val monthMax = monthDates.mapNotNull { history[it.toString()] }.maxOfOrNull { it.metric(primary) } ?: 0L
        val monthTotal = monthDates.sumOf { history[it.toString()]?.metric(primary) ?: 0L }

        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - startDow.value + 7) % 7
        val gridStart = first.minusDays(leading.toLong())
        val gridTop = cardTop + 84f

        repeat(7) { i ->
            val cx = MR + cell / 2f + i * (cell + cellGap)
            c.drawText(dowLabel(startDow.plus(i.toLong()), locale), cx, gridTop + 8f, weekHeaderPaint)
        }
        for (r in 0 until 6) {
            for (col in 0 until 7) {
                val date = gridStart.plusDays((r * 7 + col).toLong())
                val inMonth = date.month == first.month && date.year == first.year
                val day = history[date.toString()]
                val primaryVal = if (inMonth) (day?.metric(primary) ?: 0L) else 0L
                val cx = MR + cell / 2f + col * (cell + cellGap)
                val cy = gridTop + 44f + r * (cell + cellGap) + cell / 2f
                val radius = cell / 2f - 5f
                if (inMonth && date != today && primaryVal > 0L) {
                    val frac = (primaryVal.toDouble() / monthMax).toFloat().coerceIn(0f, 1f)
                    c.drawCircle(
                        cx, cy, radius,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = accent
                            alpha = ((0.12f + 0.62f * frac) * 255f).toInt().coerceIn(0, 225)
                        },
                    )
                }
                if (date == today) {
                    c.drawCircle(cx, cy, radius, accentPaint)
                }
                val numPainter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = if (inMonth) 34f else 30f
                    textAlign = Paint.Align.CENTER
                    typeface = monoAndroidTypeface()
                    color = when {
                        date == today -> Color.WHITE
                        inMonth && primaryVal > 0L -> darkened(accent, 0.5f)
                        inMonth -> ink
                        else -> (muted and 0x00FFFFFF) or 0x40000000
                    }
                }
                c.drawText(date.dayOfMonth.toString(), cx, cy + 12f, numPainter)
                if (inMonth && day != null && date != today) {
                    metrics.forEachIndexed { mi, m ->
                        val v = day.metric(m)
                        if (v > 0L) {
                            c.drawText(compactTokens(v), cx, cy + 44f + mi * 26f, dayTokensPaint)
                        }
                    }
                }
            }
        }
        c.drawText("Σ " + nf.format(monthTotal), W - 28f - 40f, cardTop + 52f, totalPaint)
        y += blockH + blockGap
    }

    if (!author.isNullOrBlank()) {
        c.drawText(author, W - MR.toFloat(), y + 28f, authorPaint)
    }
    return bmp
}

private fun buildCalendarContinuous(
    history: Map<String, TokenDay>,
    months: List<YearMonth>,
    locale: Locale,
    accent: Int,
    ink: Int,
    muted: Int,
    transparent: Boolean,
    appName: String,
    author: String?,
    monthPattern: String,
    metrics: List<CalendarMetric>,
): Bitmap {
    if (months.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val startMonth = months.first()
    val endMonth = months.last()
    val start = startMonth.atDay(1)
    val end = endMonth.atEndOfMonth()

    val contentW = W - MR * 2
    val cellGap = 10f
    val cell = (contentW - cellGap * 6) / 7f
    val wf = WeekFields.of(locale)
    val startDow = DayOfWeek.of(wf.firstDayOfWeek.value)
    val leading = (start.dayOfWeek.value - startDow.value + 7) % 7
    val gridStart = start.minusDays(leading.toLong())
    val dayCount = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
    val nRows = (dayCount.toInt() + leading + 6) / 7

    val pageH = 96f
    val cardTitleH = 92f
    val dowH = 44f
    val footerH = if (!author.isNullOrBlank()) 52f else 0f
    val gridH = nRows * (cell + cellGap)
    val blockH = cardTitleH + dowH + gridH + 40f
    val h = (pageH + blockH + footerH + 40f).toInt()
    val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (!transparent) c.drawColor(0xFFF6F8FB.toInt())

    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (transparent) 0xFFFFFFF.toInt() else Color.WHITE
        style = Paint.Style.FILL
    }
    val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (ink and 0x00FFFFFF) or 0x18000000
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    val weekHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 28f
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.06f
        typeface = monoAndroidTypeface(bold = true)
    }
    val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 32f
        typeface = monoAndroidTypeface(bold = true)
        letterSpacing = 0.12f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 46f
        typeface = monoAndroidTypeface(bold = true)
        textAlign = Paint.Align.CENTER
    }
    val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 32f
        textAlign = Paint.Align.RIGHT
        typeface = monoAndroidTypeface(bold = true)
    }
    val dayLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = monoAndroidTypeface()
    }
    val dayTokensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = darkened(accent, 0.55f)
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = monoAndroidTypeface()
    }
    val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 26f
        textAlign = Paint.Align.RIGHT
        typeface = monoAndroidTypeface()
    }

    var y = 0f
    c.drawRoundRect(RectF(48f, 66f, 72f, 90f), 8f, 8f, accentPaint)
    c.drawText("${appName.uppercase()}  ·  USAGE CALENDAR", 96f, 88f, appPaint)
    y = 96f

    val primary = metrics.firstOrNull() ?: CalendarMetric.FRESH
    val total = (0L until dayCount).map { history[start.plusDays(it).toString()]?.metric(primary) ?: 0L }
    val dayMax = (total.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val dayTotal = total.sum()

    c.drawRoundRect(RectF(28f, y, W - 28f, y + blockH), 40f, 40f, card)
    c.drawRoundRect(RectF(28f, y, W - 28f, y + blockH), 40f, 40f, cardBorder)
    val cardTop = y + 30f

    val startLabel = startMonth.format(DateTimeFormatter.ofPattern(monthPattern, locale))
    val endLabel = endMonth.format(DateTimeFormatter.ofPattern(monthPattern, locale))
    val rangeLabel = if (startMonth == endMonth) startLabel else "$startLabel — $endLabel"
    c.drawText(rangeLabel, W / 2f, cardTop + 52f, titlePaint)
    c.drawText("Σ " + NumberFormat.getIntegerInstance().format(dayTotal), W - 28f - 40f, cardTop + 52f, totalPaint)

    repeat(7) { i ->
        val cx = MR + cell / 2f + i * (cell + cellGap)
        c.drawText(dowLabel(startDow.plus(i.toLong()), locale), cx, cardTop + 84f, weekHeaderPaint)
    }
    val gridTop = cardTop + 92f
    val today = LocalDate.now()
    for (r in 0 until nRows) {
        for (col in 0 until 7) {
            val date = gridStart.plusDays((r * 7 + col).toLong())
            val inRange = !date.isBefore(start) && !date.isAfter(end)
            val cx = MR + cell / 2f + col * (cell + cellGap)
            val cy = gridTop + r * (cell + cellGap) + cell / 2f
            val radius = cell / 2f - 5f
            if (inRange) {
                val day = history[date.toString()]
                val primaryVal = day?.metric(primary) ?: 0L
                if (date != today && primaryVal > 0L) {
                    val frac = (primaryVal.toFloat() / dayMax).coerceIn(0f, 1f)
                    c.drawCircle(
                        cx, cy, radius,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = accent
                            alpha = ((0.12f + 0.62f * frac) * 255f).toInt().coerceIn(0, 225)
                        },
                    )
                }
                if (date == today) {
                    c.drawCircle(cx, cy, radius, accentPaint)
                }
                val numColor = if (date == today) Color.WHITE else if (primaryVal > 0L) darkened(accent, 0.5f) else ink
                c.drawText(date.format(DateTimeFormatter.ofPattern("MM-dd", locale)), cx, cy + 8f, dayLabelPaint.apply { color = numColor })
                if (day != null && date != today) {
                    metrics.forEachIndexed { mi, m ->
                        val v = day.metric(m)
                        if (v > 0L) {
                            c.drawText(compactTokens(v), cx, cy + 34f + mi * 24f, dayTokensPaint)
                        }
                    }
                }
            }
        }
    }

    val footerY = pageH + blockH + 40f
    if (!author.isNullOrBlank()) {
        c.drawText(author, W - MR.toFloat(), footerY + 28f, authorPaint)
    }
    return bmp
}

enum class PunchMode { HOURLY, DAILY }
enum class PunchOrientation { HORIZONTAL, VERTICAL }

fun buildPunchcardBitmap(
    history: Map<String, TokenDay>,
    hourByDay: Map<String, Map<Int, TokenDay>>,
    mode: PunchMode,
    orientation: PunchOrientation,
    accent: Int,
    ink: Int,
    muted: Int,
    transparent: Boolean,
    locale: Locale,
    author: String? = null,
): Bitmap {
    val cell = 20f
    val gap = 3f
    val padLR = 24f
    val topLabelH = 52f
    val legendH = 100f

    if (mode == PunchMode.HOURLY) {
        return buildPunchcardHourly(hourByDay, orientation, accent, ink, muted, transparent, locale, author, cell, gap, padLR, topLabelH, legendH)
    }
    return buildPunchcardDaily(history, orientation, accent, ink, muted, transparent, locale, author, cell, gap, padLR, topLabelH, legendH)
}

private fun buildPunchcardHourly(
    hourByDay: Map<String, Map<Int, TokenDay>>,
    orientation: PunchOrientation,
    accent: Int, ink: Int, muted: Int, transparent: Boolean, locale: Locale,
    author: String?,
    cell: Float, gap: Float, padLR: Float, topLabelH: Float, legendH: Float,
): Bitmap {
    val days = hourByDay.keys.sorted().takeLast(730)
    val leftW = if (orientation == PunchOrientation.HORIZONTAL) 58f else 108f

    val nCols = if (orientation == PunchOrientation.HORIZONTAL) days.size else 24
    val nRows = if (orientation == PunchOrientation.HORIZONTAL) 24 else days.size

    val bodyW = (cell + gap) * nCols
    val bodyH = (cell + gap) * nRows
    val domainX = leftW + padLR
    val w = (domainX + bodyW + padLR).toInt()
    val h = (topLabelH + padLR + bodyH + legendH).toInt()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (!transparent) c.drawColor(0xFFF6F8FB.toInt())

    var maxV = 1L
    val value = Array<Long?>(nCols * nRows) { null }
    for (di in days.indices) {
        val m = hourByDay[days[di]] ?: emptyMap()
        for (hour in 0 until 24) {
            val v = m[hour]?.fresh ?: 0L
            val idx = if (orientation == PunchOrientation.HORIZONTAL) di * 24 + hour else hour * nRows + di
            if (v > 0L) {
                value[idx] = v
                if (v > maxV) maxV = v
            }
        }
    }

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val domainY = topLabelH + padLR
    for (i in 0 until nCols) {
        for (j in 0 until nRows) {
            val v = value[if (orientation == PunchOrientation.HORIZONTAL) i * 24 + j else i * nRows + j] ?: 0L
            val x = domainX + i * (cell + gap)
            val y = domainY + j * (cell + gap)
            if (v > 0L) {
                val frac = (v.toFloat() / maxV).coerceIn(0f, 1f)
                fill.color = accent
                fill.alpha = ((0.12f + 0.82f * frac) * 255f).toInt().coerceIn(0, 235)
            } else {
                fill.color = if (transparent) Color.TRANSPARENT else 0x08000000.toInt()
            }
            c.drawRoundRect(RectF(x, y, x + cell, y + cell), 5f, 5f, fill)
        }
    }

    val monoBold: Typeface = monoAndroidTypeface(bold = true)
    val monoPlain: Typeface = monoAndroidTypeface()
    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 24f; typeface = monoPlain }
    val tickRight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 24f; typeface = monoPlain; textAlign = Paint.Align.RIGHT }
    val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 26f; typeface = monoBold; textAlign = Paint.Align.CENTER
    }
    val rightHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted; textSize = 24f; textAlign = Paint.Align.RIGHT; typeface = monoBold
    }
    val dates = days.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (orientation == PunchOrientation.HORIZONTAL) {
        dates.forEachIndexed { di, date ->
            if (date.dayOfMonth == 1) {
                val x = domainX + di * (cell + gap) + cell / 2
                c.drawText(date.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale), x, 34f, head)
            }
        }
        c.drawText("${dates.size} days", domainX + bodyW + padLR, 44f, rightHead)
        for (hour in 0 until 24) {
            val y = domainY + hour * (cell + gap) + cell / 2
            c.drawText("%02d".format(Locale.ROOT, hour), domainX - 10f, y + 8f, tickRight)
        }
    } else {
        for (hour in 0 until 24) {
            val x = domainX + hour * (cell + gap) + cell / 2
            c.drawText("%02d".format(Locale.ROOT, hour), x, 32f, head)
        }
        dates.forEachIndexed { di, date ->
            if (date.dayOfMonth == 1) {
                val y = domainY + di * (cell + gap) + cell / 2
                val txt = if (date.monthValue == 1) date.year.toString() else date.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
                c.drawText(txt, domainX - 10f, y + 9f, tickRight)
            }
        }
    }

    drawLegend(c, accent, muted, maxV, w, h, padLR, fill)
    if (!author.isNullOrBlank()) {
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 24f; textAlign = Paint.Align.RIGHT; typeface = monoPlain }
        c.drawText(author, w - padLR, h - 12f, ap)
    }
    return bmp
}

private fun buildPunchcardDaily(
    history: Map<String, TokenDay>,
    orientation: PunchOrientation,
    accent: Int, ink: Int, muted: Int, transparent: Boolean, locale: Locale,
    author: String?,
    cell: Float, gap: Float, padLR: Float, topLabelH: Float, legendH: Float,
): Bitmap {
    val today = LocalDate.now()
    val dates = history.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .sorted().takeLast(730)
    if (dates.isEmpty()) {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
    val wf = WeekFields.of(locale)
    fun weekStart(d: LocalDate): LocalDate = d.with(wf.dayOfWeek(), 1L)

    val weekStartDates = dates.map { weekStart(it) }.distinct().sorted()
    val weekToIndex = weekStartDates.withIndex().associate { (i, w) -> w to i }
    val dowOrder: List<DayOfWeek> = (0L until 7L).map { wf.firstDayOfWeek.plus(it) }
    val dowIndex = dowOrder.withIndex().associate { (i, d) -> d.value to i }

    val weeks = weekStartDates.size
    val nCols = if (orientation == PunchOrientation.HORIZONTAL) 7 else weeks
    val nRows = if (orientation == PunchOrientation.HORIZONTAL) weeks else 7
    val leftW = if (orientation == PunchOrientation.HORIZONTAL) 62f else 120f
    val topRowH = if (orientation == PunchOrientation.HORIZONTAL) 44f else topLabelH
    val bodyW = (cell + gap) * nCols
    val bodyH = (cell + gap) * nRows
    val domainX = leftW + padLR
    val w = (domainX + bodyW + padLR).toInt()
    val h = (topRowH + padLR + bodyH + legendH).toInt()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (!transparent) c.drawColor(0xFFF6F8FB.toInt())

    var maxV = 1L
    for (d in dates) {
        val v = history[d.toString()]?.fresh ?: 0L
        if (v > maxV) maxV = v
    }

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val domainY = topRowH + padLR
    for (date in dates) {
        val v = history[date.toString()]?.fresh ?: 0L
        val wIdx = weekToIndex[weekStart(date)]!!
        val dIdx = dowIndex[date.dayOfWeek.value]!!
        val (col, row) = if (orientation == PunchOrientation.HORIZONTAL) dIdx to wIdx else wIdx to dIdx
        val x = domainX + col * (cell + gap)
        val y = domainY + row * (cell + gap)
        if (v > 0L) {
            val frac = (v.toFloat() / maxV).coerceIn(0f, 1f)
            fill.color = accent
            fill.alpha = ((0.12f + 0.82f * frac) * 255f).toInt().coerceIn(0, 235)
        } else {
            fill.color = if (transparent) Color.TRANSPARENT else 0x08000000.toInt()
        }
        c.drawRoundRect(RectF(x, y, x + cell, y + cell), 5f, 5f, fill)
    }

    val monoBold: Typeface = monoAndroidTypeface(bold = true)
    val monoPlain: Typeface = monoAndroidTypeface()
    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 22f; typeface = monoPlain }
    val tickRight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 22f; typeface = monoPlain; textAlign = Paint.Align.RIGHT }
    val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 24f; typeface = monoBold; textAlign = Paint.Align.CENTER
    }

    if (orientation == PunchOrientation.HORIZONTAL) {
        dowOrder.forEachIndexed { i, dow ->
            val x = domainX + i * (cell + gap) + cell / 2
            c.drawText(dowLabel(dow, locale), x, 30f, head)
        }
        weekStartDates.forEachIndexed { wi, w ->
            val y = domainY + wi * (cell + gap) + cell / 2
            val label = if (w.dayOfMonth <= 7) {
                if (w.monthValue == 1 && w.dayOfMonth <= 7) w.year.toString() else "${w.monthValue}/${w.dayOfMonth}"
            } else {
                "${w.monthValue}/${w.dayOfMonth}"
            }
            c.drawText(label, domainX - 10f, y + 7f, tickRight)
        }
    } else {
        dowOrder.forEachIndexed { i, dow ->
            val y = domainY + i * (cell + gap) + cell / 2
            c.drawText(dowLabel(dow, locale), domainX - 10f, y + 7f, tickRight)
        }
        weekStartDates.forEachIndexed { wi, w ->
            if (w.dayOfMonth <= 7) {
                val x = domainX + wi * (cell + gap) + cell / 2
                val label = if (w.monthValue == 1 && w.dayOfMonth <= 7)
                    w.year.toString()
                else
                    w.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
                c.drawText(label, x, 30f, head)
            }
        }
    }

    drawLegend(c, accent, muted, maxV, w, h, padLR, fill)
    if (!author.isNullOrBlank()) {
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 24f; textAlign = Paint.Align.RIGHT; typeface = monoPlain }
        c.drawText(author, w - padLR, h - 12f, ap)
    }
    return bmp
}

private fun drawLegend(c: Canvas, accent: Int, muted: Int, maxV: Long, w: Int, h: Int, padLR: Float, fill: Paint) {
    fill.color = accent
    val legendY = h - 44f
    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 24f; typeface = monoAndroidTypeface() }
    c.drawText("LESS", padLR, legendY, tick)
    val steps = 10
    val stepW = 22f
    val gradX = padLR + 74f
    for (i in 0 until steps) {
        fill.alpha = ((0.12f + 0.82f * i / (steps - 1)) * 255f).toInt()
        c.drawRoundRect(RectF(gradX + i * stepW, legendY - 14f, gradX + i * stepW + stepW - 3f, legendY + 12f), 4f, 4f, fill)
    }
    c.drawText("MORE", gradX + steps * stepW + 10f, legendY + 9f, tick)
    val rightHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted; textSize = 24f; textAlign = Paint.Align.RIGHT; typeface = monoAndroidTypeface(bold = true)
    }
    c.drawText(compactTokens(maxV) + " max", w - padLR, legendY, rightHead)
}