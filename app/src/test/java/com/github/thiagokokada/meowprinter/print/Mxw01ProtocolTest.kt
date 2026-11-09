package com.github.thiagokokada.meowprinter.print

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mxw01ProtocolTest {
    @Test
    fun controlCommandsMatchReferenceVectors() {
        assertArrayEquals(bytes(0x22, 0x21, 0xA1, 0, 1, 0, 0, 0, 0xFF), Mxw01Protocol.getStatus())
        assertArrayEquals(bytes(0x22, 0x21, 0xAB, 0, 1, 0, 0, 0, 0xFF), Mxw01Protocol.getBattery())
        assertArrayEquals(bytes(0x22, 0x21, 0xAD, 0, 1, 0, 0, 0, 0xFF), Mxw01Protocol.finishPrint())
        assertArrayEquals(bytes(0x22, 0x21, 0xA2, 0, 1, 0, 93, 0x94, 0xFF), Mxw01Protocol.setIntensity(93))
        assertArrayEquals(bytes(0x22, 0x21, 0xA2, 0, 1, 0, 150, 0xEB, 0xFF), Mxw01Protocol.setIntensity(150))
        assertArrayEquals(
            bytes(0x22, 0x21, 0xA9, 0, 4, 0, 90, 0, 0x30, 0, 0x99, 0xFF),
            Mxw01Protocol.startPrint(90)
        )
        assertArrayEquals(
            bytes(0x22, 0x21, 0xA9, 0, 4, 0, 0x34, 0x12, 0x30, 0, 0x7C, 0xFF),
            Mxw01Protocol.startPrint(0x1234)
        )
    }

    @Test
    fun crcUsesNonReflectedPolynomial07OverPayloadOnly() {
        val payload = "123456789".toByteArray(Charsets.US_ASCII)
        for (id in listOf(0, 0xA2, 255)) {
            val command = Mxw01Protocol.command(id, payload)
            assertEquals(0xF4, command[command.lastIndex - 1].toInt() and 0xFF)
        }
        assertArrayEquals(bytes(0x22, 0x21, 0, 0, 0, 0, 0, 0xFF), Mxw01Protocol.command(0, byteArrayOf()))
    }

    @Test
    fun commandLengthIsUnsignedLittleEndian() {
        for (length in listOf(255, 256, 65535)) {
            val frame = Mxw01Protocol.command(0xA2, ByteArray(length))
            assertEquals(length and 255, frame[4].toInt() and 255)
            assertEquals(length shr 8, frame[5].toInt() and 255)
            assertEquals(length + 8, frame.size)
        }
    }

    @Test
    fun packsLeftmostPixelIntoLeastSignificantBit() {
        val row = BooleanArray(384).apply {
            this[0] = true
            this[7] = true
            this[8] = true
            this[383] = true
        }
        val encoded = Mxw01Protocol.encodeImage(listOf(row))
        assertEquals(0x81, encoded[0].toInt() and 255)
        assertEquals(1, encoded[1].toInt())
        assertEquals(0x80, encoded[47].toInt() and 255)
        assertTrue(encoded.drop(48).all { it == 0.toByte() })
    }

    @Test
    fun blackWhiteAndAlternatingRowsPreservePolarityAndOrder() {
        val encoded = Mxw01Protocol.encodeImage(listOf(
            BooleanArray(384) { true }, BooleanArray(384), BooleanArray(384) { it % 2 == 0 }
        ))
        assertArrayEquals(ByteArray(48) { 0xFF.toByte() }, encoded.copyOfRange(0, 48))
        assertArrayEquals(ByteArray(48), encoded.copyOfRange(48, 96))
        assertArrayEquals(ByteArray(48) { 0x55 }, encoded.copyOfRange(96, 144))
    }

    @Test
    fun padsShortImagesWithoutChangingLongImages() {
        val row = BooleanArray(384) { true }
        for (height in listOf(1, 89, 90, 91, 65535)) {
            val encoded = Mxw01Protocol.encodeImage(List(height) { row })
            assertEquals(maxOf(height, 90) * 48, encoded.size)
            assertEquals(0xFF.toByte(), encoded[height * 48 - 1])
            assertTrue(encoded.asSequence().drop(height * 48).all { it == 0.toByte() })
            val request = Mxw01Protocol.startPrint(encoded.size / 48)
            assertEquals(maxOf(height, 90), (request[6].toInt() and 255) or ((request[7].toInt() and 255) shl 8))
        }
    }

    @Test
    fun rejectsInvalidDimensionsAndCommandLimits() {
        assertInvalid { Mxw01Protocol.encodeImage(emptyList()) }
        assertInvalid { Mxw01Protocol.encodeImage(listOf(BooleanArray(383))) }
        assertInvalid { Mxw01Protocol.encodeImage(List(65536) { BooleanArray(0) }) }
        assertInvalid { Mxw01Protocol.startPrint(0) }
        assertInvalid { Mxw01Protocol.startPrint(65536) }
        assertInvalid { Mxw01Protocol.command(-1) }
        assertInvalid { Mxw01Protocol.command(256) }
        assertInvalid { Mxw01Protocol.command(0, ByteArray(65536)) }
    }

    @Test
    fun acceptsCompleteNotificationPayloadWithOptionalTrailerAndUnknownHeaderByte() {
        val prefix = bytes(0x22, 0x21, 0xAB, 3, 1, 0, 78)
        for (packet in listOf(prefix, prefix + bytes(0xFF), prefix + bytes(0, 0xFF))) {
            val parsed = requireNotNull(Mxw01Protocol.parseNotification(packet))
            assertEquals(0xAB, parsed.id)
            assertArrayEquals(bytes(78), parsed.payload)
        }
        assertArrayEquals(byteArrayOf(), Mxw01Protocol.parseNotification(bytes(0x22, 0x21, 0xAA, 0, 0, 0))?.payload)
    }

    @Test
    fun rejectsWrongHeaderAndTruncatedPayloadIncludingHighLengthByte() {
        assertNull(Mxw01Protocol.parseNotification(bytes(0x22, 0x21, 0xAB, 0, 1)))
        assertNull(Mxw01Protocol.parseNotification(bytes(0x51, 0x78, 0xAB, 0, 1, 0, 68)))
        assertNull(Mxw01Protocol.parseNotification(bytes(0x22, 0x21, 0xAA, 0, 3, 0, 0, 0)))
        assertNull(Mxw01Protocol.parseNotification(bytes(0x22, 0x21, 0xAB, 0, 0, 1, 68)))
    }

    private fun assertInvalid(action: () -> Unit) {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java, action)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
