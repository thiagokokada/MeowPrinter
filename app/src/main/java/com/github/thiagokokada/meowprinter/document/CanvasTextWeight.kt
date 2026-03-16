package com.github.thiagokokada.meowprinter.document

import android.graphics.Typeface

enum class CanvasTextWeight(
    val displayName: String,
    val typefaceStyle: Int,
    val fakeBold: Boolean
) {
    FINE("Fine", Typeface.NORMAL, false),
    NORMAL("Normal", Typeface.BOLD, false);

    companion object {
        fun fromStoredValue(value: String?): CanvasTextWeight {
            return entries.firstOrNull { it.name == value } ?: FINE
        }
    }
}
