package com.github.thiagokokada.meowprinter.document

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.github.thiagokokada.meowprinter.image.ImagePrintPreparer
import com.google.android.material.color.MaterialColors
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

class CanvasDocumentRenderer(
    private val context: Context,
    private val contentResolver: ContentResolver
) {
    private val markwon = Markwon.builder(context)
        .usePlugin(TablePlugin.create(context))
        .build()

    fun createPreviewBlockView(
        block: DocumentBlock,
        widthPx: Int
    ): View {
        return createBlockView(
            block = block,
            contentWidthPx = widthPx,
            mode = RenderMode.PREVIEW
        )
    }

    fun renderBitmap(
        document: CanvasDocument,
        widthPx: Int,
        mode: RenderMode
    ): Bitmap {
        val documentView = createDocumentView(document, widthPx, mode)
        return renderViewToBitmap(documentView, Color.WHITE)
    }

    private fun createDocumentView(
        document: CanvasDocument,
        widthPx: Int,
        mode: RenderMode
    ): LinearLayout {
        val horizontalPadding = if (mode == RenderMode.PRINT) 20 else 24
        val backgroundColor = if (mode == RenderMode.PRINT) {
            Color.WHITE
        } else {
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(horizontalPadding, 24, horizontalPadding, 24)
            setBackgroundColor(backgroundColor)

            document.blocks.forEachIndexed { index, block ->
                addView(createBlockView(block, widthPx - (horizontalPadding * 2), mode))
                if (index != document.blocks.lastIndex) {
                    addView(spaceView(if (mode == RenderMode.PRINT) 16 else 20))
                }
            }
        }
    }

    private fun createBlockView(
        block: DocumentBlock,
        contentWidthPx: Int,
        mode: RenderMode
    ): View {
        return when (block) {
            is TextBlock -> createTextBlockView(block, contentWidthPx, mode)
            is ImageBlock -> createImageBlockView(block, contentWidthPx)
        }
    }

    private fun createTextBlockView(
        block: TextBlock,
        contentWidthPx: Int,
        mode: RenderMode
    ): TextView {
        val textColor = if (mode == RenderMode.PRINT) {
            Color.BLACK
        } else {
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        }
        val backgroundColor = if (mode == RenderMode.PRINT) {
            Color.WHITE
        } else {
            Color.TRANSPARENT
        }
        return TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(contentWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            setTextColor(textColor)
            setBackgroundColor(backgroundColor)
            textSize = if (mode == RenderMode.PRINT) {
                block.textSize.printSp
            } else {
                block.textSize.previewSp
            }
            includeFontPadding = false
            gravity = block.alignment.toGravity()
            if (mode == RenderMode.PRINT) {
                setLineSpacing(2f, 1.05f)
            }
            markwon.setMarkdown(this, block.markdown.ifBlank { " " })
        }
    }

    private fun createImageBlockView(
        block: ImageBlock,
        contentWidthPx: Int
    ): View {
        val frame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val bitmap = decodeBitmap(block.imageUri, contentWidthPx)
        if (bitmap == null) {
            frame.addView(
                TextView(context).apply {
                    text = "Image unavailable"
                    setTextColor(
                        MaterialColors.getColor(
                            context,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                            Color.BLACK
                        )
                    )
                }
            )
            return frame
        }

        val scaledBitmap = if (bitmap.width > contentWidthPx) {
            val targetHeight = (bitmap.height * (contentWidthPx / bitmap.width.toFloat())).toInt().coerceAtLeast(1)
            bitmap.scale(contentWidthPx, targetHeight)
        } else {
            bitmap
        }
        val renderedBitmap = ImagePrintPreparer
            .prepare(scaledBitmap, block.ditheringMode)
            .previewBitmap

        frame.addView(
            ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    renderedBitmap.width,
                    renderedBitmap.height,
                    block.alignment.toLayoutGravity()
                )
                setImageBitmap(renderedBitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        )
        return frame
    }

    private fun decodeBitmap(imageUri: String, targetWidthPx: Int): Bitmap? {
        return runCatching {
            val source = ImageDecoder.createSource(contentResolver, Uri.parse(imageUri))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val sourceWidth = info.size.width
                val sourceHeight = info.size.height
                val targetHeight = (sourceHeight * (targetWidthPx / sourceWidth.toFloat())).toInt().coerceAtLeast(1)
                decoder.setTargetSize(targetWidthPx, targetHeight)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrNull()
    }

    private fun renderViewToBitmap(view: View, backgroundColor: Int): Bitmap {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(view.layoutParams.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        return createBitmap(view.measuredWidth, view.measuredHeight).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(backgroundColor)
            view.draw(canvas)
        }
    }

    private fun spaceView(heightPx: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )
        }
    }

    enum class RenderMode {
        PREVIEW,
        PRINT
    }
}

private fun BlockAlignment.toGravity(): Int {
    return when (this) {
        BlockAlignment.LEFT -> Gravity.START
        BlockAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
        BlockAlignment.RIGHT -> Gravity.END
    }
}

private fun BlockAlignment.toLayoutGravity(): Int {
    return when (this) {
        BlockAlignment.LEFT -> Gravity.START
        BlockAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
        BlockAlignment.RIGHT -> Gravity.END
    }
}
