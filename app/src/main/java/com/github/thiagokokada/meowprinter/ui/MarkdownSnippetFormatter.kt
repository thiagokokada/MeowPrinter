package com.github.thiagokokada.meowprinter.ui

data class MarkdownEditResult(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

object MarkdownSnippetFormatter {
    fun heading1(text: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult {
        return insertInlineSnippet(text, selectionStart, selectionEnd, "# ")
    }

    fun heading2(text: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult {
        return insertInlineSnippet(text, selectionStart, selectionEnd, "## ")
    }

    fun bold(text: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult {
        return wrapSelection(text, selectionStart, selectionEnd, "**", "**", "bold")
    }

    fun italic(text: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult {
        return wrapSelection(text, selectionStart, selectionEnd, "_", "_", "italic")
    }

    fun bulletList(text: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult {
        return insertInlineSnippet(text, selectionStart, selectionEnd, "- ")
    }

    fun blockquote(text: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult {
        return insertInlineSnippet(text, selectionStart, selectionEnd, "> ")
    }

    fun table(text: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult {
        val snippet = "| Column 1 | Column 2 |\n| --- | --- |\n| Value 1 | Value 2 |"
        return insertSnippet(text, selectionStart, selectionEnd, snippet, 2, 10)
    }

    private fun wrapSelection(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        prefix: String,
        suffix: String,
        placeholder: String
    ): MarkdownEditResult {
        val safeStart = selectionStart.coerceIn(0, text.length)
        val safeEnd = selectionEnd.coerceIn(safeStart, text.length)
        val selectedText = text.substring(safeStart, safeEnd)
        val content = selectedText.ifEmpty { placeholder }
        val replacement = prefix + content + suffix
        val updatedText = text.replaceRange(safeStart, safeEnd, replacement)
        val contentStart = safeStart + prefix.length
        val contentEnd = contentStart + content.length
        return MarkdownEditResult(
            text = updatedText,
            selectionStart = contentStart,
            selectionEnd = contentEnd
        )
    }

    private fun insertInlineSnippet(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        snippet: String
    ): MarkdownEditResult {
        val safeStart = selectionStart.coerceIn(0, text.length)
        val safeEnd = selectionEnd.coerceIn(safeStart, text.length)
        val updatedText = text.replaceRange(safeStart, safeEnd, snippet)
        return MarkdownEditResult(
            text = updatedText,
            selectionStart = safeStart + snippet.length,
            selectionEnd = safeStart + snippet.length
        )
    }

    private fun insertSnippet(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        snippet: String,
        highlightStartOffset: Int,
        highlightEndOffset: Int
    ): MarkdownEditResult {
        val safeStart = selectionStart.coerceIn(0, text.length)
        val safeEnd = selectionEnd.coerceIn(safeStart, text.length)
        val updatedText = text.replaceRange(safeStart, safeEnd, snippet)
        return MarkdownEditResult(
            text = updatedText,
            selectionStart = safeStart + highlightStartOffset,
            selectionEnd = safeStart + highlightEndOffset
        )
    }
}
