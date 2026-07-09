package com.draft.editor.format

/**
 * Draft's neutral document model: the meeting point between the editor's HTML,
 * the .draft container, and the OOXML (.docx) subset. Pure Kotlin, no Android
 * or Compose types, so the whole format layer runs (and is tested) on the JVM.
 */

enum class BlockType {
    BODY,
    HEADING1,
    HEADING2,
    HEADING3,
    BULLET_ITEM,
    NUMBER_ITEM,
}

enum class BlockAlign { START, CENTER, END }

data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    /** Text colour as "#RRGGBB", or null for the theme default. */
    val color: String? = null,
    /** Highlight (background) colour as "#RRGGBB", or null for none. */
    val highlight: String? = null,
    val linkUrl: String? = null,
) {
    val isPlain: Boolean
        get() = !bold && !italic && !underline && !strikethrough &&
            color == null && highlight == null && linkUrl == null

    companion object {
        val PLAIN = InlineStyle()
    }
}

data class TextRun(
    val text: String,
    val style: InlineStyle = InlineStyle.PLAIN,
)

data class DocBlock(
    val type: BlockType = BlockType.BODY,
    val align: BlockAlign = BlockAlign.START,
    val runs: List<TextRun> = emptyList(),
) {
    val plainText: String get() = runs.joinToString(separator = "") { it.text }

    val headingLevel: Int?
        get() = when (type) {
            BlockType.HEADING1 -> 1
            BlockType.HEADING2 -> 2
            BlockType.HEADING3 -> 3
            else -> null
        }
}

data class DraftDocument(val blocks: List<DocBlock>) {
    val plainText: String get() = blocks.joinToString(separator = "\n") { it.plainText }

    companion object {
        val EMPTY = DraftDocument(listOf(DocBlock()))
    }
}

/** An entry in the live outline: a heading and where it starts in the plain text. */
data class OutlineItem(
    val level: Int,
    val text: String,
    val charOffset: Int,
)

/**
 * Builds the outline from a document. [charOffset] indexes into the document's
 * plain text where paragraphs are joined with a single '\n', matching how the
 * editor lays out its text, so the offset can be used to move the cursor.
 */
fun buildOutline(document: DraftDocument): List<OutlineItem> {
    val items = mutableListOf<OutlineItem>()
    var offset = 0
    for ((index, block) in document.blocks.withIndex()) {
        if (index > 0) offset += 1 // the '\n' separator
        val level = block.headingLevel
        val text = block.plainText
        if (level != null && text.isNotBlank()) {
            items += OutlineItem(level = level, text = text.trim(), charOffset = offset)
        }
        offset += text.length
    }
    return items
}

/** Normalises CSS colour notations to "#RRGGBB", or null if unparseable. */
fun normalizeCssColor(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    val rgba = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*[\d.]+\s*)?\)""")
        .find(value)
    if (rgba != null) {
        val (r, g, b) = rgba.destructured
        val ri = r.toIntOrNull()?.coerceIn(0, 255) ?: return null
        val gi = g.toIntOrNull()?.coerceIn(0, 255) ?: return null
        val bi = b.toIntOrNull()?.coerceIn(0, 255) ?: return null
        return "#%02X%02X%02X".format(ri, gi, bi)
    }

    val hex = Regex("""#?([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})""").matchEntire(value)?.groupValues?.get(1)
    if (hex != null) {
        val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
        return "#${full.uppercase()}"
    }

    val named = mapOf(
        "black" to "#000000", "white" to "#FFFFFF", "red" to "#FF0000",
        "green" to "#008000", "blue" to "#0000FF", "yellow" to "#FFFF00",
        "cyan" to "#00FFFF", "magenta" to "#FF00FF", "gray" to "#808080",
        "grey" to "#808080", "orange" to "#FFA500", "purple" to "#800080",
    )
    return named[value.lowercase()]
}
