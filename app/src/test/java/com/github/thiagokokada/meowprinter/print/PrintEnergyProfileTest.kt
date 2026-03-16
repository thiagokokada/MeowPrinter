package com.github.thiagokokada.meowprinter.print

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintEnergyProfileTest {
    @Test
    fun lightProfileMatchesFiftyPercent() {
        assertEquals(PrintEnergy.fromPercent(50), PrintEnergyProfile.LIGHT.toEnergy(13))
    }

    @Test
    fun mediumProfileMatchesSixtyFivePercent() {
        assertEquals(PrintEnergy.fromPercent(65), PrintEnergyProfile.MEDIUM.toEnergy(13))
    }

    @Test
    fun darkProfileMatchesSeventyFivePercent() {
        assertEquals(PrintEnergy.fromPercent(75), PrintEnergyProfile.DARK.toEnergy(13))
    }

    @Test
    fun customProfileUsesProvidedPercent() {
        assertEquals(PrintEnergy.fromPercent(82), PrintEnergyProfile.CUSTOM.toEnergy(82))
    }

    @Test
    fun fromStoredValueFallsBackToMedium() {
        assertEquals(PrintEnergyProfile.MEDIUM, PrintEnergyProfile.fromStoredValue(null))
        assertEquals(PrintEnergyProfile.MEDIUM, PrintEnergyProfile.fromStoredValue("unknown"))
    }
}
