package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.application.AccountingScreenshotQueue
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AccountingScreenshotQueueTest {
    @Test fun threeImagesRunInOrderAndEachIsDisposedExactlyOnce() = runBlocking {
        withTimeout(5_000) {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()
            val starts = mutableListOf<String>()
            val finishes = mutableListOf<String>()
            val queue = AccountingScreenshotQueue<String>(this, { id, image ->
                assertEquals("image-$id", image)
                starts += id
                if (id == "1") { firstStarted.complete(Unit); releaseFirst.await() }
            }, { id, _, failure ->
                assertNull(failure)
                finishes += id
                if (id == "3") completed.complete(Unit)
            })
            queue.submit("1", "image-1")
            firstStarted.await()
            queue.submit("2", "image-2")
            queue.submit("3", "image-3")
            yield()
            assertEquals(3, queue.size)
            assertEquals(listOf("1"), starts)
            releaseFirst.complete(Unit)
            completed.await()
            assertEquals(listOf("1", "2", "3"), starts)
            assertEquals(starts, finishes)
        }
    }

    @Test fun cancelWaitingAndRunningItemsWithoutBlockingFollowingItem() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()
            val starts = mutableListOf<String>()
            val finishes = mutableMapOf<String, Throwable?>()
            val queue = AccountingScreenshotQueue<Unit>(this, { id, _ ->
                starts += id
                if (id == "1") { started.complete(Unit); awaitCancellation() }
            }, { id, _, error ->
                assertFalse(finishes.containsKey(id))
                finishes[id] = error
                if (id == "3") completed.complete(Unit)
            })
            queue.submit("1", Unit); started.await()
            queue.submit("2", Unit); queue.submit("3", Unit)
            queue.cancel("2")
            assertTrue(finishes["2"] is CancellationException)
            queue.cancel("1")
            completed.await()
            assertEquals(listOf("1", "3"), starts)
            assertEquals(3, finishes.size)
            assertTrue(finishes["1"] is CancellationException)
            assertNull(finishes["3"])
        }
    }

    @Test fun modelAndFeedbackFailuresDoNotPoisonQueue() = runBlocking {
        withTimeout(5_000) {
            val completed = CompletableDeferred<Unit>()
            val finishes = mutableListOf<String>()
            val queue = AccountingScreenshotQueue<Unit>(this, { id, _ ->
                if (id == "1") error("model failed")
            }, { id, _, failure ->
                finishes += id
                if (id == "1") { assertNotNull(failure); error("notification failed") }
                completed.complete(Unit)
            })
            queue.submit("1", Unit); queue.submit("2", Unit)
            completed.await()
            assertEquals(listOf("1", "2"), finishes)
        }
    }

    @Test fun cancelBeforeWorkerStartsAndServiceShutdownDisposeAllImages() = runBlocking {
        withTimeout(5_000) {
            val parent = SupervisorJob(coroutineContext[Job])
            val scope = CoroutineScope(coroutineContext + parent)
            val disposed = mutableListOf<String>()
            val queue = AccountingScreenshotQueue<Unit>(scope, { _, _ -> awaitCancellation() },
                { id, _, _ -> disposed += id })
            queue.submit("1", Unit); queue.submit("2", Unit)
            queue.cancelAll()
            parent.cancelAndJoin()
            assertEquals(listOf("1", "2"), disposed)
        }
    }
}
