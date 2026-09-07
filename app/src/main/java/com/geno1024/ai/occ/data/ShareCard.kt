package com.geno1024.ai.occ.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.text.NumberFormat
import com.geno1024.ai.occ.ui.monoAndroidTypeface

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
    val accent: Int,
    val ink: Int,
    val muted: Int,
    val footer: String,
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
        typeface = monoAndroidTypeface()
        letterSpacing = 0.08f
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.muted
        textSize = 40f
        typeface = monoAndroidTypeface()
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 96f
        typeface = monoAndroidTypeface(bold = true)
        letterSpacing = 0.05f
    }
    val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 34f
        typeface = monoAndroidTypeface(bold = true)
        letterSpacing = 0.14f
    }
    val chipValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 46f
        typeface = monoAndroidTypeface(bold = true)
    }
    val bigValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = data.ink
        textSize = 58f
        typeface = monoAndroidTypeface(bold = true)
    }

    val pad = 36
    val totalH = 128 + pad
    val gridRows = 3
    val chipGap = 22
    val chipH = 148
    val gridH = gridRows * chipH + (gridRows - 1) * chipGap + pad
    val topH = 40 + 170 + pad
    val footerH = 72

    val h = topH + totalH + gridH + footerH

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

    // header
    roundRect(margin.toFloat(), 72f, 26f, 26f, 8f, accentPaint)
    c.drawText(data.appName, margin + 44f, 92f, appPaint)
    val footerW = subtitlePaint.measureText(data.footer)
    c.drawText(data.footer, w - margin - footerW, 92f, subtitlePaint)
    if (!data.author.isNullOrBlank()) {
        val ownerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = data.muted
            textSize = 40f
            textAlign = Paint.Align.RIGHT
            typeface = monoAndroidTypeface()
        }
        c.drawText(data.author!!, (w - margin).toFloat(), 142f, ownerPaint)
    }

    var y = 178f
    c.drawText(data.monthLabel.uppercase(), margin.toFloat(), y, titlePaint)
    c.drawText(data.sublabel, margin.toFloat(), y + 54f, subtitlePaint)
    y += 170f + pad

    // total chip
    roundRect(margin.toFloat(), y, contentW.toFloat(), 128f, 34f, front)
    roundRect(margin.toFloat(), y, contentW.toFloat(), 128f, 34f, frontBorder)
    c.drawText("TOTAL TOKENS", margin + 40f, y + 48f, label)
    c.drawText(nf.format(data.totalTokens), margin + 40f, y + 104f, bigValue)
    y += totalH

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

    return bmp
}