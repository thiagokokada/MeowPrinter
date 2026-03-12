package com.github.thiagokokada.meowprinter.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.createBitmap

object PrinterTestPage {
    fun createRows(): List<BooleanArray> {
        val width = CatPrinterProtocol.PRINT_WIDTH
        val height = 240
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = false
        }
        val fillPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        canvas.drawRect(6f, 6f, (width - 6).toFloat(), (height - 6).toFloat(), borderPaint)
        canvas.drawText("MEOW PRINTER", 24f, 48f, titlePaint)
        canvas.drawText("BLE test page", 24f, 78f, bodyPaint)
        canvas.drawText("Width: 384 px", 24f, 106f, bodyPaint)
        canvas.drawText("Protocol: Cats/Meow", 24f, 134f, bodyPaint)

        val barTop = 162f
        val barHeight = 18f
        repeat(8) { index ->
            if (index % 2 == 0) {
                val left = 24f + index * 42f
                canvas.drawRect(left, barTop, left + 32f, barTop + barHeight, fillPaint)
            }
        }

        canvas.drawCircle(64f, 210f, 16f, fillPaint)
        canvas.drawCircle(112f, 210f, 10f, fillPaint)
        canvas.drawCircle(156f, 210f, 6f, fillPaint)
        canvas.drawText("If this prints, BLE is working.", 190f, 216f, bodyPaint)

        return bitmapToRows(bitmap)
    }

    private fun bitmapToRows(bitmap: Bitmap): List<BooleanArray> {
        val width = bitmap.width
        val pixels = IntArray(width)
        return buildList(bitmap.height) {
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
                add(
                    BooleanArray(width) { x ->
                        val pixel = pixels[x]
                        val luminance = (pixel.red * 0.299f) + (pixel.green * 0.587f) + (pixel.blue * 0.114f)
                        luminance < 128f
                    }
                )
            }
        }
    }
}
