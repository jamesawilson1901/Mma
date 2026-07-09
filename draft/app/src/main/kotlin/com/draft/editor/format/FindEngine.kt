package com.draft.editor.format

/** Plain-text search used by Find & Replace. */
object FindEngine {

    data class Match(val start: Int, val end: Int)

    fun findAll(text: String, query: String, ignoreCase: Boolean = true): List<Match> {
        if (query.isEmpty() || text.isEmpty()) return emptyList()
        val matches = mutableListOf<Match>()
        var index = text.indexOf(query, startIndex = 0, ignoreCase = ignoreCase)
        while (index >= 0) {
            matches += Match(index, index + query.length)
            index = text.indexOf(query, startIndex = index + query.length, ignoreCase = ignoreCase)
        }
        return matches
    }

    /** Index of the first match at or after [fromIndex], wrapping around. */
    fun nextMatchIndex(matches: List<Match>, fromIndex: Int): Int {
        if (matches.isEmpty()) return -1
        val next = matches.indexOfFirst { it.start >= fromIndex }
        return if (next >= 0) next else 0
    }
}

/** Live word/character statistics for the top bar and the details dialog. */
object WordCount {

    data class Stats(
        val words: Int,
        val characters: Int,
        val charactersNoSpaces: Int,
        val paragraphs: Int,
        val readingMinutes: Int,
    )

    private val WHITESPACE = Regex("""\s+""")

    fun of(text: String): Stats {
        val trimmed = text.trim()
        val words = if (trimmed.isEmpty()) 0 else trimmed.split(WHITESPACE).size
        val characters = text.length
        val charactersNoSpaces = text.count { !it.isWhitespace() }
        val paragraphs = text.split('\n').count { it.isNotBlank() }
        val readingMinutes = if (words == 0) 0 else ((words + 199) / 200)
        return Stats(words, characters, charactersNoSpaces, paragraphs, readingMinutes)
    }
}
