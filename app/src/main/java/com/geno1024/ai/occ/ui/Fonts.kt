package com.geno1024.ai.occ.ui

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

private val MonoFontCandidates = listOf(
    "/system/fonts/CutiveMono.ttf",
    "/system/fonts/DroidSansMono.ttf",
)

val MonoFontFamily: FontFamily = MonoFontCandidates
    .asSequence()
    .map { File(it) }
    .firstOrNull { it.exists() }
    ?.let { file ->
        FontFamily(
            Font(file, weight = FontWeight.Normal),
            Font(file, weight = FontWeight.Bold),
        )
    }
    ?: FontFamily.Monospace

fun monoAndroidTypeface(bold: Boolean = false): Typeface {
    val base = MonoFontCandidates.asSequence()
        .map { File(it) }
        .firstOrNull { it.exists() }
        ?.let { file -> runCatching { Typeface.createFromFile(file) }.getOrNull() }
        ?: Typeface.MONOSPACE
    return if (bold) Typeface.create(base, Typeface.BOLD) else base
}