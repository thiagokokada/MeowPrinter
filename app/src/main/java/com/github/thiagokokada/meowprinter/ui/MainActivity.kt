package com.github.thiagokokada.meowprinter.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.ble.BlePermissions
import com.github.thiagokokada.meowprinter.ble.BlePrinterManager
import com.github.thiagokokada.meowprinter.ble.BlePrinterScanner
import com.github.thiagokokada.meowprinter.ble.DiscoveredPrinter
import com.github.thiagokokada.meowprinter.data.AppSettings
import com.github.thiagokokada.meowprinter.data.LogStore
import com.github.thiagokokada.meowprinter.databinding.ActivityMainBinding
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImagePrintPreparer
import com.github.thiagokokada.meowprinter.image.PreparedPrintImage
import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import com.github.thiagokokada.meowprinter.print.PrintEnergy
import com.github.thiagokokada.meowprinter.print.PrinterTestPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import java.io.File
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity

class MainActivity : AppCompatActivity(), TextFragment.Host {
    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: BlePrinterScanner
    private lateinit var appSettings: AppSettings
    private lateinit var ditheringAdapter: ArrayAdapter<String>
    private lateinit var printerAdapter: ArrayAdapter<String>

    private var printerManager: BlePrinterManager? = null
    private var connectedPrinterName: String? = null
    private var selectedImageUri: Uri? = null
    private var selectedImage: PreparedPrintImage? = null
    private var currentStatus = "Pick an image and print."
    private var currentJob: Job? = null
    private var discoveredPrinters: List<DiscoveredPrinter> = emptyList()
    private var selectedTabId: Int = R.id.navigation_image
    private var selectedScannedPrinterIndex: Int? = null
    private var ignorePrinterSelectionCallback = false
    private var ignoreEnergySliderCallback = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasBlePermissions()) {
            maybeAutoConnect(force = true)
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
        launchImageEditor(uri)
    }

    private val imageEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_OK -> {
                val editedUri = result.data?.let(UCrop::getOutput)
                if (editedUri == null) {
                    currentStatus = getString(R.string.image_edit_failed)
                    appendLog("Image editing finished without an output file.")
                    render()
                    return@registerForActivityResult
                }
                selectedImageUri = editedUri
                prepareSelectedImage(editedUri, appendPreparedLog = true)
            }

            RESULT_CANCELED -> {
                currentStatus = if (selectedImage != null) {
                    getString(R.string.image_ready_to_print)
                } else {
                    getString(R.string.pick_image_for_preview)
                }
                appendLog("Image editor canceled.")
                render()
            }

            else -> {
                val error = result.data?.let(UCrop::getError)
                currentStatus = getString(R.string.image_edit_failed)
                appendLog("Image editing failed: ${error?.message ?: getString(R.string.unknown_error)}")
                render()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scanner = BlePrinterScanner(applicationContext)
        appSettings = AppSettings(applicationContext)
        selectedTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB, R.id.navigation_image)
            ?: R.id.navigation_image

        binding.toolbar.applyTopSystemBarPadding()
        binding.imageContent.applySideAndBottomSystemBarsPadding()
        binding.settingsContent.applySideAndBottomSystemBarsPadding()
        binding.logsContent.applySideAndBottomSystemBarsPadding()
        binding.bottomNavigation.applySideAndBottomSystemBarsPadding()
        binding.appTitle.text = getString(R.string.app_name)
        binding.toolbar.setNavigationOnClickListener {
            if (selectedTabId == SCREEN_LOGS) {
                showScreen(R.id.navigation_settings)
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
                val uri = selectedImageUri
                if (uri != null) {
                    prepareSelectedImage(uri, appendPreparedLog = false)
                } else {
                    currentStatus = getString(R.string.pick_image_for_preview)
                    render()
                }
            }
        }

        printerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ArrayList<String>()
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerPrinters.adapter = printerAdapter
        binding.spinnerPrinters.onItemSelectedListener = SimpleItemSelectedListener { position ->
            if (ignorePrinterSelectionCallback || position !in discoveredPrinters.indices) {
                return@SimpleItemSelectedListener
            }
            selectedScannedPrinterIndex = position
            render()
        }
        binding.sliderEnergy.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || ignoreEnergySliderCallback) {
                return@addOnChangeListener
            }
            appSettings.selectedPrintEnergy = PrintEnergy.fromPercent(value.toInt())
            render()
        }

        binding.buttonPickImage.setOnClickListener {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.buttonImageConnection.setOnClickListener {
            ensureBlePermissionsThen { maybeAutoConnect(force = true) }
        }
        binding.buttonPrintImage.setOnClickListener {
            printSelectedImage()
        }
        binding.buttonTestPrint.setOnClickListener {
            ensureBlePermissionsThen { printTestPageFromCurrentPrinter() }
        }
        binding.buttonOpenLogs.setOnClickListener {
            showScreen(SCREEN_LOGS)
        }
        binding.buttonClearLogs.setOnClickListener {
            LogStore.clear()
            render()
        }
        binding.buttonScanPrinters.setOnClickListener {
            ensureBlePermissionsThen { scanPrinters() }
        }
        binding.buttonSavePrinter.setOnClickListener {
            saveSelectedPrinter()
        }
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showScreen(item.itemId)
            true
        }
        binding.bottomNavigation.selectedItemId =
            if (selectedTabId == SCREEN_LOGS) R.id.navigation_settings else selectedTabId

        showScreen(selectedTabId)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTabId)
    }

    private fun showScreen(tabId: Int) {
        selectedTabId = tabId
        binding.imageScroll.isVisible = tabId == R.id.navigation_image
        binding.textFragmentContainer.isVisible = tabId == R.id.navigation_text
        binding.settingsScroll.isVisible = tabId == R.id.navigation_settings
        binding.logsScroll.isVisible = tabId == SCREEN_LOGS
        binding.screenTitle.text = when (tabId) {
            R.id.navigation_image -> getString(R.string.nav_image)
            R.id.navigation_text -> getString(R.string.nav_text)
            SCREEN_LOGS -> getString(R.string.logs_screen_title)
            else -> getString(R.string.nav_settings)
        }
        val showBack = tabId == SCREEN_LOGS
        binding.toolbar.navigationIcon = if (showBack) {
            AppCompatResources.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        } else {
            null
        }
        render()
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

    private fun maybeAutoConnect(force: Boolean = false) {
        if (currentJob?.isActive == true) return
        if (!force && printerManager?.isPrinterReady == true) return
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
        if (force) {
            disconnectForegroundConnection()
        }
        scanAndConnect()
    }

    private fun prepareSelectedImage(uri: Uri, appendPreparedLog: Boolean) {
        val ditheringMode = appSettings.selectedDitheringMode
        runTrackedJob(getString(R.string.preparing_image)) { launchedJob ->
            try {
                val prepared = ImagePrintPreparer.prepare(contentResolver, uri, ditheringMode)
                selectedImage = prepared
                currentStatus = getString(R.string.image_ready_to_print)
                if (appendPreparedLog) {
                    appendLog(
                        "Prepared image ${prepared.originalWidth}x${prepared.originalHeight} -> " +
                            "${prepared.printWidth}x${prepared.printHeight} using ${prepared.ditheringMode.displayName}."
                    )
                } else {
                    appendLog("Updated preview using ${prepared.ditheringMode.displayName}.")
                }
            } catch (e: Exception) {
                currentStatus = getString(R.string.image_preparation_failed)
                appendLog("Image preparation failed: ${e.message ?: getString(R.string.unknown_error)}")
            } finally {
                finishTrackedJob(launchedJob)
            }
        }
    }

    private fun launchImageEditor(sourceUri: Uri) {
        currentStatus = getString(R.string.editing_image)
        render()

        val destinationUri = Uri.fromFile(
            File(cacheDir, "edited-${System.currentTimeMillis()}.png")
        )
        val options = UCrop.Options().apply {
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.PNG)
            setCompressionQuality(100)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(true)
            setToolbarColor(ContextCompat.getColor(this@MainActivity, R.color.meow_surface))
            setToolbarWidgetColor(ContextCompat.getColor(this@MainActivity, R.color.meow_on_surface))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@MainActivity, R.color.meow_secondary))
            setRootViewBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.meow_background))
            setDimmedLayerColor(ContextCompat.getColor(this@MainActivity, R.color.meow_primary_container))
            setCropGridColor(ContextCompat.getColor(this@MainActivity, R.color.meow_outline))
            setCropFrameColor(ContextCompat.getColor(this@MainActivity, R.color.meow_secondary))
        }
        val intent = UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .getIntent(this)
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                setClass(this@MainActivity, UCropActivity::class.java)
            }

        imageEditorLauncher.launch(intent)
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
        val image = selectedImage
        if (image == null) {
            Toast.makeText(this, R.string.no_image_selected, Toast.LENGTH_SHORT).show()
            return
        }

        printPreparedImage(image, "selected image")
    }

    override fun printPreparedImage(preparedImage: PreparedPrintImage, sourceLabel: String) {
        val manager = printerManager
        if (manager == null || !manager.isPrinterReady) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            ensureBlePermissionsThen { maybeAutoConnect(force = true) }
            return
        }

        runTrackedJob(getString(R.string.generating_image_print_job)) { launchedJob ->
            try {
                val energy = appSettings.selectedPrintEnergy
                val energyLabel = formatEnergy(PrintEnergy.toPercent(energy))
                val payload = CatPrinterProtocol.commandsPrintImage(preparedImage.rows, energy = energy)
                appendLog(
                    "Generated ${preparedImage.rows.size} rows and ${payload.size} bytes from $sourceLabel at $energyLabel."
                )
                currentStatus = getString(R.string.printing_selected_image_with_energy, energyLabel)
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

    override fun selectedTextDithering(): DitheringMode = appSettings.selectedDitheringMode

    override fun connectionSummary(): ConnectionSummary {
        val connected = printerManager?.isPrinterReady == true
        val printerName = connectedPrinterName ?: appSettings.selectedPrinterName ?: getString(R.string.no_printer_selected)
        val statusText = currentStatus
        val actionLabel = if (connected) getString(R.string.connected) else getString(R.string.refresh)
        return ConnectionSummary(
            printerName = printerName,
            statusText = statusText,
            actionLabel = actionLabel,
            actionEnabled = connected.not() && currentJob?.isActive != true,
            isConnected = connected
        )
    }

    override fun refreshPrinterConnection() {
        ensureBlePermissionsThen { maybeAutoConnect(force = true) }
    }

    private fun printTestPageFromCurrentPrinter() {
        val printerAddress = appSettings.selectedPrinterAddress
        if (printerAddress == null) {
            Toast.makeText(this, R.string.select_printer_in_settings, Toast.LENGTH_SHORT).show()
            return
        }
        if (!scanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        runTrackedJob(getString(R.string.testing_saved_printer)) { launchedJob ->
            try {
                val printer = scanner.findFirstCompatiblePrinter(preferredAddress = printerAddress)
                if (printer == null) {
                    currentStatus = getString(R.string.saved_printer_not_found)
                    return@runTrackedJob
                }

                val manager = BlePrinterManager(applicationContext) {}
                try {
                    val energy = appSettings.selectedPrintEnergy
                    val energyLabel = formatEnergy(PrintEnergy.toPercent(energy))
                    manager.connectAndInitialize(printer.device)
                    currentStatus = getString(R.string.testing_saved_printer_with_energy, energyLabel)
                    render()
                    val payload = CatPrinterProtocol.commandsPrintImage(
                        PrinterTestPage.createRows(),
                        energy = energy
                    )
                    manager.print(payload)
                    appendLog("Test page printed on ${printer.displayName} at $energyLabel.")
                    currentStatus = getString(R.string.test_page_sent)
                } finally {
                    manager.release()
                }
            } catch (e: Exception) {
                currentStatus = getString(R.string.scan_failed_message, e.message ?: getString(R.string.unknown_error))
            } finally {
                finishTrackedJob(launchedJob)
            }
        }
    }

    private fun scanPrinters() {
        if (!scanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        runTrackedJob(getString(R.string.scanning_printers)) { launchedJob ->
            try {
                discoveredPrinters = scanner.scanCompatiblePrinters()
                selectedScannedPrinterIndex = discoveredPrinters.indexOfFirst {
                    it.device.address == appSettings.selectedPrinterAddress
                }.takeIf { it >= 0 } ?: discoveredPrinters.indices.firstOrNull()
                currentStatus = if (discoveredPrinters.isEmpty()) {
                    getString(R.string.no_printers_found)
                } else {
                    getString(R.string.printers_found, discoveredPrinters.size)
                }
                renderPrinterChoices()
            } catch (e: Exception) {
                currentStatus = getString(R.string.scan_failed_message, e.message ?: getString(R.string.unknown_error))
            } finally {
                finishTrackedJob(launchedJob)
            }
        }
    }

    private fun disconnectForegroundConnection() {
        printerManager?.release()
        printerManager = null
        connectedPrinterName = null
        binding.progressIndicator.isVisible = false
        render()
    }

    private fun runTrackedJob(status: String, block: suspend (Job) -> Unit) {
        currentJob?.cancel()
        val launchedJob = lifecycleScope.launch {
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
        val printerName = connectedPrinterName ?: appSettings.selectedPrinterName ?: getString(R.string.no_printer_selected)
        val savedPrinterAddress = appSettings.selectedPrinterAddress
        val selectedPrinter = selectedScannedPrinterIndex?.takeIf { it in discoveredPrinters.indices }?.let(discoveredPrinters::get)

        binding.printerValue.text = printerName
        binding.imageStatusValue.text = currentStatus
        binding.buttonImageConnection.text = if (connected) getString(R.string.connected) else getString(R.string.refresh)
        binding.buttonImageConnection.isEnabled = connected.not() && currentJob?.isActive != true
        binding.imageSelectionValue.text = selectedImage?.let { prepared ->
            "${prepared.printWidth}x${prepared.printHeight} • ${prepared.ditheringMode.displayName}"
        } ?: getString(R.string.no_image_selected_label)
        binding.imagePreview.setImageBitmap(selectedImage?.previewBitmap)
        binding.imagePreview.isVisible = selectedImage != null
        binding.buttonPickImage.isEnabled = true
        binding.buttonPrintImage.isEnabled = connected && selectedImage != null && currentJob?.isActive != true

        binding.savedPrinterValue.text = if (savedPrinterAddress == null) {
            getString(R.string.no_printer_selected)
        } else {
            getString(
                R.string.saved_printer_description,
                appSettings.selectedPrinterName ?: getString(R.string.no_printer_selected),
                savedPrinterAddress
            )
        }
        binding.energyValue.text = formatEnergy(PrintEnergy.toPercent(appSettings.selectedPrintEnergy))
        ignoreEnergySliderCallback = true
        binding.sliderEnergy.value = PrintEnergy.toPercent(appSettings.selectedPrintEnergy).toFloat()
        ignoreEnergySliderCallback = false
        binding.buttonScanPrinters.isEnabled = currentJob?.isActive != true
        binding.buttonSavePrinter.isEnabled = selectedPrinter != null && currentJob?.isActive != true
        binding.buttonTestPrint.isEnabled = appSettings.selectedPrinterAddress != null && currentJob?.isActive != true
        binding.buttonOpenLogs.isEnabled = true
        binding.scanStatus.text = when {
            discoveredPrinters.isNotEmpty() -> getString(R.string.printers_found, discoveredPrinters.size)
            selectedTabId == R.id.navigation_settings -> currentStatus
            else -> ""
        }

        binding.logsValue.text = LogStore.asText().ifBlank { getString(R.string.no_logs_yet) }
        renderPrinterChoices()
        (supportFragmentManager.findFragmentById(R.id.text_fragment_container) as? TextFragment)
            ?.refreshConnectionSummary()
    }

    private fun renderPrinterChoices() {
        ignorePrinterSelectionCallback = true
        printerAdapter.clear()
        printerAdapter.addAll(
            if (discoveredPrinters.isEmpty()) {
                listOf(getString(R.string.no_printers_found))
            } else {
                discoveredPrinters.map { printer ->
                    getString(
                        R.string.nearby_printer_item,
                        printer.displayName,
                        printer.device.address
                    )
                }
            }
        )
        printerAdapter.notifyDataSetChanged()

        if (discoveredPrinters.isEmpty()) {
            binding.spinnerPrinters.setSelection(0, false)
        } else {
            val index = selectedScannedPrinterIndex?.takeIf { it in discoveredPrinters.indices }
                ?: discoveredPrinters.indexOfFirst { it.device.address == appSettings.selectedPrinterAddress }
                    .takeIf { it >= 0 }
                ?: 0
            binding.spinnerPrinters.setSelection(index, false)
        }
        ignorePrinterSelectionCallback = false
    }

    private fun saveSelectedPrinter() {
        val printer = selectedScannedPrinterIndex
            ?.takeIf { it in discoveredPrinters.indices }
            ?.let(discoveredPrinters::get)
        if (printer == null) {
            Toast.makeText(this, R.string.no_scanned_printer_selected, Toast.LENGTH_SHORT).show()
            return
        }

        if (appSettings.selectedPrinterAddress != printer.device.address) {
            appSettings.selectedPrinterAddress = printer.device.address
            appSettings.selectedPrinterName = printer.displayName
            Toast.makeText(this, getString(R.string.printer_saved, printer.displayName), Toast.LENGTH_SHORT).show()
            maybeAutoConnect(force = true)
        }
        render()
    }

    private fun formatEnergy(percent: Int): String {
        return getString(R.string.energy_value, percent)
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

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val SCREEN_LOGS = -1
    }
}
