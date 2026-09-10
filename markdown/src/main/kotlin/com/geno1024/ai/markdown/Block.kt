package com.geno1024.ai.markdown

sealed interface BlockMarkdown {
    val level: Int

    data class Heading(val hLevel: Int, val content: List<InlineMarkdown>) : BlockMarkdown {
        override val level get() = 0
    }

    data class Paragraph(val content: List<InlineMarkdown>) : BlockMarkdown {
        override val level get() = 0
    }

    data class Bullet(val indent: Int, val content: List<InlineMarkdown>) : BlockMarkdown {
        override val level get() = indent
    }

    data class Ordered(val indent: Int, val num: Int, val content: List<InlineMarkdown>) : BlockMarkdown {
        override val level get() = indent
    }

    data class Task(val checked: Boolean, val indent: Int, val content: List<InlineMarkdown>) : BlockMarkdown {
        override val level get() = indent
    }

    data class Quote(val content: List<InlineMarkdown>) : BlockMarkdown {
        override val level get() = 0
    }

    data class CodeFence(val lang: String, val code: String) : BlockMarkdown {
        override val level get() = 0
    }

    data class Table(val headers: List<List<InlineMarkdown>>, val rows: List<List<List<InlineMarkdown>>>) : BlockMarkdown {
        override val level get() = 0
    }

    data class Mermaid(val source: String) : BlockMarkdown {
        override val level get() = 0
    }

    data object Hr : BlockMarkdown {
        override val level get() = 0
    }

    data object Blank : BlockMarkdown {
        override val level get() = 0
    }
}

fun parseMarkdown(text: String): List<BlockMarkdown> {
    val regex = Regex("(?<![`])```\\s*mermaid\\s*\\n(.*?)```\\s*\\n?", RegexOption.DOT_MATCHES_ALL)
    val parts = mutableListOf<Pair<Boolean, String>>()
    var cursor = 0
    for (m in regex.findAll(text)) {
        if (m.range.first > cursor) parts += false to text.substring(cursor, m.range.first)
        parts += true to m.groupValues[1].trim()
        cursor = m.range.last + 1
    }
    if (cursor < text.length) parts += false to text.substring(cursor)

    val blocks = mutableListOf<BlockMarkdown>()
    for (part in parts) {
        if (part.first) {
            blocks += BlockMarkdown.Mermaid(part.second)
        } else {
            blocks += parseMarkdownSegment(part.second)
        }
    }
    return blocks.ifEmpty { parseMarkdownSegment(text) }
}

private fun parseMarkdownSegment(text: String): List<BlockMarkdown> {
    val lines = text.split("\n")
    val result = mutableListOf<BlockMarkdown>()
    var inCodeBlock = false
    var fenceLang = ""
    val codeBuffer = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("```") -> {
                if (inCodeBlock) {
                    result.add(BlockMarkdown.CodeFence(fenceLang, codeBuffer.toString().trimEnd()))
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
                val headers = parseRow(line)
                i++
                i++
                val rows = mutableListOf<List<List<InlineMarkdown>>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows.add(parseRow(lines[i]))
                    i++
                }
                result.add(BlockMarkdown.Table(headers, rows))
                continue
            }

            line.startsWith("# ") -> result.add(BlockMarkdown.Heading(1, parseInlines(line.removePrefix("# "))))
            line.startsWith("## ") -> result.add(BlockMarkdown.Heading(2, parseInlines(line.removePrefix("## "))))
            line.startsWith("### ") -> result.add(BlockMarkdown.Heading(3, parseInlines(line.removePrefix("### "))))
            line.startsWith("#### ") -> result.add(BlockMarkdown.Heading(4, parseInlines(line.removePrefix("#### "))))
            line.startsWith("##### ") -> result.add(BlockMarkdown.Heading(5, parseInlines(line.removePrefix("##### "))))
            line.startsWith("###### ") -> result.add(BlockMarkdown.Heading(6, parseInlines(line.removePrefix("###### "))))

            line.matches(Regex("^\\s*[-*]\\s\\[x\\]\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val content = line.trimStart().removePrefix("- ").removePrefix("* ").removePrefix("[x] ")
                result.add(BlockMarkdown.Task(true, indent / 2, parseInlines(content)))
            }

            line.matches(Regex("^\\s*[-*]\\s\\[ \\]\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val content = line.trimStart().removePrefix("- ").removePrefix("* ").removePrefix("[ ] ")
                result.add(BlockMarkdown.Task(false, indent / 2, parseInlines(content)))
            }

            line.matches(Regex("^\\s*[-*]\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val content = line.trimStart().removePrefix("- ").removePrefix("* ")
                result.add(BlockMarkdown.Bullet(indent / 2, parseInlines(content)))
            }

            line.matches(Regex("^\\s*\\d+\\.\\s.*")) -> {
                val indent = line.length - line.trimStart().length
                val num = Regex("^\\d+").find(line.trimStart())?.value?.toIntOrNull() ?: 0
                val content = line.trimStart().replace(Regex("^\\d+\\.\\s+"), "")
                result.add(BlockMarkdown.Ordered(indent / 2, num, parseInlines(content)))
            }

            line.startsWith("> ") -> result.add(BlockMarkdown.Quote(parseInlines(line.removePrefix("> "))))
            line.matches(Regex("^\\s*[-*_]{3,}\\s*$")) -> result.add(BlockMarkdown.Hr)
            line.isBlank() -> result.add(BlockMarkdown.Blank)
            else -> result.add(BlockMarkdown.Paragraph(parseInlines(line)))
        }
        i++
    }
    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        result.add(BlockMarkdown.CodeFence(fenceLang, codeBuffer.toString().trimEnd()))
    }
    return result
}

private fun parseRow(line: String): List<List<InlineMarkdown>> =
    line.split("|").map { it.trim() }.filter { it.isNotEmpty() }.map { parseInlines(it) }