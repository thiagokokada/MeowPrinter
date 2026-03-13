package com.github.thiagokokada.meowprinter.document

enum class CanvasTextSize(
    val displayName: String,
    val previewSp: Float,
    val printSp: Float
) {
    SMALL("Small", 14f, 16f),
    NORMAL("Normal", 16f, 18f),
    LARGE("Large", 18f, 21f),
    XLARGE("Extra large", 22f, 26f);

    companion object {
        fun fromStoredValue(value: String?): CanvasTextSize {
            return entries.firstOrNull { it.name == value } ?: NORMAL
        }
    }
}
