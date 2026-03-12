package com.github.thiagokokada.meowprinter

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.thiagokokada.meowprinter.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: BlePrinterScanner
    private lateinit var appSettings: AppSettings
    private lateinit var ditheringAdapter: ArrayAdapter<String>

    private var printerManager: BlePrinterManager? = null
    private var connectedPrinterName: String? = null
    private var selectedImage: PreparedPrintImage? = null
    private var currentStatus = "Pick an image and print."
    private var currentJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasBlePermissions()) {
            maybeAutoConnect()
        }
        render()
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            appendLog("Image picker canceled.")
            return@registerForActivityResult
        }

        val ditheringMode = appSettings.selectedDitheringMode
        runTrackedJob(getString(R.string.preparing_image)) { launchedJob ->
            try {
                val prepared = ImagePrintPreparer.prepare(contentResolver, uri, ditheringMode)
                selectedImage = prepared
                appendLog(
                    "Prepared image ${prepared.originalWidth}x${prepared.originalHeight} -> " +
                        "${prepared.printWidth}x${prepared.printHeight} using ${prepared.ditheringMode.displayName}."
                )
                currentStatus = getString(R.string.image_ready_to_print)
            } catch (e: Exception) {
                currentStatus = getString(R.string.image_preparation_failed)
                appendLog("Image preparation failed: ${e.message ?: getString(R.string.unknown_error)}")
            } finally {
                finishTrackedJob(launchedJob)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scanner = BlePrinterScanner(applicationContext)
        appSettings = AppSettings(applicationContext)

        binding.toolbar.applyTopSystemBarPadding()
        binding.contentContainer.applySideAndBottomSystemBarsPadding()
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else {
                false
            }
        }

        ditheringAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            DitheringMode.entries.map { it.displayName }
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerDithering.adapter = ditheringAdapter
        binding.spinnerDithering.setSelection(appSettings.selectedDitheringMode.ordinal, false)
        binding.spinnerDithering.onItemSelectedListener = SimpleItemSelectedListener { position ->
            val selectedMode = DitheringMode.entries[position]
            if (appSettings.selectedDitheringMode != selectedMode) {
                appSettings.selectedDitheringMode = selectedMode
                if (selectedImage != null) {
                    selectedImage = null
                    currentStatus = getString(R.string.repick_image_after_dithering_change)
                    appendLog("Dithering mode changed. Pick the image again to preview and print with the new mode.")
                }
                render()
            }
        }

        binding.buttonPickImage.setOnClickListener {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.buttonPrintImage.setOnClickListener {
            printSelectedImage()
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        maybeAutoConnect()
        render()
    }

    override fun onStop() {
        disconnectForegroundConnection()
        super.onStop()
    }

    override fun onDestroy() {
        currentJob?.cancel()
        printerManager?.release()
        super.onDestroy()
    }

    private fun scanAndConnect() {
        val preferredAddress = appSettings.selectedPrinterAddress
        if (preferredAddress == null) {
            currentStatus = getString(R.string.select_printer_in_settings)
            appendLog("No printer saved. Open Settings and select a printer first.")
            render()
            return
        }

        runTrackedJob(getString(R.string.scanning_for_saved_printer)) { launchedJob ->
            try {
                if (!scanner.isBluetoothEnabled()) {
                    currentStatus = getString(R.string.bluetooth_disabled)
                    appendLog("Bluetooth is disabled on the device.")
                    return@runTrackedJob
                }

                val result = scanner.findFirstCompatiblePrinter(preferredAddress = preferredAddress)
                if (result == null) {
                    currentStatus = getString(R.string.saved_printer_not_found)
                    appendLog("Saved printer was not found nearby.")
                    return@runTrackedJob
                }

                appendLog("Found ${result.displayName} (${result.device.address}). Connecting...")
                connectToPrinter(result)
            } catch (e: Exception) {
                currentStatus = getString(R.string.scan_failed)
                appendLog("Scan failed: ${e.message ?: getString(R.string.unknown_error)}")
            } finally {
                finishTrackedJob(launchedJob)
            }
        }
    }

    private fun maybeAutoConnect() {
        if (currentJob?.isActive == true) {
            return
        }
        if (printerManager?.isPrinterReady == true) {
            return
        }
        if (!hasBlePermissions()) {
            currentStatus = getString(R.string.bluetooth_permission_missing_idle)
            render()
            return
        }
        if (appSettings.selectedPrinterAddress == null) {
            currentStatus = getString(R.string.select_printer_in_settings)
            render()
            return
        }
        scanAndConnect()
    }

    private suspend fun connectToPrinter(printer: DiscoveredPrinter) {
        printerManager?.release()
        val manager = BlePrinterManager(applicationContext) {
            runOnUiThread {
                connectedPrinterName = null
                currentStatus = getString(R.string.printer_disconnected)
                appendLog("Printer disconnected.")
                printerManager = null
                render()
            }
        }
        printerManager = manager
        try {
            currentStatus = getString(R.string.connecting)
            render()

            manager.connectAndInitialize(printer.device)

            connectedPrinterName = printer.displayName
            currentStatus = getString(R.string.ready_to_print)
            appendLog("Connected to ${printer.displayName}. MTU ${manager.negotiatedMtu}.")
            render()
        } catch (e: Exception) {
            manager.release()
            printerManager = null
            connectedPrinterName = null
            currentStatus = getString(R.string.connection_failed)
            appendLog("Connection failed: ${e.message ?: getString(R.string.unknown_error)}")
            render()
        }
    }

    private fun printSelectedImage() {
        val manager = printerManager
        if (manager == null || !manager.isPrinterReady) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            ensureBlePermissionsThen { maybeAutoConnect() }
            return
        }

        val image = selectedImage
        if (image == null) {
            Toast.makeText(this, R.string.no_image_selected, Toast.LENGTH_SHORT).show()
            return
        }

        runTrackedJob(getString(R.string.generating_image_print_job)) { launchedJob ->
            try {
                val payload = CatPrinterProtocol.commandsPrintImage(image.rows)
                appendLog("Generated ${image.rows.size} rows and ${payload.size} bytes from selected image.")
                currentStatus = getString(R.string.printing_selected_image)
                render()

                manager.print(payload)

                currentStatus = getString(R.string.printer_ready_again)
                appendLog("Image print completed successfully.")
            } catch (e: Exception) {
                currentStatus = getString(R.string.image_print_failed)
                appendLog("Image print failed: ${e.message ?: getString(R.string.unknown_error)}")
            } finally {
                finishTrackedJob(launchedJob)
            }
        }
    }

    private fun disconnectForegroundConnection() {
        currentJob?.cancel()
        printerManager?.release()
        printerManager = null
        connectedPrinterName = null
        currentStatus = getString(R.string.disconnected)
        binding.progressIndicator.isVisible = false
        render()
    }

    private fun runTrackedJob(status: String, block: suspend (Job) -> Unit) {
        currentJob?.cancel()
        var launchedJob: Job? = null
        launchedJob = lifecycleScope.launch {
            val job = currentCoroutineContext()[Job] ?: return@launch
            currentStatus = status
            binding.progressIndicator.isVisible = true
            render()
            block(job)
        }
        currentJob = launchedJob
    }

    private fun finishTrackedJob(job: Job) {
        if (currentJob === job) {
            currentJob = null
        }
        binding.progressIndicator.isVisible = false
        render()
    }

    private fun appendLog(message: String) {
        LogStore.append(message)
    }

    private fun render() {
        val connected = printerManager?.isPrinterReady == true
        binding.printerValue.text = connectedPrinterName ?: appSettings.selectedPrinterName ?: getString(R.string.no_printer_selected)
        binding.connectionBadge.text = if (connected) getString(R.string.connected) else getString(R.string.disconnected)
        binding.statusValue.text = currentStatus
        binding.imageSelectionValue.text = selectedImage?.let { prepared ->
            "${prepared.printWidth}x${prepared.printHeight} • ${prepared.ditheringMode.displayName}"
        } ?: getString(R.string.no_image_selected_label)
        binding.imagePreview.setImageBitmap(selectedImage?.previewBitmap)
        binding.imagePreview.isVisible = selectedImage != null

        binding.buttonPickImage.isEnabled = currentJob?.isActive != true
        binding.buttonPrintImage.isEnabled = connected && selectedImage != null && currentJob?.isActive != true
    }

    private fun hasBlePermissions(): Boolean {
        return BlePermissions.required.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureBlePermissionsThen(onGranted: () -> Unit) {
        when {
            hasBlePermissions() -> onGranted()
            shouldShowBlePermissionRationale() -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_dialog_title)
                    .setMessage(R.string.permissions_rationale_long)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.grant_permissions) { _, _ ->
                        appSettings.hasRequestedBlePermissions = true
                        permissionLauncher.launch(BlePermissions.required)
                    }
                    .show()
            }
            isPermissionPermanentlyDenied() -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_dialog_title)
                    .setMessage(R.string.permissions_open_settings)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.open_app_settings) { _, _ ->
                        openAppSettings()
                    }
                    .show()
            }
            else -> {
                appSettings.hasRequestedBlePermissions = true
                permissionLauncher.launch(BlePermissions.required)
            }
        }
    }

    private fun shouldShowBlePermissionRationale(): Boolean {
        return BlePermissions.required.any { permission ->
            shouldShowRequestPermissionRationale(permission)
        }
    }

    private fun isPermissionPermanentlyDenied(): Boolean {
        return !hasBlePermissions() &&
            appSettings.hasRequestedBlePermissions &&
            !shouldShowBlePermissionRationale()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }
}
