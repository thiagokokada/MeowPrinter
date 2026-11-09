package com.github.thiagokokada.meowprinter.ble

import com.github.thiagokokada.meowprinter.print.Mxw01Protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** One connection's request/response state. Nordic's GATT queue is not a job lock. */
internal class Mxw01Session(private val onFailure: (Mxw01Session) -> Unit) {
    private val transactionMutex = Mutex()
    private val stateLock = Any()
    private val pending = mutableMapOf<Int, CompletableDeferred<ByteArray>>()

    @Volatile
    var isActive: Boolean = true
        private set

    suspend fun <T> transaction(action: suspend () -> T): T = transactionMutex.withLock {
        checkActive()
        try {
            action()
        } catch (e: Exception) {
            // There is no transaction ID on the wire. After a timeout/cancellation,
            // reconnect rather than letting a late reply satisfy the next job.
            invalidate()
            onFailure(this)
            if (e is TimeoutCancellationException) {
                throw IOException("MXW01 response or BLE write timed out. Reconnect the printer.", e)
            }
            throw e
        }
    }

    suspend fun request(id: Int, send: suspend () -> Unit): ByteArray =
        withTimeout(10_000) {
            withResponse(id) { response ->
                send()
                response.await()
            }
        }

    // Caller holds the transaction mutex. Separate IDs allow AA to be armed before
    // A9 is sent, without sharing/overwriting the A9 acknowledgement's deferred.
    suspend fun <T> withResponse(id: Int, action: suspend (Deferred<ByteArray>) -> T): T {
        val response = CompletableDeferred<ByteArray>()
        synchronized(stateLock) {
            checkActive()
            check(id !in pending) { "MXW01 response already pending: $id" }
            pending[id] = response
        }
        try {
            return action(response)
        } finally {
            synchronized(stateLock) {
                if (pending[id] === response) pending.remove(id)
            }
            response.cancel()
        }
    }

    fun onNotification(packet: ByteArray) {
        val notification = Mxw01Protocol.parseNotification(packet) ?: return
        // These replies require at least one byte. AA's payload is undocumented
        // and may be empty; do not invent a success/error byte for it.
        if (notification.id in listOf(0xA1, 0xA9, 0xAB) && notification.payload.isEmpty()) return
        synchronized(stateLock) {
            if (isActive) pending[notification.id]?.complete(notification.payload)
        }
    }

    fun invalidate() {
        val responses = synchronized(stateLock) {
            isActive = false
            pending.values.toList().also { pending.clear() }
        }
        // Completion may resume a coroutine immediately and run its cleanup.
        // Never iterate the live map while those cleanups can remove waiters.
        responses.forEach {
            it.completeExceptionally(IOException("MXW01 session ended. Reconnect the printer."))
        }
    }

    private fun checkActive() {
        if (!isActive) throw IOException("MXW01 session unavailable. Reconnect the printer.")
    }
}
