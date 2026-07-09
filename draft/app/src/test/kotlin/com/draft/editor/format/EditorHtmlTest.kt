package com.draft.editor.format

import org.junit.Assert.assertEquals
import org.junit.Test

/** Parsing and writing of the rich-editor HTML dialect. */
class EditorHtmlTest {

    @Test
    fun `parses editor output with rgba colours`() {
        // This is the shape RichTextState.toHtml() emits.
        val html = "<p><span style=\"color: rgba(211, 47, 47, 1.0);\">red</span>" +
            "<span style=\"background: rgba(255, 241, 118, 1.0);\">hi</span></p>"
        val doc = EditorHtml.parse(html)
        assertEquals(1, doc.blocks.size)
        assertEquals(
            listOf(
                TextRun("red", InlineStyle(color = "#D32F2F")),
                TextRun("hi", InlineStyle(highlight = "#FFF176")),
            ),
            doc.blocks[0].runs,
        )
    }

    @Test
    fun `parses headings lists links and alignment`() {
        val html = "<h1>Big</h1><h2>Mid</h2><h3>Small</h3>" +
            "<ul><li>a</li><li>b</li></ul><ol><li>1</li></ol>" +
            "<p style=\"text-align: right;\"><a href=\"https://x.y\" target=\"_blank\">link</a></p>"
        val doc = EditorHtml.parse(html)
        assertEquals(
            listOf(
                BlockType.HEADING1, BlockType.HEADING2, BlockType.HEADING3,
                BlockType.BULLET_ITEM, BlockType.BULLET_ITEM, BlockType.NUMBER_ITEM,
                BlockType.BODY,
            ),
            doc.blocks.map { it.type },
        )
        assertEquals(BlockAlign.END, doc.blocks.last().align)
        assertEquals(InlineStyle(linkUrl = "https://x.y"), doc.blocks.last().runs.single().style)
    }

    @Test
    fun `parses strong em ins del mark synonyms`() {
        val doc = EditorHtml.parse(
            "<p><strong>b</strong><em>i</em><ins>u</ins><del>s</del><mark>m</mark></p>",
        )
        assertEquals(
            listOf(
                TextRun("b", InlineStyle(bold = true)),
                TextRun("i", InlineStyle(italic = true)),
                TextRun("u", InlineStyle(underline = true)),
                TextRun("s", InlineStyle(strikethrough = true)),
                TextRun("m", InlineStyle(highlight = "#FFFF00")),
            ),
            doc.blocks.single().runs,
        )
    }

    @Test
    fun `bare br becomes an empty paragraph and h4 to h6 degrade to h3`() {
        val doc = EditorHtml.parse("<p>a</p><br><h4>deep</h4>")
        assertEquals(3, doc.blocks.size)
        assertEquals("", doc.blocks[1].plainText)
        assertEquals(BlockType.HEADING3, doc.blocks[2].type)
    }

    @Test
    fun `br inside a paragraph splits the block`() {
        val doc = EditorHtml.parse("<p>line one<br>line two</p>")
        assertEquals(listOf("line one", "line two"), doc.blocks.map { it.plainText })
    }

    @Test
    fun `html tables degrade to plain paragraphs`() {
        val doc = EditorHtml.parse("<table><tr><td>x</td><td>y</td></tr></table>")
        assertEquals(listOf("x", "y"), doc.blocks.map { it.plainText })
    }

    @Test
    fun `write emits the editor dialect exactly`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(BlockType.HEADING1, runs = listOf(TextRun("T"))),
                DocBlock(
                    align = BlockAlign.CENTER,
                    runs = listOf(
                        TextRun("a "),
                        TextRun("b", InlineStyle(bold = true, italic = true)),
                        TextRun("c", InlineStyle(color = "#112233")),
                        TextRun("d", InlineStyle(linkUrl = "https://e.f")),
                    ),
                ),
                DocBlock(BlockType.BULLET_ITEM, runs = listOf(TextRun("item"))),
            ),
        )
        assertEquals(
            "<h1>T</h1>" +
                "<p style=\"text-align: center;\">a <b><i>b</i></b>" +
                "<span style=\"color: #112233;\">c</span>" +
                "<a href=\"https://e.f\">d</a></p>" +
                "<ul><li>item</li></ul>",
            EditorHtml.write(doc),
        )
    }

    @Test
    fun `write then parse is identity for the supported subset`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(BlockType.HEADING2, BlockAlign.CENTER, listOf(TextRun("Head"))),
                DocBlock(
                    runs = listOf(
                        TextRun("mix ", InlineStyle(bold = true)),
                        TextRun("of", InlineStyle(italic = true, underline = true)),
                        TextRun(" styles", InlineStyle(strikethrough = true, color = "#00FF00", highlight = "#FFFF00")),
                    ),
                ),
                DocBlock(BlockType.NUMBER_ITEM, runs = listOf(TextRun("one"))),
                DocBlock(BlockType.NUMBER_ITEM, runs = listOf(TextRun("two"))),
                DocBlock(runs = emptyList()),
                DocBlock(runs = listOf(TextRun("tail & <end>"))),
            ),
        )
        assertEquals(doc, EditorHtml.parse(EditorHtml.write(doc)))
    }

    @Test
    fun `outline uses plain text offsets with newline separators`() {
        val doc = EditorHtml.parse("<h1>One</h1><p>body</p><h2>Two</h2>")
        val outline = buildOutline(doc)
        assertEquals(2, outline.size)
        assertEquals(OutlineItem(1, "One", 0), outline[0])
        // "One\nbody\n" = 9 chars before "Two"
        assertEquals(OutlineItem(2, "Two", 9), outline[1])
    }

    @Test
    fun `draft container round trips html and meta`() {
        val meta = DraftFile.Meta(title = "My Doc", createdAt = 123L, modifiedAt = 456L)
        val html = "<p>content &amp; more</p>"
        val bytes = DraftFile.write(html, meta)
        val contents = DraftFile.read(bytes)!!
        assertEquals(html, contents.html)
        assertEquals(meta, contents.meta)
    }

    @Test
    fun `draft reader tolerates missing meta and rejects non zips`() {
        assertEquals(null, DraftFile.read("plain text".toByteArray()))
    }
}
