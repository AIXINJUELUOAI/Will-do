package com.antgskds.calendarassistant.store

import java.util.concurrent.atomic.AtomicInteger

object SyncLoopGuard {
    private val pullDepth = AtomicInteger(0)

    fun beginPullSync(): String {
        pullDepth.incrementAndGet()
        return "pull-" + System.currentTimeMillis()
    }

    fun endPullSync() {
        while (true) {
            val current = pullDepth.get()
            if (current <= 0) return
            if (pullDepth.compareAndSet(current, current - 1)) return
        }
    }

    fun isPullSyncInProgress(): Boolean = pullDepth.get() > 0
}
