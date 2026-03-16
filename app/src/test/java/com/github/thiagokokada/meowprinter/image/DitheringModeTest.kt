package com.github.thiagokokada.meowprinter.image

import org.junit.Assert.assertEquals
import org.junit.Test

class DitheringModeTest {
    @Test
    fun fromStoredValueFallsBackToDefaultWhenValueIsUnknown() {
        assertEquals(DitheringMode.FLOYD_STEINBERG, DitheringMode.fromStoredValue(null))
        assertEquals(DitheringMode.FLOYD_STEINBERG, DitheringMode.fromStoredValue("unknown"))
    }

    @Test
    fun fromStoredValueReturnsMatchingMode() {
        assertEquals(DitheringMode.ATKINSON, DitheringMode.fromStoredValue("ATKINSON"))
    }
}
