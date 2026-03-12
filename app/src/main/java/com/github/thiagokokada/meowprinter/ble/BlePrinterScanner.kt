package com.github.thiagokokada.meowprinter.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BlePrinterScanner(context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    suspend fun findFirstCompatiblePrinter(
        preferredAddress: String? = null,
        timeoutMs: Long = 10_000
    ): DiscoveredPrinter? {
        val printers = scanCompatiblePrinters(timeoutMs)
        return if (preferredAddress != null) {
            printers.firstOrNull { it.device.address == preferredAddress }
        } else {
            printers.firstOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun scanCompatiblePrinters(timeoutMs: Long = 10_000): List<DiscoveredPrinter> {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth LE scanner unavailable.")
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val foundPrinters = linkedMapOf<String, DiscoveredPrinter>()
            lateinit var timeoutRunnable: Runnable

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (!result.isCompatiblePrinter()) {
                        return
                    }

                    val displayName = result.device.name
                        ?: result.scanRecord?.deviceName
                        ?: "Unnamed printer"
                    foundPrinters[result.device.address] = DiscoveredPrinter(result.device, displayName)
                }

                override fun onScanFailed(errorCode: Int) {
                    handler.removeCallbacks(timeoutRunnable)
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("BLE scan failed with code $errorCode.")
                        )
                    }
                }
            }

            timeoutRunnable = Runnable {
                scanner.stopScan(callback)
                if (continuation.isActive) {
                    continuation.resume(foundPrinters.values.toList())
                }
            }

            scanner.startScan(emptyList<ScanFilter>(), settings, callback)
            handler.postDelayed(timeoutRunnable, timeoutMs)
            continuation.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                scanner.stopScan(callback)
            }
        }
    }

    private fun ScanResult.isCompatiblePrinter(): Boolean {
        val knownNames = setOf("GT01", "GB02", "GB03")
        val serviceUuids = scanRecord?.serviceUuids.orEmpty().map { it.uuid }
        return device.name in knownNames ||
            scanRecord?.deviceName in knownNames ||
            serviceUuids.any { uuid ->
                uuid == CatPrinterProtocol.primaryServiceUuid || uuid == CatPrinterProtocol.secondaryServiceUuid
            }
    }
}

data class DiscoveredPrinter(
    val device: BluetoothDevice,
    val displayName: String
)
