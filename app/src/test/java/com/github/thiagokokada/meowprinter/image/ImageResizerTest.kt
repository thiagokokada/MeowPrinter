package com.github.thiagokokada.meowprinter.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageResizerTest {
    @Test
    fun registryReturnsExpectedImplementations() {
        assertEquals(SystemFilteredImageResizer, ImageBitmapResizers.forMode(ImageResizerMode.SYSTEM_FILTERED))
        assertEquals(NearestNeighborImageResizer, ImageBitmapResizers.forMode(ImageResizerMode.NEAREST_NEIGHBOR))
        assertEquals(AreaAverageImageResizer, ImageBitmapResizers.forMode(ImageResizerMode.AREA_AVERAGE))
    }

    @Test
    fun systemFilteredDecodeWidthUsesTargetWidth() {
        assertEquals(384, SystemFilteredImageResizer.decodeWidth(originalWidth = 4000, targetWidth = 384))
    }

    @Test
    fun customResizersDecodeWidthUseBoundedHeadroom() {
        assertEquals(1536, NearestNeighborImageResizer.decodeWidth(originalWidth = 4000, targetWidth = 384))
        assertEquals(1536, AreaAverageImageResizer.decodeWidth(originalWidth = 4000, targetWidth = 384))
    }

    @Test
    fun customResizersDecodeWidthDoNotExceedOriginalWidth() {
        assertEquals(600, NearestNeighborImageResizer.decodeWidth(originalWidth = 600, targetWidth = 384))
        assertEquals(600, AreaAverageImageResizer.decodeWidth(originalWidth = 600, targetWidth = 384))
    }
}
