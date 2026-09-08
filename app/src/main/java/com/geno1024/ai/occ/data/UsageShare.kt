package com.geno1024.ai.occ.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.geno1024.ai.occ.ui.monoAndroidTypeface
import java.text.NumberFormat
import java.util.Locale

data class UsageShareItem(
    val model: String,
    val total: Long,
    val input: Long,
    val output: Long,
    val reasoning: Long,
    val cacheRead: Long,
    val cacheWrite: Long,
    val cost: Double,
    val share: Float,
)

data class UsageShareData(
    val appName: String,
    val title: String,
    val periodLabel: String,
    val footer: String,
    val totalTokens: Long,
    val input: Long,
    val output: Long,
    val reasoning: Long,
    val cacheRead: Long,
    val cacheWrite: Long,
    val messages: Long,
    val cost: Double,
    val elapsed: String,
    val items: List<UsageShareItem>,
    val accent: Int,
)

fun buildUsageShareBitmap(data: UsageShareData): Bitmap {
    val w = 1080
    val margin = 56f
    val contentW = w - margin * 2
    val cardRad = 34f
    val cardPadT = 36f
    val cardPadB = 30f
    val metricRow = 44f
    val cardGap = 24f

    val ink = 0xFF161A1E.toInt()
    val muted = 0xFF6B7280.toInt()
    val background = 0xFFF6F8FB.toInt()
    val cardBg = 0xFFEDEFF3.toInt()
    val strokeBg = 0x1A000000.toInt()

    val nf = NumberFormat.getIntegerInstance()

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 32f
        typeface = monoAndroidTypeface()
        letterSpacing = 0.06f
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 38f
        typeface = monoAndroidTypeface(bold = true)
    }
    val bigValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 56f
        typeface = monoAndroidTypeface(bold = true)
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 72f
        typeface = monoAndroidTypeface(bold = true)
    }
    val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 34f
        typeface = monoAndroidTypeface(bold = true)
        letterSpacing = 0.14f
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 38f
        typeface = monoAndroidTypeface()
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = data.accent }

    fun ellipsize(p: Paint, text: String, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && p.measureText(t + "…") > maxW) t = t.dropLast(1)
        return t + "…"
    }

    val headerLine = appPaint.fontSpacing
    val titleLine = titlePaint.fontSpacing
    val subLine = subPaint.fontSpacing
    val labelLine = labelPaint.fontSpacing
    val valueLine = valuePaint.fontSpacing
    val bigLine = bigValue.fontSpacing

    val totalCardRows = 8
    val totalCardH = cardPadT + labelLine + 8f + bigLine + 28f + totalCardRows * metricRow + cardPadB
    val modelCardH = cardPadT + valueLine + 20f + 5 * metricRow + 12f + labelLine + cardPadB

    var top = margin
    top += headerLine + 28f
    top += titleLine + 14f
    top += subLine + 36f
    top += totalCardH
    if (data.items.isNotEmpty()) top += cardGap + data.items.size * modelCardH + cardGap
    top += 40f
    val totalH = top

    val bmp = Bitmap.createBitmap(w, totalH.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(background)

    fun roundRect(x: Float, y: Float, cw: Float, ch: Float, r: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
        c.drawRoundRect(RectF(x, y, x + cw, y + ch), r, r, p)
        val b = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeBg
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRoundRect(RectF(x, y, x + cw, y + ch), r, r, b)
    }

    val valueRight = margin + contentW - 36f

    fun metric(x: Float, rowTop: Float, label: String, value: String) {
        val center = rowTop + metricRow / 2f
        val lb = center - (labelPaint.ascent() + labelPaint.descent()) / 2f
        val vb = center - (valuePaint.ascent() + valuePaint.descent()) / 2f
        c.drawText(label, x, lb, labelPaint)
        c.drawText(value, valueRight - valuePaint.measureText(value), vb, valuePaint)
    }

    var y = margin
    c.drawRoundRect(RectF(margin, y - 26f, margin + 26f, y + 0f), 8f, 8f, accentPaint)
    c.drawText(data.appName, margin + 44f, y, appPaint)
    val footerW = subPaint.measureText(data.footer)
    c.drawText(data.footer, w - margin - footerW, y, subPaint)
    y += headerLine + 28f

    c.drawText(data.title.uppercase(Locale.getDefault()), margin, y, titlePaint)
    y += titleLine + 14f

    c.drawText(data.periodLabel, margin, y, subPaint)
    y += subLine + 36f

    val cardTop = y
    val cardBottom = cardTop + totalCardH
    roundRect(margin, cardTop, contentW, totalCardH, cardRad)
    var baseline = cardTop + cardPadT + labelLine
    c.drawText("TOTAL TOKENS", margin + 36f, baseline, labelPaint)
    baseline += 8f + bigLine
    c.drawText(nf.format(data.totalTokens), margin + 36f, baseline, bigValue)
    var rowTop = baseline + 14f
    fun totalMetric(l: String, v: String) {
        metric(margin + 36f, rowTop, l, v)
        rowTop += metricRow
    }
    totalMetric("in", nf.format(data.input))
    totalMetric("out", nf.format(data.output))
    totalMetric("infer", nf.format(data.reasoning))
    totalMetric("crd", nf.format(data.cacheRead))
    totalMetric("cwr", nf.format(data.cacheWrite))
    totalMetric("msgs", nf.format(data.messages))
    totalMetric("cost", String.format(Locale.US, "$%.4f", data.cost))
    totalMetric("elapsed", data.elapsed)
    y = cardBottom + cardGap

    data.items.forEachIndexed { index, item ->
        val imTop = y
        val imBottom = imTop + modelCardH
        roundRect(margin, imTop, contentW, modelCardH, cardRad)
        val modelX = margin + 36f
        var headerBase = imTop + cardPadT + valueLine
        val rankW = valuePaint.measureText("#${index + 1}")
        c.drawText("#${index + 1}", modelX, headerBase, accentPaint)
        val totalStr = nf.format(item.total)
        val nameX = modelX + rankW + 16f
        val maxNameW = valueRight - nameX - 16f - valuePaint.measureText(totalStr)
        c.drawText(ellipsize(valuePaint, item.model, maxNameW), nameX, headerBase, valuePaint)
        c.drawText(totalStr, valueRight - valuePaint.measureText(totalStr), headerBase, valuePaint)
        var rowTop = headerBase + 22f
        fun metricRow(l: String, v: String) {
            metric(modelX, rowTop, l, v)
            rowTop += metricRow
        }
        metricRow("in", nf.format(item.input))
        metricRow("out", nf.format(item.output))
        metricRow("infer", nf.format(item.reasoning))
        metricRow("crd", nf.format(item.cacheRead))
        metricRow("cwr", nf.format(item.cacheWrite))
        val footBaseline = imBottom - cardPadB - labelPaint.descent()
        val foot = "${String.format(Locale.US, "$%.4f", item.cost)} · ${String.format(Locale.US, "%.1f%%", item.share * 100f)}"
        c.drawText(foot, modelX, footBaseline, labelPaint)
        y = imBottom + cardGap
    }

    return bmp
}