package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class TableBlockTextCodecTest {
    @Test
    fun decodePadsAndTrimsCellsToRequestedDimensions() {
        val decoded = TableBlockTextCodec.decode(
            input = "A | B | C\nD",
            rows = 2,
            columns = 2
        )

        assertEquals(listOf("A", "B"), decoded[0])
        assertEquals(listOf("D", ""), decoded[1])
    }
}
