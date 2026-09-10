package com.geno1024.markdown

sealed interface InlineMarkdown {
    val plainLength: Int

    data class Text(val text: String) : InlineMarkdown {
        override val plainLength get() = text.length
    }

    data class Code(val text: String) : InlineMarkdown {
        override val plainLength get() = text.length
    }

    data class Bold(val children: List<InlineMarkdown>) : InlineMarkdown {
        override val plainLength get() = children.sumOf { it.plainLength }
    }

    data class Italic(val children: List<InlineMarkdown>) : InlineMarkdown {
        override val plainLength get() = children.sumOf { it.plainLength }
    }

    data class Strikethrough(val children: List<InlineMarkdown>) : InlineMarkdown {
        override val plainLength get() = children.sumOf { it.plainLength }
    }

    data class Image(val alt: String) : InlineMarkdown {
        override val plainLength get() = alt.length
    }

    data class Link(val children: List<InlineMarkdown>, val url: String) : InlineMarkdown {
        override val plainLength get() = children.sumOf { it.plainLength }
    }
}

fun parseInlines(text: String): List<InlineMarkdown> = parseInlines(text, 0, text.length)

private fun parseInlines(text: String, start: Int, end: Int): List<InlineMarkdown> {
    val out = mutableListOf<InlineMarkdown>()
    val plainBuffer = StringBuilder()

    fun flushText() {
        if (plainBuffer.isNotEmpty()) {
            out += InlineMarkdown.Text(plainBuffer.toString())
            plainBuffer.clear()
        }
    }

    var i = start
    while (i < end) {
        val ch = text[i]
        when {
            text.startsWith("```", i) -> {
                val close = text.indexOf("```", i + 3)
                if (close > i && close + 3 <= end) {
                    val code = text.substring(i + 3, close)
                    if (code.isNotEmpty()) {
                        flushText()
                        out += InlineMarkdown.Code(code)
                    }
                    i = close + 3
                } else {
                    i += 3
                }
            }

            ch == '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close > i && close < end) {
                    val code = text.substring(i + 1, close)
                    if (code.isNotEmpty()) {
                        flushText()
                        out += InlineMarkdown.Code(code)
                        i = close + 1
                    } else {
                        plainBuffer.append(ch)
                        i++
                    }
                } else {
                    i++
                }
            }

            text.startsWith("~~", i) -> {
                val close = text.indexOf("~~", i + 2)
                if (close > i && close < end) {
                    flushText()
                    out += InlineMarkdown.Strikethrough(parseInlines(text, i + 2, close))
                    i = close + 2
                } else {
                    plainBuffer.append(ch)
                    i++
                }
            }

            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val token = if (text.startsWith("**", i)) "**" else "__"
                val close = text.indexOf(token, i + 2)
                if (close > i && close < end) {
                    flushText()
                    out += InlineMarkdown.Bold(parseInlines(text, i + 2, close))
                    i = close + 2
                } else {
                    plainBuffer.append(ch)
                    i++
                }
            }

            ch == '*' && !(i + 1 < end && text[i + 1] == '*') -> {
                val close = text.indexOf('*', i + 1)
                if (close > i + 1 && close < end) {
                    flushText()
                    out += InlineMarkdown.Italic(parseInlines(text, i + 1, close))
                    i = close + 1
                } else {
                    plainBuffer.append(ch)
                    i++
                }
            }

            ch == '_' && !(i + 1 < end && text[i + 1] == '_') -> {
                val close = text.indexOf('_', i + 1)
                if (close > i + 1 && close < end) {
                    flushText()
                    out += InlineMarkdown.Italic(parseInlines(text, i + 1, close))
                    i = close + 1
                } else {
                    plainBuffer.append(ch)
                    i++
                }
            }

            text.startsWith("![", i) -> {
                val closeBracket = text.indexOf("]", i + 2)
                if (closeBracket > i && closeBracket < end) {
                    val openParen = text.indexOf("(", closeBracket + 1)
                    val closeParen = if (openParen > closeBracket) text.indexOf(")", openParen) else -1
                    if (openParen == closeBracket + 1 && closeParen > openParen) {
                        val alt = text.substring(i + 2, closeBracket)
                        if (alt.isNotEmpty()) {
                            flushText()
                            out += InlineMarkdown.Image(alt)
                        }
                        i = closeParen + 1
                    } else {
                        plainBuffer.append(ch)
                        i++
                    }
                } else {
                    plainBuffer.append(ch)
                    i++
                }
            }

            ch == '[' -> {
                val closeBracket = text.indexOf("]", i)
                if (closeBracket > i && closeBracket < end) {
                    val openParen = text.indexOf("(", closeBracket)
                    val closeParen = if (openParen > closeBracket) text.indexOf(")", openParen) else -1
                    if (openParen == closeBracket + 1 && closeParen > openParen) {
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(openParen + 1, closeParen)
                        flushText()
                        out += InlineMarkdown.Link(parseInlines(label, 0, label.length), url)
                        i = closeParen + 1
                    } else {
                        plainBuffer.append(ch)
                        i++
                    }
                } else {
                    plainBuffer.append(ch)
                    i++
                }
            }

            else -> {
                plainBuffer.append(ch)
                i++
            }
        }
    }
    flushText()
    return out
}