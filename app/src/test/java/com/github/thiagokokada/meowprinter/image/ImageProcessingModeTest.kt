package com.github.thiagokokada.meowprinter.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProcessingModeTest {
    @Test
    fun fromStoredValueDefaultsToNormal() {
        assertEquals(ImageProcessingMode.NORMAL, ImageProcessingMode.fromStoredValue(null))
        assertEquals(ImageProcessingMode.NORMAL, ImageProcessingMode.fromStoredValue("unknown"))
    }

    @Test
    fun fromStoredValueRestoresKnownMode() {
        assertEquals(ImageProcessingMode.HIGH_CONTRAST, ImageProcessingMode.fromStoredValue("HIGH_CONTRAST"))
        assertEquals(ImageProcessingMode.SHARPEN, ImageProcessingMode.fromStoredValue("SHARPEN"))
    }
}
