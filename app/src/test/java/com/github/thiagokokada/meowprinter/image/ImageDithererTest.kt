package com.github.thiagokokada.meowprinter.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImageDithererTest {
    @Test
    fun registryReturnsExpectedImplementations() {
        assertEquals(ThresholdImageDitherer, ImageDitherers.forMode(DitheringMode.THRESHOLD))
        assertEquals(FloydSteinbergImageDitherer, ImageDitherers.forMode(DitheringMode.FLOYD_STEINBERG))
        assertEquals(AtkinsonImageDitherer, ImageDitherers.forMode(DitheringMode.ATKINSON))
        assertEquals(Ordered4x4ImageDitherer, ImageDitherers.forMode(DitheringMode.ORDERED_4X4))
        assertEquals(OrderedBayer8x8ImageDitherer, ImageDitherers.forMode(DitheringMode.ORDERED_BAYER_8X8))
    }

    @Test
    fun ditherersReturnRowsMatchingRequestedDimensions() {
        val grayscale = floatArrayOf(
            0f, 64f, 128f, 255f,
            255f, 128f, 64f, 0f
        )

        DitheringMode.entries.forEach { mode ->
            val rows = ImageDitherers.forMode(mode).rowsFor(grayscale, width = 4, height = 2)
            assertEquals(2, rows.size)
            assertEquals(4, rows[0].size)
            assertEquals(4, rows[1].size)
        }
    }

    @Test
    fun differentDitherersCanProduceDifferentOutputsForSameInput() {
        val grayscale = FloatArray(16) { index ->
            when (index % 4) {
                0 -> 32f
                1 -> 96f
                2 -> 160f
                else -> 224f
            }
        }

        val thresholdRows = ImageDitherers.forMode(DitheringMode.THRESHOLD).rowsFor(grayscale, width = 4, height = 4)
        val orderedRows = ImageDitherers.forMode(DitheringMode.ORDERED_4X4).rowsFor(grayscale, width = 4, height = 4)

        assertFalse(rowsEqual(thresholdRows, orderedRows))
    }

    @Test
    fun ordered4x4AndOrderedBayer8x8CanProduceDifferentOutputs() {
        val grayscale = FloatArray(64) { index ->
            when (index % 8) {
                0 -> 24f
                1 -> 56f
                2 -> 88f
                3 -> 120f
                4 -> 152f
                5 -> 184f
                6 -> 216f
                else -> 248f
            }
        }

        val ordered4x4 = ImageDitherers.forMode(DitheringMode.ORDERED_4X4).rowsFor(grayscale, width = 8, height = 8)
        val ordered8x8 = ImageDitherers.forMode(DitheringMode.ORDERED_BAYER_8X8).rowsFor(grayscale, width = 8, height = 8)

        assertFalse(rowsEqual(ordered4x4, ordered8x8))
    }

    private fun rowsEqual(first: List<BooleanArray>, second: List<BooleanArray>): Boolean {
        if (first.size != second.size) {
            return false
        }
        return first.zip(second).all { (left, right) -> left.contentEquals(right) }
    }
}
