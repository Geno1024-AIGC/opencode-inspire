package com.geno1024.ai.occ.ui

import com.geno1024.ai.occ.R
import com.geno1024.markdown.BlockMarkdown
import com.geno1024.markdown.InlineMarkdown
import com.geno1024.markdown.parseInlines
import com.geno1024.markdown.parseMarkdown
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                if (code[i] == '\\' && i + 1 < n) i++
                i++
            }
            i++
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

private fun AnnotatedString.Builder.appendInline(
    nodes: List<InlineMarkdown>,
    linkColor: androidx.compose.ui.graphics.Color?,
    codeStyle: SpanStyle?,
) {
    for (node in nodes) {
        when (node) {
            is InlineMarkdown.Text -> append(node.text)
            is InlineMarkdown.Code -> {
                val style = codeStyle ?: SpanStyle(fontFamily = MonoFontFamily)
                withStyle(style) { append(node.text) }
            }
            is InlineMarkdown.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInline(node.children, linkColor, codeStyle)
            }
            is InlineMarkdown.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInline(node.children, linkColor, codeStyle)
            }
            is InlineMarkdown.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInline(node.children, linkColor, codeStyle)
            }
            is InlineMarkdown.Link -> {
                val start = length
                appendInline(node.children, linkColor, codeStyle)
                if (node.url.isNotEmpty()) {
                    val color = linkColor ?: androidx.compose.ui.graphics.Color.Unspecified
                    val linkStyle = if (color != androidx.compose.ui.graphics.Color.Unspecified)
                        SpanStyle(color = color) else null
                    addLink(LinkAnnotation.Url(node.url, TextLinkStyles(style = linkStyle)), start, length)
                }
            }
            is InlineMarkdown.Image -> append(node.alt)
        }
    }
}

@Composable
private fun InlineMarkdownNodes(
    nodes: List<InlineMarkdown>,
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
    val annotated = remember(nodes, prefix) {
        buildAnnotatedString {
            if (prefix != null) append(prefix)
            appendInline(nodes, linkColor, codeStyle)
        }
    }
    Text(
        annotated,
        style = style,
        modifier = modifier,
    )
}

private fun mermaidHtml(source: String, theme: String): String {
    val escaped = source
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
    return """
        <!DOCTYPE html>
        <html><head>
        <meta charset="utf-8">
        <script src="file:///android_asset/mermaid/mermaid.min.js"></script>
        <style>
          html, body { margin:0; padding:0; background:transparent; }
          #c { display:inline-block; padding:8px; }
          #e { color:#999; font-family:sans-serif; white-space:pre-wrap; }
        </style>
        </head><body>
        <div id="c"></div><div id="e"></div>
        <script>
          mermaid.initialize({ startOnLoad:false, theme:"$theme", fontFamily:"sans-serif", securityLevel:"loose" });
          var src = "$escaped";
          mermaid.render("mermaidSvg", src).then(function(res){
            document.getElementById("c").innerHTML = res.svg;
            if (window.MermaidTo) window.MermaidTo.onResize(document.body.scrollWidth, document.body.scrollHeight);
          }).catch(function(e){
            document.getElementById("e").textContent = src;
            if (window.MermaidTo) window.MermaidTo.onResize(document.body.scrollWidth, document.body.scrollHeight);
          });
        </script>
        </body></html>
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidBlock(source: String) {
    val density = LocalDensity.current
    val isLight = LocalContentColor.current.luminance() > 0.5f
    val themeName = if (isLight) "default" else "dark"
    val size = remember(source) { mutableStateOf(IntSize.Zero) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val html = remember(source, themeName) { mermaidHtml(source, themeName) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(0x00000000)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                addJavascriptInterface(
                    object {
                        @SuppressLint("JavascriptInterface")
                        @JavascriptInterface
                        fun onResize(w: Int, h: Int) {
                            mainHandler.post { size.value = IntSize(w, h) }
                        }
                    },
                    "MermaidTo",
                )
                webViewClient = WebViewClient()
                loadDataWithBaseURL("file:///android_asset/mermaid/", html, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (size.value.height > 0) {
                    Modifier.height(with(density) { size.value.height.toDp() })
                } else {
                    Modifier
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                },
            ),
    )
}

@Composable
fun MarkdownMessage(content: String) {
    val blocks = remember(content) { parseMarkdown(content) }
    // Split blocks into runs: each run is either a list of non-mermaid blocks,
    // or a single mermaid block.
    val runs = remember(blocks) {
        val result = mutableListOf<List<BlockMarkdown>>()
        var current = mutableListOf<BlockMarkdown>()
        for (b in blocks) {
            if (b is BlockMarkdown.Mermaid) {
                if (current.isNotEmpty()) {
                    result += current
                    current = mutableListOf()
                }
                result += listOf(b)
            } else {
                current += b
            }
        }
        if (current.isNotEmpty()) result += current
        result
    }
    Column(Modifier.fillMaxWidth()) {
        for (run in runs) {
            if (run.size == 1 && run[0] is BlockMarkdown.Mermaid) {
                MermaidBlock((run[0] as BlockMarkdown.Mermaid).source)
            } else {
                SelectionContainer {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (b in run) BlockContent(b)
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownMessageCustom(content: String) {
    val blocks = remember(content) { parseMarkdown(content) }
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            for (b in blocks) BlockContent(b)
        }
    }
}

@Composable
private fun BlockContent(block: BlockMarkdown) {
    when (block) {
        is BlockMarkdown.Heading -> {
            val (style, padding) = when (block.hLevel) {
                1 -> MaterialTheme.typography.headlineLarge to 8.dp
                2 -> MaterialTheme.typography.headlineMedium to 6.dp
                3 -> MaterialTheme.typography.headlineSmall to 4.dp
                4 -> MaterialTheme.typography.titleLarge to 4.dp
                5 -> MaterialTheme.typography.titleMedium to 2.dp
                6 -> MaterialTheme.typography.titleSmall to 2.dp
                else -> MaterialTheme.typography.bodyLarge to 2.dp
            }
            InlineMarkdownNodes(
                nodes = block.content,
                style = style,
                modifier = Modifier.padding(vertical = padding),
                linkColor = MaterialTheme.colorScheme.primary,
            )
        }
        is BlockMarkdown.Paragraph -> InlineMarkdownNodes(
            nodes = block.content,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 2.dp),
            linkColor = MaterialTheme.colorScheme.primary,
        )
        is BlockMarkdown.Bullet -> InlineMarkdownNodes(
            nodes = block.content,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp + (block.level * 16).dp, top = 2.dp, bottom = 2.dp),
            linkColor = MaterialTheme.colorScheme.primary,
            prefix = "•  ",
        )
        is BlockMarkdown.Ordered -> InlineMarkdownNodes(
            nodes = block.content,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp + (block.level * 16).dp, top = 2.dp, bottom = 2.dp),
            linkColor = MaterialTheme.colorScheme.primary,
            prefix = "${block.num}.  ",
        )
        is BlockMarkdown.Task -> InlineMarkdownNodes(
            nodes = block.content,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp + (block.level * 16).dp, top = 2.dp, bottom = 2.dp),
            linkColor = MaterialTheme.colorScheme.primary,
            prefix = if (block.checked) "☑  " else "☐  ",
        )
        is BlockMarkdown.Quote -> InlineMarkdownNodes(
            nodes = block.content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(4.dp)
                )
                .padding(8.dp),
            linkColor = MaterialTheme.colorScheme.primary,
        )
        is BlockMarkdown.CodeFence -> CodeBlockRenderer(block.code, block.lang)
        is BlockMarkdown.Table -> TableRenderer(block)
        is BlockMarkdown.Hr -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        is BlockMarkdown.Blank -> Spacer(Modifier.height(6.dp))
        is BlockMarkdown.Mermaid -> MermaidBlock(block.source)
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
private fun TableRenderer(table: BlockMarkdown.Table) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val colCount = (listOf(table.headers.size) + table.rows.map { it.size }).maxOrNull()?.coerceAtLeast(1) ?: 1
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val cellBg = MaterialTheme.colorScheme.surface

    val colWidths = remember(table, colCount) {
        FloatArray(colCount) { c ->
            var maxLen = table.headers.getOrNull(c)?.sumOf { it.plainLength } ?: 0
            for (r in table.rows) maxLen = maxOf(maxLen, r.getOrNull(c)?.sumOf { it.plainLength } ?: 0)
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
                            InlineMarkdownNodes(
                                nodes = header,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
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
                                InlineMarkdownNodes(
                                    nodes = cell,
                                    style = MaterialTheme.typography.bodyMedium,
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
