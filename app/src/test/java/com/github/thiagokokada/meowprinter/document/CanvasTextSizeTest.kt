package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTextSizeTest {
    @Test
    fun entriesExposeSmallSizesFirst() {
        assertEquals(
            listOf(
                CanvasTextSize.SP6,
                CanvasTextSize.SP8,
                CanvasTextSize.SP10,
                CanvasTextSize.SP12,
                CanvasTextSize.SP14,
                CanvasTextSize.SP16,
                CanvasTextSize.SP18,
                CanvasTextSize.SP20,
                CanvasTextSize.SP24,
                CanvasTextSize.SP28
            ),
            CanvasTextSize.entries.toList()
        )
    }

    @Test
    fun fromStoredValueFallsBackToNormalWhenUnknown() {
        assertEquals(CanvasTextSize.SP12, CanvasTextSize.fromStoredValue(null))
        assertEquals(CanvasTextSize.SP12, CanvasTextSize.fromStoredValue("invalid"))
    }

    @Test
    fun fromStoredValueReturnsMatchingOption() {
        assertEquals(CanvasTextSize.SP24, CanvasTextSize.fromStoredValue("SP24"))
    }
}
