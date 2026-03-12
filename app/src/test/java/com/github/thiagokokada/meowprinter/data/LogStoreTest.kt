package com.github.thiagokokada.meowprinter.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LogStoreTest {
    @Before
    fun resetStore() {
        LogStore.clear()
    }

    @Test
    fun appendAndClearManageSessionLog() {
        LogStore.append("first")
        LogStore.append("second")

        assertEquals("first\nsecond", LogStore.asText())

        LogStore.clear()

        assertEquals("", LogStore.asText())
    }
}
