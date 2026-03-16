package com.github.thiagokokada.meowprinter.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePrintPreparerInstrumentedTest {
    @Test
    fun preparePreservesRequestedModesAndTargetSize() {
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            setPixel(0, 0, Color.BLACK)
            setPixel(1, 0, Color.BLACK)
        }

        val prepared = ImagePrintPreparer.prepare(
            sourceBitmap = bitmap,
            ditheringMode = DitheringMode.ATKINSON,
            processingMode = ImageProcessingMode.SHARPEN,
            resizerMode = ImageResizerMode.AREA_AVERAGE,
            targetWidth = 4
        )

        assertEquals(8, prepared.originalWidth)
        assertEquals(4, prepared.originalHeight)
        assertEquals(4, prepared.printWidth)
        assertEquals(2, prepared.printHeight)
        assertEquals(DitheringMode.ATKINSON, prepared.ditheringMode)
        assertEquals(ImageProcessingMode.SHARPEN, prepared.processingMode)
        assertEquals(ImageResizerMode.AREA_AVERAGE, prepared.resizerMode)
        assertEquals(2, prepared.rows.size)
        assertEquals(4, prepared.rows.first().size)
        assertEquals(4, prepared.previewBitmap.width)
        assertEquals(2, prepared.previewBitmap.height)
    }

    @Test
    fun areaAverageAndNearestNeighborProduceDifferentRows() {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 2) {
                setPixel(0, y, Color.BLACK)
                setPixel(1, y, Color.BLACK)
                setPixel(2, y, Color.WHITE)
                setPixel(3, y, Color.BLACK)
            }
        }

        val areaAverage = ImagePrintPreparer.prepare(
            sourceBitmap = bitmap,
            ditheringMode = DitheringMode.THRESHOLD,
            resizerMode = ImageResizerMode.AREA_AVERAGE,
            targetWidth = 2
        )
        val nearestNeighbor = ImagePrintPreparer.prepare(
            sourceBitmap = bitmap,
            ditheringMode = DitheringMode.THRESHOLD,
            resizerMode = ImageResizerMode.NEAREST_NEIGHBOR,
            targetWidth = 2
        )

        assertFalse(areaAverage.rows.single().contentEquals(nearestNeighbor.rows.single()))
    }

    @Test
    fun highContrastChangesOrderedDitherOutput() {
        val bitmap = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(120, 120, 120))
            setPixel(1, 0, Color.rgb(125, 125, 125))
            setPixel(2, 0, Color.rgb(130, 130, 130))
            setPixel(3, 0, Color.rgb(135, 135, 135))
        }

        val normal = ImagePrintPreparer.prepare(
            sourceBitmap = bitmap,
            ditheringMode = DitheringMode.ORDERED_4X4,
            processingMode = ImageProcessingMode.NORMAL,
            targetWidth = 4
        )
        val highContrast = ImagePrintPreparer.prepare(
            sourceBitmap = bitmap,
            ditheringMode = DitheringMode.ORDERED_4X4,
            processingMode = ImageProcessingMode.HIGH_CONTRAST,
            targetWidth = 4
        )

        assertArrayEquals(booleanArrayOf(false, true, false, true), normal.rows.single())
        assertArrayEquals(booleanArrayOf(true, true, false, false), highContrast.rows.single())
        assertNotEquals(
            rowSignature(normal.rows.single()),
            rowSignature(highContrast.rows.single())
        )
        assertTrue(highContrast.rows.single()[0])
    }

    @Test
    fun prepareRenderedDocumentUsesDirectMonochromeRows() {
        val bitmap = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.BLACK)
            setPixel(1, 0, Color.rgb(180, 180, 180))
            setPixel(2, 0, Color.rgb(220, 220, 220))
            setPixel(3, 0, Color.WHITE)
        }

        val prepared = ImagePrintPreparer.prepareRenderedDocument(bitmap, targetWidth = 4)

        assertEquals(DitheringMode.THRESHOLD, prepared.ditheringMode)
        assertEquals(ImageProcessingMode.NORMAL, prepared.processingMode)
        assertEquals(ImageResizerMode.SYSTEM_FILTERED, prepared.resizerMode)
        assertArrayEquals(booleanArrayOf(true, true, false, false), prepared.rows.single())
        assertEquals(4, prepared.previewBitmap.width)
        assertEquals(1, prepared.previewBitmap.height)
    }

    private fun rowSignature(row: BooleanArray): String {
        return buildString(row.size) {
            row.forEach { append(if (it) '1' else '0') }
        }
    }
}
