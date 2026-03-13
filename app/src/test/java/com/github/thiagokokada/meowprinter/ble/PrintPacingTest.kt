package com.github.thiagokokada.meowprinter.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintPacingTest {
    @Test
    fun fromPercentMapsFastestBounds() {
        val pacing = PrintPacing.fromPercent(0)

        assertEquals(4L, pacing.controlCommandDelayMs)
        assertEquals(4L, pacing.rowCommandDelayMs)
        assertEquals(36, pacing.rowCommandExtraPauseEvery)
        assertEquals(30L, pacing.rowCommandExtraPauseMs)
    }

    @Test
    fun fromPercentMapsSlowestBounds() {
        val pacing = PrintPacing.fromPercent(100)

        assertEquals(14L, pacing.controlCommandDelayMs)
        assertEquals(14L, pacing.rowCommandDelayMs)
        assertEquals(20, pacing.rowCommandExtraPauseEvery)
        assertEquals(120L, pacing.rowCommandExtraPauseMs)
    }

    @Test
    fun fromPercentClampsOutOfRangeInput() {
        assertEquals(PrintPacing.fromPercent(0), PrintPacing.fromPercent(-10))
        assertEquals(PrintPacing.fromPercent(100), PrintPacing.fromPercent(150))
    }

    @Test
    fun higherPercentProducesSlowerPacing() {
        val faster = PrintPacing.fromPercent(25)
        val slower = PrintPacing.fromPercent(75)

        assertTrue(slower.controlCommandDelayMs >= faster.controlCommandDelayMs)
        assertTrue(slower.rowCommandDelayMs >= faster.rowCommandDelayMs)
        assertTrue(slower.rowCommandExtraPauseEvery <= faster.rowCommandExtraPauseEvery)
        assertTrue(slower.rowCommandExtraPauseMs >= faster.rowCommandExtraPauseMs)
    }
}
