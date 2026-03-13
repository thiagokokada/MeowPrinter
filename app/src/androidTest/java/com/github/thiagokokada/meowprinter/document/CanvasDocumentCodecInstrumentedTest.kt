package com.github.thiagokokada.meowprinter.document

import androidx.test.ext.junit.runners.AndroidJUnit4
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
                    text = "Bold text",
                    alignment = BlockAlignment.RIGHT,
                    style = TextBlockStyle(
                        isBold = true,
                        isUnderline = true,
                        fontFamily = CanvasFontFamily.SERIF,
                        textSize = CanvasTextSize.LARGE
                    )
                ),
                TableBlock(
                    id = "table-1",
                    alignment = BlockAlignment.CENTER,
                    rows = 2,
                    columns = 2,
                    hasHeaderRow = true,
                    cells = listOf(
                        listOf("H1", "H2"),
                        listOf("A", "B")
                    )
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

        assertEquals(3, restored.blocks.size)
        val textBlock = restored.blocks.first() as TextBlock
        assertEquals("Bold text", textBlock.text)
        assertEquals(BlockAlignment.RIGHT, textBlock.alignment)
        assertTrue(textBlock.style.isBold)
        assertEquals(CanvasFontFamily.SERIF, textBlock.style.fontFamily)
        assertEquals(CanvasTextSize.LARGE, textBlock.style.textSize)
        val imageBlock = restored.blocks.last() as ImageBlock
        assertEquals(DitheringMode.ATKINSON, imageBlock.ditheringMode)
    }
}
