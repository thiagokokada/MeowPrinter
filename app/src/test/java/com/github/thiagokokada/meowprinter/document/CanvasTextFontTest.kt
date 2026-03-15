package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTextFontTest {
    @Test
    fun entriesExposeExpectedOrder() {
        assertEquals(
            listOf(
                CanvasTextFont.SANS_SERIF,
                CanvasTextFont.SERIF,
                CanvasTextFont.MONOSPACE,
                CanvasTextFont.CURSIVE
            ),
            CanvasTextFont.entries.toList()
        )
    }

    @Test
    fun fromStoredValueFallsBackToSansSerifWhenUnknown() {
        assertEquals(CanvasTextFont.SANS_SERIF, CanvasTextFont.fromStoredValue(null))
        assertEquals(CanvasTextFont.SANS_SERIF, CanvasTextFont.fromStoredValue("invalid"))
    }

    @Test
    fun fromStoredValueReturnsMatchingOption() {
        assertEquals(CanvasTextFont.MONOSPACE, CanvasTextFont.fromStoredValue("MONOSPACE"))
    }
}
