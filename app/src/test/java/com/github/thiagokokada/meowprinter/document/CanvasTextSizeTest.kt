package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTextSizeTest {
    @Test
    fun fromStoredValueFallsBackToNormalWhenUnknown() {
        assertEquals(CanvasTextSize.NORMAL, CanvasTextSize.fromStoredValue(null))
        assertEquals(CanvasTextSize.NORMAL, CanvasTextSize.fromStoredValue("invalid"))
    }

    @Test
    fun fromStoredValueReturnsMatchingOption() {
        assertEquals(CanvasTextSize.XLARGE, CanvasTextSize.fromStoredValue("XLARGE"))
    }
}
