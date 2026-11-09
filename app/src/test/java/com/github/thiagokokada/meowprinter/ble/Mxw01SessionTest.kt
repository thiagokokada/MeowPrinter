package com.github.thiagokokada.meowprinter.ble

import com.github.thiagokokada.meowprinter.print.Mxw01Protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class Mxw01SessionTest {
    @Test
    fun batteryAndWholePrintTransactionsCannotOverwriteEachOther() = runTest {
        val session = Mxw01Session { error("Unexpected session failure") }
        val writes = mutableListOf<Int>()
        val battery = async {
            session.transaction { session.request(0xAB) { writes.add(0xAB) } }
        }
        val print = async {
            session.transaction {
                session.request(0xA1) { writes.add(0xA1) }
                session.withResponse(0xAA) { completed ->
                    session.request(0xA9) { writes.add(0xA9) }
                    writes.add(0xAD)
                    completed.await()
                }
            }
        }
        runCurrent()
        assertEquals(listOf(0xAB), writes)
        session.onNotification(reply(0xAB, 68))
        runCurrent()
        assertEquals(listOf(0xAB, 0xA1), writes)
        session.onNotification(reply(0xA1, 0))
        runCurrent()
        assertEquals(listOf(0xAB, 0xA1, 0xA9), writes)
        session.onNotification(reply(0xA9, 0))
        runCurrent()
        val nextBattery = async {
            session.transaction { session.request(0xAB) { writes.add(0xAB) } }
        }
        runCurrent()
        assertEquals(listOf(0xAB, 0xA1, 0xA9, 0xAD), writes)
        session.onNotification(reply(0xAA))
        runCurrent()
        assertEquals(listOf(0xAB, 0xA1, 0xA9, 0xAD, 0xAB), writes)
        session.onNotification(reply(0xAB, 78))
        assertArrayEquals(byteArrayOf(68), battery.await())
        print.await()
        assertArrayEquals(byteArrayOf(78), nextBattery.await())
        assertTrue(session.isActive)
    }

    @Test
    fun registersWaiterBeforeSendingAndPreservesImmediateCompletion() = runTest {
        val session = Mxw01Session { error("Unexpected session failure") }
        session.transaction {
            session.withResponse(0xAA) { completed ->
                val ack = session.request(0xA9) {
                    session.onNotification(reply(0xA9, 0))
                    session.onNotification(reply(0xAA))
                }
                assertArrayEquals(byteArrayOf(0), ack)
                assertTrue(completed.isCompleted)
                completed.await()
            }
        }
    }

    @Test
    fun ignoresMalformedEmptyAndUnrelatedRepliesAndDoesNotCacheDuplicates() = runTest {
        val session = Mxw01Session { error("Unexpected session failure") }
        session.onNotification(reply(0xAB, 99)) // unsolicited, must not be cached
        val request = async { session.transaction { session.request(0xAB) {} } }
        runCurrent()
        session.onNotification(reply(0xA9, 0))
        session.onNotification(reply(0xAB))
        session.onNotification(byteArrayOf(0x22, 0x21, 0xAB.toByte(), 0, 3, 0, 1, 2))
        runCurrent()
        assertFalse(request.isCompleted)
        session.onNotification(reply(0xAB, 68))
        session.onNotification(reply(0xAB, 78))
        assertArrayEquals(byteArrayOf(68), request.await())

        val next = async { session.transaction { session.request(0xAB) {} } }
        runCurrent()
        assertFalse(next.isCompleted)
        session.onNotification(reply(0xAB, 79))
        assertArrayEquals(byteArrayOf(79), next.await())
    }

    @Test
    fun timeoutIncludesSuspendedWriteAndInvalidatesBeforeAnotherTransaction() = runTest {
        var failures = 0
        val session = Mxw01Session { failures += 1 }
        val result = async {
            runCatching { session.transaction { session.request(0xAB) { awaitCancellation() } } }
        }
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(result.await().exceptionOrNull() is IOException)
        assertFalse(session.isActive)
        assertEquals(1, failures)
        session.onNotification(reply(0xAB, 68))
        var sent = false
        val retry = runCatching { session.transaction { sent = true } }
        assertTrue(retry.exceptionOrNull() is IOException)
        assertFalse(sent)
    }

    @Test
    fun missingResponseTimesOutAndFreshSessionDoesNotConsumeOldNotifications() = runTest {
        val old = Mxw01Session {}
        val timedOut = runCatching { old.transaction { old.request(0xAB) {} } }
        assertTrue(timedOut.exceptionOrNull() is IOException)
        val fresh = Mxw01Session { error("Unexpected session failure") }
        val request = async { fresh.transaction { fresh.request(0xAB) {} } }
        runCurrent()
        old.onNotification(reply(0xAB, 99))
        runCurrent()
        assertFalse(request.isCompleted)
        fresh.onNotification(reply(0xAB, 68))
        assertArrayEquals(byteArrayOf(68), request.await())
    }

    @Test
    fun cancellingActiveTransactionInvalidatesSessionAndReleasesLock() = runTest {
        var failures = 0
        val session = Mxw01Session { failures += 1 }
        val active = launch { session.transaction { session.request(0xAB) {} } }
        val waiting = async { runCatching { session.transaction { error("Must not execute") } } }
        runCurrent()
        active.cancelAndJoin()
        assertTrue(active.isCancelled)
        assertFalse(session.isActive)
        assertEquals(1, failures)
        assertTrue(waiting.await().exceptionOrNull() is IOException)
    }

    @Test
    fun cancellingQueuedBatteryDoesNotCancelActivePrint() = runTest {
        val session = Mxw01Session { error("Unexpected session failure") }
        val finish = CompletableDeferred<Unit>()
        val print = async { session.transaction { finish.await() } }
        val battery = launch { session.transaction { error("Must not execute") } }
        runCurrent()
        battery.cancelAndJoin()
        assertTrue(session.isActive)
        finish.complete(Unit)
        print.await()
    }

    @Test
    fun invalidationFailsPendingResponsesAndRejectsQueuedWork() = runTest {
        val session = Mxw01Session {}
        val pending = async { runCatching { session.transaction { session.request(0xA1) {} } } }
        val queued = async { runCatching { session.transaction { error("Must not execute") } } }
        runCurrent()
        session.invalidate()
        assertTrue(pending.await().exceptionOrNull() is IOException)
        assertTrue(queued.await().exceptionOrNull() is IOException)
        assertFalse(session.isActive)
    }

    @Test
    fun invalidatingNestedWaitersIsSafeWhenCleanupRunsImmediately() = runTest(UnconfinedTestDispatcher()) {
        val session = Mxw01Session {}
        val pending = async {
            runCatching {
                session.transaction {
                    session.withResponse(0xAA) { session.request(0xA9) {} }
                }
            }
        }
        session.invalidate()
        assertTrue(pending.await().exceptionOrNull() is IOException)
        assertFalse(session.isActive)
    }

    @Test
    fun gattWriteErrorPreservesCauseAndInvalidatesSession() = runTest {
        val failure = IOException("BLE request failed with status 133.")
        val session = Mxw01Session {}
        val result = runCatching {
            session.transaction { session.request(0xAB) { throw failure } }
        }
        // Coroutine stack-trace recovery may copy IOException across withTimeout.
        val thrown = result.exceptionOrNull()
        assertTrue(thrown is IOException)
        assertEquals(failure.message, thrown?.message)
        assertFalse(session.isActive)
    }

    @Test
    fun missingPrintCompletionInvalidatesSession() = runTest {
        val session = Mxw01Session {}
        val result = runCatching {
            session.transaction {
                session.withResponse(0xAA) { completed ->
                    session.request(0xA9) { session.onNotification(reply(0xA9, 0)) }
                    withTimeout(60_000) { completed.await() }
                }
            }
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(session.isActive)
    }

    private fun reply(id: Int, vararg payload: Int): ByteArray =
        Mxw01Protocol.command(id, ByteArray(payload.size) { payload[it].toByte() })
}
