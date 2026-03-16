package com.github.thiagokokada.meowprinter.document

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object QrContentEncoder {
    fun encode(payload: QrPayload): String {
        return when (payload) {
            is TextQrPayload -> payload.text
            is UrlQrPayload -> payload.url
            is WifiQrPayload -> buildString {
                append("WIFI:")
                append("T:${payload.security.encodedValue};")
                if (payload.security.transitionModeDisabled) {
                    append("R:1;")
                }
                append("S:${escapeWifiValue(payload.ssid)};")
                if (payload.security != QrWifiSecurity.NONE) {
                    append("P:${escapeWifiValue(payload.password)};")
                }
                if (payload.hidden) {
                    append("H:true;")
                }
                append(";")
            }
            is PhoneQrPayload -> "tel:${payload.number}"
            is EmailQrPayload -> buildString {
                append("mailto:${payload.to}")
                val queryParts = buildList {
                    if (payload.subject.isNotBlank()) add("subject=${urlEncode(payload.subject)}")
                    if (payload.body.isNotBlank()) add("body=${urlEncode(payload.body)}")
                }
                if (queryParts.isNotEmpty()) {
                    append("?")
                    append(queryParts.joinToString("&"))
                }
            }
            is SmsQrPayload -> "SMSTO:${payload.number}:${payload.message}"
            is GeoQrPayload -> buildString {
                append("geo:${payload.latitude},${payload.longitude}")
                if (payload.query.isNotBlank()) {
                    append("?q=${urlEncode(payload.query)}")
                }
            }
            is ContactQrPayload -> buildString {
                append("MECARD:")
                appendMecardField("N", payload.name)
                appendMecardField("TEL", payload.phone)
                appendMecardField("EMAIL", payload.email)
                appendMecardField("ORG", payload.organization)
                appendMecardField("ADR", payload.address)
                appendMecardField("URL", payload.url)
                append(";")
            }
            is CalendarQrPayload -> buildString {
                appendLine("BEGIN:VEVENT")
                appendLine("SUMMARY:${escapeCalendarValue(payload.title)}")
                appendLine("DTSTART:${escapeCalendarValue(payload.start)}")
                appendLine("DTEND:${escapeCalendarValue(payload.end)}")
                if (payload.location.isNotBlank()) {
                    appendLine("LOCATION:${escapeCalendarValue(payload.location)}")
                }
                if (payload.description.isNotBlank()) {
                    appendLine("DESCRIPTION:${escapeCalendarValue(payload.description)}")
                }
                append("END:VEVENT")
            }
        }
    }

    private fun StringBuilder.appendMecardField(name: String, value: String) {
        if (value.isBlank()) {
            return
        }
        append("$name:${escapeMecardValue(value)};")
    }

    private fun escapeWifiValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")
    }

    private fun escapeMecardValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(":", "\\:")
            .replace(",", "\\,")
    }

    private fun escapeCalendarValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
