package com.github.thiagokokada.meowprinter.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import kotlin.math.max
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.ceil
import kotlin.math.floor

data class PreparedPrintImage(
    val previewBitmap: Bitmap,
    val rows: List<BooleanArray>,
    val originalWidth: Int,
    val originalHeight: Int,
    val printWidth: Int,
    val printHeight: Int,
    val ditheringMode: DitheringMode,
    val processingMode: ImageProcessingMode,
    val resizerMode: ImageResizerMode
)

object ImagePrintPreparer {
    fun prepare(
        contentResolver: ContentResolver,
        uri: Uri,
        ditheringMode: DitheringMode,
        processingMode: ImageProcessingMode = ImageProcessingMode.NORMAL,
        resizerMode: ImageResizerMode = ImageResizerMode.SYSTEM_FILTERED,
        targetWidth: Int = CatPrinterProtocol.PRINT_WIDTH
    ): PreparedPrintImage {
        var originalWidth = 0
        var originalHeight = 0
        val source = ImageDecoder.createSource(contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            originalWidth = info.size.width
            originalHeight = info.size.height
            if (resizerMode == ImageResizerMode.SYSTEM_FILTERED) {
                val targetHeight = max(1, originalHeight * targetWidth / originalWidth)
                decoder.setTargetSize(targetWidth, targetHeight)
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        val scaledBitmap = scaleBitmap(bitmap, targetWidth, resizerMode)

        val grayscale = preprocessGrayscale(
            grayscale = extractGrayscale(scaledBitmap),
            width = scaledBitmap.width,
            height = scaledBitmap.height,
            processingMode = processingMode
        )
        val rows = when (ditheringMode) {
            DitheringMode.THRESHOLD -> thresholdRows(grayscale, scaledBitmap.width, scaledBitmap.height)
            DitheringMode.FLOYD_STEINBERG -> floydSteinbergRows(grayscale, scaledBitmap.width, scaledBitmap.height)
            DitheringMode.ATKINSON -> atkinsonRows(grayscale, scaledBitmap.width, scaledBitmap.height)
            DitheringMode.ORDERED_4X4 -> orderedRows(grayscale, scaledBitmap.width, scaledBitmap.height)
        }
        val previewBitmap = previewBitmap(rows, scaledBitmap.width, scaledBitmap.height)

        return PreparedPrintImage(
            previewBitmap = previewBitmap,
            rows = rows,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            printWidth = previewBitmap.width,
            printHeight = previewBitmap.height,
            ditheringMode = ditheringMode,
            processingMode = processingMode,
            resizerMode = resizerMode
        )
    }

    fun prepare(
        sourceBitmap: Bitmap,
        ditheringMode: DitheringMode,
        processingMode: ImageProcessingMode = ImageProcessingMode.NORMAL,
        resizerMode: ImageResizerMode = ImageResizerMode.SYSTEM_FILTERED,
        targetWidth: Int = CatPrinterProtocol.PRINT_WIDTH
    ): PreparedPrintImage {
        val bitmap = scaleBitmap(sourceBitmap, targetWidth, resizerMode)
        val grayscale = preprocessGrayscale(
            grayscale = extractGrayscale(bitmap),
            width = bitmap.width,
            height = bitmap.height,
            processingMode = processingMode
        )
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
            ditheringMode = ditheringMode,
            processingMode = processingMode,
            resizerMode = resizerMode
        )
    }

    private fun scaleBitmap(
        bitmap: Bitmap,
        targetWidth: Int,
        resizerMode: ImageResizerMode
    ): Bitmap {
        if (bitmap.width == targetWidth) {
            return bitmap
        }

        val targetHeight = max(1, bitmap.height * targetWidth / bitmap.width)
        return when (resizerMode) {
            ImageResizerMode.SYSTEM_FILTERED -> bitmap.scale(targetWidth, targetHeight)
            ImageResizerMode.NEAREST_NEIGHBOR -> scaleNearestNeighbor(bitmap, targetWidth, targetHeight)
            ImageResizerMode.AREA_AVERAGE -> scaleAreaAverage(bitmap, targetWidth, targetHeight)
        }
    }

    private fun scaleNearestNeighbor(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
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

    private fun scaleAreaAverage(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (targetWidth >= bitmap.width || targetHeight >= bitmap.height) {
            return scaleNearestNeighbor(bitmap, targetWidth, targetHeight)
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

    private fun preprocessGrayscale(
        grayscale: FloatArray,
        width: Int,
        height: Int,
        processingMode: ImageProcessingMode
    ): FloatArray {
        return when (processingMode) {
            ImageProcessingMode.NORMAL -> grayscale
            ImageProcessingMode.HIGH_CONTRAST -> applyHighContrast(grayscale)
            ImageProcessingMode.SHARPEN -> applySharpen(grayscale, width, height)
        }
    }

    private fun applyHighContrast(grayscale: FloatArray): FloatArray {
        if (grayscale.isEmpty()) {
            return grayscale
        }

        val histogram = IntArray(256)
        grayscale.forEach { histogram[it.toInt().coerceIn(0, 255)]++ }

        val clipCount = (grayscale.size * 0.01f).toInt()
        val low = findHistogramBound(histogram, clipCount)
        val high = findHistogramBound(histogram, clipCount, reverse = true)
        if (high <= low) {
            return grayscale.copyOf()
        }

        return FloatArray(grayscale.size) { index ->
            (((grayscale[index] - low) * 255f) / (high - low)).coerceIn(0f, 255f)
        }
    }

    private fun findHistogramBound(
        histogram: IntArray,
        clipCount: Int,
        reverse: Boolean = false
    ): Int {
        var cumulative = 0
        if (reverse) {
            for (value in 255 downTo 0) {
                cumulative += histogram[value]
                if (cumulative > clipCount) {
                    return value
                }
            }
        } else {
            for (value in 0..255) {
                cumulative += histogram[value]
                if (cumulative > clipCount) {
                    return value
                }
            }
        }
        return if (reverse) 255 else 0
    }

    private fun applySharpen(
        grayscale: FloatArray,
        width: Int,
        height: Int
    ): FloatArray {
        if (width < 3 || height < 3) {
            return grayscale.copyOf()
        }

        val output = grayscale.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = (y * width) + x
                val sharpened =
                    (5f * grayscale[index]) -
                        grayscale[index - 1] -
                        grayscale[index + 1] -
                        grayscale[index - width] -
                        grayscale[index + width]
                output[index] = sharpened.coerceIn(0f, 255f)
            }
        }
        return output
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
