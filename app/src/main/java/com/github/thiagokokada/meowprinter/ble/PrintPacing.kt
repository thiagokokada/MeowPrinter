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
            val fraction = 1f - (clampedPercent / 100f)
            return PrintPacing(
                controlCommandDelayMs = lerp(4, 14, fraction),
                rowCommandDelayMs = lerp(4, 14, fraction),
                rowCommandExtraPauseEvery = lerp(36, 20, fraction).toInt().coerceAtLeast(1),
                rowCommandExtraPauseMs = lerp(30, 120, fraction)
            )
        }

        private fun lerp(start: Int, end: Int, fraction: Float): Long {
            return (start + ((end - start) * fraction)).toLong()
        }
    }
}
