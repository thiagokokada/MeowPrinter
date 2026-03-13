package com.github.thiagokokada.meowprinter.data

import android.content.Context
import androidx.core.content.edit
import com.github.thiagokokada.meowprinter.document.CanvasDocument
import com.github.thiagokokada.meowprinter.document.CanvasDocumentCodec
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.print.PrintEnergy

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

    var selectedPrintPacingPercent: Int
        get() = preferences.getInt(KEY_PRINT_PACING_PERCENT, DEFAULT_PRINT_PACING_PERCENT).coerceIn(0, 100)
        set(value) = preferences.edit {
            putInt(KEY_PRINT_PACING_PERCENT, value.coerceIn(0, 100))
        }

    var hasRequestedBlePermissions: Boolean
        get() = preferences.getBoolean(KEY_REQUESTED_BLE_PERMISSIONS, false)
        set(value) = preferences.edit { putBoolean(KEY_REQUESTED_BLE_PERMISSIONS, value) }

    var hasRequestedNotificationPermission: Boolean
        get() = preferences.getBoolean(KEY_REQUESTED_NOTIFICATION_PERMISSION, false)
        set(value) = preferences.edit { putBoolean(KEY_REQUESTED_NOTIFICATION_PERMISSION, value) }

    var canvasDocumentDraft: CanvasDocument
        get() = CanvasDocumentCodec.decode(preferences.getString(KEY_CANVAS_DOCUMENT_DRAFT, null))
        set(value) = preferences.edit { putString(KEY_CANVAS_DOCUMENT_DRAFT, CanvasDocumentCodec.encode(value)) }

    companion object {
        private const val PREFERENCES_NAME = "meow_printer_settings"
        private const val KEY_PRINTER_ADDRESS = "selected_printer_address"
        private const val KEY_PRINTER_NAME = "selected_printer_name"
        private const val KEY_DITHERING_MODE = "selected_dithering_mode"
        private const val KEY_PRINT_ENERGY = "selected_print_energy"
        private const val KEY_PRINT_PACING_PERCENT = "selected_print_pacing_percent"
        private const val KEY_REQUESTED_BLE_PERMISSIONS = "requested_ble_permissions"
        private const val KEY_REQUESTED_NOTIFICATION_PERMISSION = "requested_notification_permission"
        private const val KEY_CANVAS_DOCUMENT_DRAFT = "canvas_document_draft"
        private const val DEFAULT_PRINT_PACING_PERCENT = 60
    }
}
