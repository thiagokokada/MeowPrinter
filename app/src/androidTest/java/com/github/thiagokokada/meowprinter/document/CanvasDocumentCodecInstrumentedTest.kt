package com.github.thiagokokada.meowprinter.document

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.thiagokokada.meowprinter.document.CanvasTextSize
import com.github.thiagokokada.meowprinter.image.DitheringMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasDocumentCodecInstrumentedTest {
    @Test
    fun encodeAndDecodeRoundTripPreservesBlocks() {
        val document = CanvasDocument(
            blocks = listOf(
                TextBlock(
                    id = "text-1",
                    markdown = "## Bold text\n\n| A | B |\n| --- | --- |\n| 1 | 2 |",
                    alignment = BlockAlignment.RIGHT,
                    textSize = CanvasTextSize.SP20
                ),
                ImageBlock(
                    id = "image-1",
                    imageUri = "content://example/image.png",
                    alignment = BlockAlignment.LEFT,
                    ditheringMode = DitheringMode.ATKINSON
                )
            )
        )

        val restored = CanvasDocumentCodec.decode(CanvasDocumentCodec.encode(document))

        assertEquals(2, restored.blocks.size)
        val textBlock = restored.blocks.first() as TextBlock
        assertEquals("## Bold text\n\n| A | B |\n| --- | --- |\n| 1 | 2 |", textBlock.markdown)
        assertEquals(BlockAlignment.RIGHT, textBlock.alignment)
        assertEquals(CanvasTextSize.SP20, textBlock.textSize)
        val imageBlock = restored.blocks.last() as ImageBlock
        assertEquals(DitheringMode.ATKINSON, imageBlock.ditheringMode)
    }

    @Test
    fun encodeAndDecodeRoundTripPreservesEmptyDocument() {
        val restored = CanvasDocumentCodec.decode(CanvasDocumentCodec.encode(CanvasDocument.empty()))

        assertTrue(restored.blocks.isEmpty())
    }
}
