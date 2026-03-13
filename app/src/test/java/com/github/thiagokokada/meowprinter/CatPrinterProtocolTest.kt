package com.github.thiagokokada.meowprinter

import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatPrinterProtocolTest {
    @Test
    fun cmdSetEnergyMatchesPythonReference() {
        assertArrayEquals(
            byteArrayOf(81, 120, -81, 0, 2, 0, 18, 52, -15, -1),
            CatPrinterProtocol.cmdSetEnergy(0x1234)
        )
    }

    @Test
    fun cmdFeedPaperMatchesPythonReference() {
        assertArrayEquals(
            byteArrayOf(81, 120, -67, 0, 1, 0, 25, 79, -1),
            CatPrinterProtocol.cmdFeedPaper(25)
        )
    }

    @Test
    fun cmdAdvancePaperMatchesLinkedReference() {
        assertArrayEquals(
            byteArrayOf(81, 120, -95, 0, 2, 0, 25, 0, -22, -1),
            CatPrinterProtocol.cmdAdvancePaper(25)
        )
    }

    @Test
    fun cmdRetractPaperMatchesLinkedReference() {
        assertArrayEquals(
            byteArrayOf(81, 120, -96, 0, 2, 0, 25, 0, -22, -1),
            CatPrinterProtocol.cmdRetractPaper(25)
        )
    }

    @Test
    fun cmdPrintRowFallsBackToByteEncodingForAlternatingPixels() {
        val row = BooleanArray(CatPrinterProtocol.PRINT_WIDTH) { index -> index % 2 == 0 }
        val expected = ByteArray(56).apply {
            this[0] = 81
            this[1] = 120
            this[2] = (-94).toByte()
            this[4] = 48
            for (index in 0 until 48) {
                this[index + 6] = 85
            }
            this[54] = (-91).toByte()
            this[55] = (-1).toByte()
        }

        assertArrayEquals(expected, CatPrinterProtocol.cmdPrintRow(row))
    }

    @Test
    fun cmdPrintRowUsesRunLengthEncodingWhenSmaller() {
        val row = BooleanArray(CatPrinterProtocol.PRINT_WIDTH) { true }
        assertArrayEquals(
            byteArrayOf(81, 120, -65, 0, 4, 0, -1, -1, -1, -125, -83, -1),
            CatPrinterProtocol.cmdPrintRow(row)
        )
    }

    @Test
    fun commandsPrintImageMatchesPythonReferencePrefix() {
        val row = BooleanArray(CatPrinterProtocol.PRINT_WIDTH) { index -> index % 2 == 0 }
        val blankRow = BooleanArray(CatPrinterProtocol.PRINT_WIDTH)
        val payload = CatPrinterProtocol.commandsPrintImage(listOf(row, blankRow), energy = 0x1234)

        assertEquals(191, payload.size)
        assertArrayEquals(
            byteArrayOf(
                81, 120, -93, 0, 1, 0, 0, 0, -1,
                81, 120, -92, 0, 1, 0, 50, -98, -1,
                81, 120, -81, 0, 2, 0, 18, 52, -15, -1,
                81, 120, -66, 0, 1, 0, 1, 7, -1,
                81, 120, -90
            ),
            payload.copyOf(40)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun cmdPrintRowRejectsUnexpectedWidth() {
        CatPrinterProtocol.cmdPrintRow(BooleanArray(CatPrinterProtocol.PRINT_WIDTH - 1))
    }

    @Test
    fun commandsPrintImageEndsWithDeviceStateQuery() {
        val payload = CatPrinterProtocol.commandsPrintImage(
            listOf(BooleanArray(CatPrinterProtocol.PRINT_WIDTH))
        )

        assertTrue(
            payload.takeLast(9).toByteArray().contentEquals(
                byteArrayOf(81, 120, -93, 0, 1, 0, 0, 0, -1)
            )
        )
    }
}
