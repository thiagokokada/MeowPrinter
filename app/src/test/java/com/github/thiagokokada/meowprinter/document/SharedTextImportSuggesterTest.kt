package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextImportSuggesterTest {
    @Test
    fun prefersQrCodeForUrlLikeText() {
        val suggestion = SharedTextImportSuggester.suggest("https://example.com")

        assertEquals(UrlQrPayload("https://example.com"), suggestion.qrPayload)
        assertEquals(SharedTextImportTarget.QR_CODE, suggestion.defaultTarget)
    }

    @Test
    fun prefersQrCodeForEmailLikeText() {
        val suggestion = SharedTextImportSuggester.suggest("cat@example.com")

        assertEquals(EmailQrPayload("cat@example.com", "", ""), suggestion.qrPayload)
        assertEquals(SharedTextImportTarget.QR_CODE, suggestion.defaultTarget)
    }

    @Test
    fun prefersTextBlockForPlainText() {
        val suggestion = SharedTextImportSuggester.suggest("Share me as plain text")

        assertEquals(TextQrPayload("Share me as plain text"), suggestion.qrPayload)
        assertEquals(SharedTextImportTarget.TEXT_BLOCK, suggestion.defaultTarget)
    }
}
