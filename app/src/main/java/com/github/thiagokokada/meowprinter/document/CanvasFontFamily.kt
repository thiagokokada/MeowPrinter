package com.github.thiagokokada.meowprinter.document

import android.graphics.Typeface

enum class CanvasFontFamily(
    val displayName: String
) {
    SANS("Sans"),
    SERIF("Serif"),
    MONO("Mono");

    fun toTypeface(styleFlags: Int): Typeface {
        return when (this) {
            SANS -> Typeface.create(Typeface.SANS_SERIF, styleFlags)
            SERIF -> Typeface.create(Typeface.SERIF, styleFlags)
            MONO -> Typeface.create(Typeface.MONOSPACE, styleFlags)
        }
    }

    companion object {
        fun fromStoredValue(value: String?): CanvasFontFamily {
            return entries.firstOrNull { it.name == value } ?: SANS
        }
    }
}
