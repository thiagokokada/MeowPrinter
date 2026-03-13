package com.github.thiagokokada.meowprinter.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.thiagokokada.meowprinter.document.BlockAlignment
import com.github.thiagokokada.meowprinter.document.CanvasDocument
import com.github.thiagokokada.meowprinter.document.CanvasTextSize
import com.github.thiagokokada.meowprinter.document.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsInstrumentedTest {
    private lateinit var context: Context
    private lateinit var appSettings: AppSettings

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("meow_printer_settings", Context.MODE_PRIVATE).edit().clear().commit()
        appSettings = AppSettings(context)
    }

    @Test
    fun canvasDocumentDraftPersists() {
        val draft = CanvasDocument(
            blocks = listOf(
                TextBlock(
                    id = "text-1",
                    markdown = "## Hello printer",
                    alignment = BlockAlignment.CENTER,
                    textSize = CanvasTextSize.SP20
                )
            )
        )

        appSettings.canvasDocumentDraft = draft

        val restored = AppSettings(context).canvasDocumentDraft
        val restoredBlock = restored.blocks.single() as TextBlock

        assertEquals("## Hello printer", restoredBlock.markdown)
        assertEquals(BlockAlignment.CENTER, restoredBlock.alignment)
        assertEquals(CanvasTextSize.SP20, restoredBlock.textSize)
    }

    @Test
    fun printPacingPercentPersists() {
        appSettings.selectedPrintPacingPercent = 72

        val restored = AppSettings(context).selectedPrintPacingPercent

        assertEquals(72, restored)
    }
}
