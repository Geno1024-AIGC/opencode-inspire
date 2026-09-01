package com.example.opencodeclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class MdSpan(val start: Int, val end: Int, val style: SpanStyle?)
private data class MdLine(val type: String, val content: String, val level: Int = 0)
private data class MdTable(val headers: List<String>, val rows: List<List<String>>)

private fun parseInline(text: String, linkColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val close = text.indexOf(if (text[i] == '*') "**" else "__", i + 2)
                    if (close > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, close))
                        }
                        i = close + 2
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("*", i) && (i + 1 < text.length && text[i + 1] != '*') -> {
                    val close = text.indexOf("*", i + 1)
                    if (close > 0 && close > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, close))
                        }
                        i = close + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("_", i) && (i + 1 < text.length && text[i + 1] != '_') -> {
                    val close = text.indexOf("_", i + 1)
                    if (close > 0 && close > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, close))
                        }
                        i = close + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("`", i) -> {
                    val close = text.indexOf("`", i + 1)
                    if (close > 0) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(text.substring(i + 1, close))
                        }
                        i = close + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                text.startsWith("[", i) -> {
                    val closeBracket = text.indexOf("]", i)
                    val openParen = if (closeBracket > 0) text.indexOf("(", closeBracket) else -1
                    val closeParen = if (openParen > closeBracket && openParen > 0) text.indexOf(")", openParen) else -1
                    if (closeBracket > 0 && openParen == closeBracket + 1 && closeParen > openParen) {
                        val linkText = text.substring(i + 1, closeBracket)
                        if (linkColor != androidx.compose.ui.graphics.Color.Unspecified) {
                            withStyle(SpanStyle(color = linkColor)) {
                                append(linkText)
                            }
                        } else {
                            append(linkText)
                        }
                        i = closeParen + 1
                    } else {
                        append(text[i]); i++
                    }
                }
                else -> {
                    append(text[i]); i++
                }
            }
        }
    }
}

private fun parseMarkdown(text: String): List<Any> {
    val lines = text.split("\n")
    val result = mutableListOf<Any>()
    var inCodeBlock = false
    val codeBuffer = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("```") -> {
                if (inCodeBlock) {
                    result.add(MdLine("code", codeBuffer.toString().trimEnd()))
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
            }
            inCodeBlock -> {
                if (codeBuffer.isNotEmpty()) codeBuffer.append("\n")
                codeBuffer.append(line)
            }
            line.trimStart().startsWith("|") && i + 1 < lines.size && lines[i + 1].trimStart().startsWith("|") -> {
                val headers = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                i++
                val separator = lines[i]
                i++
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows.add(lines[i].split("|").map { it.trim() }.filter { it.isNotEmpty() })
                    i++
                }
                result.add(MdTable(headers, rows))
                continue
            }
            line.startsWith("# ") -> result.add(MdLine("h1", line.removePrefix("# ")))
            line.startsWith("## ") -> result.add(MdLine("h2", line.removePrefix("## ")))
            line.startsWith("### ") -> result.add(MdLine("h3", line.removePrefix("### ")))
            line.startsWith("#### ") -> result.add(MdLine("h4", line.removePrefix("#### ")))
            line.startsWith("##### ") -> result.add(MdLine("h5", line.removePrefix("##### ")))
            line.startsWith("###### ") -> result.add(MdLine("h6", line.removePrefix("###### ")))
            line.startsWith("- ") || line.startsWith("* ") -> result.add(MdLine("bullet", line.substring(2)))
            line.matches(Regex("^\\d+\\.\\s.*")) -> {
                val content = line.replace(Regex("^\\d+\\.\\s"), "")
                result.add(MdLine("ordered", content))
            }
            line.startsWith("> ") -> result.add(MdLine("quote", line.removePrefix("> ")))
            line.isBlank() -> result.add(MdLine("blank", ""))
            else -> result.add(MdLine("text", line))
        }
        i++
    }
    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        result.add(MdLine("code", codeBuffer.toString().trimEnd()))
    }
    return result
}

@Composable
fun MarkdownMessage(content: String) {
    val items = remember(content) { parseMarkdown(content) }

    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            for (item in items) {
                when (item) {
                    is MdTable -> TableRenderer(item)
                    is MdLine -> when (item.type) {
                        "h1" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        "h2" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        "h3" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        "h4" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        "h5" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        "h6" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        "bullet" -> Text(
                            buildAnnotatedString {
                                append("•  ")
                                append(parseInline(item.content, MaterialTheme.colorScheme.primary))
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                        )
                        "ordered" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                        )
                        "quote" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp),
                        )
                        "code" -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                item.content,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                ),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                        }
                        "text" -> Text(
                            parseInline(item.content, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        "blank" -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRenderer(table: MdTable) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .background(borderColor)
                .padding(1.dp)
        ) {
            for (header in table.headers) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        parseInline(header, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
        for (row in table.rows) {
            Row(
                modifier = Modifier
                    .background(borderColor)
                    .padding(1.dp)
            ) {
                for (cell in row) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            parseInline(cell, MaterialTheme.colorScheme.primary),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
