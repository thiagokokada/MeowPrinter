package com.github.thiagokokada.meowprinter.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.thiagokokada.meowprinter.ui.TextSizeOption
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
    fun markdownDraftPersists() {
        appSettings.markdownDraft = "# Test"

        assertEquals("# Test", AppSettings(context).markdownDraft)
    }

    @Test
    fun markdownTextSizePersists() {
        appSettings.markdownTextSize = TextSizeOption.LARGE

        assertEquals(TextSizeOption.LARGE, AppSettings(context).markdownTextSize)
    }
}
