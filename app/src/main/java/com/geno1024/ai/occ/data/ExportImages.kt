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
import kotlin.math.min

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

/**
 * Vertical long image stacking one calendar month grid each.
 */
fun buildCalendarBitmap(
    history: Map<String, TokenDay>,
    months: List<YearMonth>,
    locale: Locale,
    accent: Int,
    ink: Int,
    muted: Int,
    transparent: Boolean,
    appName: String,
): Bitmap {
    val contentW = W - MR * 2
    val cellGap = 10f
    val cell = (contentW - cellGap * 6) / 7f
    val blockH = 68f + 40f + 6f * cell + 5f * cellGap + 60f
    val pageH = 96f
    val blockGap = 44f
    val monthsCapped = months.take(24)

    val h = (pageH + monthsCapped.size * blockH + (monthsCapped.size - 1).coerceAtLeast(0) * blockGap + 40f).toInt()
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
    val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 28f
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }
    val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.12f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 32f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val dayTokensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = darkened(accent, 0.55f)
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    val mutedOut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (muted and 0x00FFFFFF) or 0x55000000
        textSize = 30f
        textAlign = Paint.Align.CENTER
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
        c.drawText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), W / 2f, cardTop + 52f, titlePaint)

        val monthDates = history.keys.mapNotNull { k ->
            runCatching { LocalDate.parse(k) }.getOrNull()
        }.filter { it.year == month.year && it.month == month.month }
        val monthMax = monthDates.mapNotNull { history[it.toString()] }.maxOfOrNull { it.fresh } ?: 0L
        val monthTotal = monthDates.sumOf { history[it.toString()]?.fresh ?: 0L }

        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - startDow.value + 7) % 7
        val gridStart = first.minusDays(leading.toLong())
        val gridTop = cardTop + 84f

        repeat(7) { i ->
            val cx = MR + cell / 2f + i * (cell + cellGap)
            val label = startDow.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale)
            c.drawText(label.uppercase(locale), cx, gridTop + 8f, weekdayPaint)
        }
        for (r in 0 until 6) {
            for (col in 0 until 7) {
                val date = gridStart.plusDays((r * 7 + col).toLong())
                val inMonth = date.month == first.month && date.year == first.year
                val tokens = if (inMonth) (history[date.toString()]?.fresh ?: 0L) else 0L
                val cx = MR + cell / 2f + col * (cell + cellGap)
                val cy = gridTop + 44f + r * (cell + cellGap) + cell / 2f
                val radius = cell / 2f - 5f
                if (inMonth && date != today && tokens > 0L) {
                    val frac = (tokens.toDouble() / monthMax).toFloat().coerceIn(0f, 1f)
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
                    color = when {
                        date == today -> Color.WHITE
                        inMonth && tokens > 0L -> darkened(accent, 0.5f)
                        inMonth -> ink
                        else -> (muted and 0x00FFFFFF) or 0x40000000
                    }
                }
                c.drawText(date.dayOfMonth.toString(), cx, cy + 12f, numPainter)
                if (inMonth && tokens > 0L && date != today) {
                    c.drawText(compactTokens(tokens), cx, cy + 42f, dayTokensPaint)
                }
            }
        }
        c.drawText("Σ " + nf.format(monthTotal), W - 28f - 40f, cardTop + 52f, totalPaint)
        y += blockH + blockGap
    }
    return bmp
}

enum class PunchOrientation { HORIZONTAL, VERTICAL }

/**
 * Long image of the 24-hour punchcard heatmap.
 * HORIZONTAL: days across (wide), hours down.
 * VERTICAL: days down (tall), hours across.
 */
fun buildPunchcardBitmap(
    hourByDay: Map<String, Map<Int, TokenDay>>,
    orientation: PunchOrientation,
    accent: Int,
    ink: Int,
    muted: Int,
    transparent: Boolean,
    locale: Locale,
): Bitmap {
    val days = hourByDay.keys.sorted().takeLast(730)
    val cell = 20f
    val gap = 3f
    val padLR = 24f
    val topLabelH = 52f
    val legendH = 100f
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

    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 24f }
    val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val rightHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 24f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
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
            c.drawText("%02d".format(Locale.ROOT, hour), leftW + 4f, y + 8f, tick)
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
                c.drawText(txt, leftW + 2f, y + 9f, tick)
            }
        }
    }

    // legend
    fill.color = accent
    val legendY = h - 44f
    c.drawText("LESS", padLR, legendY, tick)
    val steps = 10
    val stepW = 22f
    val gradX = padLR + 74f
    for (i in 0 until steps) {
        fill.alpha = ((0.12f + 0.82f * i / (steps - 1)) * 255f).toInt()
        c.drawRoundRect(RectF(gradX + i * stepW, legendY - 14f, gradX + i * stepW + stepW - 3f, legendY + 12f), 4f, 4f, fill)
    }
    c.drawText("MORE", gradX + steps * stepW + 10f, legendY + 9f, tick)
    c.drawText(compactTokens(maxV) + " max", w - padLR, legendY, rightHead)
    return bmp
}