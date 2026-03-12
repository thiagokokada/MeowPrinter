package com.github.thiagokokada.meowprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BlePrinterScanner(context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    suspend fun findFirstCompatiblePrinter(timeoutMs: Long = 10_000): DiscoveredPrinter? {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth LE scanner unavailable.")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        if (!result.isCompatiblePrinter()) {
                            return
                        }

                        scanner.stopScan(this)
                        val displayName = result.device.name
                            ?: result.scanRecord?.deviceName
                            ?: "Unnamed printer"
                        if (continuation.isActive) {
                            continuation.resume(DiscoveredPrinter(result.device, displayName))
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("BLE scan failed with code $errorCode.")
                            )
                        }
                    }
                }

                scanner.startScan(emptyList<ScanFilter>(), settings, callback)
                continuation.invokeOnCancellation {
                    scanner.stopScan(callback)
                }
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
