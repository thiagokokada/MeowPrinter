package com.github.thiagokokada.meowprinter

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun View.applyTopSystemBarPadding() {
    val initialPaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(top = initialPaddingTop + bars.top)
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.applySideAndBottomSystemBarsPadding() {
    val initialPadding = Insets.of(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = initialPadding.left + bars.left,
            top = initialPadding.top,
            right = initialPadding.right + bars.right,
            bottom = initialPadding.bottom + bars.bottom
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.applySystemBarsPadding() {
    val initialPadding = Insets.of(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = initialPadding.left + bars.left,
            top = initialPadding.top + bars.top,
            right = initialPadding.right + bars.right,
            bottom = initialPadding.bottom + bars.bottom
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
