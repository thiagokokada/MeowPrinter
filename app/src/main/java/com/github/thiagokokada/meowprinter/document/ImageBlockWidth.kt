package com.github.thiagokokada.meowprinter.document

enum class ImageBlockWidth(
    val displayName: String,
    val fraction: Float
) {
    QUARTER("25%", 0.25f),
    HALF("50%", 0.5f),
    THREE_QUARTERS("75%", 0.75f),
    FULL("100%", 1f);

    companion object {
        fun fromStoredValue(value: String?): ImageBlockWidth {
            return entries.firstOrNull { it.name == value } ?: FULL
        }
    }
}
