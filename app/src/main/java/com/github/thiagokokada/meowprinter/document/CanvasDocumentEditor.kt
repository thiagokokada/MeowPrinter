package com.github.thiagokokada.meowprinter.document

object CanvasDocumentEditor {
    fun appendBlock(document: CanvasDocument, block: DocumentBlock): CanvasDocument {
        return document.copy(blocks = document.blocks + block)
    }

    fun replaceBlock(document: CanvasDocument, updatedBlock: DocumentBlock): CanvasDocument {
        return document.copy(
            blocks = document.blocks.map { block ->
                if (block.id == updatedBlock.id) updatedBlock else block
            }
        )
    }

    fun removeBlock(document: CanvasDocument, blockId: String): CanvasDocument {
        val updatedBlocks = document.blocks.filterNot { it.id == blockId }
        return if (updatedBlocks.isEmpty()) {
            CanvasDocument.default()
        } else {
            document.copy(blocks = updatedBlocks)
        }
    }

    fun moveBlock(document: CanvasDocument, blockId: String, offset: Int): CanvasDocument {
        val currentIndex = document.blocks.indexOfFirst { it.id == blockId }
        if (currentIndex == -1) {
            return document
        }

        val targetIndex = (currentIndex + offset).coerceIn(0, document.blocks.lastIndex)
        if (targetIndex == currentIndex) {
            return document
        }

        val mutableBlocks = document.blocks.toMutableList()
        val block = mutableBlocks.removeAt(currentIndex)
        mutableBlocks.add(targetIndex, block)
        return document.copy(blocks = mutableBlocks.toList())
    }
}
