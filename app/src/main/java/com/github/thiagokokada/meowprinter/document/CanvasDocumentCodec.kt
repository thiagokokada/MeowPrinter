package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.data.DocumentImageStore
import com.github.thiagokokada.meowprinter.image.DitheringMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

object CanvasDocumentCodec {
    fun encode(document: CanvasDocument): String {
        return JSONObject()
            .put("version", INTERNAL_VERSION)
            .put("blocks", JSONArray().apply {
                document.blocks.forEach { block ->
                    put(encodeBlock(block))
                }
            })
            .toString()
    }

    fun encodeForExport(document: CanvasDocument, imageStore: DocumentImageStore): String {
        return JSONObject()
            .put("version", EXPORTED_IMAGE_VERSION)
            .put("blocks", JSONArray().apply {
                document.blocks.forEach { block ->
                    put(encodeBlockForExport(block, imageStore))
                }
            })
            .toString()
    }

    fun decode(raw: String?): CanvasDocument {
        if (raw.isNullOrBlank()) {
            return CanvasDocument.default()
        }

        return runCatching {
            val root = JSONObject(raw)
            val blocksJson = root.optJSONArray("blocks") ?: JSONArray()
            val blocks = buildList {
                for (index in 0 until blocksJson.length()) {
                    decodeBlock(blocksJson.getJSONObject(index))?.let(::add)
                }
            }
            CanvasDocument(blocks = blocks)
        }.getOrElse {
            CanvasDocument.default()
        }
    }

    fun decodeImported(raw: String?, imageStore: DocumentImageStore): CanvasDocument {
        if (raw.isNullOrBlank()) {
            return CanvasDocument.default()
        }

        return runCatching {
            val root = JSONObject(raw)
            val blocksJson = root.optJSONArray("blocks") ?: JSONArray()
            val blocks = buildList {
                for (index in 0 until blocksJson.length()) {
                    decodeImportedBlock(blocksJson.getJSONObject(index), imageStore)?.let(::add)
                }
            }
            CanvasDocument(blocks = blocks)
        }.getOrElse {
            CanvasDocument.default()
        }
    }

    private fun encodeBlock(block: DocumentBlock): JSONObject {
        return when (block) {
            is TextBlock -> JSONObject()
                .put("type", "text")
                .put("id", block.id)
                .put("markdown", block.markdown)
                .put("alignment", block.alignment.name)
                .put("textSize", block.textSize.name)

            is ImageBlock -> JSONObject()
                .put("type", "image")
                .put("id", block.id)
                .put("imageUri", block.imageUri)
                .put("alignment", block.alignment.name)
                .put("ditheringMode", block.ditheringMode.name)
                .put("width", block.width.name)
        }
    }

    private fun encodeBlockForExport(block: DocumentBlock, imageStore: DocumentImageStore): JSONObject {
        return when (block) {
            is TextBlock -> encodeBlock(block)
            is ImageBlock -> {
                val storedImage = imageStore.readImage(block.imageUri)
                JSONObject()
                    .put("type", "image")
                    .put("id", block.id)
                    .put("alignment", block.alignment.name)
                    .put("ditheringMode", block.ditheringMode.name)
                    .put("width", block.width.name)
                    .put(
                        "image",
                        JSONObject()
                            .put("storage", "embedded")
                            .put("mimeType", storedImage.mimeType)
                            .put("dataBase64", Base64.getEncoder().encodeToString(storedImage.bytes))
                    )
            }
        }
    }

    private fun decodeBlock(jsonObject: JSONObject): DocumentBlock? {
        return when (jsonObject.optString("type")) {
            "text" -> TextBlock(
                id = jsonObject.optString("id"),
                markdown = jsonObject.optString("markdown")
                    .ifBlank { jsonObject.optString("text") },
                alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                textSize = decodeTextSize(jsonObject)
            )

            "image" -> ImageBlock(
                id = jsonObject.optString("id"),
                imageUri = jsonObject.optString("imageUri"),
                alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                ditheringMode = DitheringMode.fromStoredValue(jsonObject.optString("ditheringMode")),
                width = ImageBlockWidth.fromStoredValue(jsonObject.optString("width"))
            )

            // Migrate legacy table blocks into markdown text blocks so saved drafts still load.
            "table" -> TextBlock(
                id = jsonObject.optString("id"),
                markdown = decodeLegacyTableMarkdown(jsonObject),
                alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                textSize = CanvasTextSize.SP14
            )

            else -> null
        }
    }

    private fun decodeImportedBlock(
        jsonObject: JSONObject,
        imageStore: DocumentImageStore
    ): DocumentBlock? {
        if (jsonObject.optString("type") != "image") {
            return decodeBlock(jsonObject)
        }

        val imageObject = jsonObject.optJSONObject("image")
        val embeddedImageData = imageObject
            ?.takeIf { it.optString("storage") == "embedded" }
            ?.let { embedded ->
                val mimeType = embedded.optString("mimeType").ifBlank { DEFAULT_EMBEDDED_IMAGE_MIME_TYPE }
                val data = embedded.optString("dataBase64")
                    .takeIf(String::isNotBlank)
                    ?.let { Base64.getDecoder().decode(it) }
                    ?: return null
                imageStore.persistEmbeddedImage(mimeType, data)
            }

        return ImageBlock(
            id = jsonObject.optString("id"),
            imageUri = embeddedImageData ?: jsonObject.optString("imageUri"),
            alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
            ditheringMode = DitheringMode.fromStoredValue(jsonObject.optString("ditheringMode")),
            width = ImageBlockWidth.fromStoredValue(jsonObject.optString("width"))
        )
    }

    private fun decodeTextSize(jsonObject: JSONObject): CanvasTextSize {
        val directValue = jsonObject.optString("textSize")
        if (directValue.isNotBlank()) {
            return CanvasTextSize.fromStoredValue(directValue)
        }

        val legacyStyle = jsonObject.optJSONObject("style")
        return CanvasTextSize.fromStoredValue(legacyStyle?.optString("textSize"))
    }

    private fun decodeLegacyTableMarkdown(jsonObject: JSONObject): String {
        val rows = jsonObject.optInt("rows").coerceAtLeast(1)
        val columns = jsonObject.optInt("columns").coerceAtLeast(1)
        val cellsJson = jsonObject.optJSONArray("cells") ?: JSONArray()
        val cells = List(rows) { rowIndex ->
            val rowJson = cellsJson.optJSONArray(rowIndex) ?: JSONArray()
            List(columns) { columnIndex ->
                rowJson.optString(columnIndex)
            }
        }

        if (cells.isEmpty()) {
            return ""
        }

        val header = cells.first()
        val separator = List(header.size) { "---" }
        val body = cells.drop(1)
        val markdownRows = buildList {
            add(header)
            add(separator)
            addAll(body)
        }
        return markdownRows.joinToString(separator = "\n") { row ->
            row.joinToString(prefix = "| ", postfix = " |", separator = " | ")
        }
    }

    private const val INTERNAL_VERSION = 3
    private const val EXPORTED_IMAGE_VERSION = 4
    private const val DEFAULT_EMBEDDED_IMAGE_MIME_TYPE = "image/jpeg"
}
