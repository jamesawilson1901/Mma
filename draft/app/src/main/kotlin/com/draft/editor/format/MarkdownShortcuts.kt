package com.draft.editor.format

/**
 * Pure detection logic for markdown speed shortcuts. The editor screen feeds
 * these functions the current line and cursor position after every text
 * change; they only decide, they never mutate.
 */
object MarkdownShortcuts {

    enum class Block { HEADING1, HEADING2, HEADING3, BULLET, NUMBERED }

    /**
     * Detects a block shortcut when [linePrefix] is the full text of the line
     * from its start up to the cursor, ending in the space the user just
     * typed. Returns null unless the prefix is exactly the marker.
     */
    fun detectBlock(linePrefix: String): Block? = when (linePrefix) {
        "# " -> Block.HEADING1
        "## " -> Block.HEADING2
        "### " -> Block.HEADING3
        "- ", "* " -> Block.BULLET
        "1. ", "1) " -> Block.NUMBERED
        else -> null
    }

    /**
     * An inline emphasis match inside a line. Offsets are relative to the
     * start of the line. [contentStart, contentEnd) is the emphasised text,
     * [openStart, closeEnd) is the whole marked-up region including markers.
     */
    data class Emphasis(
        val openStart: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val closeEnd: Int,
        val bold: Boolean,
    )

    private val BOLD = Regex("""\*\*([^\s*](?:[^*]*[^\s*])?)\*\*$""")
    private val ITALIC = Regex("""(?<![*\w])\*([^\s*](?:[^*]*[^\s*])?)\*$""")

    /**
     * Detects `**bold**` or `*italic*` immediately before the cursor, i.e.
     * right after the closing marker was typed. [lineToCursor] is the line
     * text from line start up to the cursor.
     */
    fun detectEmphasis(lineToCursor: String): Emphasis? {
        BOLD.find(lineToCursor)?.let { match ->
            val start = match.range.first
            val contentLength = match.groupValues[1].length
            return Emphasis(
                openStart = start,
                contentStart = start + 2,
                contentEnd = start + 2 + contentLength,
                closeEnd = match.range.last + 1,
                bold = true,
            )
        }
        ITALIC.find(lineToCursor)?.let { match ->
            val start = match.range.first
            val contentLength = match.groupValues[1].length
            return Emphasis(
                openStart = start,
                contentStart = start + 1,
                contentEnd = start + 1 + contentLength,
                closeEnd = match.range.last + 1,
                bold = false,
            )
        }
        return null
    }
}
