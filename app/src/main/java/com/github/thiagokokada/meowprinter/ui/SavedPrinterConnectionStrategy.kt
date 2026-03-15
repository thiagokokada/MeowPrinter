package com.github.thiagokokada.meowprinter.ui

enum class SavedPrinterConnectionStrategy {
    REUSE_FOREGROUND,
    RECONNECT_FOREGROUND
}

object SavedPrinterConnectionSelector {
    fun select(
        activeManagerReady: Boolean,
        activePrinterAddress: String?,
        requestedPrinterAddress: String
    ): SavedPrinterConnectionStrategy {
        return if (activeManagerReady && activePrinterAddress == requestedPrinterAddress) {
            SavedPrinterConnectionStrategy.REUSE_FOREGROUND
        } else {
            SavedPrinterConnectionStrategy.RECONNECT_FOREGROUND
        }
    }
}
