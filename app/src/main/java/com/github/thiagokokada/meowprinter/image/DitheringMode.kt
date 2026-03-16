package com.github.thiagokokada.meowprinter.image

enum class DitheringMode(val displayName: String) {
    THRESHOLD("No dithering"),
    FLOYD_STEINBERG("Floyd-Steinberg"),
    ATKINSON("Atkinson"),
    ORDERED_4X4("Ordered 4x4");

    companion object {
        fun fromStoredValue(value: String?): DitheringMode {
            return entries.firstOrNull { it.name == value } ?: THRESHOLD
        }
    }
}
