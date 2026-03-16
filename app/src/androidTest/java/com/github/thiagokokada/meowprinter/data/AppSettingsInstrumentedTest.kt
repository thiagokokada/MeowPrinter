package com.github.thiagokokada.meowprinter.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.thiagokokada.meowprinter.ble.PrintPacingProfile
import com.github.thiagokokada.meowprinter.document.BlockAlignment
import com.github.thiagokokada.meowprinter.document.CanvasDocument
import com.github.thiagokokada.meowprinter.document.CanvasTextFont
import com.github.thiagokokada.meowprinter.document.CanvasTextSize
import com.github.thiagokokada.meowprinter.document.CanvasTextWeight
import com.github.thiagokokada.meowprinter.document.TextBlock
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImageProcessingMode
import com.github.thiagokokada.meowprinter.image.ImageResizerMode
import com.github.thiagokokada.meowprinter.print.PrintEnergyProfile
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
    fun defaultsMatchAppExpectations() {
        assertEquals(DitheringMode.THRESHOLD, appSettings.selectedDitheringMode)
        assertEquals(PrintEnergyProfile.MEDIUM, appSettings.selectedPrintEnergyProfile)
        assertEquals(65, appSettings.selectedCustomPrintEnergyPercent)
        assertEquals(PrintPacingProfile.BALANCED, appSettings.selectedPrintPacingProfile)
        assertEquals(1, appSettings.selectedEndPaperPasses)
    }

    @Test
    fun canvasDocumentDraftPersists() {
        val draft = CanvasDocument(
            blocks = listOf(
                TextBlock(
                    id = "text-1",
                    markdown = "## Hello printer",
                    alignment = BlockAlignment.CENTER,
                    textSize = CanvasTextSize.SP20,
                    textFont = CanvasTextFont.SERIF,
                    textWeight = CanvasTextWeight.NORMAL
                )
            )
        )

        appSettings.canvasDocumentDraft = draft

        val restored = AppSettings(context).canvasDocumentDraft
        val restoredBlock = restored.blocks.single() as TextBlock

        assertEquals("## Hello printer", restoredBlock.markdown)
        assertEquals(BlockAlignment.CENTER, restoredBlock.alignment)
        assertEquals(CanvasTextSize.SP20, restoredBlock.textSize)
        assertEquals(CanvasTextFont.SERIF, restoredBlock.textFont)
        assertEquals(CanvasTextWeight.NORMAL, restoredBlock.textWeight)
    }

    @Test
    fun printEnergyProfilePersists() {
        appSettings.selectedPrintEnergyProfile = PrintEnergyProfile.DARK

        val restored = AppSettings(context).selectedPrintEnergyProfile

        assertEquals(PrintEnergyProfile.DARK, restored)
    }

    @Test
    fun customPrintEnergyPercentPersists() {
        appSettings.selectedCustomPrintEnergyPercent = 83

        val restored = AppSettings(context).selectedCustomPrintEnergyPercent

        assertEquals(83, restored)
    }

    @Test
    fun printPacingProfilePersists() {
        appSettings.selectedPrintPacingProfile = PrintPacingProfile.SAFE

        val restored = AppSettings(context).selectedPrintPacingProfile

        assertEquals(PrintPacingProfile.SAFE, restored)
    }

    @Test
    fun customPrintPacingPercentPersists() {
        appSettings.selectedCustomPrintPacingPercent = 73

        val restored = AppSettings(context).selectedCustomPrintPacingPercent

        assertEquals(73, restored)
    }

    @Test
    fun imageProcessingModePersists() {
        appSettings.selectedImageProcessingMode = ImageProcessingMode.SHARPEN

        val restored = AppSettings(context).selectedImageProcessingMode

        assertEquals(ImageProcessingMode.SHARPEN, restored)
    }

    @Test
    fun imageResizerModePersists() {
        appSettings.selectedImageResizerMode = ImageResizerMode.AREA_AVERAGE

        val restored = AppSettings(context).selectedImageResizerMode

        assertEquals(ImageResizerMode.AREA_AVERAGE, restored)
    }

    @Test
    fun paperStepSettingsPersist() {
        appSettings.selectedPaperMoveSteps = 42

        val restored = AppSettings(context)

        assertEquals(42, restored.selectedPaperMoveSteps)
        assertEquals(42, restored.selectedPrintGapSteps)
    }
}
