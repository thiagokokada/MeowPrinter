package com.github.thiagokokada.meowprinter

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.thiagokokada.meowprinter.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: BlePrinterScanner

    private var printerManager: BlePrinterManager? = null
    private var printerName: String? = null
    private var connectionLabel = "Disconnected"
    private var currentStatus = "Request BLE permissions to start."
    private var currentJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasBlePermissions()) {
            appendLog("BLE permissions granted.")
        } else {
            appendLog("BLE permissions are required to scan and connect.")
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        scanner = BlePrinterScanner(applicationContext)

        binding.buttonPermissions.setOnClickListener {
            permissionLauncher.launch(BLE_PERMISSIONS)
        }
        binding.buttonScanConnect.setOnClickListener {
            if (!hasBlePermissions()) {
                currentStatus = "BLE permissions are missing."
                render()
                permissionLauncher.launch(BLE_PERMISSIONS)
            } else {
                scanAndConnect()
            }
        }
        binding.buttonDisconnect.setOnClickListener {
            disconnectPrinter()
        }
        binding.buttonPrintTest.setOnClickListener {
            printTestPage()
        }
        binding.buttonClearLog.setOnClickListener {
            binding.logView.text = ""
        }

        if (hasBlePermissions()) {
            appendLog("BLE permissions already granted.")
        }
        render()
    }

    override fun onDestroy() {
        currentJob?.cancel()
        printerManager?.release()
        super.onDestroy()
    }

    private fun scanAndConnect() {
        currentJob?.cancel()
        var launchedJob: Job? = null
        launchedJob = lifecycleScope.launch {
            setBusy("Scanning for a compatible printer...")
            try {
                if (!scanner.isBluetoothEnabled()) {
                    currentStatus = "Bluetooth is disabled."
                    appendLog("Bluetooth is disabled on the device.")
                    render()
                    return@launch
                }

                val result = scanner.findFirstCompatiblePrinter()
                if (result == null) {
                    currentStatus = "No compatible printer found."
                    appendLog("No Cat/Meow printer advertisement was found within 10 seconds.")
                    render()
                    return@launch
                }

                appendLog("Found ${result.displayName} (${result.device.address}). Connecting...")
                connectToPrinter(result.device, result.displayName)
            } catch (e: Exception) {
                currentStatus = "Scan failed."
                appendLog("Scan failed: ${e.message ?: "unknown error"}")
                render()
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

    private suspend fun connectToPrinter(device: BluetoothDevice, displayName: String) {
        printerManager?.release()
        val manager = BlePrinterManager(applicationContext) {
            runOnUiThread {
                printerName = null
                connectionLabel = "Disconnected"
                currentStatus = "Printer disconnected."
                appendLog("Printer disconnected.")
                printerManager = null
                render()
            }
        }
        printerManager = manager
        try {
            currentStatus = "Connecting..."
            connectionLabel = "Connecting"
            render()

            manager.connectAndInitialize(device)

            printerName = displayName
            connectionLabel = "Connected"
            currentStatus = "Ready to print."
            appendLog("Connected to $displayName. MTU ${manager.negotiatedMtu}.")
            render()
        } catch (e: Exception) {
            manager.release()
            printerManager = null
            printerName = null
            connectionLabel = "Disconnected"
            currentStatus = "Connection failed."
            appendLog("Connection failed: ${e.message ?: "unknown error"}")
            render()
        }
    }

    private fun printTestPage() {
        val manager = printerManager
        if (manager == null || !manager.isPrinterReady) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        currentJob?.cancel()
        var launchedJob: Job? = null
        launchedJob = lifecycleScope.launch {
            setBusy("Generating test page...")
            try {
                val rows = PrinterTestPage.createRows()
                val payload = CatPrinterProtocol.commandsPrintImage(rows)
                appendLog("Generated ${rows.size} rows and ${payload.size} bytes of print data.")
                currentStatus = "Printing test page..."
                render()

                manager.print(payload)

                currentStatus = "Printer is ready again."
                appendLog("Print completed successfully.")
                render()
            } catch (e: Exception) {
                currentStatus = "Print failed."
                appendLog("Print failed: ${e.message ?: "unknown error"}")
                render()
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

    private fun disconnectPrinter() {
        currentJob?.cancel()
        printerManager?.release()
        printerManager = null
        printerName = null
        connectionLabel = "Disconnected"
        currentStatus = "Disconnected."
        appendLog("Disconnected from printer.")
        binding.progressIndicator.isVisible = false
        render()
    }

    private fun setBusy(status: String) {
        currentStatus = status
        binding.progressIndicator.isVisible = true
        render()
    }

    private fun appendLog(message: String) {
        val current = binding.logView.text?.toString().orEmpty()
        binding.logView.text = buildString {
            if (current.isNotBlank()) {
                append(current)
                append('\n')
            }
            append(message)
        }
    }

    private fun render() {
        val hasPermissions = hasBlePermissions()
        val connected = printerManager?.isPrinterReady == true

        binding.permissionsValue.text = if (hasPermissions) "Granted" else "Missing"
        binding.deviceValue.text = printerName ?: "None"
        binding.connectionValue.text = connectionLabel
        binding.statusValue.text = currentStatus

        binding.buttonPermissions.isEnabled = !hasPermissions
        binding.buttonScanConnect.isEnabled = hasPermissions && currentJob?.isActive != true && !connected
        binding.buttonDisconnect.isEnabled = connected
        binding.buttonPrintTest.isEnabled = connected && currentJob?.isActive != true
    }

    private fun hasBlePermissions(): Boolean {
        return BLE_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private val BLE_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
}
