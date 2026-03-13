package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.image.DitheringMode
import org.json.JSONArray
import org.json.JSONObject

object CanvasDocumentCodec {
    fun encode(document: CanvasDocument): String {
        return JSONObject()
            .put("version", 3)
            .put("blocks", JSONArray().apply {
                document.blocks.forEach { block ->
                    put(encodeBlock(block))
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
}
