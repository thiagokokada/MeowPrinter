package com.github.thiagokokada.meowprinter.ui

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.data.LogStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogsActivityTest {
    private var scenario: ActivityScenario<LogsActivity>? = null

    @Before
    fun setUp() {
        LogStore.clear()
    }

    @After
    fun tearDown() {
        scenario?.close()
        LogStore.clear()
    }

    @Test
    fun launchesWithVisibleLogCardAndTitle() {
        scenario = ActivityScenario.launch(LogsActivity::class.java)

        scenario?.onActivity { activity ->
            assertEquals(
                activity.getString(R.string.logs_screen_title),
                activity.findViewById<TextView>(R.id.screen_title).text.toString()
            )
            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.button_clear_logs).visibility
            )
            assertEquals(
                View.VISIBLE,
                activity.findViewById<View>(R.id.logs_value).visibility
            )
        }
    }

    @Test
    fun clearLogShowsEmptyState() {
        LogStore.append("Connected to GT01.")
        LogStore.append("Prepared image 384x500.")
        scenario = ActivityScenario.launch(LogsActivity::class.java)

        scenario?.onActivity { activity ->
            activity.findViewById<View>(R.id.button_clear_logs).performClick()
            assertEquals(
                activity.getString(R.string.no_logs_yet),
                activity.findViewById<TextView>(R.id.logs_value).text.toString()
            )
        }
    }
}
