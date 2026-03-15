package com.github.thiagokokada.meowprinter.image

enum class ImageProcessingMode(val displayName: String) {
    NORMAL("Normal"),
    HIGH_CONTRAST("High contrast"),
    SHARPEN("Sharpen");

    companion object {
        fun fromStoredValue(value: String?): ImageProcessingMode {
            return entries.firstOrNull { it.name == value } ?: NORMAL
        }
    }
}
