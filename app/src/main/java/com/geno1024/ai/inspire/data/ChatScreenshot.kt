package com.geno1024.ai.inspire.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.geno1024.ai.inspire.ui.ChatMessage
import com.geno1024.ai.inspire.ui.monoAndroidTypeface

private const val IMG_W = 1080
private const val H_PAD = 40
private const val V_GAP = 24
private const val BUBBLE_PAD_H = 28
private const val BUBBLE_PAD_V = 22
private const val BODY_W = IMG_W - H_PAD * 2
private const val USER_W = 0.85f
private const val ASSIST_W = 0.95f

private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) monoAndroidTypeface(bold = true) else monoAndroidTypeface()
    }

private fun wrapText(p: Paint, text: String, maxW: Float): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    val current = StringBuilder()
    for (w in words) {
        if (current.isEmpty()) {
            current.append(w)
        } else if (p.measureText(current.toString() + " " + w) <= maxW) {
            current.append(' ').append(w)
        } else {
            lines.add(current.toString())
            current.setLength(0)
            current.append(w)
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
}

/**
 * Renders the selected [messages] as a single long chat screenshot.
 * Returns a bitmap, or null if nothing to draw.
 */
fun buildChatScreenshot(messages: List<ChatMessage>): Bitmap? {
    val nonEmpty = messages.filter { it.role != "system" }
    if (nonEmpty.isEmpty()) return null

    val ink = 0xFF161A1E.toInt()
    val userBg = 0xFFBBDDFB.toInt()
    val assistantBg = 0xFFEDEFF3.toInt()
    val muted = 0xFF6B7280.toInt()
    val background = 0xFFF6F8FB.toInt()

    val bodyP = textPaint(34f, ink)
    val roleP = textPaint(28f, muted, bold = true)

    val blocks = nonEmpty.map { msg ->
        val isUser = msg.role == "user"
        val frac = if (isUser) USER_W else ASSIST_W
        val maxContentW = (BODY_W * frac).coerceAtMost(BODY_W.toFloat())
        val innerW = maxContentW - BUBBLE_PAD_H * 2
        val lines = wrapText(bodyP, msg.text.trim(), innerW)
        val lineH = bodyP.fontSpacing
        val textH = lines.size * lineH + 8f
        BubbleBlock(
            isUser = isUser,
            lines = lines,
            lineH = lineH,
            contentW = maxContentW,
            bubbleH = textH + BUBBLE_PAD_V * 2,
            roleH = roleP.fontSpacing,
        )
    }

    val totalH = H_PAD +
        blocks.fold(0f) { acc, b -> acc + b.roleH + b.bubbleH + V_GAP }

    val bmp = Bitmap.createBitmap(IMG_W, totalH.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(background)

    var y = H_PAD.toFloat()
    blocks.forEach { b ->
        val x = if (b.isUser) IMG_W - H_PAD - b.contentW else H_PAD.toFloat()
        val label = if (b.isUser) "YOU" else "AI"

        c.drawText(label, if (b.isUser) x + b.contentW - roleP.measureText(label) else x, y + b.roleH, roleP)
        y += b.roleH + 6f

        val bubbleTop = y
        val bubbleBottom = bubbleTop + b.bubbleH
        val bg = if (b.isUser) userBg else assistantBg
        val radius = 40f
        c.drawRoundRect(RectF(x, bubbleTop, x + b.contentW, bubbleBottom), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })

        var ty = bubbleTop + BUBBLE_PAD_V
        for (line in b.lines) {
            c.drawText(line, x + BUBBLE_PAD_H, ty - bodyP.ascent(), bodyP)
            ty += b.lineH
        }
        y = bubbleBottom + V_GAP
    }

    return bmp
}

private data class BubbleBlock(
    val isUser: Boolean,
    val lines: List<String>,
    val lineH: Float,
    val contentW: Float,
    val bubbleH: Float,
    val roleH: Float,
)
