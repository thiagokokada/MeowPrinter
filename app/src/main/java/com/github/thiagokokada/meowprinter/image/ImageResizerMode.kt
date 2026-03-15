package com.github.thiagokokada.meowprinter.image

enum class ImageResizerMode(val displayName: String) {
    SYSTEM_FILTERED("Default"),
    AREA_AVERAGE("Area average"),
    NEAREST_NEIGHBOR("Nearest neighbor");

    companion object {
        fun fromStoredValue(value: String?): ImageResizerMode {
            return entries.firstOrNull { it.name == value } ?: SYSTEM_FILTERED
        }
    }
}
