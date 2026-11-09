package com.github.thiagokokada.meowprinter.print

import org.junit.Assert.assertEquals
import org.junit.Test

class Mxw01IntensityTest {
    @Test
    fun preservesExistingUiMappingIncludingTestedMaximum() {
        val expected = mapOf(0 to 0, 50 to 73, 65 to 96, 75 to 111, 100 to 150)
        expected.forEach { (percent, intensity) ->
            assertEquals(intensity, Mxw01Intensity.fromEnergySetting(PrintEnergy.fromPercent(percent)))
        }
    }

    @Test
    fun allUiValuesMatchPreviousMappingAndStayWithinTestedRange() {
        for (percent in 0..100) {
            val energy = PrintEnergy.fromPercent(percent)
            val oldValue = (PrintEnergy.toPercent(energy).coerceIn(0, 100) * 150 / 100).coerceIn(0, 150)
            assertEquals(oldValue, Mxw01Intensity.fromEnergySetting(energy))
        }
        assertEquals(0, Mxw01Intensity.fromEnergySetting(-1))
        assertEquals(150, Mxw01Intensity.fromEnergySetting(Int.MAX_VALUE))
    }
}
