package com.github.thiagokokada.meowprinter.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

interface ImageBitmapResizer {
    val mode: ImageResizerMode

    fun decodeWidth(originalWidth: Int, targetWidth: Int): Int

    fun resize(bitmap: Bitmap, targetWidth: Int): Bitmap
}

object ImageBitmapResizers {
    private val resizers = listOf(
        SystemFilteredImageResizer,
        NearestNeighborImageResizer,
        AreaAverageImageResizer
    )

    fun forMode(mode: ImageResizerMode): ImageBitmapResizer {
        return resizers.first { it.mode == mode }
    }
}

object SystemFilteredImageResizer : ImageBitmapResizer {
    override val mode = ImageResizerMode.SYSTEM_FILTERED

    override fun decodeWidth(originalWidth: Int, targetWidth: Int): Int = targetWidth

    override fun resize(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width == targetWidth) {
            return bitmap
        }

        val targetHeight = max(1, bitmap.height * targetWidth / bitmap.width)
        return bitmap.scale(targetWidth, targetHeight)
    }
}

object NearestNeighborImageResizer : ImageBitmapResizer {
    override val mode = ImageResizerMode.NEAREST_NEIGHBOR

    override fun decodeWidth(originalWidth: Int, targetWidth: Int): Int {
        return minOf(originalWidth, max(targetWidth, targetWidth * 4))
    }

    override fun resize(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width == targetWidth) {
            return bitmap
        }

        val targetHeight = max(1, bitmap.height * targetWidth / bitmap.width)
        val sourcePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(sourcePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val scaledPixels = IntArray(targetWidth * targetHeight)

        for (y in 0 until targetHeight) {
            val sourceY = ((y + 0.5f) * bitmap.height / targetHeight).toInt().coerceIn(0, bitmap.height - 1)
            for (x in 0 until targetWidth) {
                val sourceX = ((x + 0.5f) * bitmap.width / targetWidth).toInt().coerceIn(0, bitmap.width - 1)
                scaledPixels[(y * targetWidth) + x] = sourcePixels[(sourceY * bitmap.width) + sourceX]
            }
        }

        return createBitmap(targetWidth, targetHeight).apply {
            setPixels(scaledPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        }
    }
}

object AreaAverageImageResizer : ImageBitmapResizer {
    override val mode = ImageResizerMode.AREA_AVERAGE

    override fun decodeWidth(originalWidth: Int, targetWidth: Int): Int {
        return minOf(originalWidth, max(targetWidth, targetWidth * 4))
    }

    override fun resize(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width == targetWidth) {
            return bitmap
        }

        val targetHeight = max(1, bitmap.height * targetWidth / bitmap.width)
        if (targetWidth >= bitmap.width || targetHeight >= bitmap.height) {
            return NearestNeighborImageResizer.resize(bitmap, targetWidth)
        }

        val sourcePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(sourcePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val scaledPixels = IntArray(targetWidth * targetHeight)

        for (y in 0 until targetHeight) {
            val sourceYStart = floor(y * bitmap.height / targetHeight.toFloat()).toInt().coerceIn(0, bitmap.height - 1)
            val sourceYEnd = ceil((y + 1) * bitmap.height / targetHeight.toFloat()).toInt().coerceIn(sourceYStart + 1, bitmap.height)
            for (x in 0 until targetWidth) {
                val sourceXStart = floor(x * bitmap.width / targetWidth.toFloat()).toInt().coerceIn(0, bitmap.width - 1)
                val sourceXEnd = ceil((x + 1) * bitmap.width / targetWidth.toFloat()).toInt().coerceIn(sourceXStart + 1, bitmap.width)

                var alpha = 0L
                var red = 0L
                var green = 0L
                var blue = 0L
                var count = 0

                for (sourceY in sourceYStart until sourceYEnd) {
                    for (sourceX in sourceXStart until sourceXEnd) {
                        val pixel = sourcePixels[(sourceY * bitmap.width) + sourceX]
                        alpha += Color.alpha(pixel).toLong()
                        red += Color.red(pixel).toLong()
                        green += Color.green(pixel).toLong()
                        blue += Color.blue(pixel).toLong()
                        count++
                    }
                }

                scaledPixels[(y * targetWidth) + x] = Color.argb(
                    (alpha / count).toInt(),
                    (red / count).toInt(),
                    (green / count).toInt(),
                    (blue / count).toInt()
                )
            }
        }

        return createBitmap(targetWidth, targetHeight).apply {
            setPixels(scaledPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        }
    }
}
