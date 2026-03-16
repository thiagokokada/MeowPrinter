package com.github.thiagokokada.meowprinter.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import kotlin.math.max

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
    fun prepareRenderedDocument(
        sourceBitmap: Bitmap,
        targetWidth: Int = CatPrinterProtocol.PRINT_WIDTH
    ): PreparedPrintImage {
        val bitmap = if (sourceBitmap.width == targetWidth) {
            sourceBitmap
        } else {
            ImageBitmapResizers.forMode(ImageResizerMode.SYSTEM_FILTERED).resize(sourceBitmap, targetWidth)
        }
        val rows = monochromeRows(bitmap)
        val previewBitmap = ImageDitherers.previewBitmap(rows, bitmap.width, bitmap.height)

        return PreparedPrintImage(
            previewBitmap = previewBitmap,
            rows = rows,
            originalWidth = sourceBitmap.width,
            originalHeight = sourceBitmap.height,
            printWidth = previewBitmap.width,
            printHeight = previewBitmap.height,
            ditheringMode = DitheringMode.THRESHOLD,
            processingMode = ImageProcessingMode.NORMAL,
            resizerMode = ImageResizerMode.SYSTEM_FILTERED
        )
    }

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
        val resizer = ImageBitmapResizers.forMode(resizerMode)
        val ditherer = ImageDitherers.forMode(ditheringMode)
        val source = ImageDecoder.createSource(contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            originalWidth = info.size.width
            originalHeight = info.size.height
            val decodeWidth = resizer.decodeWidth(originalWidth, targetWidth)
            val decodeHeight = max(1, originalHeight * decodeWidth / originalWidth)
            decoder.setTargetSize(decodeWidth, decodeHeight)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        val scaledBitmap = resizer.resize(bitmap, targetWidth)

        val grayscale = preprocessGrayscale(
            grayscale = extractGrayscale(scaledBitmap),
            width = scaledBitmap.width,
            height = scaledBitmap.height,
            processingMode = processingMode
        )
        val rows = ditherer.rowsFor(grayscale, scaledBitmap.width, scaledBitmap.height)
        val previewBitmap = ImageDitherers.previewBitmap(rows, scaledBitmap.width, scaledBitmap.height)

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
        val resizer = ImageBitmapResizers.forMode(resizerMode)
        val ditherer = ImageDitherers.forMode(ditheringMode)
        val bitmap = resizer.resize(sourceBitmap, targetWidth)
        val grayscale = preprocessGrayscale(
            grayscale = extractGrayscale(bitmap),
            width = bitmap.width,
            height = bitmap.height,
            processingMode = processingMode
        )
        val rows = ditherer.rowsFor(grayscale, bitmap.width, bitmap.height)
        val previewBitmap = ImageDitherers.previewBitmap(rows, bitmap.width, bitmap.height)

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

    private fun monochromeRows(bitmap: Bitmap): List<BooleanArray> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return buildList(bitmap.height) {
            for (y in 0 until bitmap.height) {
                add(BooleanArray(bitmap.width) { x ->
                    val pixel = pixels[(y * bitmap.width) + x]
                    val alpha = Color.alpha(pixel) / 255f
                    val red = ((255f * (1f - alpha)) + (Color.red(pixel) * alpha))
                    val green = ((255f * (1f - alpha)) + (Color.green(pixel) * alpha))
                    val blue = ((255f * (1f - alpha)) + (Color.blue(pixel) * alpha))
                    val grayscale = (red * 0.299f) + (green * 0.587f) + (blue * 0.114f)
                    grayscale < 200f
                })
            }
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

}
