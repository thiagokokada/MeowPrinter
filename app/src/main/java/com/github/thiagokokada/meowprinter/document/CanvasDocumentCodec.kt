package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.data.DocumentImageStore
import org.json.JSONObject

object CanvasDocumentCodec {
    private val parsers: Map<Int, CanvasDocumentCodecParser> = listOf(
        CanvasDocumentCodecParserV1(),
        CanvasDocumentCodecParserV2()
    ).associateBy(CanvasDocumentCodecParser::version)

    private val currentParser: CanvasDocumentCodecParser = requireNotNull(parsers[CURRENT_VERSION]) {
        "Missing canvas document parser for version $CURRENT_VERSION"
    }

    fun encode(document: CanvasDocument): String {
        return currentParser.encode(document).toString()
    }

    fun encodeForExport(document: CanvasDocument, imageStore: DocumentImageStore): String {
        return currentParser.encodeForExport(document, imageStore).toString()
    }

    fun decode(raw: String?): CanvasDocument {
        return decodeWithParser(raw) { parser, root ->
            parser.decode(root)
        }
    }

    fun decodeImported(raw: String?, imageStore: DocumentImageStore): CanvasDocument {
        return decodeWithParser(raw) { parser, root ->
            parser.decodeImported(root, imageStore)
        }
    }

    private fun decodeWithParser(
        raw: String?,
        decode: (CanvasDocumentCodecParser, JSONObject) -> CanvasDocument
    ): CanvasDocument {
        if (raw.isNullOrBlank()) {
            return CanvasDocument.default()
        }

        return runCatching {
            val root = JSONObject(raw)
            val version = root.optInt("version", CURRENT_VERSION)
            val parser = requireNotNull(parsers[version]) {
                "Unsupported canvas document version: $version"
            }
            decode(parser, root)
        }.getOrElse {
            CanvasDocument.default()
        }
    }

    private const val CURRENT_VERSION = 2
}
