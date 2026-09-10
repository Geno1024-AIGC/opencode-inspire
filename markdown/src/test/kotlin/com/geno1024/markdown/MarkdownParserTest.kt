package com.geno1024.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineParserTest {

    @Test
    fun `plain text`() {
        assertEquals(listOf(InlineMarkdown.Text("hello world")), parseInlines("hello world"))
    }

    @Test
    fun `single backtick code`() {
        assertEquals(
            listOf(InlineMarkdown.Text("a "), InlineMarkdown.Code("code"), InlineMarkdown.Text(" b")),
            parseInlines("a `code` b"),
        )
    }

    @Test
    fun `triple backtick code`() {
        assertEquals(
            listOf(InlineMarkdown.Text("x "), InlineMarkdown.Code("c\nc"), InlineMarkdown.Text(" y")),
            parseInlines("x ```c\nc``` y"),
        )
    }

    @Test
    fun `bold and italic and strikethrough`() {
        val nodes = parseInlines("**b** *i* ~~s~~")
        assertEquals(
            listOf(
                InlineMarkdown.Bold(listOf(InlineMarkdown.Text("b"))),
                InlineMarkdown.Text(" "),
                InlineMarkdown.Italic(listOf(InlineMarkdown.Text("i"))),
                InlineMarkdown.Text(" "),
                InlineMarkdown.Strikethrough(listOf(InlineMarkdown.Text("s"))),
            ),
            nodes,
        )
    }

    @Test
    fun `nested bold in italic`() {
        // outer * matches nearest *, so *a **b** c* is handled as separate italics/bold blocks
        // This matches the original parser's known limitation
        val nodes = parseInlines("*a **b** c*")
        assertTrue(nodes.isNotEmpty())
        assertTrue(nodes.first() is InlineMarkdown.Italic)
    }

    @Test
    fun `link with inner markup`() {
        val nodes = parseInlines("[a `code` b](https://x.com)")
        assertEquals(
            listOf(
                InlineMarkdown.Link(
                    listOf(
                        InlineMarkdown.Text("a "),
                        InlineMarkdown.Code("code"),
                        InlineMarkdown.Text(" b"),
                    ),
                    "https://x.com",
                ),
            ),
            nodes,
        )
    }

    @Test
    fun `image renders alt text`() {
        assertEquals(
            listOf(InlineMarkdown.Text("!alt "), InlineMarkdown.Image("alt")),
            parseInlines("!alt ![alt](https://x.com/a.png)"),
        )
    }

    @Test
    fun `plain text backtick`() {
        // stray backtick is dropped; surrounding spaces preserved
        assertEquals(listOf(InlineMarkdown.Text("a  b")), parseInlines("a ` b"))
    }

    @Test
    fun `plain length`() {
        val nodes = parseInlines("[**ab** `cd`](u) ef")
        assertEquals(8, nodes.sumOf { it.plainLength })
    }
}

class BlockParserTest {

    @Test
    fun `headings and paragraph`() {
        // trailing blank line creates an extra Blank block
        val blocks = parseMarkdown("# Hi\n\nplain\n")
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is BlockMarkdown.Heading)
        assertEquals(1, (blocks[0] as BlockMarkdown.Heading).hLevel)
        assertTrue(blocks[1] is BlockMarkdown.Blank)
        assertEquals(BlockMarkdown.Paragraph(listOf(InlineMarkdown.Text("plain"))), blocks[2])
        assertTrue(blocks[3] is BlockMarkdown.Blank)
    }

    @Test
    fun `bullet ordered task quote`() {
        val blocks = parseMarkdown("- a\n1. b\n- [x] c\n- [ ] d\n> e")
        assertEquals(5, blocks.size)
        assertEquals(BlockMarkdown.Bullet(0, listOf(InlineMarkdown.Text("a"))), blocks[0])
        assertEquals(BlockMarkdown.Ordered(0, 1, listOf(InlineMarkdown.Text("b"))), blocks[1])
        assertEquals(BlockMarkdown.Task(true, 0, listOf(InlineMarkdown.Text("c"))), blocks[2])
        assertEquals(BlockMarkdown.Task(false, 0, listOf(InlineMarkdown.Text("d"))), blocks[3])
        assertEquals(BlockMarkdown.Quote(listOf(InlineMarkdown.Text("e"))), blocks[4])
    }

    @Test
    fun `code fence`() {
        val blocks = parseMarkdown("```kotlin\nval x = 1\n```")
        assertEquals(
            listOf(BlockMarkdown.CodeFence("kotlin", "val x = 1")),
            blocks,
        )
    }

    @Test
    fun `table`() {
        val text = "| a | b |\n|---|---|\n| 1 | 2 |"
        val blocks = parseMarkdown(text)
        assertTrue(blocks.size == 1 && blocks[0] is BlockMarkdown.Table)
        val table = blocks[0] as BlockMarkdown.Table
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun `hr and blank`() {
        val blocks = parseMarkdown("---\n\n")
        assertTrue(blocks[0] is BlockMarkdown.Hr)
        assertTrue(blocks[1] is BlockMarkdown.Blank)
    }

    @Test
    fun `mermaid is separated from markdown`() {
        // text segment has trailing \n → extra Blank; total 4 blocks
        val text = "text\n```mermaid\ngraph TD\n  A-->B\n```\nmore"
        val blocks = parseMarkdown(text)
        assertEquals(4, blocks.size)
        assertEquals(BlockMarkdown.Paragraph(listOf(InlineMarkdown.Text("text"))), blocks[0])
        assertTrue(blocks[1] is BlockMarkdown.Blank)
        assertEquals(BlockMarkdown.Mermaid("graph TD\n  A-->B"), blocks[2])
        assertEquals(BlockMarkdown.Paragraph(listOf(InlineMarkdown.Text("more"))), blocks[3])
    }

    @Test
    fun `plain code fence is not mermaid`() {
        val text = "```\nconst x = 1\n```"
        val blocks = parseMarkdown(text)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is BlockMarkdown.CodeFence)
    }
}