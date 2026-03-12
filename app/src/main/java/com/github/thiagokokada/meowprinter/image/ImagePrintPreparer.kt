package com.github.thiagokokada.meowprinter.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import kotlin.math.max
import androidx.core.graphics.createBitmap

data class PreparedPrintImage(
    val previewBitmap: Bitmap,
    val rows: List<BooleanArray>,
    val originalWidth: Int,
    val originalHeight: Int,
    val printWidth: Int,
    val printHeight: Int,
    val ditheringMode: DitheringMode
)

object ImagePrintPreparer {
    fun prepare(
        contentResolver: ContentResolver,
        uri: Uri,
        ditheringMode: DitheringMode
    ): PreparedPrintImage {
        var originalWidth = 0
        var originalHeight = 0
        val source = ImageDecoder.createSource(contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            originalWidth = info.size.width
            originalHeight = info.size.height

            val targetHeight = max(1, originalHeight * CatPrinterProtocol.PRINT_WIDTH / originalWidth)
            decoder.setTargetSize(CatPrinterProtocol.PRINT_WIDTH, targetHeight)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }

        val grayscale = extractGrayscale(bitmap)
        val rows = when (ditheringMode) {
            DitheringMode.THRESHOLD -> thresholdRows(grayscale, bitmap.width, bitmap.height)
            DitheringMode.FLOYD_STEINBERG -> floydSteinbergRows(grayscale, bitmap.width, bitmap.height)
            DitheringMode.ATKINSON -> atkinsonRows(grayscale, bitmap.width, bitmap.height)
            DitheringMode.ORDERED_4X4 -> orderedRows(grayscale, bitmap.width, bitmap.height)
        }
        val previewBitmap = previewBitmap(rows, bitmap.width, bitmap.height)

        return PreparedPrintImage(
            previewBitmap = previewBitmap,
            rows = rows,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            printWidth = previewBitmap.width,
            printHeight = previewBitmap.height,
            ditheringMode = ditheringMode
        )
    }

    fun prepare(
        sourceBitmap: Bitmap,
        ditheringMode: DitheringMode
    ): PreparedPrintImage {
        val bitmap = scaleForPrint(sourceBitmap)
        val grayscale = extractGrayscale(bitmap)
        val rows = when (ditheringMode) {
            DitheringMode.THRESHOLD -> thresholdRows(grayscale, bitmap.width, bitmap.height)
            DitheringMode.FLOYD_STEINBERG -> floydSteinbergRows(grayscale, bitmap.width, bitmap.height)
            DitheringMode.ATKINSON -> atkinsonRows(grayscale, bitmap.width, bitmap.height)
            DitheringMode.ORDERED_4X4 -> orderedRows(grayscale, bitmap.width, bitmap.height)
        }
        val previewBitmap = previewBitmap(rows, bitmap.width, bitmap.height)

        return PreparedPrintImage(
            previewBitmap = previewBitmap,
            rows = rows,
            originalWidth = sourceBitmap.width,
            originalHeight = sourceBitmap.height,
            printWidth = previewBitmap.width,
            printHeight = previewBitmap.height,
            ditheringMode = ditheringMode
        )
    }

    private fun scaleForPrint(bitmap: Bitmap): Bitmap {
        if (bitmap.width == CatPrinterProtocol.PRINT_WIDTH) {
            return bitmap
        }

        val targetHeight = max(1, bitmap.height * CatPrinterProtocol.PRINT_WIDTH / bitmap.width)
        return Bitmap.createScaledBitmap(bitmap, CatPrinterProtocol.PRINT_WIDTH, targetHeight, true)
    }

    private fun extractGrayscale(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return FloatArray(pixels.size) { index ->
            val pixel = pixels[index]
            val alpha = Color.alpha(pixel) / 255f
            val red = ((255f * (1f - alpha)) + (Color.red(pixel) * alpha))
            val green = ((255f * (1f - alpha)) + (Color.green(pixel) * alpha))
            val blue = ((255f * (1f - alpha)) + (Color.blue(pixel) * alpha))
            (red * 0.299f) + (green * 0.587f) + (blue * 0.114f)
        }
    }

    private fun thresholdRows(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
        val threshold = grayscale.average().toFloat()
        return buildRows(width, height) { x, y ->
            grayscale[(y * width) + x] < threshold
        }
    }

    private fun floydSteinbergRows(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
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

    private fun atkinsonRows(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
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

    private fun orderedRows(grayscale: FloatArray, width: Int, height: Int): List<BooleanArray> {
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

    private fun previewBitmap(rows: List<BooleanArray>, width: Int, height: Int): Bitmap {
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
}
