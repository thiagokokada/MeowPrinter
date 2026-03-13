package com.github.thiagokokada.meowprinter.document

enum class BlockAlignment(
    val displayName: String
) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right");

    companion object {
        fun fromStoredValue(value: String?): BlockAlignment {
            return entries.firstOrNull { it.name == value } ?: LEFT
        }
    }
}
