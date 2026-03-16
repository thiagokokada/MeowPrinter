package com.github.thiagokokada.meowprinter.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintPacingProfileTest {
    @Test
    fun fastProfileMatchesHundredPercent() {
        val pacing = PrintPacingProfile.FAST.toPacing(customPercent = 42)

        assertEquals(0L, pacing.controlCommandDelayMs)
        assertEquals(0L, pacing.rowCommandDelayMs)
        assertEquals(56, pacing.rowCommandExtraPauseEvery)
        assertEquals(0L, pacing.rowCommandExtraPauseMs)
    }

    @Test
    fun balancedProfileMatchesFortyFivePercent() {
        val balanced = PrintPacingProfile.BALANCED.toPacing(customPercent = 42)

        assertEquals(PrintPacing.fromPercent(45), balanced)
    }

    @Test
    fun safeProfileMatchesZeroPercent() {
        val pacing = PrintPacingProfile.SAFE.toPacing(customPercent = 42)

        assertEquals(14L, pacing.controlCommandDelayMs)
        assertEquals(14L, pacing.rowCommandDelayMs)
        assertEquals(20, pacing.rowCommandExtraPauseEvery)
        assertEquals(120L, pacing.rowCommandExtraPauseMs)
    }

    @Test
    fun customProfileUsesProvidedPercent() {
        val custom = PrintPacingProfile.CUSTOM.toPacing(customPercent = 73)

        assertEquals(PrintPacing.fromPercent(73), custom)
    }

    @Test
    fun fromStoredValueFallsBackToBalanced() {
        assertEquals(PrintPacingProfile.BALANCED, PrintPacingProfile.fromStoredValue(null))
        assertEquals(PrintPacingProfile.BALANCED, PrintPacingProfile.fromStoredValue("unknown"))
    }
}
