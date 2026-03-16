package com.github.thiagokokada.meowprinter.ui

import android.graphics.Bitmap
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImageProcessingMode
import com.github.thiagokokada.meowprinter.image.ImageResizerMode
import com.github.thiagokokada.meowprinter.image.PreparedPrintImage
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun openingSettingsShowsSavedPrinterCard() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario?.onActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
                R.id.navigation_settings

            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.settings_scroll).visibility
            )
            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.saved_printer_value).visibility
            )
        }
    }

    @Test
    fun launchShowsImageScreenByDefault() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario?.onActivity { activity ->
            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.image_scroll).visibility
            )
            assertEquals(
                activity.getString(R.string.nav_image),
                activity.findViewById<TextView>(R.id.screen_title).text.toString()
            )
            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.button_pick_image).visibility
            )
        }
    }

    @Test
    fun openingLogsUpdatesVisibleScreenAndTitle() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(LogsActivity::class.java.name, null, false)

        try {
            scenario?.onActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
                R.id.navigation_settings
            activity.findViewById<View>(R.id.button_open_logs).performClick()
            }

            val logsActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
            checkNotNull(logsActivity)
            assertEquals(
                activityString(logsActivity, R.string.logs_screen_title),
                logsActivity.findViewById<TextView>(R.id.screen_title).text.toString()
            )
            logsActivity.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun openingTextShowsComposerFragment() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario?.onActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
                R.id.navigation_text

            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.text_fragment_container).visibility
            )
            assertEquals(
                activity.getString(R.string.nav_text),
                activity.findViewById<TextView>(R.id.screen_title).text.toString()
            )
            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.button_add_text_block).visibility
            )
            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.button_add_qr_block).visibility
            )
        }
    }

    @Test
    fun previewDocumentShowsComposePreviewDialog() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario?.onActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
                R.id.navigation_text
            activity.findViewById<View>(R.id.button_preview_document).performClick()

            val fragment = activity.supportFragmentManager.findFragmentById(R.id.text_fragment_container) as TextFragment
            assertEquals(true, fragment.isPreviewDialogShowingForTest())
        }
    }

    @Test
    fun addQrBlockCreatesComposeBlock() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario?.onActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
                R.id.navigation_text
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.text_fragment_container) as TextFragment
            val container = activity.findViewById<LinearLayout>(R.id.text_blocks_container)

            activity.findViewById<View>(R.id.button_add_qr_block).performClick()

            val dialog = fragment.qrDialogForTest()
            checkNotNull(dialog)
            val textInput = dialog.window?.decorView?.findViewWithTag("qr_text_label") as? EditText
            checkNotNull(textInput)
            textInput.setText("https://example.com")
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()

            val hasQrCard = (0 until container.childCount)
                .mapNotNull { index -> container.getChildAt(index) }
                .flatMap { card ->
                    collectText(card)
                }
                .any { it == activity.getString(R.string.block_title_qr) }

            assertEquals(true, hasQrCard)
        }
    }

    private fun collectText(view: View): List<String> {
        return when (view) {
            is TextView -> listOf(view.text.toString())
            is android.view.ViewGroup -> (0 until view.childCount).flatMap { index ->
                collectText(view.getChildAt(index))
            }
            else -> emptyList()
        }
    }

    @Test
    fun previewImageShowsImagePreviewDialog() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario?.onActivity { activity ->
            activity.setSelectedImageForTest(
                PreparedPrintImage(
                    previewBitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888),
                    rows = List(24) { BooleanArray(32) },
                    originalWidth = 32,
                    originalHeight = 24,
                    printWidth = 32,
                    printHeight = 24,
                    ditheringMode = DitheringMode.FLOYD_STEINBERG,
                    processingMode = ImageProcessingMode.NORMAL,
                    resizerMode = ImageResizerMode.SYSTEM_FILTERED
                )
            )
            activity.findViewById<View>(R.id.button_preview_image).performClick()
            assertEquals(true, activity.isImagePreviewDialogShowingForTest())
        }
    }

    private fun activityString(activity: android.app.Activity, resId: Int): String {
        return activity.getString(resId)
    }
}
