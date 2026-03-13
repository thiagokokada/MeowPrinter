package com.github.thiagokokada.meowprinter.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintPacingTest {
    @Test
    fun fromPercentMapsSlowestBounds() {
        val pacing = PrintPacing.fromPercent(0)

        assertEquals(14L, pacing.controlCommandDelayMs)
        assertEquals(14L, pacing.rowCommandDelayMs)
        assertEquals(20, pacing.rowCommandExtraPauseEvery)
        assertEquals(120L, pacing.rowCommandExtraPauseMs)
    }

    @Test
    fun fromPercentMapsFastestBounds() {
        val pacing = PrintPacing.fromPercent(100)

        assertEquals(4L, pacing.controlCommandDelayMs)
        assertEquals(4L, pacing.rowCommandDelayMs)
        assertEquals(36, pacing.rowCommandExtraPauseEvery)
        assertEquals(30L, pacing.rowCommandExtraPauseMs)
    }

    @Test
    fun fromPercentClampsOutOfRangeInput() {
        assertEquals(PrintPacing.fromPercent(0), PrintPacing.fromPercent(-10))
        assertEquals(PrintPacing.fromPercent(100), PrintPacing.fromPercent(150))
    }

    @Test
    fun higherPercentProducesFasterPacing() {
        val slower = PrintPacing.fromPercent(25)
        val faster = PrintPacing.fromPercent(75)

        assertTrue(faster.controlCommandDelayMs <= slower.controlCommandDelayMs)
        assertTrue(faster.rowCommandDelayMs <= slower.rowCommandDelayMs)
        assertTrue(faster.rowCommandExtraPauseEvery >= slower.rowCommandExtraPauseEvery)
        assertTrue(faster.rowCommandExtraPauseMs <= slower.rowCommandExtraPauseMs)
    }
}
