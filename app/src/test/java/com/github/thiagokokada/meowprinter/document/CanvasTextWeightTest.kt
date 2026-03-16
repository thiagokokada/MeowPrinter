package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTextWeightTest {
    @Test
    fun fromStoredValueFallsBackToFine() {
        assertEquals(CanvasTextWeight.FINE, CanvasTextWeight.fromStoredValue(null))
        assertEquals(CanvasTextWeight.FINE, CanvasTextWeight.fromStoredValue("unknown"))
    }

    @Test
    fun fromStoredValueReturnsMatchingWeight() {
        assertEquals(CanvasTextWeight.NORMAL, CanvasTextWeight.fromStoredValue("NORMAL"))
    }
}
