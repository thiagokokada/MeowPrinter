package com.github.thiagokokada.meowprinter.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.github.thiagokokada.meowprinter.print.CatPrinterProtocol
import com.github.thiagokokada.meowprinter.print.Mxw01Protocol
import com.github.thiagokokada.meowprinter.print.PrinterProtocolProfile
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
    val protocolProfile: PrinterProtocolProfile = PrinterProtocolProfile.CAT,
    private val onDisconnected: (BlePrinterManager) -> Unit
) : BleManager(context) {
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private var mxw01DataCharacteristic: BluetoothGattCharacteristic? = null

    private var mxw01Session: Mxw01Session? = null
    private var notificationsEnabled = false

    private var readySignal = CompletableDeferred<Unit>()
    private var suppressDisconnectCallback = false

    var negotiatedMtu: Int = DEFAULT_MTU
        private set

    val isPrinterReady: Boolean
        get() = isConnected && txCharacteristic != null && rxCharacteristic != null &&
            (protocolProfile != PrinterProtocolProfile.MXW01 ||
                (notificationsEnabled && mxw01DataCharacteristic != null && mxw01Session?.isActive == true))

    init {
        setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) = Unit

            override fun onDeviceConnected(device: BluetoothDevice) = Unit

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) = Unit

            override fun onDeviceReady(device: BluetoothDevice) = Unit

            override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                if (!suppressDisconnectCallback) {
                    onDisconnected(this@BlePrinterManager)
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
        readySignal = CompletableDeferred()
        send(payload)
        withTimeout(30_000) {
            readySignal.await()
        }
    }

    suspend fun printCommands(
        commands: List<ByteArray>,
        pacing: PrintPacing = PrintPacingProfile.BALANCED.toPacing(100)
    ) {
        readySignal = CompletableDeferred()
        commands.forEachIndexed { index, command ->
            send(command)
            applyCommandPacing(index, command, pacing)
        }
        withTimeout(30_000) {
            readySignal.await()
        }
    }

    suspend fun send(payload: ByteArray) {
        val tx = txCharacteristic ?: throw IOException("TX characteristic unavailable.")
        val writeType = preferredWriteType(tx)
        val chunkSize = (negotiatedMtu - 3).coerceAtLeast(20)
        var chunkIndex = 0
        for (offset in payload.indices step chunkSize) {
            val end = minOf(offset + chunkSize, payload.size)
            val chunk = payload.copyOfRange(offset, end)
            await(
                writeCharacteristic(
                    tx,
                    chunk,
                    writeType
                )
            )
            applyWritePacing(writeType, chunkIndex)
            chunkIndex += 1
        }
    }


    suspend fun printMxw01(
        rows: List<BooleanArray>,
        intensity: Int = 93,
        pacing: PrintPacing = PrintPacingProfile.BALANCED.toPacing(100)
    ) {
        check(protocolProfile == PrinterProtocolProfile.MXW01) { "MXW01 mode is not enabled." }
        check(isPrinterReady) { "Printer is not ready." }

        val dataChannel = mxw01DataCharacteristic
            ?: throw IOException("MXW01 AE03 channel unavailable.")

        val image = Mxw01Protocol.encodeImage(rows)
        val paddedLines = image.size / Mxw01Protocol.BYTES_PER_ROW
        val session = mxw01Session ?: throw IOException("MXW01 session unavailable.")

        session.transaction {
            withTimeout(10_000) { send(Mxw01Protocol.setIntensity(intensity)) }
            val status = session.request(0xA1) { send(Mxw01Protocol.getStatus()) }
            Log.d("MeowMXW01", "Status payload: ${status.toHexString()}")

            session.withResponse(0xAA) { completed ->
                val accepted = session.request(0xA9) {
                    send(Mxw01Protocol.startPrint(paddedLines))
                }
                if (accepted.firstOrNull()?.toInt() != 0) {
                    throw IOException("MXW01 rejected print request.")
                }

                val chunkSize = (negotiatedMtu - 3).coerceIn(20, 244)
                for (offset in image.indices step chunkSize) {
                    val chunk = image.copyOfRange(offset, minOf(offset + chunkSize, image.size))
                    withTimeout(10_000) {
                        await(writeCharacteristic(dataChannel, chunk, preferredWriteType(dataChannel)))
                    }
                    // Preserve the pacing used by the physically tested implementation.
                    delay((pacing.rowCommandDelayMs + 20L).coerceIn(8L, 50L))
                }

                withTimeout(10_000) { send(Mxw01Protocol.finishPrint()) }
                withTimeout(60_000) { completed.await() }
            }
        }
    }

    suspend fun getMxw01Battery(): Int {
        check(protocolProfile.supportsBattery) {
            "Battery query only supported in MXW01 mode."
        }
        check(isPrinterReady) { "Printer is not ready." }
        val session = mxw01Session ?: throw IOException("MXW01 session unavailable.")
        return session.transaction {
            val payload = session.request(0xAB) { send(Mxw01Protocol.getBattery()) }
            payload[0].toInt() and 0xFF
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") {
            "%02X".format(it.toInt() and 0xFF)
        }


    fun release() {
        suppressDisconnectCallback = true
        mxw01Session?.invalidate()
        mxw01Session = null
        notificationsEnabled = false
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
            mxw01DataCharacteristic = service?.getCharacteristic(Mxw01Protocol.dataCharacteristicUuid)

            val txValid = txCharacteristic?.properties?.let { properties ->
                properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            } == true
            val rxValid = rxCharacteristic?.properties?.let { properties ->
                properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            } == true
            val dataValid =
                mxw01DataCharacteristic?.properties?.let { properties ->
                    properties and (
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                    ) != 0
                } == true

            return txValid && rxValid && (protocolProfile != PrinterProtocolProfile.MXW01 || dataValid)
        }

        @Deprecated("Required override from Nordic BleManager API.")
        override fun initialize() {
            val rx = requireNotNull(rxCharacteristic)
            mxw01Session?.invalidate()
            val session = if (protocolProfile == PrinterProtocolProfile.MXW01) {
                Mxw01Session { failedSession ->
                    // No wire transaction IDs: recover the connection after ambiguity.
                    if (mxw01Session === failedSession) {
                        release()
                        onDisconnected(this@BlePrinterManager)
                    }
                }
            } else null
            mxw01Session = session
            notificationsEnabled = false
            setNotificationCallback(rx).with { _, data ->
                val value = data.value ?: return@with
                if (session != null) {
                    Log.d("MeowMXW01", "RX: ${value.toHexString()}")
                    session.onNotification(value)
                }
                if (value.contentEquals(CatPrinterProtocol.readyNotification) && !readySignal.isCompleted) {
                    readySignal.complete(Unit)
                }
            }
            enableNotifications(rx)
                .done { if (mxw01Session === session) notificationsEnabled = true }
                .fail { _, _ ->
                    if (session != null && mxw01Session === session) {
                        session.invalidate()
                        release()
                        onDisconnected(this@BlePrinterManager)
                    }
                }
                .enqueue()
            requestMtu(MAX_MTU)
                .with { _, mtu -> negotiatedMtu = mtu }
                .fail { _, _ -> negotiatedMtu = DEFAULT_MTU }
                .enqueue()
            requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH).enqueue()
        }

        @Deprecated("Required override from Nordic BleManager API.")
        override fun onServicesInvalidated() {
            txCharacteristic = null
            rxCharacteristic = null
            negotiatedMtu = DEFAULT_MTU
            mxw01DataCharacteristic = null
            notificationsEnabled = false
            mxw01Session?.invalidate()
            mxw01Session = null
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

    private fun preferredWriteType(characteristic: BluetoothGattCharacteristic): Int {
        val supportsWrite =
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        return if (supportsWrite) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
    }

    private suspend fun applyWritePacing(writeType: Int, chunkIndex: Int) {
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
            delay(8)
            return
        }

        delay(30)
        if ((chunkIndex + 1) % LONG_TRANSFER_PAUSE_EVERY_CHUNKS == 0) {
            delay(LONG_TRANSFER_EXTRA_PAUSE_MS)
        }
    }

    private suspend fun applyCommandPacing(index: Int, command: ByteArray, pacing: PrintPacing) {
        val commandId = command.getOrNull(2)?.toInt()
        when (commandId) {
            PRINT_ROW_RLE_COMMAND_ID, PRINT_ROW_RAW_COMMAND_ID -> {
                delay(pacing.rowCommandDelayMs)
                if ((index + 1) % pacing.rowCommandExtraPauseEvery == 0) {
                    delay(pacing.rowCommandExtraPauseMs)
                }
            }
            else -> delay(pacing.controlCommandDelayMs)
        }
    }

    companion object {
        private const val DEFAULT_MTU = 23
        private const val MAX_MTU = 247
        private const val LONG_TRANSFER_PAUSE_EVERY_CHUNKS = 32
        private const val LONG_TRANSFER_EXTRA_PAUSE_MS = 180L
        private const val PRINT_ROW_RLE_COMMAND_ID = -65
        private const val PRINT_ROW_RAW_COMMAND_ID = -94
    }
}
