package com.draft.editor.format

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a [DraftDocument] as a minimal-but-valid OOXML word processing
 * package (.docx). Pure Kotlin: a .docx is just a zip of XML parts.
 *
 * Emitted parts: [Content_Types].xml, _rels/.rels, word/document.xml,
 * word/_rels/document.xml.rels, word/styles.xml, word/numbering.xml.
 *
 * Supported subset: paragraphs, headings 1-3, bold/italic/underline/strike,
 * text colour, highlight (as run shading, arbitrary hex), bullet + numbered
 * lists, alignment, hyperlinks.
 */
object DocxWriter {

    fun write(document: DraftDocument): ByteArray {
        val hyperlinks = mutableListOf<String>() // index -> url; rel id = rId{100+index}
        val documentXml = buildDocumentXml(document, hyperlinks)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putEntry("[Content_Types].xml", CONTENT_TYPES_XML)
            zip.putEntry("_rels/.rels", ROOT_RELS_XML)
            zip.putEntry("word/document.xml", documentXml)
            zip.putEntry("word/_rels/document.xml.rels", buildDocumentRels(hyperlinks))
            zip.putEntry("word/styles.xml", STYLES_XML)
            zip.putEntry("word/numbering.xml", NUMBERING_XML)
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun buildDocumentXml(document: DraftDocument, hyperlinks: MutableList<String>): String {
        val body = StringBuilder()
        for (block in document.blocks) {
            body.append(buildParagraphXml(block, hyperlinks))
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body>
</w:document>"""
    }

    private fun buildParagraphXml(block: DocBlock, hyperlinks: MutableList<String>): String {
        val pPr = StringBuilder()

        when (block.type) {
            BlockType.HEADING1 -> pPr.append("""<w:pStyle w:val="Heading1"/>""")
            BlockType.HEADING2 -> pPr.append("""<w:pStyle w:val="Heading2"/>""")
            BlockType.HEADING3 -> pPr.append("""<w:pStyle w:val="Heading3"/>""")
            BlockType.BULLET_ITEM ->
                pPr.append("""<w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr>""")
            BlockType.NUMBER_ITEM ->
                pPr.append("""<w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr>""")
            BlockType.BODY -> Unit
        }
        when (block.align) {
            BlockAlign.CENTER -> pPr.append("""<w:jc w:val="center"/>""")
            BlockAlign.END -> pPr.append("""<w:jc w:val="right"/>""")
            BlockAlign.START -> Unit
        }

        val content = StringBuilder()
        var i = 0
        val runs = block.runs
        while (i < runs.size) {
            val run = runs[i]
            val url = run.style.linkUrl
            if (url != null) {
                // Group consecutive runs sharing the same link into one w:hyperlink.
                var j = i
                while (j < runs.size && runs[j].style.linkUrl == url) j++
                val relId = hyperlinkRelId(hyperlinks, url)
                content.append("""<w:hyperlink r:id="$relId">""")
                for (k in i until j) content.append(buildRunXml(runs[k], inHyperlink = true))
                content.append("</w:hyperlink>")
                i = j
            } else {
                content.append(buildRunXml(run, inHyperlink = false))
                i++
            }
        }

        val pPrXml = if (pPr.isEmpty()) "" else "<w:pPr>$pPr</w:pPr>"
        return "<w:p>$pPrXml$content</w:p>"
    }

    private fun hyperlinkRelId(hyperlinks: MutableList<String>, url: String): String {
        val existing = hyperlinks.indexOf(url)
        val index = if (existing >= 0) existing else hyperlinks.also { it.add(url) }.lastIndex
        return "rId${100 + index}"
    }

    private fun buildRunXml(run: TextRun, inHyperlink: Boolean): String {
        if (run.text.isEmpty()) return ""
        val style = run.style
        val rPr = StringBuilder()
        if (inHyperlink) rPr.append("""<w:rStyle w:val="Hyperlink"/>""")
        if (style.bold) rPr.append("<w:b/>")
        if (style.italic) rPr.append("<w:i/>")
        if (style.strikethrough) rPr.append("<w:strike/>")
        if (style.underline) rPr.append("""<w:u w:val="single"/>""")
        style.color?.let { rPr.append("""<w:color w:val="${it.removePrefix("#")}"/>""") }
        style.highlight?.let {
            rPr.append("""<w:shd w:val="clear" w:color="auto" w:fill="${it.removePrefix("#")}"/>""")
        }
        val rPrXml = if (rPr.isEmpty()) "" else "<w:rPr>$rPr</w:rPr>"

        val text = StringBuilder()
        // Word has no notion of a soft line break inside w:t; split on \n.
        val parts = run.text.split('\n')
        for ((index, part) in parts.withIndex()) {
            if (index > 0) text.append("<w:br/>")
            if (part.isNotEmpty()) {
                text.append("""<w:t xml:space="preserve">${escapeXml(part)}</w:t>""")
            }
        }
        return "<w:r>$rPrXml$text</w:r>"
    }

    private fun buildDocumentRels(hyperlinks: List<String>): String {
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""",
        )
        for ((index, url) in hyperlinks.withIndex()) {
            sb.append(
                """
<Relationship Id="rId${100 + index}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${escapeXmlAttribute(url)}" TargetMode="External"/>""",
            )
        }
        sb.append("\n</Relationships>")
        return sb.toString()
    }

    internal fun escapeXml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }

    internal fun escapeXmlAttribute(text: String): String =
        escapeXml(text).replace("\"", "&quot;").replace("'", "&apos;")

    private val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
</Types>"""

    private val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault/></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:outlineLvl w:val="0"/><w:spacing w:before="360" w:after="120"/></w:pPr><w:rPr><w:b/><w:sz w:val="44"/><w:szCs w:val="44"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:outlineLvl w:val="1"/><w:spacing w:before="280" w:after="100"/></w:pPr><w:rPr><w:b/><w:sz w:val="34"/><w:szCs w:val="34"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:outlineLvl w:val="2"/><w:spacing w:before="240" w:after="80"/></w:pPr><w:rPr><w:b/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr></w:style>
<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/><w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/></w:style>
</w:styles>"""

    private val NUMBERING_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="singleLevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#8226;"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum>
<w:abstractNum w:abstractNumId="1"><w:multiLevelType w:val="singleLevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
<w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
</w:numbering>"""
}
