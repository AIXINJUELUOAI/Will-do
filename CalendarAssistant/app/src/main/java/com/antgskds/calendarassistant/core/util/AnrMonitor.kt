package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AnrMonitor {
    private const val TAG = "AnrMonitor"
    private const val HEARTBEAT_INTERVAL_MS = 500L
    private const val ANR_THRESHOLD_MS = 5000L
    private const val CHECK_INTERVAL_MS = 1000L

    private val started = AtomicBoolean(false)
    private val lastBeat = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var inStall = false

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        lastBeat.set(SystemClock.uptimeMillis())
        mainHandler.post(object : Runnable {
            override fun run() {
                lastBeat.set(SystemClock.uptimeMillis())
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        })

        scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                val now = SystemClock.uptimeMillis()
                val lag = now - lastBeat.get()
                if (lag >= ANR_THRESHOLD_MS) {
                    if (!inStall) {
                        inStall = true
                        val stack = Looper.getMainLooper().thread.stackTrace
                            .take(20)
                            .joinToString("\n")
                        val message = "Main thread unresponsive for ${lag}ms\n$stack"
                        ExceptionLogStore.append(context, TAG, message)
                    }
                } else if (inStall) {
                    inStall = false
                }
            }
        }
    }
}
