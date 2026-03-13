package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.image.DitheringMode
import java.util.UUID

data class CanvasDocument(
    val blocks: List<DocumentBlock>
) {
    companion object {
        fun empty(): CanvasDocument {
            return CanvasDocument(blocks = emptyList())
        }

        fun default(): CanvasDocument {
            return CanvasDocument(
                blocks = listOf(
                    TextBlock(
                        id = UUID.randomUUID().toString(),
                        markdown = "## Meow Printer\n\nThis block supports **Markdown** tables, emphasis, and lists.",
                        alignment = BlockAlignment.LEFT,
                        textSize = CanvasTextSize.SP14
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
    val markdown: String,
    override val alignment: BlockAlignment,
    val textSize: CanvasTextSize
) : DocumentBlock

data class ImageBlock(
    override val id: String,
    val imageUri: String,
    override val alignment: BlockAlignment,
    val ditheringMode: DitheringMode = DitheringMode.FLOYD_STEINBERG,
    val width: ImageBlockWidth = ImageBlockWidth.FULL
) : DocumentBlock
