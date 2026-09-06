package com.geno1024.ai.occ.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.text.NumberFormat

data class ShareCardModel(
    val name: String,
    val share: Double,
    val color: Int,
)

data class ShareCardData(
    val appName: String,
    val monthLabel: String,
    val sublabel: String,
    val totalTokens: Long,
    val input: Long,
    val output: Long,
    val reasoning: Long,
    val cacheRead: Long,
    val messages: Long,
    val cost: Double,
    val days: List<Long>,
    val models: List<ShareCardModel>,
    val accent: Int,
    val ink: Int,
    val muted: Int,
    val footer: String,
)

fun buildShareCardBitmap(data: ShareCardData): Bitmap {
    val w = 1080
    val h = 1600
    val margin = 56
    val contentW = w - margin * 2
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.TRANSPARENT)
    val c = Canvas(bmp)

    val radius = 30f
    val front = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF2FFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val frontBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (data.ink and 0x00FFFFFF) or 0x1A000000
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = data.accent }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.muted
        textSize = 32f
        letterSpacing = 0.08f
    }
    val smallMuted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.muted
        textSize = 26f
    }
    val chipValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 46f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val bigValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 58f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 96f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.05f
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.muted
        textSize = 40f
    }
    val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.14f
    }

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, p: Paint) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), r, r, p)
    }

    fun chip(x: Float, y: Float, cw: Float, ch: Float, title: String, value: String) {
        roundRect(x, y, cw, ch, radius, front)
        roundRect(x, y, cw, ch, radius, frontBorder)
        c.drawText(title, x + 38f, y + 50f, label)
        c.drawText(value, x + 38f, y + ch - 32f, chipValue)
    }

    val nf = NumberFormat.getIntegerInstance()

    // ── header badge + date ──
    roundRect(margin.toFloat(), 72f, 26f, 26f, 8f, accent)
    c.drawText(data.appName, margin + 44f, 92f, appPaint)
    val dateWidth = subtitlePaint.measureText(data.footer)
    c.drawText(data.footer, w - margin - dateWidth, 92f, subtitlePaint)

    // ── title ──
    c.drawText(data.monthLabel.uppercase(), margin.toFloat(), 178f, titlePaint)
    c.drawText(data.sublabel, margin.toFloat(), 232f, subtitlePaint)

    // ── 14-day trend ──
    val trendTop = 300f
    val trendBottom = 520f
    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (data.ink and 0x00FFFFFF) or 0x12000000
        strokeWidth = 2f
    }
    val gridStep = (trendBottom - trendTop) / 4f
    for (i in 0..4) {
        val y = trendTop + i * gridStep
        c.drawLine(margin.toFloat(), y, (w - margin).toFloat(), y, gridPaint)
    }
    val dayMax = data.days.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val n = data.days.size
    val stepX = contentW.toFloat() / (n - 1).coerceAtLeast(1).toFloat()
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.accent
        strokeWidth = 9f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val linePath = Path()
    val areaPath = Path()
    data.days.forEachIndexed { i, v ->
        val x = margin + i * stepX
        val r = trendBottom - (v.toFloat() / dayMax.toFloat()) * (trendBottom - trendTop)
        if (i == 0) {
            linePath.moveTo(x, r)
            areaPath.moveTo(x, trendBottom)
            areaPath.lineTo(x, r)
        } else {
            linePath.lineTo(x, r)
            areaPath.lineTo(x, r)
        }
    }
    areaPath.lineTo((margin + (n - 1) * stepX).toFloat(), trendBottom)
    areaPath.close()
    c.drawPath(areaPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (data.accent and 0x00FFFFFF) or 0x26000000
        style = Paint.Style.FILL
    })
    c.drawPath(linePath, linePaint)
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = data.accent }
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    data.days.forEachIndexed { i, v ->
        val x = margin + i * stepX
        val r = trendBottom - (v.toFloat() / dayMax.toFloat()) * (trendBottom - trendTop)
        if (v > 0L || i == n - 1) {
            c.drawCircle(x, r, 12f, dotPaint)
        }
    }
    val lastX = margin + (n - 1) * stepX
    val lastY = trendBottom - (data.days.lastOrNull() ?: 0L).toFloat() / dayMax.toFloat() * (trendBottom - trendTop)
    c.drawCircle(lastX, lastY, 28f, haloPaint)
    c.drawCircle(lastX, lastY, 19f, dotPaint)
    c.drawText("14-DAY TREND", margin.toFloat(), 566f, label)
    c.drawText(nf.format(data.days.sum()), w - margin - label.measureText(nf.format(data.days.sum())), 566f, label)

    // ── model share ──
    val barY = 612f
    val barH = 34f
    c.drawText("MODEL SHARE", margin.toFloat(), 596f, label)
    val barPath = Path().apply {
        addRoundRect(RectF(margin.toFloat(), barY, (w - margin).toFloat(), barY + barH), 17f, 17f, Path.Direction.CW)
    }
    c.save()
    c.clipPath(barPath)
    var xx = margin.toFloat()
    val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    for (m in data.models) {
        val segW = contentW.toFloat() * m.share.toFloat()
        if (segW <= 0f) continue
        segPaint.color = m.color
        c.drawRect(RectF(xx, barY, xx + segW, barY + barH), segPaint)
        xx += segW
    }
    if (data.models.all { it.share <= 0.0 }) {
        c.drawRect(RectF(margin.toFloat(), barY, (w - margin).toFloat(), barY + barH), accent)
    }
    c.restore()

    val legendName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 34f
    }
    val legendPct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.muted
        textSize = 34f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val legendTop = barY + barH + 42f
    val colGap = 64f
    val colW = (contentW - colGap) / 2f
    data.models.take(4).forEachIndexed { i, m ->
        val col = i % 2
        val row = i / 2
        val x = margin + col * (colW + colGap)
        val y = legendTop + row * 56f
        roundRect(x, y - 16f, 18f, 18f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m.color })
        val nameW = legendName.measureText(m.name)
        val maxNameW = colW - 120f
        val shownName = if (nameW > maxNameW) m.name.take(16) + "…" else m.name
        c.drawText(shownName, x + 30f, y + 6f, legendName)
        val pct = String.format("%.0f%%", m.share * 100.0)
        c.drawText(pct, x + colW - legendPct.measureText(pct), y + 6f, legendPct)
    }

    // ── total chip ──
    val totalY = 958f
    roundRect(margin.toFloat(), totalY, contentW.toFloat(), 128f, 34f, front)
    roundRect(margin.toFloat(), totalY, contentW.toFloat(), 128f, 34f, frontBorder)
    c.drawText("TOTAL TOKENS", margin + 40f, totalY + 48f, label)
    c.drawText(nf.format(data.totalTokens), margin + 40f, totalY + 104f, bigValue)

    // ── stat grid ──
    val gap = 22f
    val chipW = (contentW - gap) / 2f
    val chipH = 148f
    val gridTop = totalY + 158f
    val rows = listOf(
        Triple("INPUT", nf.format(data.input), data.input),
        Triple("OUTPUT", nf.format(data.output), data.output),
        Triple("INFER", nf.format(data.reasoning), data.reasoning),
        Triple("CACHE R", nf.format(data.cacheRead), data.cacheRead),
        Triple("MESSAGES", nf.format(data.messages), data.messages),
        Triple("COST", String.format("$%.4f", data.cost), data.cost),
    )
    rows.forEachIndexed { i, (t, v, _) ->
        val row = i / 2
        val col = i % 2
        val x = margin + col * (chipW + gap)
        val y = gridTop + row * (chipH + gap)
        chip(x, y, chipW, chipH, t, v)
    }

    return bmp
}