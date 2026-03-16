package com.github.thiagokokada.meowprinter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedImportRequestParserTest {
    @Test
    fun ignoresNonSendIntent() {
        val request = SharedImportRequestParser.resolve(
            SharedImportInput(
                action = "android.intent.action.VIEW",
                mimeType = "text/plain",
                extraStreamUri = null,
                clipDataUri = null,
                extraText = "https://example.com",
                clipDataText = null
            )
        )

        assertEquals(SharedImportResolution.None, request)
    }

    @Test
    fun parsesSharedImageFromExtraStream() {
        val uri = "content://com.github.thiagokokada.meowprinter.test/shared-image"
        val request = SharedImportRequestParser.resolve(
            SharedImportInput(
                action = "android.intent.action.SEND",
                mimeType = "image/png",
                extraStreamUri = uri,
                clipDataUri = null,
                extraText = null,
                clipDataText = null
            )
        )

        assertEquals(SharedImportResolution.Image(uri), request)
    }

    @Test
    fun parsesSharedTextFromExtraText() {
        val request = SharedImportRequestParser.resolve(
            SharedImportInput(
                action = "android.intent.action.SEND",
                mimeType = "text/plain",
                extraStreamUri = null,
                clipDataUri = null,
                extraText = "https://example.com",
                clipDataText = null
            )
        )

        assertEquals(SharedImportResolution.Text("https://example.com"), request)
    }

    @Test
    fun fallsBackToProvidedClipDataText() {
        val request = SharedImportRequestParser.resolve(
            SharedImportInput(
                action = "android.intent.action.SEND",
                mimeType = "text/plain",
                extraStreamUri = null,
                clipDataUri = null,
                extraText = null,
                clipDataText = "Shared from clip data"
            )
        )

        assertEquals(SharedImportResolution.Text("Shared from clip data"), request)
    }

    @Test
    fun ignoresBlankSharedText() {
        val request = SharedImportRequestParser.resolve(
            SharedImportInput(
                action = "android.intent.action.SEND",
                mimeType = "text/plain",
                extraStreamUri = null,
                clipDataUri = null,
                extraText = "   ",
                clipDataText = null
            )
        )

        assertEquals(SharedImportResolution.None, request)
    }
}
