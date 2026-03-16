package com.github.thiagokokada.meowprinter.ble

enum class PrintPacingProfile(
    val displayName: String,
    val presetPercent: Int?
) {
    FAST(
        displayName = "Fast",
        presetPercent = 100
    ),
    BALANCED(
        displayName = "Balanced",
        presetPercent = 45
    ),
    SAFE(
        displayName = "Safe",
        presetPercent = 0
    ),
    CUSTOM(
        displayName = "Custom",
        presetPercent = null
    );

    fun toPacing(customPercent: Int): PrintPacing {
        return PrintPacing.fromPercent(presetPercent ?: customPercent)
    }

    companion object {
        fun fromStoredValue(value: String?): PrintPacingProfile {
            return entries.firstOrNull { it.name == value } ?: BALANCED
        }
    }
}
