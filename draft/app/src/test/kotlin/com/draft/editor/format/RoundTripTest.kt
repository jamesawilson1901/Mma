package com.draft.editor.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HTML -> DocModel -> docx -> DocModel -> HTML round trips of the supported
 * subset: paragraphs, headings 1-3, bold/italic/underline/strikethrough,
 * text colour, highlight, bullet + numbered lists, alignment, hyperlinks.
 */
class RoundTripTest {

    private fun roundTrip(document: DraftDocument): DraftDocument =
        DocxReader.read(DocxWriter.write(document))

    @Test
    fun `plain paragraphs survive docx round trip`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(runs = listOf(TextRun("Hello world."))),
                DocBlock(runs = listOf(TextRun("Second paragraph."))),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun `headings survive docx round trip`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(BlockType.HEADING1, runs = listOf(TextRun("Title"))),
                DocBlock(BlockType.HEADING2, runs = listOf(TextRun("Section"))),
                DocBlock(BlockType.HEADING3, runs = listOf(TextRun("Subsection"))),
                DocBlock(runs = listOf(TextRun("Body text"))),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun `inline styles survive docx round trip`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(
                    runs = listOf(
                        TextRun("bold", InlineStyle(bold = true)),
                        TextRun(" and "),
                        TextRun("italic", InlineStyle(italic = true)),
                        TextRun(" and "),
                        TextRun("underline", InlineStyle(underline = true)),
                        TextRun(" and "),
                        TextRun("struck", InlineStyle(strikethrough = true)),
                        TextRun(" and "),
                        TextRun("all", InlineStyle(bold = true, italic = true, underline = true, strikethrough = true)),
                    ),
                ),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun `colours survive docx round trip`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(
                    runs = listOf(
                        TextRun("red text", InlineStyle(color = "#D32F2F")),
                        TextRun(" plain "),
                        TextRun("highlighted", InlineStyle(highlight = "#FFF176")),
                        TextRun(" both ", InlineStyle(color = "#1976D2", highlight = "#B9F6CA")),
                    ),
                ),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun `lists survive docx round trip`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(BlockType.BULLET_ITEM, runs = listOf(TextRun("First bullet"))),
                DocBlock(BlockType.BULLET_ITEM, runs = listOf(TextRun("Second bullet"))),
                DocBlock(runs = listOf(TextRun("Interlude"))),
                DocBlock(BlockType.NUMBER_ITEM, runs = listOf(TextRun("Step one"))),
                DocBlock(BlockType.NUMBER_ITEM, runs = listOf(TextRun("Step two"))),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun `alignment survives docx round trip`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(align = BlockAlign.CENTER, runs = listOf(TextRun("Centered"))),
                DocBlock(align = BlockAlign.END, runs = listOf(TextRun("Right"))),
                DocBlock(align = BlockAlign.START, runs = listOf(TextRun("Left"))),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun `hyperlinks survive docx round trip`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(
                    runs = listOf(
                        TextRun("Visit "),
                        TextRun("the site", InlineStyle(linkUrl = "https://example.com/a?b=1&c=2")),
                        TextRun(" today."),
                    ),
                ),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun `xml specials survive docx round trip`() {
        val doc = DraftDocument(
            listOf(
                DocBlock(runs = listOf(TextRun("a < b && \"c\" > 'd'"))),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun `empty document round trips to empty`() {
        assertEquals(DraftDocument.EMPTY, roundTrip(DraftDocument.EMPTY))
    }

    @Test
    fun `full pipeline html to docx and back to html`() {
        val html = "<h1>Title</h1><p>Some <b>bold</b> and <i>italic</i> text.</p>" +
            "<ul><li>One</li><li>Two</li></ul>" +
            "<p style=\"text-align: center;\">Centered <u>underlined</u></p>"
        val doc = EditorHtml.parse(html)
        val restored = DocxReader.read(DocxWriter.write(doc))
        assertEquals(doc, restored)
        assertEquals(html, EditorHtml.write(restored))
    }

    // ------------------------------------------------------- degradation

    @Test
    fun `docx with a table degrades to paragraphs without crashing`() {
        // Hand-built docx containing a 2x1 table between two paragraphs.
        val tableDocx = buildDocxWithBody(
            """
            <w:p><w:r><w:t>Before</w:t></w:r></w:p>
            <w:tbl>
              <w:tr>
                <w:tc><w:p><w:r><w:t>Cell A</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>Cell B</w:t></w:r></w:p></w:tc>
              </w:tr>
            </w:tbl>
            <w:p><w:r><w:t>After</w:t></w:r></w:p>
            """.trimIndent(),
        )
        val doc = DocxReader.read(tableDocx)
        assertEquals(listOf("Before", "Cell A", "Cell B", "After"), doc.blocks.map { it.plainText })
        assertTrue(doc.blocks.all { it.type == BlockType.BODY })
    }

    @Test
    fun `docx with drawings and unknown elements never crashes`() {
        val weirdDocx = buildDocxWithBody(
            """
            <w:p><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"/></w:drawing><w:t>Text next to image</w:t></w:r></w:p>
            <w:p><w:r><w:t>Normal</w:t></w:r></w:p>
            """.trimIndent(),
        )
        val doc = DocxReader.read(weirdDocx)
        assertEquals(listOf("Text next to image", "Normal"), doc.blocks.map { it.plainText })
    }

    @Test
    fun `garbage bytes yield an empty document instead of crashing`() {
        assertEquals(DraftDocument.EMPTY, DocxReader.read(ByteArray(64) { it.toByte() }))
        assertEquals(DraftDocument.EMPTY, DocxReader.read("not a zip at all".toByteArray()))
        assertEquals(DraftDocument.EMPTY, DocxReader.read(ByteArray(0)))
    }

    @Test
    fun `docx produced here is recognised as docx`() {
        val bytes = DocxWriter.write(DraftDocument.EMPTY)
        assertTrue(DraftFile.looksLikeDocx(bytes))
        assertNull(DraftFile.read(bytes)?.html?.takeIf { it.isNotEmpty() })
    }

    private fun buildDocxWithBody(bodyXml: String): ByteArray {
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>$bodyXml</w:body></w:document>"""
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zip.write(
                """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>"""
                    .toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
