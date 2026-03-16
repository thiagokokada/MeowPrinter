package com.github.thiagokokada.meowprinter.document

enum class QrContentType(
    val displayName: String
) {
    TEXT("Text"),
    URL("URL"),
    WIFI("WiFi"),
    PHONE("Phone"),
    EMAIL("Email"),
    SMS("SMS"),
    GEO("Geo"),
    CONTACT("Contact"),
    CALENDAR("Calendar");

    companion object {
        fun fromStoredValue(value: String?): QrContentType {
            return entries.firstOrNull { it.name == value } ?: TEXT
        }
    }
}

enum class QrBlockSize(
    val displayName: String,
    val fraction: Float
) {
    SMALL("Small", 0.35f),
    MEDIUM("Medium", 0.5f),
    LARGE("Large", 0.7f),
    FULL("Full", 1f);

    companion object {
        fun fromStoredValue(value: String?): QrBlockSize {
            return entries.firstOrNull { it.name == value } ?: MEDIUM
        }
    }
}

enum class QrWifiSecurity(
    val displayName: String,
    val encodedValue: String,
    val transitionModeDisabled: Boolean = false
) {
    WPA("WPA/WPA2/WPA3", "WPA"),
    WPA3_ONLY("WPA3-only", "WPA", transitionModeDisabled = true),
    WEP("WEP", "WEP"),
    NONE("None", "nopass");

    companion object {
        fun fromStoredValue(value: String?): QrWifiSecurity {
            return entries.firstOrNull { it.name == value } ?: WPA
        }
    }
}

sealed interface QrPayload {
    val type: QrContentType
}

data class TextQrPayload(
    val text: String
) : QrPayload {
    override val type: QrContentType = QrContentType.TEXT
}

data class UrlQrPayload(
    val url: String
) : QrPayload {
    override val type: QrContentType = QrContentType.URL
}

data class WifiQrPayload(
    val ssid: String,
    val password: String,
    val security: QrWifiSecurity,
    val hidden: Boolean
) : QrPayload {
    override val type: QrContentType = QrContentType.WIFI
}

data class PhoneQrPayload(
    val number: String
) : QrPayload {
    override val type: QrContentType = QrContentType.PHONE
}

data class EmailQrPayload(
    val to: String,
    val subject: String,
    val body: String
) : QrPayload {
    override val type: QrContentType = QrContentType.EMAIL
}

data class SmsQrPayload(
    val number: String,
    val message: String
) : QrPayload {
    override val type: QrContentType = QrContentType.SMS
}

data class GeoQrPayload(
    val latitude: String,
    val longitude: String,
    val query: String
) : QrPayload {
    override val type: QrContentType = QrContentType.GEO
}

data class ContactQrPayload(
    val name: String,
    val phone: String,
    val email: String,
    val organization: String,
    val address: String,
    val url: String
) : QrPayload {
    override val type: QrContentType = QrContentType.CONTACT
}

data class CalendarQrPayload(
    val title: String,
    val start: String,
    val end: String,
    val location: String,
    val description: String
) : QrPayload {
    override val type: QrContentType = QrContentType.CALENDAR
}

fun defaultQrPayload(type: QrContentType): QrPayload {
    return when (type) {
        QrContentType.TEXT -> TextQrPayload(text = "")
        QrContentType.URL -> UrlQrPayload(url = "")
        QrContentType.WIFI -> WifiQrPayload(
            ssid = "",
            password = "",
            security = QrWifiSecurity.WPA,
            hidden = false
        )
        QrContentType.PHONE -> PhoneQrPayload(number = "")
        QrContentType.EMAIL -> EmailQrPayload(to = "", subject = "", body = "")
        QrContentType.SMS -> SmsQrPayload(number = "", message = "")
        QrContentType.GEO -> GeoQrPayload(latitude = "", longitude = "", query = "")
        QrContentType.CONTACT -> ContactQrPayload(
            name = "",
            phone = "",
            email = "",
            organization = "",
            address = "",
            url = ""
        )
        QrContentType.CALENDAR -> CalendarQrPayload(
            title = "",
            start = "",
            end = "",
            location = "",
            description = ""
        )
    }
}
