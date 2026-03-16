package com.github.thiagokokada.meowprinter.document

import org.junit.Assert.assertEquals
import org.junit.Test

class QrContentEncoderTest {
    @Test
    fun encodesWifiPayload() {
        val encoded = QrContentEncoder.encode(
            WifiQrPayload(
                ssid = "MeowNet",
                password = "secret",
                security = QrWifiSecurity.WPA,
                hidden = true
            )
        )

        assertEquals("WIFI:T:WPA;S:MeowNet;P:secret;H:true;;", encoded)
    }

    @Test
    fun encodesWpa3OnlyWifiPayloadWithTransitionModeDisabled() {
        val encoded = QrContentEncoder.encode(
            WifiQrPayload(
                ssid = "MeowNet",
                password = "secret",
                security = QrWifiSecurity.WPA3_ONLY,
                hidden = false
            )
        )

        assertEquals("WIFI:T:WPA;R:1;S:MeowNet;P:secret;;", encoded)
    }

    @Test
    fun encodesEmailPayload() {
        val encoded = QrContentEncoder.encode(
            EmailQrPayload(
                to = "cat@example.com",
                subject = "Hello world",
                body = "Printed by Meow Printer"
            )
        )

        assertEquals(
            "mailto:cat@example.com?subject=Hello+world&body=Printed+by+Meow+Printer",
            encoded
        )
    }

    @Test
    fun encodesContactPayloadAsMecard() {
        val encoded = QrContentEncoder.encode(
            ContactQrPayload(
                name = "Cat Printer",
                phone = "+123",
                email = "cat@example.com",
                organization = "Meow Printer",
                address = "123 Printer St",
                url = "https://example.com"
            )
        )

        assertEquals(
            "MECARD:N:Cat Printer;TEL:+123;EMAIL:cat@example.com;ORG:Meow Printer;ADR:123 Printer St;URL:https\\://example.com;;",
            encoded
        )
    }
}
