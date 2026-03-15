package com.github.thiagokokada.meowprinter.document

import android.graphics.Typeface

enum class CanvasTextFont(
    val displayName: String
) {
    SANS_SERIF("Sans Serif"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    CURSIVE("Cursive");

    fun toTypeface(): Typeface {
        return when (this) {
            SANS_SERIF -> Typeface.SANS_SERIF
            SERIF -> Typeface.SERIF
            MONOSPACE -> Typeface.MONOSPACE
            CURSIVE -> Typeface.create("cursive", Typeface.NORMAL)
        }
    }

    companion object {
        fun fromStoredValue(value: String?): CanvasTextFont {
            return entries.firstOrNull { it.name == value } ?: SANS_SERIF
        }
    }
}
