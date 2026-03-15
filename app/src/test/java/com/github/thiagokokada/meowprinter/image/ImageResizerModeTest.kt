package com.github.thiagokokada.meowprinter.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageResizerModeTest {
    @Test
    fun fromStoredValueDefaultsToSystemFiltered() {
        assertEquals(ImageResizerMode.SYSTEM_FILTERED, ImageResizerMode.fromStoredValue(null))
        assertEquals(ImageResizerMode.SYSTEM_FILTERED, ImageResizerMode.fromStoredValue("unknown"))
    }

    @Test
    fun fromStoredValueRestoresKnownMode() {
        assertEquals(ImageResizerMode.AREA_AVERAGE, ImageResizerMode.fromStoredValue("AREA_AVERAGE"))
        assertEquals(ImageResizerMode.NEAREST_NEIGHBOR, ImageResizerMode.fromStoredValue("NEAREST_NEIGHBOR"))
    }
}
