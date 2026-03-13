package com.github.thiagokokada.meowprinter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownSnippetFormatterTest {
    @Test
    fun boldWrapsSelectedText() {
        val result = MarkdownSnippetFormatter.bold("Hello world", 6, 11)

        assertEquals("Hello **world**", result.text)
        assertEquals(8, result.selectionStart)
        assertEquals(13, result.selectionEnd)
    }

    @Test
    fun italicInsertsPlaceholderWhenNothingIsSelected() {
        val result = MarkdownSnippetFormatter.italic("", 0, 0)

        assertEquals("_italic_", result.text)
        assertEquals(1, result.selectionStart)
        assertEquals(7, result.selectionEnd)
    }

    @Test
    fun tableInsertsSkeletonAndSelectsFirstHeader() {
        val result = MarkdownSnippetFormatter.table("", 0, 0)

        assertEquals("| Column 1 | Column 2 |\n| --- | --- |\n| Value 1 | Value 2 |", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(10, result.selectionEnd)
    }

    @Test
    fun headingInsertsAtCurrentLineStart() {
        val result = MarkdownSnippetFormatter.heading2("Line one\nLine two", 9, 9)

        assertEquals("Line one\n## Line two", result.text)
        assertEquals(12, result.selectionStart)
        assertEquals(12, result.selectionEnd)
    }
}
