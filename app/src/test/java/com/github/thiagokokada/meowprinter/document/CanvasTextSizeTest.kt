package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTextSizeTest {
    @Test
    fun fromStoredValueFallsBackToNormalWhenUnknown() {
        assertEquals(CanvasTextSize.SP14, CanvasTextSize.fromStoredValue(null))
        assertEquals(CanvasTextSize.SP14, CanvasTextSize.fromStoredValue("invalid"))
    }

    @Test
    fun fromStoredValueReturnsMatchingOption() {
        assertEquals(CanvasTextSize.SP24, CanvasTextSize.fromStoredValue("SP24"))
    }
}
