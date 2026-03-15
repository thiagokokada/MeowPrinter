package com.github.thiagokokada.meowprinter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPrinterConnectionSelectorTest {
    @Test
    fun reuseForegroundWhenActiveManagerIsReadyForRequestedPrinter() {
        val result = SavedPrinterConnectionSelector.select(
            activeManagerReady = true,
            activePrinterAddress = "AA:BB:CC:DD:EE:FF",
            requestedPrinterAddress = "AA:BB:CC:DD:EE:FF"
        )

        assertEquals(SavedPrinterConnectionStrategy.REUSE_FOREGROUND, result)
    }

    @Test
    fun reconnectForegroundWhenNoActiveManagerIsReady() {
        val result = SavedPrinterConnectionSelector.select(
            activeManagerReady = false,
            activePrinterAddress = "AA:BB:CC:DD:EE:FF",
            requestedPrinterAddress = "AA:BB:CC:DD:EE:FF"
        )

        assertEquals(SavedPrinterConnectionStrategy.RECONNECT_FOREGROUND, result)
    }

    @Test
    fun reconnectForegroundWhenDifferentPrinterIsActive() {
        val result = SavedPrinterConnectionSelector.select(
            activeManagerReady = true,
            activePrinterAddress = "11:22:33:44:55:66",
            requestedPrinterAddress = "AA:BB:CC:DD:EE:FF"
        )

        assertEquals(SavedPrinterConnectionStrategy.RECONNECT_FOREGROUND, result)
    }

    @Test
    fun reconnectForegroundWhenNoActivePrinterAddressIsKnown() {
        val result = SavedPrinterConnectionSelector.select(
            activeManagerReady = true,
            activePrinterAddress = null,
            requestedPrinterAddress = "AA:BB:CC:DD:EE:FF"
        )

        assertEquals(SavedPrinterConnectionStrategy.RECONNECT_FOREGROUND, result)
    }
}
