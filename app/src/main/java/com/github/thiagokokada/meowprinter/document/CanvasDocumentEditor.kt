package com.github.thiagokokada.meowprinter.document

import java.util.UUID

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

        val movedBlock = document.blocks[currentIndex]
        val remainingBlocks = document.blocks.filterIndexed { index, _ -> index != currentIndex }
        val reorderedBlocks = buildList {
            remainingBlocks.forEachIndexed { index, block ->
                if (index == targetIndex) {
                    add(movedBlock)
                }
                add(block)
            }
            if (targetIndex >= remainingBlocks.size) {
                add(movedBlock)
            }
        }
        return document.copy(blocks = reorderedBlocks)
    }

    fun duplicateBlock(document: CanvasDocument, blockId: String): CanvasDocument {
        val sourceIndex = document.blocks.indexOfFirst { it.id == blockId }
        if (sourceIndex == -1) {
            return document
        }

        val sourceBlock = document.blocks[sourceIndex]
        val duplicatedBlock = when (sourceBlock) {
            is TextBlock -> sourceBlock.copy(id = UUID.randomUUID().toString())
            is ImageBlock -> sourceBlock.copy(id = UUID.randomUUID().toString())
        }

        return document.copy(
            blocks = buildList {
                document.blocks.forEachIndexed { index, block ->
                    add(block)
                    if (index == sourceIndex) {
                        add(duplicatedBlock)
                    }
                }
            }
        )
    }
}
