package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasDocumentEditorTest {
    @Test
    fun moveBlockReordersTheRequestedBlock() {
        val first = TextBlock("1", "First", BlockAlignment.LEFT, CanvasTextSize.NORMAL)
        val second = TextBlock("2", "Second", BlockAlignment.LEFT, CanvasTextSize.NORMAL)
        val third = TextBlock("3", "Third", BlockAlignment.LEFT, CanvasTextSize.NORMAL)
        val document = CanvasDocument(listOf(first, second, third))

        val moved = CanvasDocumentEditor.moveBlock(document, "3", -2)

        assertEquals(listOf("3", "1", "2"), moved.blocks.map { it.id })
    }

    @Test
    fun removeBlockFallsBackToDefaultDocumentWhenLastBlockIsDeleted() {
        val only = TextBlock("1", "Only", BlockAlignment.LEFT, CanvasTextSize.NORMAL)
        val document = CanvasDocument(listOf(only))

        val updated = CanvasDocumentEditor.removeBlock(document, "1")

        assertTrue(updated.blocks.isNotEmpty())
        assertTrue(updated.blocks.first() is TextBlock)
    }
}
