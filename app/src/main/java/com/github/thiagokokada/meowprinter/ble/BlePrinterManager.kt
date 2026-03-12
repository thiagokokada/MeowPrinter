package com.github.thiagokokada.meowprinter.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest
import no.nordicsemi.android.ble.Request
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BlePrinterManager(
    context: Context,
    private val onDisconnected: () -> Unit
) : BleManager(context) {
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var readySignal = CompletableDeferred<Unit>()
    private var suppressDisconnectCallback = false

    var negotiatedMtu: Int = DEFAULT_MTU
        private set

    val isPrinterReady: Boolean
        get() = isConnected && txCharacteristic != null && rxCharacteristic != null

    init {
        setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) = Unit

            override fun onDeviceConnected(device: BluetoothDevice) = Unit

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) = Unit

            override fun onDeviceReady(device: BluetoothDevice) = Unit

            override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                if (!suppressDisconnectCallback) {
                    onDisconnected()
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAndInitialize(device: BluetoothDevice) {
        suppressDisconnectCallback = false
        await(connect(device).retry(3, 100).timeout(15_000))
    }

    suspend fun print(payload: ByteArray) {
        val tx = txCharacteristic ?: throw IOException("TX characteristic unavailable.")
        readySignal = CompletableDeferred()

        val chunkSize = (negotiatedMtu - 3).coerceAtLeast(20)
        for (offset in payload.indices step chunkSize) {
            val end = minOf(offset + chunkSize, payload.size)
            val chunk = payload.copyOfRange(offset, end)
            await(
                writeCharacteristic(
                    tx,
                    chunk,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            )
            delay(20)
        }

        withTimeout(30_000) {
            readySignal.await()
        }
    }

    fun release() {
        suppressDisconnectCallback = true
        if (isConnected) {
            disconnect().enqueue()
        }
        close()
    }

    @Deprecated("Required override from Nordic BleManager API.")
    override fun getGattCallback(): BleManagerGattCallback = object : BleManagerGattCallback() {
        @Deprecated("Required override from Nordic BleManager API.")
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(CatPrinterProtocol.primaryServiceUuid)
                ?: gatt.getService(CatPrinterProtocol.secondaryServiceUuid)

            txCharacteristic = service?.getCharacteristic(CatPrinterProtocol.txCharacteristicUuid)
            rxCharacteristic = service?.getCharacteristic(CatPrinterProtocol.rxCharacteristicUuid)

            val txValid = txCharacteristic?.properties?.let { properties ->
                properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            } == true
            val rxValid = rxCharacteristic?.properties?.let { properties ->
                properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            } == true
            return txValid && rxValid
        }

        override fun initialize() {
            val rx = requireNotNull(rxCharacteristic)
            setNotificationCallback(rx).with { _, data ->
                val value = data.value ?: return@with
                if (value.contentEquals(CatPrinterProtocol.readyNotification) && !readySignal.isCompleted) {
                    readySignal.complete(Unit)
                }
            }
            enableNotifications(rx).enqueue()
            requestMtu(MAX_MTU)
                .with { _, mtu -> negotiatedMtu = mtu }
                .fail { _, _ -> negotiatedMtu = DEFAULT_MTU }
                .enqueue()
            requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH).enqueue()
        }

        override fun onServicesInvalidated() {
            txCharacteristic = null
            rxCharacteristic = null
            negotiatedMtu = DEFAULT_MTU
        }
    }

    private suspend fun await(request: Request) {
        suspendCancellableCoroutine { continuation ->
            request
                .done {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                .fail { _, status ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException("BLE request failed with status $status."))
                    }
                }
                .enqueue()
        }
    }

    companion object {
        private const val DEFAULT_MTU = 23
        private const val MAX_MTU = 247
    }
}
