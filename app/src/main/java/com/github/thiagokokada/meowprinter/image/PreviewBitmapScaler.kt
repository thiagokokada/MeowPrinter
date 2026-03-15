package com.github.thiagokokada.meowprinter.image

import android.graphics.Bitmap
import androidx.core.graphics.scale

object PreviewBitmapScaler {
    fun scaleForDisplay(bitmap: Bitmap, displayWidth: Int): Bitmap {
        if (displayWidth <= 0 || bitmap.width == displayWidth) {
            return bitmap
        }

        val displayHeight = (bitmap.height * (displayWidth / bitmap.width.toFloat())).toInt().coerceAtLeast(1)
        return bitmap.scale(displayWidth, displayHeight, false)
    }
}
