package com.github.thiagokokada.meowprinter.ui

enum class TextSizeOption(
    val displayName: String,
    val previewSp: Float,
    val printSp: Float
) {
    SMALL("Small", 14f, 16f),
    NORMAL("Normal", 16f, 18f),
    LARGE("Large", 18f, 21f);

    companion object {
        fun fromStoredValue(value: String?): TextSizeOption {
            return entries.firstOrNull { it.name == value } ?: NORMAL
        }
    }
}
