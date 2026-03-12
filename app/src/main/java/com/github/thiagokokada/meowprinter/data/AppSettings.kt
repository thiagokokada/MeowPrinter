package com.github.thiagokokada.meowprinter.data

import android.content.Context
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.print.PrintEnergy
import androidx.core.content.edit

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var selectedPrinterAddress: String?
        get() = preferences.getString(KEY_PRINTER_ADDRESS, null)
        set(value) = preferences.edit { putString(KEY_PRINTER_ADDRESS, value) }

    var selectedPrinterName: String?
        get() = preferences.getString(KEY_PRINTER_NAME, null)
        set(value) = preferences.edit { putString(KEY_PRINTER_NAME, value) }

    var selectedDitheringMode: DitheringMode
        get() = DitheringMode.fromStoredValue(preferences.getString(KEY_DITHERING_MODE, null))
        set(value) = preferences.edit { putString(KEY_DITHERING_MODE, value.name) }

    var selectedPrintEnergy: Int
        get() = preferences.getInt(KEY_PRINT_ENERGY, PrintEnergy.MAX_VALUE).coerceIn(0, PrintEnergy.MAX_VALUE)
        set(value) = preferences.edit {
            putInt(KEY_PRINT_ENERGY, value.coerceIn(0, PrintEnergy.MAX_VALUE))
        }

    var hasRequestedBlePermissions: Boolean
        get() = preferences.getBoolean(KEY_REQUESTED_BLE_PERMISSIONS, false)
        set(value) = preferences.edit { putBoolean(KEY_REQUESTED_BLE_PERMISSIONS, value) }

    var markdownDraft: String
        get() = preferences.getString(KEY_MARKDOWN_DRAFT, DEFAULT_MARKDOWN_DRAFT).orEmpty()
        set(value) = preferences.edit { putString(KEY_MARKDOWN_DRAFT, value) }

    companion object {
        private const val PREFERENCES_NAME = "meow_printer_settings"
        private const val KEY_PRINTER_ADDRESS = "selected_printer_address"
        private const val KEY_PRINTER_NAME = "selected_printer_name"
        private const val KEY_DITHERING_MODE = "selected_dithering_mode"
        private const val KEY_PRINT_ENERGY = "selected_print_energy"
        private const val KEY_REQUESTED_BLE_PERMISSIONS = "requested_ble_permissions"
        private const val KEY_MARKDOWN_DRAFT = "markdown_draft"
        private const val DEFAULT_MARKDOWN_DRAFT = """
# Meow Printer

Write Markdown here and print the preview.
"""
    }
}
