package com.github.thiagokokada.meowprinter.print

import java.io.ByteArrayOutputStream
import java.util.UUID

object CatPrinterProtocol {
    const val PRINT_WIDTH = 384

    val primaryServiceUuid: UUID = UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb")
    val secondaryServiceUuid: UUID = UUID.fromString("0000af30-0000-1000-8000-00805f9b34fb")
    val txCharacteristicUuid: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
    val rxCharacteristicUuid: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")

    val readyNotification = bytes(81, 120, -82, 1, 1, 0, 0, 0, -1)

    private val getDeviceState = bytes(81, 120, -93, 0, 1, 0, 0, 0, -1)
    private val setQuality200Dpi = bytes(81, 120, -92, 0, 1, 0, 50, -98, -1)
    private val latticeStart = bytes(
        81, 120, -90, 0, 11, 0, -86, 85, 23,
        56, 68, 95, 95, 95, 68, 56, 44, -95, -1
    )
    private val latticeEnd = bytes(
        81, 120, -90, 0, 11, 0, -86, 85,
        23, 0, 0, 0, 0, 0, 0, 0, 23, 17, -1
    )
    private val setPaper = bytes(81, 120, -95, 0, 2, 0, 48, 0, -7, -1)

    private val checksumTable = bytes(
        0, 7, 14, 9, 28, 27, 18, 21, 56, 63, 54, 49, 36, 35, 42, 45,
        112, 119, 126, 121, 108, 107, 98, 101, 72, 79, 70, 65, 84, 83, 90, 93,
        -32, -25, -18, -23, -4, -5, -14, -11, -40, -33, -42, -47, -60, -61, -54, -51,
        -112, -105, -98, -103, -116, -117, -126, -123, -88, -81, -90, -95, -76, -77, -70, -67,
        -57, -64, -55, -50, -37, -36, -43, -46, -1, -8, -15, -10, -29, -28, -19, -22,
        -73, -80, -71, -66, -85, -84, -91, -94, -113, -120, -127, -122, -109, -108, -99, -102,
        39, 32, 41, 46, 59, 60, 53, 50, 31, 24, 17, 22, 3, 4, 13, 10,
        87, 80, 89, 94, 75, 76, 69, 66, 111, 104, 97, 102, 115, 116, 125, 122,
        -119, -114, -121, -128, -107, -110, -101, -100, -79, -74, -65, -72, -83, -86, -93, -92,
        -7, -2, -9, -16, -27, -30, -21, -20, -63, -58, -49, -56, -35, -38, -45, -44,
        105, 110, 103, 96, 117, 114, 123, 124, 81, 86, 95, 88, 77, 74, 67, 68,
        25, 30, 23, 16, 5, 2, 11, 12, 33, 38, 47, 40, 61, 58, 51, 52,
        78, 73, 64, 71, 82, 85, 92, 91, 118, 113, 120, 127, 106, 109, 100, 99,
        62, 57, 48, 55, 34, 37, 44, 43, 6, 1, 8, 15, 26, 29, 20, 19,
        -82, -87, -96, -89, -78, -75, -68, -69, -106, -111, -104, -97, -118, -115, -124, -125,
        -34, -39, -48, -41, -62, -59, -52, -53, -26, -31, -24, -17, -6, -3, -12, -13
    )

    fun cmdFeedPaper(amount: Int): ByteArray {
        val command = bytes(81, 120, -67, 0, 1, 0, amount and 0xff, 0, -1)
        command[7] = checksum(command, 6, 1)
        return command
    }

    fun cmdSetEnergy(value: Int): ByteArray {
        val command = bytes(
            81, 120, -81, 0, 2, 0,
            (value shr 8) and 0xff,
            value and 0xff,
            0,
            -1
        )
        command[8] = checksum(command, 6, 2)
        return command
    }

    fun cmdApplyEnergy(): ByteArray {
        val command = bytes(81, 120, -66, 0, 1, 0, 1, 0, -1)
        command[7] = checksum(command, 6, 1)
        return command
    }

    fun cmdPrintRow(row: BooleanArray): ByteArray {
        require(row.size == PRINT_WIDTH) { "Expected row width $PRINT_WIDTH but was ${row.size}" }

        val runLengthEncoded = runLengthEncode(row)
        return if (runLengthEncoded.size > PRINT_WIDTH / 8) {
            buildRowCommand(-94, byteEncode(row))
        } else {
            buildRowCommand(-65, runLengthEncoded)
        }
    }

    fun commandsPrintImage(rows: List<BooleanArray>, energy: Int = 0xffff): ByteArray {
        val data = ByteArrayOutputStream()
        data.write(getDeviceState)
        data.write(setQuality200Dpi)
        data.write(cmdSetEnergy(energy))
        data.write(cmdApplyEnergy())
        data.write(latticeStart)
        rows.forEach { row -> data.write(cmdPrintRow(row)) }
        data.write(cmdFeedPaper(25))
        data.write(setPaper)
        data.write(setPaper)
        data.write(setPaper)
        data.write(latticeEnd)
        data.write(getDeviceState)
        return data.toByteArray()
    }

    private fun buildRowCommand(commandId: Int, payload: List<Int>): ByteArray {
        val command = bytes(buildList {
            addAll(listOf(81, 120, commandId, 0, payload.size, 0))
            addAll(payload)
            add(0)
            add(-1)
        })
        command[command.lastIndex - 1] = checksum(command, 6, payload.size)
        return command
    }

    private fun runLengthEncode(row: BooleanArray): List<Int> {
        return buildList {
            var count = 0
            var lastValue = -1

            row.forEach { pixel ->
                val value = if (pixel) 1 else 0
                if (value == lastValue) {
                    count += 1
                } else {
                    addAll(encodeRunLengthRepetition(count, lastValue))
                    count = 1
                }
                lastValue = value
            }

            if (count > 0) {
                addAll(encodeRunLengthRepetition(count, lastValue))
            }
        }
    }

    private fun encodeRunLengthRepetition(count: Int, value: Int): List<Int> {
        if (count <= 0 || value < 0) {
            return emptyList()
        }

        return buildList {
            var remaining = count
            while (remaining > 0x7f) {
                add(0x7f or (value shl 7))
                remaining -= 0x7f
            }
            if (remaining > 0) {
                add((value shl 7) or remaining)
            }
        }
    }

    private fun byteEncode(row: BooleanArray): List<Int> {
        return buildList(PRINT_WIDTH / 8) {
            for (chunkStart in row.indices step 8) {
                var value = 0
                for (bitIndex in 0 until 8) {
                    if (row[chunkStart + bitIndex]) {
                        value = value or (1 shl bitIndex)
                    }
                }
                add(value)
            }
        }
    }

    private fun checksum(data: ByteArray, start: Int, length: Int): Byte {
        var result = 0
        for (index in start until start + length) {
            result = checksumTable[(result xor data[index].toInt().and(0xff)) and 0xff].toInt().and(0xff)
        }
        return result.toByte()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index ->
        values[index].toByte()
    }

    private fun bytes(values: List<Int>): ByteArray = ByteArray(values.size) { index ->
        values[index].toByte()
    }
}
