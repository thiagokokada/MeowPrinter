package com.github.thiagokokada.meowprinter.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintEnergyTest {
    @Test
    fun fromPercentMapsExpectedBounds() {
        assertEquals(0, PrintEnergy.fromPercent(0))
        assertEquals(PrintEnergy.MAX_VALUE, PrintEnergy.fromPercent(100))
    }

    @Test
    fun toPercentMapsExpectedBounds() {
        assertEquals(0, PrintEnergy.toPercent(0))
        assertEquals(100, PrintEnergy.toPercent(PrintEnergy.MAX_VALUE))
    }

    @Test
    fun conversionClampsOutOfRangeInput() {
        assertEquals(0, PrintEnergy.fromPercent(-10))
        assertEquals(PrintEnergy.MAX_VALUE, PrintEnergy.fromPercent(120))
    }

    @Test
    fun percentRoundTripStaysWithinOnePercent() {
        val originalPercent = 37
        val roundTripPercent = PrintEnergy.toPercent(PrintEnergy.fromPercent(originalPercent))

        assertTrue(kotlin.math.abs(originalPercent - roundTripPercent) <= 1)
    }
}
