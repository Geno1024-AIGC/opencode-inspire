package com.example.opencodeclient.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.opencodeclient.R

val MonoFontFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, weight = FontWeight.Bold),
    Font(R.font.jetbrains_mono_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
)