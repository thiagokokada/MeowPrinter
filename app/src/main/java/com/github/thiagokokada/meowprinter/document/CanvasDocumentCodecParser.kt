package com.github.thiagokokada.meowprinter.document

import com.github.thiagokokada.meowprinter.data.DocumentImageStore
import org.json.JSONObject

interface CanvasDocumentCodecParser {
    val version: Int

    fun encode(document: CanvasDocument): JSONObject

    fun encodeForExport(document: CanvasDocument, imageStore: DocumentImageStore): JSONObject

    fun decode(root: JSONObject): CanvasDocument

    fun decodeImported(root: JSONObject, imageStore: DocumentImageStore): CanvasDocument
}
