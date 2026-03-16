package com.github.thiagokokada.meowprinter.ble

data class PrintPacing(
    val controlCommandDelayMs: Long,
    val rowCommandDelayMs: Long,
    val rowCommandExtraPauseEvery: Int,
    val rowCommandExtraPauseMs: Long
) {
    companion object {
        fun fromPercent(percent: Int): PrintPacing {
            val clampedPercent = percent.coerceIn(0, 100)
            val fraction = clampedPercent / 100f
            return PrintPacing(
                controlCommandDelayMs = lerp(14, 0, fraction),
                rowCommandDelayMs = lerp(14, 0, fraction),
                rowCommandExtraPauseEvery = lerp(20, 56, fraction).toInt().coerceAtLeast(1),
                rowCommandExtraPauseMs = lerp(120, 0, fraction)
            )
        }

        private fun lerp(start: Int, end: Int, fraction: Float): Long {
            return (start + ((end - start) * fraction)).toLong()
        }
    }
}
