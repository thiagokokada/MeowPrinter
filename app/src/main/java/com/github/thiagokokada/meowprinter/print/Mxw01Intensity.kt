package com.github.thiagokokada.meowprinter.print

object Mxw01Intensity {
    const val MAX_VALUE = 150

    // Preserve the original UI mapping (including its percent quantization), rather
    // than silently recalibrating physically tested settings. This is not a physical
    // equivalence between Cat energy and MXW01 intensity. UI 100 maps to A2=150.
    fun fromEnergySetting(energy: Int): Int =
        PrintEnergy.toPercent(energy) * MAX_VALUE / 100
}
