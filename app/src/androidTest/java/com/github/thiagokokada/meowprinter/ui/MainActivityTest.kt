package com.github.thiagokokada.meowprinter.ui

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.github.thiagokokada.meowprinter.R
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
    fun openingLogsUpdatesVisibleScreenAndTitle() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario?.onActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId =
                R.id.navigation_settings
            activity.findViewById<View>(R.id.button_open_logs).performClick()

            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.logs_scroll).visibility
            )
            assertEquals(
                activity.getString(R.string.logs_screen_title),
                activity.findViewById<TextView>(R.id.screen_title).text.toString()
            )
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
        }
    }
}
