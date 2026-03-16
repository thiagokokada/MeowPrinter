package com.github.thiagokokada.meowprinter.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap

interface ImageDitherer {
    val mode: DitheringMode

    fun rowsFor(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray>
}

object ImageDitherers {
    private val ditherers = listOf(
        ThresholdImageDitherer,
        FloydSteinbergImageDitherer,
        AtkinsonImageDitherer,
        Ordered4x4ImageDitherer,
        OrderedBayer8x8ImageDitherer
    )

    fun forMode(mode: DitheringMode): ImageDitherer {
        return ditherers.first { it.mode == mode }
    }

    fun previewBitmap(rows: List<BooleanArray>, width: Int, height: Int): Bitmap {
        val preview = createBitmap(width, height)
        val pixels = IntArray(width)
        rows.forEachIndexed { y, row ->
            for (x in 0 until width) {
                pixels[x] = if (row[x]) Color.BLACK else Color.WHITE
            }
            preview.setPixels(pixels, 0, width, 0, y, width, 1)
        }
        return preview
    }
}

object ThresholdImageDitherer : ImageDitherer {
    override val mode = DitheringMode.THRESHOLD

    override fun rowsFor(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
        val threshold = grayscale.average().toFloat()
        return buildRows(width, height) { x, y ->
            grayscale[(y * width) + x] < threshold
        }
    }
}

object FloydSteinbergImageDitherer : ImageDitherer {
    override val mode = DitheringMode.FLOYD_STEINBERG

    override fun rowsFor(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
        val working = grayscale.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width) + x
                val oldValue = working[index]
                val newValue = if (oldValue > 127f) 255f else 0f
                val error = oldValue - newValue
                working[index] = newValue
                distribute(working, width, height, x + 1, y, error * 7f / 16f)
                distribute(working, width, height, x - 1, y + 1, error * 3f / 16f)
                distribute(working, width, height, x, y + 1, error * 5f / 16f)
                distribute(working, width, height, x + 1, y + 1, error * 1f / 16f)
            }
        }
        return buildRows(width, height) { x, y -> working[(y * width) + x] < 127f }
    }
}

object AtkinsonImageDitherer : ImageDitherer {
    override val mode = DitheringMode.ATKINSON

    override fun rowsFor(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
        val working = grayscale.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width) + x
                val oldValue = working[index]
                val newValue = if (oldValue > 127f) 255f else 0f
                val error = (oldValue - newValue) / 8f
                working[index] = newValue
                distribute(working, width, height, x + 1, y, error)
                distribute(working, width, height, x + 2, y, error)
                distribute(working, width, height, x - 1, y + 1, error)
                distribute(working, width, height, x, y + 1, error)
                distribute(working, width, height, x + 1, y + 1, error)
                distribute(working, width, height, x, y + 2, error)
            }
        }
        return buildRows(width, height) { x, y -> working[(y * width) + x] < 127f }
    }
}

object Ordered4x4ImageDitherer : ImageDitherer {
    override val mode = DitheringMode.ORDERED_BAYER_4X4

    override fun rowsFor(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
        val matrix = arrayOf(
            intArrayOf(0, 8, 2, 10),
            intArrayOf(12, 4, 14, 6),
            intArrayOf(3, 11, 1, 9),
            intArrayOf(15, 7, 13, 5)
        )
        return buildRows(width, height) { x, y ->
            val threshold = ((matrix[y % 4][x % 4] + 0.5f) / 16f) * 255f
            grayscale[(y * width) + x] < threshold
        }
    }
}

object OrderedBayer8x8ImageDitherer : ImageDitherer {
    override val mode = DitheringMode.ORDERED_BAYER_8X8

    override fun rowsFor(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
        val matrix = arrayOf(
            intArrayOf(0, 48, 12, 60, 3, 51, 15, 63),
            intArrayOf(32, 16, 44, 28, 35, 19, 47, 31),
            intArrayOf(8, 56, 4, 52, 11, 59, 7, 55),
            intArrayOf(40, 24, 36, 20, 43, 27, 39, 23),
            intArrayOf(2, 50, 14, 62, 1, 49, 13, 61),
            intArrayOf(34, 18, 46, 30, 33, 17, 45, 29),
            intArrayOf(10, 58, 6, 54, 9, 57, 5, 53),
            intArrayOf(42, 26, 38, 22, 41, 25, 37, 21)
        )
        return buildRows(width, height) { x, y ->
            val threshold = ((matrix[y % 8][x % 8] + 0.5f) / 64f) * 255f
            grayscale[(y * width) + x] < threshold
        }
    }
}

private fun buildRows(
    width: Int,
    height: Int,
    producer: (x: Int, y: Int) -> Boolean
): List<BooleanArray> {
    return buildList(height) {
        for (y in 0 until height) {
            add(BooleanArray(width) { x -> producer(x, y) })
        }
    }
}

private fun distribute(
    grayscale: FloatArray,
    width: Int,
    height: Int,
    x: Int,
    y: Int,
    delta: Float
) {
    if (x !in 0 until width || y !in 0 until height) {
        return
    }
    val index = (y * width) + x
    grayscale[index] = (grayscale[index] + delta).coerceIn(0f, 255f)
}
