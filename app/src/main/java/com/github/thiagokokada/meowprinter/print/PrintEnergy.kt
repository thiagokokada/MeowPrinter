package com.github.thiagokokada.meowprinter.print

object PrintEnergy {
    const val MAX_VALUE = 0xffff

    fun toPercent(value: Int): Int {
        return ((value.coerceIn(0, MAX_VALUE) * 100f) / MAX_VALUE).toInt()
    }

    fun fromPercent(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * MAX_VALUE).toInt()
    }
}
