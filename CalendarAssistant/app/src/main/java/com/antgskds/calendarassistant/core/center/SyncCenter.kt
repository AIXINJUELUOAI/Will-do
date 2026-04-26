package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.core.center.CalendarCenter
import com.antgskds.calendarassistant.calendar.helpers.CalendarConfig
import com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager
import com.antgskds.calendarassistant.calendar.models.stubs.CalendarSyncManager
import com.antgskds.calendarassistant.calendar.sync.SystemCalendarSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 日历同步中心 — 包装 willdo CalendarCenter 的同步能力。
 */
class SyncCenter(
    private val calendarCenter: CalendarCenter,
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val config = CalendarConfig.newInstance(appContext)
    private val syncManager = SystemCalendarSyncManager(appContext)

    suspend fun enableCalendarSync(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { calendarCenter.setSyncEnabled(true) }
    }

    suspend fun disableCalendarSync(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { calendarCenter.setSyncEnabled(false) }
    }

    suspend fun manualSync(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { calendarCenter.manualSyncNow() }
    }

    suspend fun setSyncedCalendarIds(ids: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { calendarCenter.setSyncedCalendarIds(ids) }
    }

    suspend fun enableCalendarSyncAndSyncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            calendarCenter.setSyncEnabled(true)
            calendarCenter.manualSyncNow()
        }
    }

    suspend fun updateSyncIntervalSeconds(seconds: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            config.syncIntervalSeconds = seconds.coerceIn(1, 300)
            if (config.caldavSync) {
                calendarCenter.setSyncEnabled(true)
            }
        }
    }

    fun getSyncStatus(): CalendarSyncManager.SyncStatus {
        val hasPerm = hasCalendarPermission()
        val isEnabled = config.caldavSync
        val syncedIds = config.getSyncedCalendarIdsAsList().map { it.toLong() }
        return CalendarSyncManager.SyncStatus(
            isEnabled = isEnabled,
            hasPermission = hasPerm,
            targetCalendarId = config.lastUsedCaldavCalendarId.toLong(),
            sourceCalendarIds = syncedIds,
            syncIntervalSeconds = config.syncIntervalSeconds,
            lastSyncTime = 0L,
            mappedEventCount = 0
        )
    }

    fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo> {
        if (!hasCalendarPermission()) return emptyList()
        return try {
            val calendars = syncManager.getCalDAVCalendars("")
            calendars.map { cal ->
                CalendarManager.CalendarInfo(
                    id = cal.id.toLong(),
                    name = cal.displayName,
                    accountName = cal.accountName,
                    accountType = cal.accountType,
                    isVisible = true,
                    syncEvents = true,
                    isWritable = cal.accessLevel >= 500
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            calendarCenter.setSyncedCalendarIds(calendarIds.joinToString(","))
        }
    }

    private fun hasCalendarPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }
}
