package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.image.DitheringMode
import java.util.UUID

data class CanvasDocument(
    val blocks: List<DocumentBlock>
) {
    companion object {
        fun default(): CanvasDocument {
            return CanvasDocument(
                blocks = listOf(
                    TextBlock(
                        id = UUID.randomUUID().toString(),
                        text = "Meow Printer\nAdd text, images, and tables here.",
                        alignment = BlockAlignment.LEFT,
                        style = TextBlockStyle()
                    )
                )
            )
        }
    }
}

sealed interface DocumentBlock {
    val id: String
    val alignment: BlockAlignment
}

data class TextBlock(
    override val id: String,
    val text: String,
    override val alignment: BlockAlignment,
    val style: TextBlockStyle
) : DocumentBlock

data class ImageBlock(
    override val id: String,
    val imageUri: String,
    override val alignment: BlockAlignment,
    val ditheringMode: DitheringMode = DitheringMode.FLOYD_STEINBERG
) : DocumentBlock

data class TableBlock(
    override val id: String,
    override val alignment: BlockAlignment,
    val rows: Int,
    val columns: Int,
    val hasHeaderRow: Boolean,
    val cells: List<List<String>>
) : DocumentBlock

data class TextBlockStyle(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val fontFamily: CanvasFontFamily = CanvasFontFamily.SANS,
    val textSize: CanvasTextSize = CanvasTextSize.NORMAL
)
