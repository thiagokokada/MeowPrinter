package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.data.DocumentImageStore
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImageProcessingMode
import com.github.thiagokokada.meowprinter.image.ImageResizerMode
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
                .put("textFont", block.textFont.name)

            is ImageBlock -> JSONObject()
                .put("type", "image")
                .put("id", block.id)
                .put("imageUri", block.imageUri)
                .put("alignment", block.alignment.name)
                .put("ditheringMode", block.ditheringMode.name)
                .put("processingMode", block.processingMode.name)
                .put("resizerMode", block.resizerMode.name)
                .put("width", block.width.name)

            is QrBlock -> JSONObject()
                .put("type", "qr")
                .put("id", block.id)
                .put("alignment", block.alignment.name)
                .put("size", block.size.name)
                .put("payload", encodeQrPayload(block.payload))
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
                    .put("processingMode", block.processingMode.name)
                    .put("resizerMode", block.resizerMode.name)
                    .put("width", block.width.name)
                    .put(
                        "image",
                        JSONObject()
                            .put("storage", "embedded")
                            .put("mimeType", storedImage.mimeType)
                            .put("dataBase64", Base64.getEncoder().encodeToString(storedImage.bytes))
                    )
            }
            is QrBlock -> encodeBlock(block)
        }
    }

    private fun decodeBlock(jsonObject: JSONObject): DocumentBlock? {
        return when (jsonObject.optString("type")) {
            "text" -> TextBlock(
                id = jsonObject.optString("id"),
                markdown = jsonObject.optString("markdown"),
                alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                textSize = CanvasTextSize.fromStoredValue(jsonObject.optString("textSize")),
                textFont = CanvasTextFont.fromStoredValue(jsonObject.optString("textFont"))
            )

            "image" -> ImageBlock(
                id = jsonObject.optString("id"),
                imageUri = jsonObject.optString("imageUri"),
                alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
                ditheringMode = DitheringMode.fromStoredValue(jsonObject.optString("ditheringMode")),
                processingMode = ImageProcessingMode.fromStoredValue(jsonObject.optString("processingMode")),
                resizerMode = ImageResizerMode.fromStoredValue(jsonObject.optString("resizerMode")),
                width = ImageBlockWidth.fromStoredValue(jsonObject.optString("width"))
            )

            "qr" -> decodeQrBlock(jsonObject)

            else -> null
        }
    }

    private fun decodeQrBlock(jsonObject: JSONObject): QrBlock? {
        val payload = decodeQrPayload(jsonObject.optJSONObject("payload") ?: return null) ?: return null
        return QrBlock(
            id = jsonObject.optString("id"),
            payload = payload,
            alignment = BlockAlignment.fromStoredValue(jsonObject.optString("alignment")),
            size = QrBlockSize.fromStoredValue(jsonObject.optString("size"))
        )
    }

    private fun encodeQrPayload(payload: QrPayload): JSONObject {
        return when (payload) {
            is TextQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("text", payload.text)

            is UrlQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("url", payload.url)

            is WifiQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("ssid", payload.ssid)
                .put("password", payload.password)
                .put("security", payload.security.name)
                .put("hidden", payload.hidden)

            is PhoneQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("number", payload.number)

            is EmailQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("to", payload.to)
                .put("subject", payload.subject)
                .put("body", payload.body)

            is SmsQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("number", payload.number)
                .put("message", payload.message)

            is GeoQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("latitude", payload.latitude)
                .put("longitude", payload.longitude)
                .put("query", payload.query)

            is ContactQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("name", payload.name)
                .put("phone", payload.phone)
                .put("email", payload.email)
                .put("organization", payload.organization)
                .put("address", payload.address)
                .put("url", payload.url)

            is CalendarQrPayload -> JSONObject()
                .put("type", payload.type.name)
                .put("title", payload.title)
                .put("start", payload.start)
                .put("end", payload.end)
                .put("location", payload.location)
                .put("description", payload.description)
        }
    }

    private fun decodeQrPayload(jsonObject: JSONObject): QrPayload? {
        return when (QrContentType.fromStoredValue(jsonObject.optString("type"))) {
            QrContentType.TEXT -> TextQrPayload(
                text = jsonObject.optString("text")
            )

            QrContentType.URL -> UrlQrPayload(
                url = jsonObject.optString("url")
            )

            QrContentType.WIFI -> WifiQrPayload(
                ssid = jsonObject.optString("ssid"),
                password = jsonObject.optString("password"),
                security = QrWifiSecurity.fromStoredValue(jsonObject.optString("security")),
                hidden = jsonObject.optBoolean("hidden", false)
            )

            QrContentType.PHONE -> PhoneQrPayload(
                number = jsonObject.optString("number")
            )

            QrContentType.EMAIL -> EmailQrPayload(
                to = jsonObject.optString("to"),
                subject = jsonObject.optString("subject"),
                body = jsonObject.optString("body")
            )

            QrContentType.SMS -> SmsQrPayload(
                number = jsonObject.optString("number"),
                message = jsonObject.optString("message")
            )

            QrContentType.GEO -> GeoQrPayload(
                latitude = jsonObject.optString("latitude"),
                longitude = jsonObject.optString("longitude"),
                query = jsonObject.optString("query")
            )

            QrContentType.CONTACT -> ContactQrPayload(
                name = jsonObject.optString("name"),
                phone = jsonObject.optString("phone"),
                email = jsonObject.optString("email"),
                organization = jsonObject.optString("organization"),
                address = jsonObject.optString("address"),
                url = jsonObject.optString("url")
            )

            QrContentType.CALENDAR -> CalendarQrPayload(
                title = jsonObject.optString("title"),
                start = jsonObject.optString("start"),
                end = jsonObject.optString("end"),
                location = jsonObject.optString("location"),
                description = jsonObject.optString("description")
            )
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
            processingMode = ImageProcessingMode.fromStoredValue(jsonObject.optString("processingMode")),
            resizerMode = ImageResizerMode.fromStoredValue(jsonObject.optString("resizerMode")),
            width = ImageBlockWidth.fromStoredValue(jsonObject.optString("width"))
        )
    }

    private companion object {
        private const val DEFAULT_EMBEDDED_IMAGE_MIME_TYPE = "image/jpeg"
    }
}
