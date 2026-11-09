package com.github.thiagokokada.meowprinter.print

import java.io.ByteArrayOutputStream
import java.util.UUID

object Mxw01Protocol {
    const val PRINT_WIDTH = 384
    const val BYTES_PER_ROW = PRINT_WIDTH / 8
    private const val MIN_IMAGE_BYTES = 4320

    val dataCharacteristicUuid: UUID =
        UUID.fromString("0000ae03-0000-1000-8000-00805f9b34fb")

    fun command(id: Int, payload: ByteArray = byteArrayOf(0)): ByteArray {
        require(id in 0..255)
        require(payload.size <= 65535)

        val output = ByteArray(8 + payload.size)
        output[0] = 0x22
        output[1] = 0x21
        output[2] = id.toByte()
        output[3] = 0
        output[4] = (payload.size and 0xFF).toByte()
        output[5] = ((payload.size shr 8) and 0xFF).toByte()

        payload.copyInto(output, destinationOffset = 6)
        output[6 + payload.size] = crc8(payload)
        output[7 + payload.size] = 0xFF.toByte()
        return output
    }

    fun setIntensity(value: Int): ByteArray =
        command(0xA2, byteArrayOf(value.coerceIn(0, 255).toByte()))

    fun getStatus(): ByteArray = command(0xA1)

    fun getBattery(): ByteArray = command(0xAB)

    fun startPrint(lineCount: Int): ByteArray {
        require(lineCount in 1..65535)
        return command(
            0xA9,
            byteArrayOf(
                (lineCount and 0xFF).toByte(),
                ((lineCount shr 8) and 0xFF).toByte(),
                0x30,
                0x00
            )
        )
    }

    fun finishPrint(): ByteArray = command(0xAD)

    fun encodeImage(rows: List<BooleanArray>): ByteArray {
        require(rows.isNotEmpty())
        require(rows.size <= 65535)
        require(rows.all { it.size == PRINT_WIDTH })

        val output = ByteArrayOutputStream()

        for (row in rows) {
            for (start in 0 until PRINT_WIDTH step 8) {
                var packed = 0

                for (bit in 0 until 8) {
                    if (row[start + bit]) {
                        packed = packed or (1 shl bit)
                    }
                }

                output.write(packed)
            }
        }

        val image = output.toByteArray()
        require(image.size == rows.size * BYTES_PER_ROW)

        return if (image.size >= MIN_IMAGE_BYTES) {
            image
        } else {
            image.copyOf(MIN_IMAGE_BYTES)
        }
    }

    data class Notification(val id: Int, val payload: ByteArray)

    /**
     * Accept only a complete declared payload. Public firmware reports disagree about
     * trailing CRC/footer bytes, so do not require or interpret them without captures.
     * This parses one notification, not a fragmented or concatenated byte stream.
     */
    fun parseNotification(packet: ByteArray): Notification? {
        if (packet.size < 6) return null
        if ((packet[0].toInt() and 0xFF) != 0x22) return null
        if ((packet[1].toInt() and 0xFF) != 0x21) return null

        val length = (packet[4].toInt() and 0xFF) or
            ((packet[5].toInt() and 0xFF) shl 8)

        if (packet.size < 6 + length) return null
        return Notification(packet[2].toInt() and 0xFF, packet.copyOfRange(6, 6 + length))
    }

    private fun crc8(data: ByteArray): Byte {
        var crc = 0

        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }

        return crc.toByte()
    }
}
