package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasDocumentEditorTest {
    @Test
    fun moveBlockReordersTheRequestedBlock() {
        val first = TextBlock("1", "First", BlockAlignment.LEFT, CanvasTextSize.SP14, CanvasTextFont.SANS_SERIF)
        val second = TextBlock("2", "Second", BlockAlignment.LEFT, CanvasTextSize.SP14, CanvasTextFont.SANS_SERIF)
        val third = TextBlock("3", "Third", BlockAlignment.LEFT, CanvasTextSize.SP14, CanvasTextFont.SANS_SERIF)
        val document = CanvasDocument(listOf(first, second, third))

        val moved = CanvasDocumentEditor.moveBlock(document, "3", -2)

        assertEquals(listOf("3", "1", "2"), moved.blocks.map { it.id })
    }

    @Test
    fun removeBlockFallsBackToDefaultDocumentWhenLastBlockIsDeleted() {
        val only = TextBlock("1", "Only", BlockAlignment.LEFT, CanvasTextSize.SP14, CanvasTextFont.SANS_SERIF)
        val document = CanvasDocument(listOf(only))

        val updated = CanvasDocumentEditor.removeBlock(document, "1")

        assertTrue(updated.blocks.isNotEmpty())
        assertTrue(updated.blocks.first() is TextBlock)
    }

    @Test
    fun duplicateBlockClonesContentAndInsertsNextToSource() {
        val first = TextBlock("1", "First", BlockAlignment.CENTER, CanvasTextSize.SP16, CanvasTextFont.MONOSPACE)
        val second = ImageBlock(
            "2",
            "content://example/image.png",
            BlockAlignment.RIGHT,
            width = ImageBlockWidth.THREE_QUARTERS
        )
        val document = CanvasDocument(listOf(first, second))

        val updated = CanvasDocumentEditor.duplicateBlock(document, "1")

        assertEquals(listOf("1", "2"), document.blocks.map { it.id })
        assertEquals(3, updated.blocks.size)
        val duplicated = updated.blocks[1] as TextBlock
        assertNotEquals("1", duplicated.id)
        assertEquals(first.markdown, duplicated.markdown)
        assertEquals(first.alignment, duplicated.alignment)
        assertEquals(first.textSize, duplicated.textSize)
        assertEquals(first.textFont, duplicated.textFont)
    }

    @Test
    fun duplicateImageBlockPreservesWidth() {
        val image = ImageBlock(
            "1",
            "content://example/image.png",
            BlockAlignment.CENTER,
            width = ImageBlockWidth.HALF
        )
        val document = CanvasDocument(listOf(image))

        val updated = CanvasDocumentEditor.duplicateBlock(document, "1")

        val duplicated = updated.blocks[1] as ImageBlock
        assertEquals(ImageBlockWidth.HALF, duplicated.width)
    }

    @Test
    fun duplicateQrBlockPreservesPayloadAndSize() {
        val qr = QrBlock(
            id = "1",
            payload = UrlQrPayload("https://example.com"),
            alignment = BlockAlignment.RIGHT,
            size = QrBlockSize.LARGE
        )
        val document = CanvasDocument(listOf(qr))

        val updated = CanvasDocumentEditor.duplicateBlock(document, "1")

        val duplicated = updated.blocks[1] as QrBlock
        assertNotEquals("1", duplicated.id)
        assertEquals(qr.payload, duplicated.payload)
        assertEquals(QrBlockSize.LARGE, duplicated.size)
    }
}
