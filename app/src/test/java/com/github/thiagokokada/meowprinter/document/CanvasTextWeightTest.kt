package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTextWeightTest {
    @Test
    fun fromStoredValueFallsBackToNormal() {
        assertEquals(CanvasTextWeight.NORMAL, CanvasTextWeight.fromStoredValue(null))
        assertEquals(CanvasTextWeight.NORMAL, CanvasTextWeight.fromStoredValue("unknown"))
    }

    @Test
    fun fromStoredValueReturnsMatchingWeight() {
        assertEquals(CanvasTextWeight.BOLD, CanvasTextWeight.fromStoredValue("BOLD"))
    }
}
