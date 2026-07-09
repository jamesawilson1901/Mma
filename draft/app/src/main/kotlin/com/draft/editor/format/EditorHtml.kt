package com.draft.editor.format

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts between [DraftDocument] and the HTML dialect used by the
 * compose-rich-editor library (`RichTextState.setHtml`/`toHtml`).
 *
 * The editor emits: `<h1>`..`<h6>` and `<p>` blocks (inline CSS `text-align`),
 * `<ul>`/`<ol>` with `<li>` items, `<br>` for empty lines, `<b>/<i>/<u>/<s>`
 * for the basic styles, `<span style="color: rgba(..); background: rgba(..)">`
 * for colours, and `<a href="..">` for links. Its parser additionally accepts
 * `strong/em/ins/strike/del/mark` and hex colours, which this writer relies on
 * when producing HTML for import.
 */
object EditorHtml {

    // ---------------------------------------------------------------- parse

    fun parse(html: String): DraftDocument {
        val body = Jsoup.parseBodyFragment(html).body()
        val collector = BlockCollector()
        for (node in body.childNodes()) {
            parseBlockNode(node, collector)
        }
        collector.flushPending()
        val blocks = collector.blocks
        return if (blocks.isEmpty()) DraftDocument.EMPTY else DraftDocument(blocks)
    }

    private class BlockCollector {
        val blocks = mutableListOf<DocBlock>()

        private var pendingType: BlockType = BlockType.BODY
        private var pendingAlign: BlockAlign = BlockAlign.START
        private var pendingRuns = mutableListOf<TextRun>()
        private var hasPending = false

        fun begin(type: BlockType, align: BlockAlign) {
            flushPending()
            pendingType = type
            pendingAlign = align
            hasPending = true
        }

        fun addRun(text: String, style: InlineStyle) {
            if (text.isEmpty()) return
            if (!hasPending) begin(BlockType.BODY, BlockAlign.START)
            val last = pendingRuns.lastOrNull()
            if (last != null && last.style == style) {
                pendingRuns[pendingRuns.size - 1] = last.copy(text = last.text + text)
            } else {
                pendingRuns += TextRun(text, style)
            }
        }

        /** A `<br>` splits the current block into a new one of the same kind. */
        fun lineBreak() {
            if (hasPending) {
                val type = pendingType
                val align = pendingAlign
                flushPending()
                begin(type, align)
            } else {
                blocks += DocBlock()
            }
        }

        fun flushPending() {
            if (hasPending) {
                blocks += DocBlock(pendingType, pendingAlign, pendingRuns.toList())
                pendingRuns = mutableListOf()
                hasPending = false
            }
        }
    }

    private fun parseBlockNode(node: Node, out: BlockCollector) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    out.begin(BlockType.BODY, BlockAlign.START)
                    out.addRun(text, InlineStyle.PLAIN)
                    out.flushPending()
                }
            }
            is Element -> parseBlockElement(node, out)
            else -> Unit
        }
    }

    private fun parseBlockElement(element: Element, out: BlockCollector) {
        when (element.tagName().lowercase()) {
            "h1" -> parseParagraph(element, BlockType.HEADING1, out)
            "h2" -> parseParagraph(element, BlockType.HEADING2, out)
            "h3" -> parseParagraph(element, BlockType.HEADING3, out)
            // The editor supports h4-h6 but Draft's model tops out at three
            // levels; deeper headings degrade to H3 rather than being lost.
            "h4", "h5", "h6" -> parseParagraph(element, BlockType.HEADING3, out)
            "p", "div", "blockquote", "section", "article", "header", "footer", "main", "pre",
            -> parseParagraphOrContainer(element, out)
            "ul" -> parseList(element, ordered = false, out)
            "ol" -> parseList(element, ordered = true, out)
            "li" -> parseParagraph(element, BlockType.BULLET_ITEM, out)
            "br" -> out.lineBreak()
            "table" -> {
                // Graceful degradation: every table cell becomes a paragraph.
                for (cell in element.select("th, td")) {
                    parseParagraph(cell, BlockType.BODY, out)
                }
            }
            "style", "script", "head", "title" -> Unit
            else -> {
                // Unknown block-ish element (figure, aside, ...): if it holds
                // block children recurse into them, otherwise treat it as an
                // inline fragment inside a body paragraph.
                if (element.children().any { isBlockTag(it.tagName().lowercase()) }) {
                    for (child in element.childNodes()) parseBlockNode(child, out)
                } else {
                    parseParagraph(element, BlockType.BODY, out)
                }
            }
        }
    }

    private fun isBlockTag(tag: String): Boolean = tag in setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li",
        "table", "blockquote", "section", "article", "header", "footer", "main", "pre",
    )

    private fun parseParagraphOrContainer(element: Element, out: BlockCollector) {
        if (element.children().any { isBlockTag(it.tagName().lowercase()) }) {
            // e.g. a div wrapping paragraphs, or a p somehow nesting a list.
            for (child in element.childNodes()) parseBlockNode(child, out)
        } else {
            parseParagraph(element, BlockType.BODY, out)
        }
    }

    private fun parseList(element: Element, ordered: Boolean, out: BlockCollector) {
        val itemType = if (ordered) BlockType.NUMBER_ITEM else BlockType.BULLET_ITEM
        for (child in element.children()) {
            when (child.tagName().lowercase()) {
                "li" -> {
                    val nestedLists = child.children().filter {
                        it.tagName().lowercase() in setOf("ul", "ol")
                    }
                    parseParagraph(child, itemType, out, skip = nestedLists.toSet())
                    // Nested lists flatten to consecutive items of their own kind.
                    for (nested in nestedLists) {
                        parseList(nested, nested.tagName().lowercase() == "ol", out)
                    }
                }
                "ul" -> parseList(child, ordered = false, out)
                "ol" -> parseList(child, ordered = true, out)
                else -> parseParagraph(child, itemType, out)
            }
        }
    }

    private fun parseParagraph(
        element: Element,
        type: BlockType,
        out: BlockCollector,
        skip: Set<Element> = emptySet(),
    ) {
        val align = parseAlign(element.attr("style"))
        out.begin(type, align)
        val baseStyle = styleFromCss(InlineStyle.PLAIN, element.attr("style"))
        for (child in element.childNodes()) {
            parseInlineNode(child, baseStyle, out, skip)
        }
        out.flushPending()
    }

    private fun parseInlineNode(
        node: Node,
        style: InlineStyle,
        out: BlockCollector,
        skip: Set<Element>,
    ) {
        when (node) {
            is TextNode -> out.addRun(node.text(), style)
            is Element -> {
                if (node in skip) return
                val tag = node.tagName().lowercase()
                if (tag == "br") {
                    out.lineBreak()
                    return
                }
                var next = when (tag) {
                    "b", "strong" -> style.copy(bold = true)
                    "i", "em" -> style.copy(italic = true)
                    "u", "ins" -> style.copy(underline = true)
                    "s", "strike", "del" -> style.copy(strikethrough = true)
                    "mark" -> style.copy(highlight = style.highlight ?: "#FFFF00")
                    "a" -> {
                        val href = node.attr("href").trim()
                        if (href.isNotEmpty()) style.copy(linkUrl = href) else style
                    }
                    else -> style
                }
                next = styleFromCss(next, node.attr("style"))
                for (child in node.childNodes()) {
                    parseInlineNode(child, next, out, skip)
                }
            }
            else -> Unit
        }
    }

    private fun parseCssDeclarations(css: String): Map<String, String> {
        if (css.isBlank()) return emptyMap()
        return css.split(';')
            .mapNotNull { declaration ->
                val idx = declaration.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                val key = declaration.substring(0, idx).trim().lowercase()
                val value = declaration.substring(idx + 1).trim()
                if (key.isEmpty() || value.isEmpty()) null else key to value
            }
            .toMap()
    }

    private fun styleFromCss(base: InlineStyle, css: String): InlineStyle {
        if (css.isBlank()) return base
        var style = base
        val map = parseCssDeclarations(css)
        map["color"]?.let { normalizeCssColor(it)?.let { c -> style = style.copy(color = c) } }
        (map["background"] ?: map["background-color"])?.let {
            normalizeCssColor(it)?.let { c -> style = style.copy(highlight = c) }
        }
        map["font-weight"]?.let {
            val v = it.lowercase()
            if (v == "bold" || v == "bolder" || (v.toIntOrNull() ?: 0) >= 600) {
                style = style.copy(bold = true)
            }
        }
        map["font-style"]?.let {
            if (it.lowercase().startsWith("italic") || it.lowercase().startsWith("oblique")) {
                style = style.copy(italic = true)
            }
        }
        map["text-decoration"]?.let {
            val v = it.lowercase()
            if ("underline" in v) style = style.copy(underline = true)
            if ("line-through" in v) style = style.copy(strikethrough = true)
        }
        return style
    }

    private fun parseAlign(css: String): BlockAlign =
        when (parseCssDeclarations(css)["text-align"]?.lowercase()) {
            "center" -> BlockAlign.CENTER
            "right", "end" -> BlockAlign.END
            else -> BlockAlign.START
        }

    // ---------------------------------------------------------------- write

    fun write(document: DraftDocument): String {
        val sb = StringBuilder()
        var openList: String? = null // "ul" | "ol" | null

        fun closeList() {
            openList?.let { sb.append("</$it>") }
            openList = null
        }

        for (block in document.blocks) {
            val listTag = when (block.type) {
                BlockType.BULLET_ITEM -> "ul"
                BlockType.NUMBER_ITEM -> "ol"
                else -> null
            }
            if (listTag != openList) {
                closeList()
                if (listTag != null) {
                    sb.append("<$listTag>")
                    openList = listTag
                }
            }

            if (listTag == null && block.runs.all { it.text.isEmpty() }) {
                // The editor round-trips empty lines as bare <br>.
                sb.append("<br>")
                continue
            }

            val tag = when (block.type) {
                BlockType.HEADING1 -> "h1"
                BlockType.HEADING2 -> "h2"
                BlockType.HEADING3 -> "h3"
                BlockType.BULLET_ITEM, BlockType.NUMBER_ITEM -> "li"
                BlockType.BODY -> "p"
            }
            val styleAttr = when (block.align) {
                BlockAlign.CENTER -> " style=\"text-align: center;\""
                BlockAlign.END -> " style=\"text-align: right;\""
                BlockAlign.START -> ""
            }
            sb.append("<").append(tag).append(styleAttr).append(">")
            for (run in block.runs) {
                appendRun(sb, run)
            }
            sb.append("</").append(tag).append(">")
        }
        closeList()
        return sb.toString()
    }

    private fun appendRun(sb: StringBuilder, run: TextRun) {
        if (run.text.isEmpty()) return
        val style = run.style
        val open = StringBuilder()
        val close = ArrayDeque<String>()

        style.linkUrl?.let {
            open.append("<a href=\"").append(escapeAttribute(it)).append("\">")
            close.addFirst("</a>")
        }
        val css = buildList {
            style.color?.let { add("color: $it") }
            style.highlight?.let { add("background: $it") }
        }
        if (css.isNotEmpty()) {
            open.append("<span style=\"").append(css.joinToString("; ")).append(";\">")
            close.addFirst("</span>")
        }
        if (style.bold) { open.append("<b>"); close.addFirst("</b>") }
        if (style.italic) { open.append("<i>"); close.addFirst("</i>") }
        if (style.underline) { open.append("<u>"); close.addFirst("</u>") }
        if (style.strikethrough) { open.append("<s>"); close.addFirst("</s>") }

        sb.append(open)
        sb.append(escapeText(run.text))
        close.forEach { sb.append(it) }
    }

    private fun escapeText(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                ' ' -> append("&nbsp;")
                else -> append(ch)
            }
        }
    }

    private fun escapeAttribute(text: String): String =
        escapeText(text).replace("\"", "&quot;")
}
