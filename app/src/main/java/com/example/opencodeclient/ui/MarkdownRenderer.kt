package com.example.opencodeclient.ui

import com.example.opencodeclient.R
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MdSpan(val start: Int, val end: Int, val style: SpanStyle?)
private data class MdLine(val type: String, val content: String, val level: Int = 0, val lang: String = "")
private data class MdTable(val headers: List<String>, val rows: List<List<String>>)
private const val InlineCodeFontScale = 0.85f

private fun highlightCode(code: String, colorScheme: androidx.compose.material3.ColorScheme): AnnotatedString {
    val keyword = setOf(
        "abstract", "as", "async", "await", "break", "by", "catch", "class", "companion",
        "const", "constructor", "continue", "data", "def", "do", "else", "enum", "extern",
        "false", "finally", "float", "for", "fn", "from", "fun", "if", "impl", "import",
        "in", "init", "include", "int", "interface", "internal", "is", "lambda", "lateinit",
        "let", "match", "namespace", "new", "null", "object", "open", "override", "package",
        "private", "protected", "public", "return", "sealed", "Self", "static", "string",
        "struct", "super", "suspend", "this", "throw", "trait", "true", "try", "typealias",
        "using", "val", "var", "void", "when", "while", "with", "yield",
    )
    val stringColor = colorScheme.tertiary
    val keywordColor = colorScheme.primary
    val commentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val typeColor = colorScheme.secondary

    val quotedSet = mutableSetOf<Char>()
    val builder = AnnotatedString.Builder()

    var i = 0
    val n = code.length
    while (i < n) {
        val ch = code[i]
        if (ch == '"' || ch == '\'') {
            val quote = ch
            val start = i
            i++
            while (i < n && code[i] != quote) {
                if (code[i] == '\\' && i + 1 < n) i++ // skip escaped char
                i++
            }
            i++ // closing quote (or end)
            builder.withStyle(SpanStyle(color = stringColor)) {
                append(code, start, i.coerceAtMost(n))
            }
        } else if (ch == '/' && i + 1 < n && (code[i+1] == '/' || code[i+1] == '*')) {
            val start = i
            val isBlock = code[i+1] == '*'
            i += 2
            if (isBlock) {
                while (i + 1 < n && !(code[i] == '*' && code[i+1] == '/')) i++
                i += 2
            } else {
                while (i < n && code[i] != '\n') i++
            }
            builder.withStyle(SpanStyle(color = commentColor)) {
                append(code, start, i.coerceAtMost(n))
            }
        } else if (ch == '#' || ch == '@') {
            val start = i
            i++
            while (i < n && (code[i].isLetterOrDigit() || code[i] == '_' || code[i] == '-')) i++
            builder.withStyle(SpanStyle(color = typeColor)) {
                append(code, start, i.coerceAtMost(n))
            }
        } else if (ch.isLetter() || ch == '_') {
            val start = i
            i++
            while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
            val word = code.substring(start, i)
            if (keyword.contains(word)) {
                builder.withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                    append(word)
                }
            } else {
                // Try to classify types (capitalized)
                if (word.firstOrNull()?.isUpperCase() == true) {
                    builder.withStyle(SpanStyle(color = typeColor)) { append(word) }
                } else {
                    builder.append(word)
                }
            }
        } else if (ch.isDigit()) {
            val start = i
            i++
            while (i < n && (code[i].isDigit() || code[i] == '.')) i++
            builder.withStyle(SpanStyle(color = colorScheme.tertiary)) {
                append(code, start, i.coerceAtMost(n))
            }
        } else {
            builder.append(ch)
            i++
        }
    }
    return builder.toAnnotatedString()
}

@Composable
private fun InlineMarkdownText(
    content: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    linkColor: androidx.compose.ui.graphics.Color? = null,
    prefix: String? = null,
) {
    val baseFontSize = if (style.fontSize != TextUnit.Unspecified) style.fontSize else 14.sp
    val codeStyle = SpanStyle(
        fontFamily = MonoFontFamily,
        fontSize = baseFontSize * InlineCodeFontScale,
        background = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
    val parsed = remember(content, linkColor, style, codeStyle) { parseInlineInternal(content, linkColor, codeStyle) }
    val annotated = remember(parsed, prefix) {
        if (prefix != null) {
            buildAnnotatedString {
                append(prefix)
                append(parsed)
            }
        } else {
            parsed
        }
    }
    Text(
        annotated,
        style = style,
        modifier = modifier,
    )
}

private fun parseInlineInternal(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color? = null,
    codeStyle: SpanStyle? = null,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val stack = ArrayDeque<SpanStyle>()
    var i = 0
    val n = text.length

    fun matchAt(index: Int, token: String): Boolean = text.startsWith(token, index)

    while (i < n) {
        val ch = text[i]
        when {
            matchAt(i, "```") -> {
                val end = text.indexOf("```", i + 3)
                if (end > i) {
                    val code = text.substring(i + 3, end)
                    if (code.isNotEmpty()) {
                        val style = codeStyle ?: SpanStyle(fontFamily = MonoFontFamily)
                        builder.withStyle(style) { append(code) }
                    }
                    i = end + 3
                } else {
                    builder.append(ch); i++
                }
            }
            matchAt(i, "`") -> {
                val end = text.indexOf("`", i + 1)
                if (end > i) {
                    val code = text.substring(i + 1, end)
                    if (code.isNotEmpty()) {
                        val style = codeStyle ?: SpanStyle(fontFamily = MonoFontFamily)
                        builder.withStyle(style) { append(code) }
                        i = end + 1
                    } else {
                        builder.append("`"); i++
                    }
                } else {
                    builder.append(ch); i++
                }
            }
            matchAt(i, "**") || matchAt(i, "__") -> {
                val token = if (matchAt(i, "**")) "**" else "__"
                val close = text.indexOf(token, i + 2)
                if (close > 0) {
                    stack.addLast(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.withStyle(stack.last()) {
                        appendInlineNested(text, i + 2, close, codeStyle, linkColor, stack)
                    }
                    i = close + 2
                } else {
                    builder.append(ch); i++
                }
            }
            (matchAt(i, "*") || matchAt(i, "_")) && !(i + 1 < n && (text[i + 1] == '*' || text[i + 1] == '_')) -> {
                val token = ch.toString()
                val close = text.indexOf(token, i + 1)
                if (close > i + 1) {
                    stack.addLast(SpanStyle(fontStyle = FontStyle.Italic))
                    builder.withStyle(stack.last()) {
                        appendInlineNested(text, i + 1, close, codeStyle, linkColor, stack)
                    }
                    i = close + 1
                } else {
                    builder.append(ch); i++
                }
            }
            matchAt(i, "[") -> {
                val closeBracket = text.indexOf("]", i)
                val openParen = if (closeBracket > 0) text.indexOf("(", closeBracket) else -1
                val closeParen = if (openParen > closeBracket && openParen > 0) text.indexOf(")", openParen) else -1
                if (closeBracket > 0 && openParen == closeBracket + 1 && closeParen > openParen) {
                    val linkText = text.substring(i + 1, closeBracket)
                    val color = linkColor ?: androidx.compose.ui.graphics.Color.Unspecified
                    val linkStyle = if (color != androidx.compose.ui.graphics.Color.Unspecified)
                        SpanStyle(color = color) else null
                    if (linkStyle != null) {
                        builder.withStyle(linkStyle) {
                            builder.append(linkText)
                        }
                    } else {
                        builder.append(linkText)
                    }
                    i = closeParen + 1
                } else {
                    builder.append(ch); i++
                }
            }
            else -> {
                builder.append(ch); i++
            }
        }
    }
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendInlineNested(
    text: String,
    start: Int,
    end: Int,
    codeStyle: SpanStyle?,
    linkColor: androidx.compose.ui.graphics.Color?,
    stack: ArrayDeque<SpanStyle>,
) {
    var i = start
    val n = end
    fun matchAt(index: Int, token: String): Boolean = index + token.length <= n && text.startsWith(token, index)
    while (i < n) {
        val ch = text[i]
        when {
            matchAt(i, "`") -> {
                val end2 = text.indexOf("`", i + 1)
                if (end2 > i && end2 < n) {
                    val code = text.substring(i + 1, end2)
                    if (code.isNotEmpty()) {
                        val style = codeStyle ?: SpanStyle(fontFamily = MonoFontFamily)
                        withStyle(style) { append(code) }
                        i = end2 + 1
                    } else {
                        append(ch); i++
                    }
                } else {
                    append(ch); i++
                }
            }
            matchAt(i, "**") -> {
                val token = "**"
                val close = text.indexOf(token, i + 2)
                if (close > i && close < n) {
                    stack.addLast(SpanStyle(fontWeight = FontWeight.Bold))
                    this.withStyle(stack.last()) { appendInlineNested(text, i + 2, close, codeStyle, linkColor, stack) }
                    i = close + 2
                } else {
                    append(ch); i++
                }
            }
            matchAt(i, "*") && !(i + 1 < n && text[i + 1] == '*') -> {
                val close = text.indexOf("*", i + 1)
                if (close > i && close < n) {
                    stack.addLast(SpanStyle(fontStyle = FontStyle.Italic))
                    this.withStyle(stack.last()) { appendInlineNested(text, i + 1, close, codeStyle, linkColor, stack) }
                    i = close + 1
                } else {
                    append(ch); i++
                }
            }
            matchAt(i, "[") -> {
                val closeBracket = text.indexOf("]", i)
                val openParen = if (closeBracket > 0) text.indexOf("(", closeBracket) else -1
                val closeParen = if (openParen > closeBracket && openParen > 0) text.indexOf(")", openParen) else -1
                if (closeBracket > 0 && closeBracket < n && openParen == closeBracket + 1 && closeParen > openParen && closeParen < n) {
                    val linkText = text.substring(i + 1, closeBracket)
                    val color = linkColor ?: androidx.compose.ui.graphics.Color.Unspecified
                    if (color != androidx.compose.ui.graphics.Color.Unspecified) {
                        withStyle(SpanStyle(color = color)) { append(linkText) }
                    } else {
                        append(linkText)
                    }
                    i = closeParen + 1
                } else {
                    append(ch); i++
                }
            }
            else -> {
                append(ch); i++
            }
        }
    }
}

private fun parseMarkdown(text: String): List<Any> {
    val lines = text.split("\n")
    val result = mutableListOf<Any>()
    var inCodeBlock = false
    var fenceLang = ""
    val codeBuffer = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("```") -> {
                if (inCodeBlock) {
                    result.add(MdLine("code", codeBuffer.toString().trimEnd(), lang = fenceLang))
                    codeBuffer.clear()
                    inCodeBlock = false
                    fenceLang = ""
                } else {
                    inCodeBlock = true
                    fenceLang = line.drop(3).trim().substringBefore(" ").substringBefore("\t")
                }
            }
            inCodeBlock -> {
                if (codeBuffer.isNotEmpty()) codeBuffer.append("\n")
                codeBuffer.append(line)
            }
            line.trimStart().startsWith("|") && i + 1 < lines.size && lines[i + 1].trimStart().startsWith("|") -> {
                val headers = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                i++
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
            line.matches(Regex("^\\s*[-*]\\s\\[x\\]\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val content = line.trimStart().removePrefix("- ").removePrefix("* ").removePrefix("[x] ")
                result.add(MdLine("task_checked", content, indent / 2))
            }
            line.matches(Regex("^\\s*[-*]\\s\\[ \\]\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val content = line.trimStart().removePrefix("- ").removePrefix("* ").removePrefix("[ ] ")
                result.add(MdLine("task_unchecked", content, indent / 2))
            }
            line.matches(Regex("^\\s*[-*]\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val content = line.trimStart().removePrefix("- ").removePrefix("* ")
                result.add(MdLine("bullet", content, indent / 2))
            }
            line.matches(Regex("^\\s*\\d+\\.\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val content = line.trimStart().replace(Regex("^\\d+\\.\\s"), "")
                result.add(MdLine("ordered", content, indent / 2))
            }
            line.startsWith("> ") -> result.add(MdLine("quote", line.removePrefix("> ")))
            line.matches(Regex("^\\s*[-*_]{3,}\\s*$")) -> result.add(MdLine("hr", ""))
            line.isBlank() -> result.add(MdLine("blank", ""))
            else -> result.add(MdLine("text", line))
        }
        i++
    }
    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        result.add(MdLine("code", codeBuffer.toString().trimEnd(), lang = fenceLang))
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
                    is MdLine -> {
                        val indent = (item.level * 16).dp
                        when (item.type) {
                            "h1" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.headlineLarge,
                                Modifier.padding(vertical = 8.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "h2" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.headlineMedium,
                                Modifier.padding(vertical = 6.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "h3" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.headlineSmall,
                                Modifier.padding(vertical = 4.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "h4" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.titleLarge,
                                Modifier.padding(vertical = 4.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "h5" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.titleMedium,
                                Modifier.padding(vertical = 2.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "h6" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.titleSmall,
                                Modifier.padding(vertical = 2.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "bullet" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.bodyLarge,
                                Modifier.padding(start = 16.dp + indent, top = 2.dp, bottom = 2.dp),
                                MaterialTheme.colorScheme.primary,
                                prefix = "•  ",
                            )
                            "ordered" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.bodyLarge,
                                Modifier.padding(start = 16.dp + indent, top = 2.dp, bottom = 2.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "task_checked" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.bodyLarge,
                                Modifier.padding(start = 16.dp + indent, top = 2.dp, bottom = 2.dp),
                                MaterialTheme.colorScheme.primary,
                                prefix = "☑  ",
                            )
                            "task_unchecked" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.bodyLarge,
                                Modifier.padding(start = 16.dp + indent, top = 2.dp, bottom = 2.dp),
                                MaterialTheme.colorScheme.primary,
                                prefix = "☐  ",
                            )
                            "quote" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.bodyMedium,
                                Modifier
                                    .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(8.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "code" -> CodeBlockRenderer(item.content, item.lang)
                            "hr" -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            "text" -> InlineMarkdownText(
                                item.content,
                                MaterialTheme.typography.bodyLarge,
                                Modifier.padding(vertical = 2.dp),
                                MaterialTheme.colorScheme.primary,
                            )
                            "blank" -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockRenderer(code: String, lang: String) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lang.isNotEmpty()) {
                Text(
                    lang,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            val label = if (copied) stringResource(R.string.copied) else stringResource(R.string.copy)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (copied) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(4.dp)
                    )
                    .clickable {
                        clipboardManager.setText(AnnotatedString(code))
                        copied = true
                        scope.launch {
                            delay(1500)
                            copied = false
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                highlightCode(code, MaterialTheme.colorScheme),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun TableRenderer(table: MdTable) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val colCount = (listOf(table.headers.size) + table.rows.map { it.size }).maxOrNull()?.coerceAtLeast(1) ?: 1
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val cellBg = MaterialTheme.colorScheme.surface

    // Per-column min width based on the longest cell, so wide tables scroll instead of squeezing.
    val colWidths = remember(table, colCount) {
        FloatArray(colCount) { c ->
            var maxLen = table.headers.getOrNull(c)?.length ?: 0
            for (r in table.rows) maxLen = maxOf(maxLen, r.getOrNull(c)?.length ?: 0)
            (maxLen + 4).coerceAtLeast(8) * 8f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(borderColor)
            .padding(1.dp)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(end = 8.dp)
        ) {
            Column {
                Row {
                    for ((i, header) in table.headers.withIndex()) {
                        Box(
                            modifier = Modifier
                                .width(colWidths[i].dp)
                                .background(headerBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            InlineMarkdownText(
                                header,
                                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                linkColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    repeat(colCount - table.headers.size) {
                        Box(
                            modifier = Modifier
                                .width(colWidths[(table.headers.size + it) % colCount].dp)
                                .background(headerBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                for (row in table.rows) {
                    Row {
                        for ((c, cell) in row.withIndex()) {
                            Box(
                                modifier = Modifier
                                    .width(colWidths[c].dp)
                                    .background(cellBg)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                InlineMarkdownText(
                                    cell,
                                    MaterialTheme.typography.bodyMedium,
                                    linkColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        repeat(colCount - row.size) {
                            Box(
                                modifier = Modifier
                                    .width(colWidths[(row.size + it) % colCount].dp)
                                    .background(cellBg)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
