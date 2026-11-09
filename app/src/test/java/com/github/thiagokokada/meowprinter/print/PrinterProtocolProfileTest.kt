package com.github.thiagokokada.meowprinter.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterProtocolProfileTest {
    @Test
    fun onlyTestedModelSelectsMxw01() {
        for (name in listOf("MXW01", "mxw01", " MXW01 ")) {
            assertEquals(PrinterProtocolProfile.MXW01, PrinterProtocolProfile.forAdvertisedName(name))
        }
        for (name in listOf(null, "", "GT01", "GB02", "MXW01-1", "V5X", "WAGGS ITER03")) {
            assertEquals(PrinterProtocolProfile.CAT, PrinterProtocolProfile.forAdvertisedName(name))
        }
    }

    @Test
    fun capabilitiesKeepUnverifiedPaperCommandsOffMxw01() {
        assertTrue(PrinterProtocolProfile.CAT.supportsPaperMovement)
        assertFalse(PrinterProtocolProfile.CAT.supportsBattery)
        assertFalse(PrinterProtocolProfile.MXW01.supportsPaperMovement)
        assertTrue(PrinterProtocolProfile.MXW01.supportsBattery)
    }
}
