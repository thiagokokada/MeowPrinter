package com.github.thiagokokada.meowprinter.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.thiagokokada.meowprinter.data.DocumentImageStore
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImageProcessingMode
import com.github.thiagokokada.meowprinter.image.ImageResizerMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class CanvasDocumentRendererInstrumentedTest {
    private lateinit var context: Context
    private lateinit var documentImageStore: DocumentImageStore
    private lateinit var renderer: CanvasDocumentRenderer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        documentImageStore = DocumentImageStore(context)
        renderer = CanvasDocumentRenderer(context, context.contentResolver)
    }

    @Test
    fun imageBlockResizerChangesRenderedPreview() {
        val imageUri = documentImageStore.persistEmbeddedImage("image/png", createStripedImageBytes())
        val areaAverageDocument = CanvasDocument(
            blocks = listOf(
                ImageBlock(
                    id = "image-1",
                    imageUri = imageUri,
                    alignment = BlockAlignment.CENTER,
                    ditheringMode = DitheringMode.THRESHOLD,
                    processingMode = ImageProcessingMode.NORMAL,
                    resizerMode = ImageResizerMode.AREA_AVERAGE,
                    width = ImageBlockWidth.HALF
                )
            )
        )
        val nearestNeighborDocument = CanvasDocument(
            blocks = listOf(
                ImageBlock(
                    id = "image-1",
                    imageUri = imageUri,
                    alignment = BlockAlignment.CENTER,
                    ditheringMode = DitheringMode.THRESHOLD,
                    processingMode = ImageProcessingMode.NORMAL,
                    resizerMode = ImageResizerMode.NEAREST_NEIGHBOR,
                    width = ImageBlockWidth.HALF
                )
            )
        )

        val areaAverageBitmap = renderer.renderBitmap(areaAverageDocument, 160, CanvasDocumentRenderer.RenderMode.PREVIEW)
        val nearestNeighborBitmap = renderer.renderBitmap(nearestNeighborDocument, 160, CanvasDocumentRenderer.RenderMode.PREVIEW)

        assertFalse(bitmapsEqual(areaAverageBitmap, nearestNeighborBitmap))
    }

    @Test
    fun qrBlockRendersVisibleBitmap() {
        val document = CanvasDocument(
            blocks = listOf(
                QrBlock(
                    id = "qr-1",
                    payload = UrlQrPayload("https://example.com"),
                    alignment = BlockAlignment.CENTER,
                    size = QrBlockSize.MEDIUM
                )
            )
        )

        val previewBitmap = renderer.renderBitmap(document, 240, CanvasDocumentRenderer.RenderMode.PREVIEW)

        assertTrue(previewBitmap.width > 0)
        assertTrue(previewBitmap.height > 0)
        assertTrue(bitmapHasBlackPixels(previewBitmap))
    }

    private fun createStripedImageBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(768, 384, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val color = when (x % 7) {
                        0, 1, 2 -> Color.BLACK
                        3, 4 -> Color.WHITE
                        5 -> Color.BLACK
                        else -> Color.WHITE
                    }
                    setPixel(x, y, color)
                }
            }
        }
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }

    private fun bitmapsEqual(first: Bitmap, second: Bitmap): Boolean {
        if (first.width != second.width || first.height != second.height) {
            return false
        }

        val firstPixels = IntArray(first.width * first.height)
        val secondPixels = IntArray(second.width * second.height)
        first.getPixels(firstPixels, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(secondPixels, 0, second.width, 0, 0, second.width, second.height)
        return firstPixels.contentEquals(secondPixels)
    }

    private fun bitmapHasBlackPixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { it == Color.BLACK }
    }
}
