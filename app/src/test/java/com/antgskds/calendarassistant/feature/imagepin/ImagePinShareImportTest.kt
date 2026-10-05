package com.antgskds.calendarassistant.feature.imagepin

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ImagePinShareImportTest {
    private class SharedStream(text: String) : ByteArrayInputStream(text.toByteArray()) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun importIsDispatchedAwayFromTheReceivingThread() = runBlocking {
        val stream = SharedStream("image")
        val receiverThread = Thread.currentThread()
        var importThread: Thread? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            launchImagePinImport(scope, listOf(stream)) { importThread = Thread.currentThread() }.join()
            assertNotNull(importThread)
            assertNotSame(receiverThread, importThread)
            assertTrue(stream.closed)
        } finally { scope.cancel() }
    }

    @Test fun openedStreamsRemainReadableAfterHandoffAndCloseWhenImportFinishes() = runBlocking {
        val first = SharedStream("first image")
        val second = SharedStream("second image")
        val gate = CompletableDeferred<Unit>()
        val contents = mutableListOf<String>()
        val job = launchImagePinImport(this, listOf(first, second)) {
            gate.await()
            contents += first.readBytes().decodeToString()
            contents += second.readBytes().decodeToString()
        }
        assertFalse(job.isCompleted)
        assertFalse(first.closed)
        assertFalse(second.closed)
        gate.complete(Unit)
        job.join()
        assertEquals(listOf("first image", "second image"), contents)
        assertTrue(first.closed)
        assertTrue(second.closed)
    }

    @Test fun failedImportClosesEvenStreamsNotYetConsumed() = runBlocking {
        val first = SharedStream("first image")
        val unread = SharedStream("unread image")
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined +
            CoroutineExceptionHandler { _, error -> errors += error })
        try {
            launchImagePinImport(scope, listOf(first, unread)) {
                first.read()
                error("copy failed")
            }.join()
            assertEquals("copy failed", errors.single().message)
            assertTrue(first.closed)
            assertTrue(unread.closed)
        } finally { scope.cancel() }
    }

    @Test fun cancellationWhileWaitingClosesTheEntireBatch() = runBlocking {
        val stream = SharedStream("image")
        val job = launchImagePinImport(this, listOf(stream)) { awaitCancellation() }
        job.cancelAndJoin()
        assertTrue(stream.closed)
    }

    @Test fun alreadyCancelledScopeStillReleasesOpenedStreamsWithoutImporting() = runBlocking {
        val stream = SharedStream("image")
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        scope.cancel()
        val job = launchImagePinImport(scope, listOf(stream)) { error("must not import") }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(stream.closed)
    }
}
