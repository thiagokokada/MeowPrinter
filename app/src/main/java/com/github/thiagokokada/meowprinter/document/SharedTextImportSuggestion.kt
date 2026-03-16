package com.github.thiagokokada.meowprinter.document

enum class SharedTextImportTarget {
    TEXT_BLOCK,
    QR_CODE,
}

data class SharedTextImportSuggestion(
    val text: String,
    val qrPayload: QrPayload,
    val defaultTarget: SharedTextImportTarget,
)

object SharedTextImportSuggester {
    fun suggest(sharedText: String): SharedTextImportSuggestion {
        val qrPayload = SharedQrPayloadParser.parse(sharedText)
        return SharedTextImportSuggestion(
            text = sharedText,
            qrPayload = qrPayload,
            defaultTarget = when (qrPayload) {
                is TextQrPayload -> SharedTextImportTarget.TEXT_BLOCK
                else -> SharedTextImportTarget.QR_CODE
            }
        )
    }
}
