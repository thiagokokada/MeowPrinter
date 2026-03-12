package com.github.thiagokokada.meowprinter

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.thiagokokada.meowprinter.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appSettings: AppSettings
    private lateinit var scanner: BlePrinterScanner
    private lateinit var printerAdapter: ArrayAdapter<String>

    private var currentJob: Job? = null
    private var discoveredPrinters: List<DiscoveredPrinter> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appSettings = AppSettings(applicationContext)
        scanner = BlePrinterScanner(applicationContext)

        binding.toolbar.applyTopSystemBarPadding()
        binding.contentContainer.applySideAndBottomSystemBarsPadding()
        binding.toolbar.setNavigationOnClickListener { finish() }

        printerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf())
        binding.listPrinters.adapter = printerAdapter
        binding.listPrinters.setOnItemClickListener { _, _, position, _ ->
            val printer = discoveredPrinters[position]
            appSettings.selectedPrinterAddress = printer.device.address
            appSettings.selectedPrinterName = printer.displayName
            Toast.makeText(this, getString(R.string.printer_saved, printer.displayName), Toast.LENGTH_SHORT).show()
            render()
        }

        binding.buttonScanPrinters.setOnClickListener {
            ensureBlePermissionsThen { scanPrinters() }
        }
        binding.buttonForgetPrinter.setOnClickListener {
            appSettings.clearSelectedPrinter()
            render()
        }
        binding.buttonTestPrint.setOnClickListener {
            ensureBlePermissionsThen { printTestPageFromSettings() }
        }
        binding.buttonClearLogs.setOnClickListener {
            LogStore.clear()
            render()
        }

        render()
    }

    override fun onDestroy() {
        currentJob?.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun scanPrinters() {
        if (!scanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        currentJob?.cancel()
        var launchedJob: Job? = null
        launchedJob = lifecycleScope.launch {
            binding.progressIndicator.isVisible = true
            binding.scanStatus.text = getString(R.string.scanning_printers)
            try {
                discoveredPrinters = scanner.scanCompatiblePrinters()
                binding.scanStatus.text = if (discoveredPrinters.isEmpty()) {
                    getString(R.string.no_printers_found)
                } else {
                    getString(R.string.printers_found, discoveredPrinters.size)
                }
                renderPrinterList()
            } catch (e: Exception) {
                binding.scanStatus.text = getString(R.string.scan_failed_message, e.message ?: getString(R.string.unknown_error))
            } finally {
                if (currentJob === launchedJob) {
                    currentJob = null
                }
                binding.progressIndicator.isVisible = false
                render()
            }
        }
        currentJob = launchedJob
    }

    private fun printTestPageFromSettings() {
        val printerAddress = appSettings.selectedPrinterAddress
        if (printerAddress == null) {
            Toast.makeText(this, R.string.select_printer_in_settings, Toast.LENGTH_SHORT).show()
            return
        }

        if (!scanner.isBluetoothEnabled()) {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        currentJob?.cancel()
        var launchedJob: Job? = null
        launchedJob = lifecycleScope.launch {
            binding.progressIndicator.isVisible = true
            binding.scanStatus.text = getString(R.string.testing_saved_printer)
            try {
                val printer = scanner.findFirstCompatiblePrinter(preferredAddress = printerAddress)
                if (printer == null) {
                    binding.scanStatus.text = getString(R.string.saved_printer_not_found)
                    return@launch
                }

                val manager = BlePrinterManager(applicationContext) {}
                try {
                    manager.connectAndInitialize(printer.device)
                    val payload = CatPrinterProtocol.commandsPrintImage(PrinterTestPage.createRows())
                    manager.print(payload)
                    LogStore.append("Test page printed through Settings on ${printer.displayName}.")
                    binding.scanStatus.text = getString(R.string.test_page_sent)
                } finally {
                    manager.release()
                }
            } catch (e: Exception) {
                binding.scanStatus.text = getString(R.string.scan_failed_message, e.message ?: getString(R.string.unknown_error))
            } finally {
                if (currentJob === launchedJob) {
                    currentJob = null
                }
                binding.progressIndicator.isVisible = false
                render()
            }
        }
        currentJob = launchedJob
    }

    private fun render() {
        binding.savedPrinterValue.text = appSettings.selectedPrinterName ?: getString(R.string.no_printer_selected)
        binding.buttonForgetPrinter.isEnabled = appSettings.selectedPrinterAddress != null
        binding.buttonScanPrinters.isEnabled = currentJob?.isActive != true
        binding.buttonTestPrint.isEnabled = appSettings.selectedPrinterAddress != null && currentJob?.isActive != true
        binding.logsValue.text = LogStore.asText().ifBlank { getString(R.string.no_logs_yet) }
        renderPrinterList()
    }

    private fun renderPrinterList() {
        printerAdapter.clear()
        printerAdapter.addAll(
            discoveredPrinters.map { printer ->
                "${printer.displayName} (${printer.device.address})"
            }
        )
        printerAdapter.notifyDataSetChanged()

        val selectedAddress = appSettings.selectedPrinterAddress
        val checkedIndex = discoveredPrinters.indexOfFirst { it.device.address == selectedAddress }
        if (checkedIndex >= 0) {
            binding.listPrinters.setItemChecked(checkedIndex, true)
        } else {
            binding.listPrinters.clearChoices()
        }
    }

    private fun hasBlePermissions(): Boolean {
        return BlePermissions.required.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
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

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }
}
