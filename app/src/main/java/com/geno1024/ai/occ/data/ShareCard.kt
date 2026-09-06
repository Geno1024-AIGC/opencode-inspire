package com.geno1024.ai.occ.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.text.NumberFormat
import kotlin.math.ceil

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
    val includeTrend: Boolean = false,
    val includeModelChart: Boolean = false,
    val background: Int? = null,
    val author: String? = null,
)

fun buildShareCardBitmap(data: ShareCardData): Bitmap {
    val w = 1080
    val margin = 56
    val contentW = w - margin * 2

    val nf = NumberFormat.getIntegerInstance()

    val front = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF2FFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val frontBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (data.ink and 0x00FFFFFF) or 0x1A000000
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = data.accent }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.muted
        textSize = 32f
        letterSpacing = 0.08f
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.muted
        textSize = 40f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 96f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.05f
    }
    val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.14f
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
    val legendName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 34f
    }
    val legendPct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.muted
        textSize = 34f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    val pad = 36
    val trendBlockH = 330
    val modelRows = ceil(data.models.take(8).size.toDouble() / 2.0).toInt().coerceAtLeast(1)
    val modelBlockH = 44 + 34 + 46 + modelRows * 56 + pad
    val totalH = 128 + pad
    val gridRows = 3
    val chipGap = 22
    val chipH = 148
    val gridH = gridRows * chipH + (gridRows - 1) * chipGap + pad
    val topH = 40 + 170 + pad
    val footerH = 72

    val h = topH +
        (if (data.includeTrend) trendBlockH else 0) +
        (if (data.includeModelChart && data.models.isNotEmpty()) modelBlockH else 0) +
        totalH + gridH + footerH

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    bmp.eraseColor(data.background ?: Color.TRANSPARENT)

    fun roundRect(x: Float, y: Float, cw: Float, ch: Float, r: Float, p: Paint) {
        c.drawRoundRect(RectF(x, y, x + cw, y + ch), r, r, p)
    }

    fun chip(x: Float, y: Float, cw: Float, ch: Float, title: String, value: String) {
        roundRect(x, y, cw, ch, 30f, front)
        roundRect(x, y, cw, ch, 30f, frontBorder)
        c.drawText(title, x + 38f, y + 50f, label)
        c.drawText(value, x + 38f, y + ch - 32f, chipValue)
    }

    var y = 0f

    // header
    roundRect(margin.toFloat(), 72f, 26f, 26f, 8f, accentPaint)
    c.drawText(data.appName, margin + 44f, 92f, appPaint)
    val footerW = subtitlePaint.measureText(data.footer)
    c.drawText(data.footer, w - margin - footerW, 92f, subtitlePaint)
    if (!data.author.isNullOrBlank()) {
        c.drawText("by ${data.author}", (w - margin).toFloat(), 142f, subtitlePaint)
    }

    y = 178f
    c.drawText(data.monthLabel.uppercase(), margin.toFloat(), y, titlePaint)
    c.drawText(data.sublabel, margin.toFloat(), y + 54f, subtitlePaint)
    y += 170f + pad

    if (data.includeTrend) {
        val trendTop = y
        val trendBottom = y + 220f
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (data.ink and 0x00FFFFFF) or 0x12000000
            strokeWidth = 2f
        }
        val step = (trendBottom - trendTop) / 4f
        for (i in 0..4) {
            val gy = trendTop + i * step
            c.drawLine(margin.toFloat(), gy, (w - margin).toFloat(), gy, gridPaint)
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
        data.days.forEachIndexed { i, v ->
            val x = margin + i * stepX
            val r = trendBottom - (v.toFloat() / dayMax.toFloat()) * (trendBottom - trendTop)
            if (v > 0L || i == n - 1) c.drawCircle(x, r, 12f, dotPaint)
        }
        c.drawText("14-DAY TREND", margin.toFloat(), trendBottom + 46f, label)
        val sumStr = nf.format(data.days.sum())
        c.drawText(sumStr, w - margin - label.measureText(sumStr), trendBottom + 46f, label)
        y = trendBottom + 46f + 40f + pad
    }

    if (data.includeModelChart && data.models.isNotEmpty()) {
        c.drawText("MODEL SHARE", margin.toFloat(), y + 20f, label)
        val barY = y + 36f
        val barH = 34f
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
        c.restore()
        val legendTop = barY + barH + 46f
        val colGap = 64f
        val colW = (contentW - colGap) / 2f
        data.models.take(8).forEachIndexed { i, m ->
            val col = i % 2
            val row = i / 2
            val lx = margin + col * (colW + colGap)
            val ly = legendTop + row * 56f
            c.drawRoundRect(RectF(lx, ly - 16f, lx + 18f, ly + 2f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m.color })
            val maxNameW = colW - 120f
            val shown = if (legendName.measureText(m.name) > maxNameW) m.name.take(16) + "…" else m.name
            c.drawText(shown, lx + 30f, ly + 6f, legendName)
            val pct = String.format("%.0f%%", m.share * 100.0)
            c.drawText(pct, lx + colW - legendPct.measureText(pct), ly + 6f, legendPct)
        }
        y = legendTop + modelRows * 56f
    }

    // total chip
    roundRect(margin.toFloat(), y, contentW.toFloat(), 128f, 34f, front)
    roundRect(margin.toFloat(), y, contentW.toFloat(), 128f, 34f, frontBorder)
    c.drawText("TOTAL TOKENS", margin + 40f, y + 48f, label)
    c.drawText(nf.format(data.totalTokens), margin + 40f, y + 104f, bigValue)
    y += 128f + pad

    // grid
    val chipW = (contentW - chipGap) / 2f
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
        val x = margin.toFloat() + col * (chipW + chipGap)
        val cy = y + row * (chipH + chipGap)
        chip(x, cy, chipW, chipH.toFloat(), t, v)
    }
    y += gridH

    return bmp
}