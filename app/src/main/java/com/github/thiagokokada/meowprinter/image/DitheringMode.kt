package com.github.thiagokokada.meowprinter.image

enum class DitheringMode(val displayName: String) {
    THRESHOLD("No dithering"),
    FLOYD_STEINBERG("Floyd-Steinberg"),
    ATKINSON("Atkinson"),
    ORDERED_BAYER_4X4("Ordered Bayer 4x4"),
    ORDERED_BAYER_8X8("Ordered Bayer 8x8");

    companion object {
        fun fromStoredValue(value: String?): DitheringMode {
            return entries.firstOrNull { it.name == value } ?: FLOYD_STEINBERG
        }
    }
}
