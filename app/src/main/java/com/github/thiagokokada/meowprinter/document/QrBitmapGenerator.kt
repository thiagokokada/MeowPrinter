package com.github.thiagokokada.meowprinter.document

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrBitmapGenerator {
    fun generate(payload: QrPayload, sizePx: Int): Bitmap {
        return generate(QrContentEncoder.encode(payload), sizePx)
    }

    fun generate(content: String, sizePx: Int): Bitmap {
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
        )
        return createBitmap(matrix.width, matrix.height).apply {
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        }
    }
}
