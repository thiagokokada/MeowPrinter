package com.github.thiagokokada.meowprinter

import android.content.Context

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var selectedPrinterAddress: String?
        get() = preferences.getString(KEY_PRINTER_ADDRESS, null)
        set(value) = preferences.edit().putString(KEY_PRINTER_ADDRESS, value).apply()

    var selectedPrinterName: String?
        get() = preferences.getString(KEY_PRINTER_NAME, null)
        set(value) = preferences.edit().putString(KEY_PRINTER_NAME, value).apply()

    var selectedDitheringMode: DitheringMode
        get() = DitheringMode.fromStoredValue(preferences.getString(KEY_DITHERING_MODE, null))
        set(value) = preferences.edit().putString(KEY_DITHERING_MODE, value.name).apply()

    var selectedPrintEnergy: Int
        get() = preferences.getInt(KEY_PRINT_ENERGY, DEFAULT_PRINT_ENERGY).coerceIn(0, MAX_PRINT_ENERGY)
        set(value) = preferences.edit()
            .putInt(KEY_PRINT_ENERGY, value.coerceIn(0, MAX_PRINT_ENERGY))
            .apply()

    var hasRequestedBlePermissions: Boolean
        get() = preferences.getBoolean(KEY_REQUESTED_BLE_PERMISSIONS, false)
        set(value) = preferences.edit().putBoolean(KEY_REQUESTED_BLE_PERMISSIONS, value).apply()

    fun clearSelectedPrinter() {
        preferences.edit()
            .remove(KEY_PRINTER_ADDRESS)
            .remove(KEY_PRINTER_NAME)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "meow_printer_settings"
        private const val KEY_PRINTER_ADDRESS = "selected_printer_address"
        private const val KEY_PRINTER_NAME = "selected_printer_name"
        private const val KEY_DITHERING_MODE = "selected_dithering_mode"
        private const val KEY_PRINT_ENERGY = "selected_print_energy"
        private const val KEY_REQUESTED_BLE_PERMISSIONS = "requested_ble_permissions"
        private const val MAX_PRINT_ENERGY = 0xffff
        private const val DEFAULT_PRINT_ENERGY = MAX_PRINT_ENERGY
    }
}
