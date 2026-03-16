package com.github.thiagokokada.meowprinter.image

import org.junit.Assert.assertEquals
import org.junit.Test

class DitheringModeTest {
    @Test
    fun fromStoredValueFallsBackToDefaultWhenValueIsUnknown() {
        assertEquals(DitheringMode.THRESHOLD, DitheringMode.fromStoredValue(null))
        assertEquals(DitheringMode.THRESHOLD, DitheringMode.fromStoredValue("unknown"))
    }

    @Test
    fun fromStoredValueReturnsMatchingMode() {
        assertEquals(DitheringMode.ATKINSON, DitheringMode.fromStoredValue("ATKINSON"))
    }
}
