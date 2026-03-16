package com.github.thiagokokada.meowprinter.document

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object SharedQrPayloadParser {
    private val emailPattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

    fun parse(sharedText: String): QrPayload {
        val text = sharedText.trim()
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            return UrlQrPayload(text)
        }
        if (text.startsWith("mailto:", ignoreCase = true)) {
            return parseMailto(text)
        }
        if (text.startsWith("tel:", ignoreCase = true)) {
            return PhoneQrPayload(text.substringAfter(":", missingDelimiterValue = "").trim())
        }
        if (text.startsWith("smsto:", ignoreCase = true) || text.startsWith("sms:", ignoreCase = true)) {
            return parseSms(text)
        }
        if (text.startsWith("geo:", ignoreCase = true)) {
            return parseGeo(text)
        }
        if (emailPattern.matches(text)) {
            return EmailQrPayload(to = text, subject = "", body = "")
        }
        return TextQrPayload(text)
    }

    private fun parseMailto(text: String): QrPayload {
        val address = text.substringAfter("mailto:", "").substringBefore("?").trim()
        val query = text.substringAfter("?", "")
        val params = parseQueryParams(query)
        return EmailQrPayload(
            to = address,
            subject = params["subject"].orEmpty(),
            body = params["body"].orEmpty()
        )
    }

    private fun parseSms(text: String): QrPayload {
        val withoutScheme = text.substringAfter(":", "")
        val number = withoutScheme.substringBefore(":").trim()
        val message = withoutScheme.substringAfter(":", "").trim()
        return SmsQrPayload(number = number, message = message)
    }

    private fun parseGeo(text: String): QrPayload {
        val schemeSpecific = text.substringAfter("geo:", "")
        val coordinates = schemeSpecific.substringBefore("?")
        val latitude = coordinates.substringBefore(",").trim()
        val longitude = coordinates.substringAfter(",", "").trim()
        return GeoQrPayload(
            latitude = latitude,
            longitude = longitude,
            query = parseQueryParams(schemeSpecific.substringAfter("?", ""))["q"].orEmpty()
        )
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) {
            return emptyMap()
        }
        return query.split("&")
            .mapNotNull { entry ->
                val key = entry.substringBefore("=", "").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val value = entry.substringAfter("=", "")
                key to URLDecoder.decode(value, StandardCharsets.UTF_8)
            }
            .toMap()
    }
}
