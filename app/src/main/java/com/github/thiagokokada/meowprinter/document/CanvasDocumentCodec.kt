package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.image.DitheringMode
import org.json.JSONArray
import org.json.JSONObject

object CanvasDocumentCodec {
    fun encode(document: CanvasDocument): String {
        return JSONObject()
            .put("version", 1)
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
            if (blocks.isEmpty()) {
                CanvasDocument.default()
            } else {
                CanvasDocument(blocks = blocks)
            }
        }.getOrElse {
            CanvasDocument.default()
        }
    }

    private fun encodeBlock(block: DocumentBlock): JSONObject {
        return when (block) {
            is TextBlock -> JSONObject()
                .put("type", "text")
                .put("id", block.id)
                .put("text", block.text)
                .put("alignment", block.alignment.name)
                .put("style", JSONObject()
                    .put("bold", block.style.isBold)
                    .put("italic", block.style.isItalic)
                    .put("underline", block.style.isUnderline)
                    .put("strikethrough", block.style.isStrikethrough)
                    .put("fontFamily", block.style.fontFamily.name)
                    .put("textSize", block.style.textSize.name)
                )

            is ImageBlock -> JSONObject()
                .put("type", "image")
                .put("id", block.id)
                .put("imageUri", block.imageUri)
                .put("alignment", block.alignment.name)
                .put("ditheringMode", block.ditheringMode.name)

            is TableBlock -> JSONObject()
                .put("type", "table")
                .put("id", block.id)
                .put("alignment", block.alignment.name)
                .put("rows", block.rows)
                .put("columns", block.columns)
                .put("hasHeaderRow", block.hasHeaderRow)
                .put("cells", JSONArray().apply {
                    block.cells.forEach { row ->
                        put(JSONArray().apply {
                            row.forEach(::put)
                        })
                    }
                })
        }
    }

    private fun decodeBlock(jsonObject: JSONObject): DocumentBlock? {
        return when (jsonObject.optString("type")) {
            "text" -> {
                val styleJson = jsonObject.optJSONObject("style") ?: JSONObject()
                TextBlock(
                    id = jsonObject.optString("id"),
                    text = jsonObject.optString("text"),
                    alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                    style = TextBlockStyle(
                        isBold = styleJson.optBoolean("bold"),
                        isItalic = styleJson.optBoolean("italic"),
                        isUnderline = styleJson.optBoolean("underline"),
                        isStrikethrough = styleJson.optBoolean("strikethrough"),
                        fontFamily = CanvasFontFamily.fromStoredValue(styleJson.optString("fontFamily")),
                        textSize = CanvasTextSize.fromStoredValue(styleJson.optString("textSize"))
                    )
                )
            }

            "image" -> ImageBlock(
                id = jsonObject.optString("id"),
                imageUri = jsonObject.optString("imageUri"),
                alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                ditheringMode = DitheringMode.fromStoredValue(jsonObject.optString("ditheringMode"))
            )

            "table" -> {
                val rows = jsonObject.optInt("rows").coerceAtLeast(1)
                val columns = jsonObject.optInt("columns").coerceAtLeast(1)
                val cellsJson = jsonObject.optJSONArray("cells") ?: JSONArray()
                val cells = List(rows) { rowIndex ->
                    val rowJson = cellsJson.optJSONArray(rowIndex) ?: JSONArray()
                    List(columns) { columnIndex ->
                        rowJson.optString(columnIndex)
                    }
                }
                TableBlock(
                    id = jsonObject.optString("id"),
                    alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                    rows = rows,
                    columns = columns,
                    hasHeaderRow = jsonObject.optBoolean("hasHeaderRow"),
                    cells = cells
                )
            }

            else -> null
        }
    }
}
