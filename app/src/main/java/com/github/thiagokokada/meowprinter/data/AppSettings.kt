package com.github.thiagokokada.meowprinter.data

import android.content.Context
import androidx.core.content.edit
import com.github.thiagokokada.meowprinter.document.CanvasDocument
import com.github.thiagokokada.meowprinter.document.CanvasDocumentCodec
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImageProcessingMode
import com.github.thiagokokada.meowprinter.image.ImageResizerMode
import com.github.thiagokokada.meowprinter.ble.PrintPacingProfile
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

    var selectedImageProcessingMode: ImageProcessingMode
        get() = ImageProcessingMode.fromStoredValue(preferences.getString(KEY_IMAGE_PROCESSING_MODE, null))
        set(value) = preferences.edit { putString(KEY_IMAGE_PROCESSING_MODE, value.name) }

    var selectedImageResizerMode: ImageResizerMode
        get() = ImageResizerMode.fromStoredValue(preferences.getString(KEY_IMAGE_RESIZER_MODE, null))
        set(value) = preferences.edit { putString(KEY_IMAGE_RESIZER_MODE, value.name) }

    var selectedPrintEnergy: Int
        get() = preferences.getInt(KEY_PRINT_ENERGY, PrintEnergy.MAX_VALUE).coerceIn(0, PrintEnergy.MAX_VALUE)
        set(value) = preferences.edit {
            putInt(KEY_PRINT_ENERGY, value.coerceIn(0, PrintEnergy.MAX_VALUE))
        }

    var selectedPrintPacingProfile: PrintPacingProfile
        get() = PrintPacingProfile.fromStoredValue(preferences.getString(KEY_PRINT_PACING_PROFILE, null))
        set(value) = preferences.edit {
            putString(KEY_PRINT_PACING_PROFILE, value.name)
        }

    var selectedCustomPrintPacingPercent: Int
        get() = preferences.getInt(KEY_CUSTOM_PRINT_PACING_PERCENT, DEFAULT_CUSTOM_PRINT_PACING_PERCENT).coerceIn(0, 100)
        set(value) = preferences.edit {
            putInt(KEY_CUSTOM_PRINT_PACING_PERCENT, value.coerceIn(0, 100))
        }

    var selectedPaperMoveSteps: Int
        get() = preferences.getInt(KEY_PAPER_MOVE_STEPS, DEFAULT_PAPER_MOVE_STEPS).coerceIn(0, MAX_PAPER_STEPS)
        set(value) = preferences.edit {
            putInt(KEY_PAPER_MOVE_STEPS, value.coerceIn(0, MAX_PAPER_STEPS))
        }

    var selectedPrintGapSteps: Int
        get() = selectedPaperMoveSteps
        set(value) {
            selectedPaperMoveSteps = value
        }

    var selectedEndPaperPasses: Int
        get() = preferences.getInt(KEY_END_PAPER_PASSES, DEFAULT_END_PAPER_PASSES).coerceIn(0, MAX_END_PAPER_PASSES)
        set(value) = preferences.edit {
            putInt(KEY_END_PAPER_PASSES, value.coerceIn(0, MAX_END_PAPER_PASSES))
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
        private const val KEY_IMAGE_PROCESSING_MODE = "selected_image_processing_mode"
        private const val KEY_IMAGE_RESIZER_MODE = "selected_image_resizer_mode"
        private const val KEY_PRINT_ENERGY = "selected_print_energy"
        private const val KEY_PRINT_PACING_PROFILE = "selected_print_pacing_profile"
        private const val KEY_CUSTOM_PRINT_PACING_PERCENT = "selected_custom_print_pacing_percent"
        private const val KEY_PAPER_MOVE_STEPS = "selected_paper_move_steps"
        private const val KEY_END_PAPER_PASSES = "selected_end_paper_passes"
        private const val KEY_REQUESTED_BLE_PERMISSIONS = "requested_ble_permissions"
        private const val KEY_REQUESTED_NOTIFICATION_PERMISSION = "requested_notification_permission"
        private const val KEY_CANVAS_DOCUMENT_DRAFT = "canvas_document_draft"
        private const val DEFAULT_PAPER_MOVE_STEPS = 25
        private const val DEFAULT_CUSTOM_PRINT_PACING_PERCENT = 100
        private const val MAX_PAPER_STEPS = 255
        private const val DEFAULT_END_PAPER_PASSES = 1
        private const val MAX_END_PAPER_PASSES = 3
    }
}
