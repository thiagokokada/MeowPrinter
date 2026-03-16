package com.github.thiagokokada.meowprinter.document

import android.graphics.Typeface

enum class CanvasTextWeight(
    val displayName: String,
    val typefaceStyle: Int,
    val fakeBold: Boolean
) {
    NORMAL("Normal", Typeface.NORMAL, false),
    BOLD("Bold", Typeface.BOLD, false);

    companion object {
        fun fromStoredValue(value: String?): CanvasTextWeight {
            return entries.firstOrNull { it.name == value } ?: NORMAL
        }
    }
}
