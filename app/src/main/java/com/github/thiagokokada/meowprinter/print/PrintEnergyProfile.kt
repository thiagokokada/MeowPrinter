package com.github.thiagokokada.meowprinter.print

enum class PrintEnergyProfile(
    val displayName: String,
    private val presetPercent: Int?
) {
    LIGHT("Light", 50),
    MEDIUM("Medium", 65),
    DARK("Dark", 75),
    CUSTOM("Custom", null);

    fun toEnergy(customPercent: Int): Int {
        return PrintEnergy.fromPercent(presetPercent ?: customPercent)
    }

    companion object {
        fun fromStoredValue(value: String?): PrintEnergyProfile {
            return entries.firstOrNull { it.name == value } ?: MEDIUM
        }
    }
}
