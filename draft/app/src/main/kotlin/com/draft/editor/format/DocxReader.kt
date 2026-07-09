package com.draft.editor.format

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Reads the supported subset out of a .docx package. Anything it does not
 * understand degrades to plain paragraphs (tables become one paragraph per
 * cell, images and other drawings are dropped) — it never refuses a file.
 * A file that is not a readable docx at all yields a single empty document
 * rather than an exception.
 */
object DocxReader {

    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    /** w:highlight uses a fixed set of named colours. */
    private val HIGHLIGHT_NAMES = mapOf(
        "yellow" to "#FFFF00", "green" to "#00FF00", "cyan" to "#00FFFF",
        "magenta" to "#FF00FF", "blue" to "#0000FF", "red" to "#FF0000",
        "darkBlue" to "#00008B", "darkCyan" to "#008B8B", "darkGreen" to "#006400",
        "darkMagenta" to "#8B008B", "darkRed" to "#8B0000", "darkYellow" to "#808000",
        "darkGray" to "#A9A9A9", "lightGray" to "#D3D3D3",
        "black" to "#000000", "white" to "#FFFFFF",
    )

    fun read(bytes: ByteArray): DraftDocument = try {
        readOrThrow(bytes)
    } catch (_: Exception) {
        DraftDocument.EMPTY
    }

    private fun readOrThrow(bytes: ByteArray): DraftDocument {
        val parts = readZipParts(
            bytes,
            wanted = setOf(
                "word/document.xml",
                "word/_rels/document.xml.rels",
                "word/numbering.xml",
                "word/styles.xml",
            ),
        )
        val documentXml = parts["word/document.xml"] ?: return DraftDocument.EMPTY

        val doc = parseXml(documentXml)
        val rels = parts["word/_rels/document.xml.rels"]
            ?.let { runCatching { parseRels(parseXml(it)) }.getOrDefault(emptyMap()) }
            ?: emptyMap()
        val bulletNumIds = parts["word/numbering.xml"]
            ?.let { runCatching { parseBulletNumIds(parseXml(it)) }.getOrNull() }
        val headingStyleIds = parts["word/styles.xml"]
            ?.let { runCatching { parseHeadingStyleIds(parseXml(it)) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

        val body = doc.documentElement.firstChildElement(W_NS, "body") ?: return DraftDocument.EMPTY
        val blocks = mutableListOf<DocBlock>()
        collectBlocks(body, rels, bulletNumIds, headingStyleIds, blocks)
        if (blocks.isEmpty()) return DraftDocument.EMPTY
        return DraftDocument(blocks)
    }

    // ------------------------------------------------------------- zip + xml

    private fun readZipParts(bytes: ByteArray, wanted: Set<String>): Map<String, ByteArray> {
        val found = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.removePrefix("/")
                if (name in wanted) {
                    found[name] = zip.readBytes()
                    if (found.size == wanted.size) break
                }
                entry = zip.nextEntry
            }
        }
        return found
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Defense against XXE / external entity fetches in hostile files.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun parseRels(doc: Document): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val relationships = doc.getElementsByTagNameNS("*", "Relationship")
        for (i in 0 until relationships.length) {
            val el = relationships.item(i) as Element
            val id = el.getAttribute("Id")
            val target = el.getAttribute("Target")
            if (id.isNotEmpty() && target.isNotEmpty()) map[id] = target
        }
        return map
    }

    /** Returns the set of numId values whose level 0 is a bullet format. */
    private fun parseBulletNumIds(doc: Document): Set<String> {
        val abstractBullet = mutableSetOf<String>()
        val abstractNums = doc.getElementsByTagNameNS(W_NS, "abstractNum")
        for (i in 0 until abstractNums.length) {
            val el = abstractNums.item(i) as Element
            val id = el.getAttributeNS(W_NS, "abstractNumId")
            val lvl0 = el.childElements(W_NS, "lvl").firstOrNull {
                it.getAttributeNS(W_NS, "ilvl") == "0"
            } ?: el.firstChildElement(W_NS, "lvl")
            val fmt = lvl0?.firstChildElement(W_NS, "numFmt")?.getAttributeNS(W_NS, "val")
            if (fmt == "bullet") abstractBullet += id
        }
        val bulletNumIds = mutableSetOf<String>()
        val nums = doc.getElementsByTagNameNS(W_NS, "num")
        for (i in 0 until nums.length) {
            val el = nums.item(i) as Element
            val numId = el.getAttributeNS(W_NS, "numId")
            val abstractId = el.firstChildElement(W_NS, "abstractNumId")
                ?.getAttributeNS(W_NS, "val")
            if (numId.isNotEmpty() && abstractId in abstractBullet) bulletNumIds += numId
        }
        return bulletNumIds
    }

    /** Maps style ids to heading level (1..3) using style names and outline levels. */
    private fun parseHeadingStyleIds(doc: Document): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val styles = doc.getElementsByTagNameNS(W_NS, "style")
        for (i in 0 until styles.length) {
            val el = styles.item(i) as Element
            val styleId = el.getAttributeNS(W_NS, "styleId")
            if (styleId.isEmpty()) continue
            val name = el.firstChildElement(W_NS, "name")?.getAttributeNS(W_NS, "val").orEmpty()
            val outline = el.firstChildElement(W_NS, "pPr")
                ?.firstChildElement(W_NS, "outlineLvl")
                ?.getAttributeNS(W_NS, "val")?.toIntOrNull()
            val level = headingLevelFromName(styleId)
                ?: headingLevelFromName(name)
                ?: outline?.plus(1)
            if (level != null && level in 1..9) map[styleId] = level.coerceAtMost(3)
        }
        return map
    }

    private fun headingLevelFromName(name: String): Int? {
        val match = Regex("""heading\s*(\d)""", RegexOption.IGNORE_CASE).find(name) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    // ------------------------------------------------------------- blocks

    private fun collectBlocks(
        parent: Element,
        rels: Map<String, String>,
        bulletNumIds: Set<String>?,
        headingStyleIds: Map<String, Int>,
        out: MutableList<DocBlock>,
    ) {
        for (child in parent.childElementsAll()) {
            when {
                child.namespaceURI == W_NS && child.localName == "p" ->
                    out += parseParagraphs(child, rels, bulletNumIds, headingStyleIds)
                // Tables, structured document tags, and anything else that can
                // contain paragraphs: flatten by recursing.
                child.namespaceURI == W_NS && child.localName in
                    setOf("tbl", "tr", "tc", "sdt", "sdtContent", "smartTag") ->
                    collectBlocks(child, rels, bulletNumIds, headingStyleIds, out)
                child.namespaceURI == W_NS && child.localName == "sectPr" -> Unit
                else -> Unit
            }
        }
    }

    /** A single w:p can yield several blocks when it contains w:br page-ish breaks. */
    private fun parseParagraphs(
        p: Element,
        rels: Map<String, String>,
        bulletNumIds: Set<String>?,
        headingStyleIds: Map<String, Int>,
    ): List<DocBlock> {
        val pPr = p.firstChildElement(W_NS, "pPr")

        var type = BlockType.BODY
        var align = BlockAlign.START
        if (pPr != null) {
            val styleId = pPr.firstChildElement(W_NS, "pStyle")?.getAttributeNS(W_NS, "val")
            val headingLevel = styleId?.let { headingStyleIds[it] ?: headingLevelFromName(it) }
            val numPr = pPr.firstChildElement(W_NS, "numPr")
            val outline = pPr.firstChildElement(W_NS, "outlineLvl")
                ?.getAttributeNS(W_NS, "val")?.toIntOrNull()

            type = when {
                numPr != null -> {
                    val numId = numPr.firstChildElement(W_NS, "numId")
                        ?.getAttributeNS(W_NS, "val")
                    when {
                        bulletNumIds == null -> BlockType.BULLET_ITEM
                        numId != null && numId in bulletNumIds -> BlockType.BULLET_ITEM
                        else -> BlockType.NUMBER_ITEM
                    }
                }
                headingLevel != null -> headingType(headingLevel)
                outline != null && outline in 0..2 -> headingType(outline + 1)
                else -> BlockType.BODY
            }
            align = when (pPr.firstChildElement(W_NS, "jc")?.getAttributeNS(W_NS, "val")) {
                "center" -> BlockAlign.CENTER
                "right", "end" -> BlockAlign.END
                else -> BlockAlign.START
            }
        }

        val blocks = mutableListOf<DocBlock>()
        var runs = mutableListOf<TextRun>()

        fun flush() {
            blocks += DocBlock(type, align, mergeRuns(runs))
            runs = mutableListOf()
        }

        fun addText(text: String, style: InlineStyle) {
            if (text.isNotEmpty()) runs += TextRun(text, style)
        }

        fun walkRuns(parent: Element, linkUrl: String?) {
            for (child in parent.childElementsAll()) {
                if (child.namespaceURI != W_NS) continue
                when (child.localName) {
                    "r" -> {
                        val style = parseRunStyle(child, linkUrl)
                        for (piece in child.childElementsAll()) {
                            if (piece.namespaceURI != W_NS) continue
                            when (piece.localName) {
                                "t" -> addText(piece.textContent.orEmpty(), style)
                                "br", "cr" -> flush()
                                "tab" -> addText("\t", style)
                                else -> Unit
                            }
                        }
                    }
                    "hyperlink" -> {
                        val relId = child.getAttributeNS(R_NS, "id")
                        val anchor = child.getAttributeNS(W_NS, "anchor")
                        val url = rels[relId]
                            ?: anchor.takeIf { it.isNotEmpty() }?.let { "#$it" }
                        walkRuns(child, url ?: linkUrl)
                    }
                    "smartTag", "ins", "sdt", "sdtContent" -> walkRuns(child, linkUrl)
                    else -> Unit
                }
            }
        }

        walkRuns(p, null)
        flush()
        return blocks
    }

    private fun headingType(level: Int): BlockType = when (level) {
        1 -> BlockType.HEADING1
        2 -> BlockType.HEADING2
        else -> BlockType.HEADING3
    }

    private fun parseRunStyle(r: Element, linkUrl: String?): InlineStyle {
        val rPr = r.firstChildElement(W_NS, "rPr")
            ?: return InlineStyle(linkUrl = linkUrl)

        fun toggle(name: String): Boolean {
            val el = rPr.firstChildElement(W_NS, name) ?: return false
            val value = el.getAttributeNS(W_NS, "val")
            return value !in setOf("false", "0", "none")
        }

        val colorRaw = rPr.firstChildElement(W_NS, "color")?.getAttributeNS(W_NS, "val")
        val color = colorRaw?.takeIf { it.isNotEmpty() && !it.equals("auto", ignoreCase = true) }
            ?.let { normalizeCssColor(it) }

        val highlightNamed = rPr.firstChildElement(W_NS, "highlight")
            ?.getAttributeNS(W_NS, "val")
            ?.let { HIGHLIGHT_NAMES[it] ?: HIGHLIGHT_NAMES[it.lowercase()] }
        val shdFill = rPr.firstChildElement(W_NS, "shd")?.getAttributeNS(W_NS, "fill")
            ?.takeIf { it.isNotEmpty() && !it.equals("auto", ignoreCase = true) }
            ?.let { normalizeCssColor(it) }

        return InlineStyle(
            bold = toggle("b"),
            italic = toggle("i"),
            underline = toggle("u"),
            strikethrough = toggle("strike"),
            color = color,
            highlight = highlightNamed ?: shdFill,
            linkUrl = linkUrl,
        )
    }

    private fun mergeRuns(runs: List<TextRun>): List<TextRun> {
        val merged = mutableListOf<TextRun>()
        for (run in runs) {
            val last = merged.lastOrNull()
            if (last != null && last.style == run.style) {
                merged[merged.size - 1] = last.copy(text = last.text + run.text)
            } else {
                merged += run
            }
        }
        return merged
    }

    // ------------------------------------------------------------- DOM utils

    private fun Element.childElementsAll(): List<Element> {
        val result = mutableListOf<Element>()
        var node: Node? = firstChild
        while (node != null) {
            if (node is Element) result += node
            node = node.nextSibling
        }
        return result
    }

    private fun Element.childElements(ns: String, local: String): List<Element> =
        childElementsAll().filter { it.namespaceURI == ns && it.localName == local }

    private fun Element.firstChildElement(ns: String, local: String): Element? =
        childElementsAll().firstOrNull { it.namespaceURI == ns && it.localName == local }
}
