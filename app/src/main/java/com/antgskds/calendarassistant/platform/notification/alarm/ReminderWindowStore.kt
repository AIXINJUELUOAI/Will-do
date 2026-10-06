package com.antgskds.calendarassistant.platform.notification.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.antgskds.calendarassistant.feature.notification.policy.ReminderWindowPolicy
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import java.util.concurrent.atomic.AtomicInteger

class ReminderWindowStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("reminder_registration_window", Context.MODE_PRIVATE)
    private val restorationDepth = AtomicInteger()
    private val cleanupDepth = AtomicInteger()
    @Volatile var isForeground = false
        private set

    val endExclusive: Long get() = minOf(prefs.getLong("end_exclusive", 0L), ReminderWindowPolicy.endExclusive(System.currentTimeMillis()))
    val canRegister: Boolean get() = cleanupDepth.get() == 0 && (isForeground || restorationDepth.get() > 0)

    fun allows(triggerAt: Long): Boolean = cleanupDepth.get() == 0 && ReminderWindowPolicy.allowsRegistration(
        triggerAt, System.currentTimeMillis(), endExclusive, isForeground, restorationDepth.get() > 0,
    )

    fun setForeground(value: Boolean) { isForeground = value }

    fun enterForeground(): Boolean {
        isForeground = true
        prefs.edit().putLong("end_exclusive", ReminderWindowPolicy.endExclusive(System.currentTimeMillis())).commit()
        if (prefs.getBoolean("stable_receivers_migrated", false)) return false
        // Android 14 added the only public API that also cancels unknown old obfuscated components.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            prefs.edit().putBoolean("stable_receivers_migrated", true).commit()
            return false
        }
        return try {
            (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancelAll()
            prefs.edit().putBoolean("stable_receivers_migrated", true).commit()
            Log.i("ReminderWindow", "Removed legacy app alarms; rebuilding confirmed window")
            true
        } catch (error: RuntimeException) {
            Log.w("ReminderWindow", "Legacy alarm cleanup unavailable; will retry on next foreground", error)
            false
        }
    }

    suspend fun <T> cleaning(block: suspend () -> T): T {
        cleanupDepth.incrementAndGet()
        return try { block() } finally { cleanupDepth.decrementAndGet() }
    }

    suspend fun <T> restoring(block: suspend () -> T): T {
        restorationDepth.incrementAndGet()
        return try { block() } finally { restorationDepth.decrementAndGet() }
    }
}
