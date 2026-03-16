package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedQrPayloadParserTest {
    @Test
    fun parsesUrl() {
        val payload = SharedQrPayloadParser.parse("https://example.com")

        assertEquals(UrlQrPayload("https://example.com"), payload)
    }

    @Test
    fun parsesMailtoWithSubjectAndBody() {
        val payload = SharedQrPayloadParser.parse("mailto:cat@example.com?subject=Hello&body=Meow")

        assertEquals(
            EmailQrPayload(
                to = "cat@example.com",
                subject = "Hello",
                body = "Meow"
            ),
            payload
        )
    }

    @Test
    fun parsesUppercaseMailto() {
        val payload = SharedQrPayloadParser.parse("MAILTO:cat@example.com?subject=Hello")

        assertEquals(
            EmailQrPayload(
                to = "cat@example.com",
                subject = "Hello",
                body = ""
            ),
            payload
        )
    }

    @Test
    fun fallsBackToRawMalformedPercentEncodedQueryValue() {
        val payload = SharedQrPayloadParser.parse("mailto:cat@example.com?body=100%")

        assertEquals(
            EmailQrPayload(
                to = "cat@example.com",
                subject = "",
                body = "100%"
            ),
            payload
        )
    }

    @Test
    fun parsesPlainEmailAddress() {
        val payload = SharedQrPayloadParser.parse("cat@example.com")

        assertEquals(EmailQrPayload(to = "cat@example.com", subject = "", body = ""), payload)
    }

    @Test
    fun parsesSms() {
        val payload = SharedQrPayloadParser.parse("smsto:+3531234567:Hello there")

        assertEquals(SmsQrPayload(number = "+3531234567", message = "Hello there"), payload)
    }

    @Test
    fun parsesGeoUri() {
        val payload = SharedQrPayloadParser.parse("geo:53.3498,-6.2603?q=Dublin")

        assertEquals(
            GeoQrPayload(
                latitude = "53.3498",
                longitude = "-6.2603",
                query = "Dublin"
            ),
            payload
        )
    }

    @Test
    fun fallsBackToText() {
        val payload = SharedQrPayloadParser.parse("Share me as plain text")

        assertEquals(TextQrPayload("Share me as plain text"), payload)
    }
}
