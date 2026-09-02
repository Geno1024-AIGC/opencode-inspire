package com.example.opencodeclient.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

private val systemDroidSansMono: File? = File("/system/fonts/DroidSansMono.ttf")
    .takeIf { it.exists() }

val MonoFontFamily: FontFamily = if (systemDroidSansMono != null) {
    FontFamily(
        Font(systemDroidSansMono, weight = FontWeight.Normal),
        Font(systemDroidSansMono, weight = FontWeight.Bold),
    )
} else {
    FontFamily.Monospace
}