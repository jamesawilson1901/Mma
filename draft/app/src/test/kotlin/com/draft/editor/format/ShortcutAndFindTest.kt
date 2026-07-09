package com.draft.editor.format

import com.draft.editor.format.MarkdownShortcuts.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutAndFindTest {

    // ------------------------------------------------------ block shortcuts

    @Test
    fun `block markers convert at line start`() {
        assertEquals(Block.HEADING1, MarkdownShortcuts.detectBlock("# "))
        assertEquals(Block.HEADING2, MarkdownShortcuts.detectBlock("## "))
        assertEquals(Block.HEADING3, MarkdownShortcuts.detectBlock("### "))
        assertEquals(Block.BULLET, MarkdownShortcuts.detectBlock("- "))
        assertEquals(Block.BULLET, MarkdownShortcuts.detectBlock("* "))
        assertEquals(Block.NUMBERED, MarkdownShortcuts.detectBlock("1. "))
    }

    @Test
    fun `block markers do not fire mid sentence or with extra text`() {
        assertNull(MarkdownShortcuts.detectBlock("x# "))
        assertNull(MarkdownShortcuts.detectBlock("#"))
        assertNull(MarkdownShortcuts.detectBlock("#### "))
        assertNull(MarkdownShortcuts.detectBlock("2. "))
        assertNull(MarkdownShortcuts.detectBlock("hello # "))
    }

    // ------------------------------------------------------ inline emphasis

    @Test
    fun `double asterisks close bold`() {
        val m = MarkdownShortcuts.detectEmphasis("note **important**")!!
        assertEquals(5, m.openStart)
        assertEquals(7, m.contentStart)
        assertEquals(16, m.contentEnd)
        assertEquals(18, m.closeEnd)
        assertEquals(true, m.bold)
    }

    @Test
    fun `single asterisks close italic`() {
        val m = MarkdownShortcuts.detectEmphasis("a *word*")!!
        assertEquals(2, m.openStart)
        assertEquals(3, m.contentStart)
        assertEquals(7, m.contentEnd)
        assertEquals(8, m.closeEnd)
        assertEquals(false, m.bold)
    }

    @Test
    fun `emphasis does not fire on empty or spaced content`() {
        assertNull(MarkdownShortcuts.detectEmphasis("****"))
        assertNull(MarkdownShortcuts.detectEmphasis("** x**"))
        assertNull(MarkdownShortcuts.detectEmphasis("*   *"))
        assertNull(MarkdownShortcuts.detectEmphasis("2 * 3 * "))
        assertNull(MarkdownShortcuts.detectEmphasis("plain text"))
    }

    @Test
    fun `bold wins over italic when both could match`() {
        val m = MarkdownShortcuts.detectEmphasis("**bold**")!!
        assertEquals(true, m.bold)
    }

    // ------------------------------------------------------ find engine

    @Test
    fun `find all is case insensitive and counts matches`() {
        val matches = FindEngine.findAll("The cat sat on the CAT mat", "cat")
        assertEquals(2, matches.size)
        assertEquals(FindEngine.Match(4, 7), matches[0])
        assertEquals(FindEngine.Match(19, 22), matches[1])
    }

    @Test
    fun `next match wraps around`() {
        val matches = FindEngine.findAll("a b a b a", "a")
        assertEquals(0, FindEngine.nextMatchIndex(matches, 0))
        assertEquals(1, FindEngine.nextMatchIndex(matches, 1))
        assertEquals(2, FindEngine.nextMatchIndex(matches, 5))
        assertEquals(0, FindEngine.nextMatchIndex(matches, 9))
    }

    @Test
    fun `empty query yields no matches`() {
        assertEquals(0, FindEngine.findAll("anything", "").size)
        assertEquals(-1, FindEngine.nextMatchIndex(emptyList(), 0))
    }

    // ------------------------------------------------------ word count

    @Test
    fun `word count stats`() {
        val stats = WordCount.of("Hello brave new world.\n\nSecond paragraph here.")
        assertEquals(7, stats.words)
        assertEquals(46, stats.characters)
        assertEquals(2, stats.paragraphs)
        assertEquals(1, stats.readingMinutes)
    }

    @Test
    fun `empty text counts zero`() {
        val stats = WordCount.of("")
        assertEquals(0, stats.words)
        assertEquals(0, stats.characters)
        assertEquals(0, stats.paragraphs)
        assertEquals(0, stats.readingMinutes)
    }

    // ------------------------------------------------------ colours

    @Test
    fun `css colour normalisation`() {
        assertEquals("#D32F2F", normalizeCssColor("rgba(211, 47, 47, 1.0)"))
        assertEquals("#D32F2F", normalizeCssColor("rgb(211,47,47)"))
        assertEquals("#AABBCC", normalizeCssColor("#aabbcc"))
        assertEquals("#AABBCC", normalizeCssColor("abc"))
        assertEquals("#FF0000", normalizeCssColor("red"))
        assertNull(normalizeCssColor("bogus"))
    }
}
