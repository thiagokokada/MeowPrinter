package com.github.thiagokokada.meowprinter

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import kotlin.math.max

data class PreparedPrintImage(
    val previewBitmap: Bitmap,
    val rows: List<BooleanArray>,
    val originalWidth: Int,
    val originalHeight: Int,
    val printWidth: Int,
    val printHeight: Int
)

object ImagePrintPreparer {
    fun prepare(contentResolver: ContentResolver, uri: Uri): PreparedPrintImage {
        var originalWidth = 0
        var originalHeight = 0
        val source = ImageDecoder.createSource(contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            originalWidth = info.size.width
            originalHeight = info.size.height

            val targetHeight = max(1, originalHeight * CatPrinterProtocol.printWidth / originalWidth)
            decoder.setTargetSize(CatPrinterProtocol.printWidth, targetHeight)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }

        val processedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width)
        val rows = buildList(bitmap.height) {
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                val row = BooleanArray(bitmap.width)
                for (x in pixels.indices) {
                    val pixel = pixels[x]
                    val alpha = Color.alpha(pixel) / 255f
                    val red = ((255f * (1f - alpha)) + (Color.red(pixel) * alpha)).toInt()
                    val green = ((255f * (1f - alpha)) + (Color.green(pixel) * alpha)).toInt()
                    val blue = ((255f * (1f - alpha)) + (Color.blue(pixel) * alpha)).toInt()
                    val luminance = (red * 0.299f) + (green * 0.587f) + (blue * 0.114f)
                    val isBlack = luminance < 128f
                    row[x] = isBlack
                    pixels[x] = if (isBlack) Color.BLACK else Color.WHITE
                }
                processedBitmap.setPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                add(row)
            }
        }

        return PreparedPrintImage(
            previewBitmap = processedBitmap,
            rows = rows,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            printWidth = processedBitmap.width,
            printHeight = processedBitmap.height
        )
    }
}
