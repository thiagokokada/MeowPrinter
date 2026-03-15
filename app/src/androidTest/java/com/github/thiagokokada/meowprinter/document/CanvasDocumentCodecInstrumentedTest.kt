package com.github.thiagokokada.meowprinter.document

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.thiagokokada.meowprinter.data.DocumentImageStore
import com.github.thiagokokada.meowprinter.document.CanvasTextSize
import com.github.thiagokokada.meowprinter.image.DitheringMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class CanvasDocumentCodecInstrumentedTest {
    private lateinit var context: Context
    private lateinit var documentImageStore: DocumentImageStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        documentImageStore = DocumentImageStore(context)
    }

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
                    ditheringMode = DitheringMode.ATKINSON,
                    width = ImageBlockWidth.HALF
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
        assertEquals(ImageBlockWidth.HALF, imageBlock.width)
    }

    @Test
    fun exportAndImportRoundTripEmbedsImageBlocks() {
        val storedImageUri = documentImageStore.persistEmbeddedImage(
            mimeType = "image/jpeg",
            bytes = createSampleImageBytes()
        )
        val document = CanvasDocument(
            blocks = listOf(
                ImageBlock(
                    id = "image-1",
                    imageUri = storedImageUri,
                    alignment = BlockAlignment.CENTER,
                    ditheringMode = DitheringMode.ORDERED_4X4,
                    width = ImageBlockWidth.THREE_QUARTERS
                )
            )
        )

        val exported = CanvasDocumentCodec.encodeForExport(document, documentImageStore)
        val restored = CanvasDocumentCodec.decodeImported(exported, documentImageStore)
        val restoredBlock = restored.blocks.single() as ImageBlock

        assertTrue(exported.contains("\"version\":4"))
        assertTrue(exported.contains("\"dataBase64\""))
        assertTrue(restoredBlock.imageUri.startsWith("content://"))
        assertEquals(DitheringMode.ORDERED_4X4, restoredBlock.ditheringMode)
        assertEquals(ImageBlockWidth.THREE_QUARTERS, restoredBlock.width)
    }

    @Test
    fun encodeAndDecodeRoundTripPreservesEmptyDocument() {
        val restored = CanvasDocumentCodec.decode(CanvasDocumentCodec.encode(CanvasDocument.empty()))

        assertTrue(restored.blocks.isEmpty())
    }

    private fun createSampleImageBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff112233.toInt())
        }
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.toByteArray()
        }
    }
}
