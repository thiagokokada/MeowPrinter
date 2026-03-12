package com.github.thiagokokada.meowprinter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSizeOptionTest {
    @Test
    fun fromStoredValueFallsBackToNormalWhenUnknown() {
        assertEquals(TextSizeOption.NORMAL, TextSizeOption.fromStoredValue(null))
        assertEquals(TextSizeOption.NORMAL, TextSizeOption.fromStoredValue("invalid"))
    }

    @Test
    fun fromStoredValueReturnsMatchingOption() {
        assertEquals(TextSizeOption.LARGE, TextSizeOption.fromStoredValue("LARGE"))
    }
}
