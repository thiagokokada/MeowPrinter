package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.data.DocumentImageStore
import com.github.thiagokokada.meowprinter.image.DitheringMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

class CanvasDocumentCodecParserV1 : CanvasDocumentCodecParser {
    override val version: Int = 1

    override fun encode(document: CanvasDocument): JSONObject {
        return JSONObject()
            .put("version", version)
            .put("blocks", JSONArray().apply {
                document.blocks.forEach { block ->
                    put(encodeBlock(block))
                }
            })
    }

    override fun encodeForExport(document: CanvasDocument, imageStore: DocumentImageStore): JSONObject {
        return JSONObject()
            .put("version", version)
            .put("blocks", JSONArray().apply {
                document.blocks.forEach { block ->
                    put(encodeBlockForExport(block, imageStore))
                }
            })
    }

    override fun decode(root: JSONObject): CanvasDocument {
        val blocksJson = root.optJSONArray("blocks") ?: JSONArray()
        val blocks = buildList {
            for (index in 0 until blocksJson.length()) {
                decodeBlock(blocksJson.getJSONObject(index))?.let(::add)
            }
        }
        return CanvasDocument(blocks = blocks)
    }

    override fun decodeImported(root: JSONObject, imageStore: DocumentImageStore): CanvasDocument {
        val blocksJson = root.optJSONArray("blocks") ?: JSONArray()
        val blocks = buildList {
            for (index in 0 until blocksJson.length()) {
                decodeImportedBlock(blocksJson.getJSONObject(index), imageStore)?.let(::add)
            }
        }
        return CanvasDocument(blocks = blocks)
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
                markdown = jsonObject.optString("markdown"),
                alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                textSize = CanvasTextSize.fromStoredValue(jsonObject.optString("textSize"))
            )

            "image" -> ImageBlock(
                id = jsonObject.optString("id"),
                imageUri = jsonObject.optString("imageUri"),
                alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                ditheringMode = DitheringMode.fromStoredValue(jsonObject.optString("ditheringMode")),
                width = ImageBlockWidth.fromStoredValue(jsonObject.optString("width"))
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
            ?.takeIf { it.optString("storage") == "embedded" }
            ?: return null
        val mimeType = imageObject.optString("mimeType").ifBlank { DEFAULT_EMBEDDED_IMAGE_MIME_TYPE }
        val data = imageObject.optString("dataBase64")
            .takeIf(String::isNotBlank)
            ?.let { Base64.getDecoder().decode(it) }
            ?: return null
        val embeddedImageUri = imageStore.persistEmbeddedImage(mimeType, data)

        return ImageBlock(
            id = jsonObject.optString("id"),
            imageUri = embeddedImageUri,
            alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
            ditheringMode = DitheringMode.fromStoredValue(jsonObject.optString("ditheringMode")),
            width = ImageBlockWidth.fromStoredValue(jsonObject.optString("width"))
        )
    }

    private companion object {
        private const val DEFAULT_EMBEDDED_IMAGE_MIME_TYPE = "image/jpeg"
    }
}
