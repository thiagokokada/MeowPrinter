package com.github.thiagokokada.meowprinter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.thiagokokada.meowprinter.R
import com.github.thiagokokada.meowprinter.ble.BlePermissions
import com.github.thiagokokada.meowprinter.ble.BlePrinterManager
import com.github.thiagokokada.meowprinter.ble.BlePrinterScanner
import com.github.thiagokokada.meowprinter.ble.DiscoveredPrinter
import com.github.thiagokokada.meowprinter.ble.PrintPacing
import com.github.thiagokokada.meowprinter.data.AppSettings
import com.github.thiagokokada.meowprinter.data.LogStore
import com.github.thiagokokada.meowprinter.databinding.ActivityMainBinding
import com.github.thiagokokada.meowprinter.image.DitheringMode
import com.github.thiagokokada.meowprinter.image.ImagePrintPreparer
import com.github.thiagokokada.meowprinter.image.PreparedPrintImage
import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import com.github.thiagokokada.meowprinter.print.PrintEnergy
import com.github.thiagokokada.meowprinter.print.PrinterTestPage
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import java.io.File

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
    private var ignorePacingSliderCallback = false
    private var ignorePaperMoveFieldCallback = false
    private var isAppVisible = false
    private var pendingNotificationPermissionAction: (() -> Unit)? = null

    private val imageSection get() = binding.imageSection
    private val settingsSection get() = binding.settingsSection

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasBlePermissions()) {
            maybeAutoConnect(force = true)
        }
        render()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingAction = pendingNotificationPermissionAction
        pendingNotificationPermissionAction = null
        if (granted) {
            pendingAction?.invoke()
            return@registerForActivityResult
        }

        if (shouldShowNotificationPermissionRationale()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_rationale)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.grant_notification_permission) { _, _ ->
                    appSettings.hasRequestedNotificationPermission = true
                    pendingNotificationPermissionAction = pendingAction
                    requestNotificationPermission()
                }
                .show()
        } else if (isNotificationPermissionPermanentlyDenied()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_open_settings)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.open_app_settings) { _, _ ->
                    openAppSettings()
                }
                .show()
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
                val editedUri = result.data?.data
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
                currentStatus = getString(R.string.image_edit_failed)
                appendLog("Image editing failed.")
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
        binding.bottomNavigation.applySideAndBottomSystemBarsPadding()
        binding.appTitle.text = getString(R.string.app_name)
        binding.toolbar.setNavigationOnClickListener(null)

        ditheringAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            DitheringMode.entries.map { it.displayName }
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        imageSection.spinnerDithering.adapter = ditheringAdapter
        imageSection.spinnerDithering.setSelection(appSettings.selectedDitheringMode.ordinal, false)
        imageSection.spinnerDithering.onItemSelectedListener = SimpleItemSelectedListener { position ->
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
        settingsSection.spinnerPrinters.adapter = printerAdapter
        settingsSection.spinnerPrinters.onItemSelectedListener = SimpleItemSelectedListener { position ->
            if (ignorePrinterSelectionCallback || position !in discoveredPrinters.indices) {
                return@SimpleItemSelectedListener
            }
            selectedScannedPrinterIndex = position
            render()
        }
        settingsSection.sliderEnergy.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || ignoreEnergySliderCallback) {
                return@addOnChangeListener
            }
            appSettings.selectedPrintEnergy = PrintEnergy.fromPercent(value.toInt())
            render()
        }
        settingsSection.sliderPrintPacing.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || ignorePacingSliderCallback) {
                return@addOnChangeListener
            }
            appSettings.selectedPrintPacingPercent = value.toInt()
            render()
        }
        settingsSection.inputPaperMoveSteps.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitPaperMoveStepsFromInput()
                true
            } else {
                false
            }
        }
        settingsSection.inputPaperMoveSteps.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                commitPaperMoveStepsFromInput()
            }
        }
        settingsSection.buttonPaperSteps10.setOnClickListener { updatePaperMoveSteps(10) }
        settingsSection.buttonPaperSteps25.setOnClickListener { updatePaperMoveSteps(25) }
        settingsSection.buttonPaperSteps50.setOnClickListener { updatePaperMoveSteps(50) }
        settingsSection.buttonPaperSteps100.setOnClickListener { updatePaperMoveSteps(100) }
        settingsSection.buttonEndPaperPasses0.setOnClickListener { updateEndPaperPasses(0) }
        settingsSection.buttonEndPaperPasses1.setOnClickListener { updateEndPaperPasses(1) }
        settingsSection.buttonEndPaperPasses2.setOnClickListener { updateEndPaperPasses(2) }
        settingsSection.buttonEndPaperPasses3.setOnClickListener { updateEndPaperPasses(3) }
        imageSection.buttonPickImage.setOnClickListener {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        imageSection.buttonImageConnection.setOnClickListener {
            ensureBlePermissionsThen { maybeAutoConnect(force = true) }
        }
        imageSection.buttonPrintImage.setOnClickListener {
            printSelectedImage()
        }
        settingsSection.buttonTestPrint.setOnClickListener {
            ensureBlePermissionsThen { printTestPageFromCurrentPrinter() }
        }
        settingsSection.buttonAdvancePaper.setOnClickListener {
            ensureBlePermissionsThen { movePaper(forward = true) }
        }
        settingsSection.buttonRetractPaper.setOnClickListener {
            ensureBlePermissionsThen { movePaper(forward = false) }
        }
        settingsSection.buttonOpenLogs.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        settingsSection.buttonScanPrinters.setOnClickListener {
            ensureBlePermissionsThen { scanPrinters() }
        }
        settingsSection.buttonSavePrinter.setOnClickListener {
            saveSelectedPrinter()
        }
        bindBottomNavigationIcons()
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showScreen(item.itemId)
            true
        }
        binding.bottomNavigation.selectedItemId = selectedTabId

        showScreen(selectedTabId)
        handleIntent(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isAppVisible = true
        maybeAutoConnect()
        render()
    }

    override fun onStop() {
        isAppVisible = false
        if (!ActivePrintController.isPrintActive) {
            disconnectForegroundConnection()
        }
        super.onStop()
    }

    override fun onDestroy() {
        currentJob?.cancel()
        ActivePrintController.finish()
        PrintNotificationManager.dismiss(applicationContext)
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
        binding.screenTitle.text = when (tabId) {
            R.id.navigation_image -> getString(R.string.nav_image)
            R.id.navigation_text -> getString(R.string.nav_text)
            else -> getString(R.string.nav_settings)
        }
        binding.toolbar.navigationIcon = null
        render()
    }

    private fun bindBottomNavigationIcons() {
        val iconSizePx = dp(20)
        val menu = binding.bottomNavigation.menu
        menu.findItem(R.id.navigation_image).icon = IconicsDrawable(this, FontAwesome.Icon.faw_image).apply {
            sizeXPx = iconSizePx
            sizeYPx = iconSizePx
        }
        menu.findItem(R.id.navigation_text).icon = IconicsDrawable(this, FontAwesome.Icon.faw_pen).apply {
            sizeXPx = iconSizePx
            sizeYPx = iconSizePx
        }
        menu.findItem(R.id.navigation_settings).icon = IconicsDrawable(this, FontAwesome.Icon.faw_cog).apply {
            sizeXPx = iconSizePx
            sizeYPx = iconSizePx
        }
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

        val destinationFile = File(
            cacheDir,
            "edited-${System.currentTimeMillis()}.jpg"
        )
        val destinationUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            destinationFile
        )
        imageEditorLauncher.launch(ImageCropActivity.intent(this, sourceUri, destinationUri))
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

        ensureNotificationPermissionThen {
            startPrintPreparedImage(image, "selected image")
        }
    }

    override fun printPreparedImage(preparedImage: PreparedPrintImage, sourceLabel: String) {
        ensureNotificationPermissionThen {
            startPrintPreparedImage(preparedImage, sourceLabel)
        }
    }

    private fun startPrintPreparedImage(preparedImage: PreparedPrintImage, sourceLabel: String) {
        if (ActivePrintController.isPrintActive) {
            return
        }
        val manager = printerManager
        if (manager == null || !manager.isPrinterReady) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            ensureBlePermissionsThen { maybeAutoConnect(force = true) }
            return
        }

        runTrackedJob(getString(R.string.generating_image_print_job)) { launchedJob ->
            beginActivePrint(launchedJob)
            try {
                val energy = appSettings.selectedPrintEnergy
                val energyLabel = formatEnergy(PrintEnergy.toPercent(energy))
                val commands = CatPrinterProtocol.commandsPrintImageCommands(
                    preparedImage.rows,
                    energy = energy,
                    printGapSteps = appSettings.selectedPaperMoveSteps,
                    endPaperPasses = appSettings.selectedEndPaperPasses
                )
                val payloadSize = commands.sumOf { it.size }
                appendLog(
                    "Generated ${preparedImage.rows.size} rows and $payloadSize bytes from $sourceLabel at $energyLabel. Gap ${appSettings.selectedPaperMoveSteps} steps, end passes ${appSettings.selectedEndPaperPasses}."
                )
                currentStatus = getString(R.string.printing_selected_image_with_energy, energyLabel)
                render()

                manager.printCommands(commands, currentPrintPacing())

                currentStatus = getString(R.string.printer_ready_again)
                appendLog("Image print completed successfully.")
            } catch (_: CancellationException) {
                currentStatus = getString(R.string.print_canceled)
                appendLog("Image print canceled.")
            } catch (e: Exception) {
                currentStatus = getString(R.string.image_print_failed)
                appendLog("Image print failed: ${e.message ?: getString(R.string.unknown_error)}")
            } finally {
                endActivePrint()
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

    override fun isPrintInProgress(): Boolean = ActivePrintController.isPrintActive

    private fun printTestPageFromCurrentPrinter() {
        if (ActivePrintController.isPrintActive) {
            return
        }
        ensureNotificationPermissionThen(::startPrintTestPageFromCurrentPrinter)
    }

    private fun startPrintTestPageFromCurrentPrinter() {
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
            beginActivePrint(launchedJob)
            try {
                withSavedPrinterManager(printerAddress) { printerName, manager ->
                    val energy = appSettings.selectedPrintEnergy
                    val energyLabel = formatEnergy(PrintEnergy.toPercent(energy))
                    currentStatus = getString(R.string.testing_saved_printer_with_energy, energyLabel)
                    render()
                    val commands = CatPrinterProtocol.commandsPrintImageCommands(
                        PrinterTestPage.createRows(),
                        energy = energy,
                        printGapSteps = appSettings.selectedPaperMoveSteps,
                        endPaperPasses = appSettings.selectedEndPaperPasses
                    )
                    manager.printCommands(commands, currentPrintPacing())
                    appendLog("Test page printed on $printerName at $energyLabel. Gap ${appSettings.selectedPaperMoveSteps} steps, end passes ${appSettings.selectedEndPaperPasses}.")
                    currentStatus = getString(R.string.test_page_sent)
                }
            } catch (_: CancellationException) {
                currentStatus = getString(R.string.print_canceled)
                appendLog("Test page print canceled.")
            } catch (e: Exception) {
                currentStatus = getString(R.string.scan_failed_message, e.message ?: getString(R.string.unknown_error))
            } finally {
                endActivePrint()
                finishTrackedJob(launchedJob)
            }
        }
    }

    private fun movePaper(forward: Boolean) {
        val printerAddress = appSettings.selectedPrinterAddress
        if (printerAddress == null) {
            Toast.makeText(this, R.string.select_printer_in_settings, Toast.LENGTH_SHORT).show()
            return
        }
        if (!scanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        val progressStatus = if (forward) {
            getString(R.string.advancing_paper)
        } else {
            getString(R.string.retracting_paper)
        }

        runTrackedJob(progressStatus) { launchedJob ->
            try {
                withSavedPrinterManager(printerAddress) { printerName, manager ->
                    val steps = appSettings.selectedPaperMoveSteps
                    val payload = if (forward) {
                        CatPrinterProtocol.cmdAdvancePaper(steps)
                    } else {
                        CatPrinterProtocol.cmdRetractPaper(steps)
                    }
                    manager.send(payload)
                    currentStatus = if (forward) {
                        getString(R.string.paper_advanced)
                    } else {
                        getString(R.string.paper_retracted)
                    }
                    appendLog(
                        "${if (forward) "Advanced" else "Retracted"} paper on $printerName by " +
                            "$steps steps."
                    )
                }
            } catch (e: Exception) {
                currentStatus = if (forward) {
                    getString(R.string.paper_advance_failed)
                } else {
                    getString(R.string.paper_retract_failed)
                }
                appendLog(
                    "Paper ${if (forward) "advance" else "retract"} failed: " +
                        (e.message ?: getString(R.string.unknown_error))
                )
            } finally {
                finishTrackedJob(launchedJob)
            }
        }
    }

    private suspend fun withSavedPrinterManager(
        printerAddress: String,
        action: suspend (printerName: String, manager: BlePrinterManager) -> Unit
    ) {
        val activeManager = printerManager
        if (activeManager?.isPrinterReady == true && appSettings.selectedPrinterAddress == printerAddress) {
            action(connectedPrinterName ?: appSettings.selectedPrinterName ?: printerAddress, activeManager)
            return
        }

        val printer = scanner.findFirstCompatiblePrinter(preferredAddress = printerAddress)
        if (printer == null) {
            currentStatus = getString(R.string.saved_printer_not_found)
            return
        }

        val temporaryManager = BlePrinterManager(applicationContext) {}
        try {
            temporaryManager.connectAndInitialize(printer.device)
            action(printer.displayName, temporaryManager)
        } finally {
            temporaryManager.release()
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

    private fun beginActivePrint(job: Job) {
        ActivePrintController.start {
            if (job.isActive) {
                currentStatus = getString(R.string.canceling_print)
                appendLog("Canceling active print job.")
                job.cancel(CancellationException("Canceled by user."))
                disconnectForegroundConnection()
                render()
            }
        }
        PrintNotificationManager.show(applicationContext)
    }

    private fun endActivePrint() {
        ActivePrintController.finish()
        PrintNotificationManager.dismiss(applicationContext)
        if (!isAppVisible) {
            disconnectForegroundConnection()
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_CANCEL_PRINT -> {
                ActivePrintController.cancel()
                intent.action = null
            }

            Intent.ACTION_SEND -> {
                val sharedImageUri = extractSharedImageUri(intent)
                if (sharedImageUri != null) {
                    showScreen(R.id.navigation_image)
                    appendLog("Received shared image.")
                    launchImageEditor(sharedImageUri)
                } else {
                    appendLog("Ignored share intent without an image.")
                }
                intent.action = null
            }
        }
    }

    private fun extractSharedImageUri(intent: Intent): Uri? {
        val isImageShare = intent.type?.startsWith("image/") == true
        if (!isImageShare) {
            return null
        }
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { return it }
        return intent.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun render() {
        val connected = printerManager?.isPrinterReady == true
        val isBusy = currentJob?.isCompleted == false
        val printerName = connectedPrinterName ?: appSettings.selectedPrinterName ?: getString(R.string.no_printer_selected)
        val savedPrinterAddress = appSettings.selectedPrinterAddress
        val selectedPrinter = selectedScannedPrinterIndex?.takeIf { it in discoveredPrinters.indices }?.let(discoveredPrinters::get)

        imageSection.printerValue.text = printerName
        imageSection.imageStatusValue.text = currentStatus
        imageSection.buttonImageConnection.text = if (connected) getString(R.string.connected) else getString(R.string.refresh)
        imageSection.buttonImageConnection.isEnabled = connected.not() && !isBusy
        imageSection.imageSelectionValue.text = selectedImage?.let { prepared ->
            "${prepared.printWidth}x${prepared.printHeight} • ${prepared.ditheringMode.displayName}"
        } ?: getString(R.string.no_image_selected_label)
        imageSection.imagePreview.setImageBitmap(selectedImage?.previewBitmap)
        imageSection.imagePreview.isVisible = selectedImage != null
        imageSection.buttonPickImage.isEnabled = true
        imageSection.buttonPrintImage.isEnabled = connected && selectedImage != null && !ActivePrintController.isPrintActive && !isBusy

        val savedPrinterName = appSettings.selectedPrinterName ?: getString(R.string.no_printer_selected)
        settingsSection.savedPrinterValue.text = savedPrinterName
        settingsSection.savedPrinterStatusValue.text = when {
            savedPrinterAddress == null -> ""
            connected && connectedPrinterName == appSettings.selectedPrinterName -> currentStatus
            else -> getString(R.string.disconnected)
        }
        settingsSection.savedPrinterStatusValue.isVisible = savedPrinterAddress != null
        settingsSection.energyValue.text = formatEnergy(PrintEnergy.toPercent(appSettings.selectedPrintEnergy))
        ignoreEnergySliderCallback = true
        settingsSection.sliderEnergy.value = PrintEnergy.toPercent(appSettings.selectedPrintEnergy).toFloat()
        ignoreEnergySliderCallback = false
        val pacingPercent = appSettings.selectedPrintPacingPercent
        settingsSection.printPacingValue.text = formatPrintPacing(pacingPercent)
        ignorePacingSliderCallback = true
        settingsSection.sliderPrintPacing.value = pacingPercent.toFloat()
        ignorePacingSliderCallback = false
        val paperMoveSteps = appSettings.selectedPaperMoveSteps
        val paperMoveStepsText = paperMoveSteps.toString()
        if (settingsSection.inputPaperMoveSteps.text?.toString() != paperMoveStepsText) {
            ignorePaperMoveFieldCallback = true
            settingsSection.inputPaperMoveSteps.setText(paperMoveStepsText)
            settingsSection.inputPaperMoveSteps.setSelection(paperMoveStepsText.length)
            ignorePaperMoveFieldCallback = false
        }
        settingsSection.inputLayoutPaperMoveSteps.error = null
        settingsSection.endPaperPassesValue.text = getString(R.string.end_paper_passes_value, appSettings.selectedEndPaperPasses)
        settingsSection.buttonScanPrinters.isEnabled = !isBusy
        settingsSection.buttonSavePrinter.isEnabled = selectedPrinter != null && !isBusy
        settingsSection.buttonTestPrint.isEnabled = appSettings.selectedPrinterAddress != null && !isBusy
        settingsSection.buttonAdvancePaper.isEnabled = appSettings.selectedPrinterAddress != null && !isBusy
        settingsSection.buttonRetractPaper.isEnabled = appSettings.selectedPrinterAddress != null && !isBusy
        settingsSection.buttonOpenLogs.isEnabled = true
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
            settingsSection.spinnerPrinters.setSelection(0, false)
        } else {
            val index = selectedScannedPrinterIndex?.takeIf { it in discoveredPrinters.indices }
                ?: discoveredPrinters.indexOfFirst { it.device.address == appSettings.selectedPrinterAddress }
                    .takeIf { it >= 0 }
                ?: 0
            settingsSection.spinnerPrinters.setSelection(index, false)
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

    private fun formatPrintPacing(percent: Int): String {
        return getString(R.string.print_pacing_value, percent)
    }

    private fun commitPaperMoveStepsFromInput() {
        if (ignorePaperMoveFieldCallback) {
            return
        }
        val rawValue = settingsSection.inputPaperMoveSteps.text?.toString()?.trim().orEmpty()
        val parsed = rawValue.toIntOrNull()
        if (parsed == null) {
            settingsSection.inputLayoutPaperMoveSteps.error = getString(R.string.paper_move_steps_invalid)
            return
        }
        settingsSection.inputLayoutPaperMoveSteps.error = null
        updatePaperMoveSteps(parsed)
    }

    private fun updatePaperMoveSteps(steps: Int) {
        appSettings.selectedPaperMoveSteps = steps
        render()
    }

    private fun updateEndPaperPasses(passes: Int) {
        appSettings.selectedEndPaperPasses = passes
        render()
    }

    private fun currentPrintPacing(): PrintPacing {
        return PrintPacing.fromPercent(appSettings.selectedPrintPacingPercent)
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

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationPermissionThen(onGranted: () -> Unit): Boolean {
        if (hasNotificationPermission()) {
            onGranted()
            return true
        }

        pendingNotificationPermissionAction = onGranted
        when {
            shouldShowNotificationPermissionRationale() -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.notification_permission_title)
                    .setMessage(R.string.notification_permission_rationale)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.grant_notification_permission) { _, _ ->
                        appSettings.hasRequestedNotificationPermission = true
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .show()
            }
            isNotificationPermissionPermanentlyDenied() -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.notification_permission_title)
                    .setMessage(R.string.notification_permission_open_settings)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.open_app_settings) { _, _ ->
                        openAppSettings()
                    }
                    .show()
            }
            else -> {
                appSettings.hasRequestedNotificationPermission = true
                requestNotificationPermission()
            }
        }
        return false
    }

    private fun requestNotificationPermission() {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun shouldShowNotificationPermissionRationale(): Boolean {
        return shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun isNotificationPermissionPermanentlyDenied(): Boolean {
        return !hasNotificationPermission() &&
            appSettings.hasRequestedNotificationPermission &&
            !shouldShowNotificationPermissionRationale()
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
        const val ACTION_CANCEL_PRINT = "com.github.thiagokokada.meowprinter.action.CANCEL_PRINT"
        private const val KEY_SELECTED_TAB = "selected_tab"
    }
}
