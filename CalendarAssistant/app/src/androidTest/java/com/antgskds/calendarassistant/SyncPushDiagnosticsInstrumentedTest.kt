package com.antgskds.calendarassistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.calendar.helpers.CalendarConfig
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.store.SyncLoopGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class SyncPushDiagnosticsInstrumentedTest {

    @Test
    fun createRecurringEvent_normalPush() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as App
        val calendarCenter = app.calendarCenter

        val config = CalendarConfig.newInstance(context)
        calendarCenter.setSyncEnabled(true)
        if (config.caldavSyncedCalendarIds.isBlank()) {
            calendarCenter.setSyncedCalendarIds("1")
        }
        val pullBeforeCreate = SyncLoopGuard.isPullSyncInProgress()
        val hasReadPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

        val title = "SYNC_PROBE_NORMAL_${System.currentTimeMillis()}"
        val id = calendarCenter.createEvent(buildRecurringEvent(title), syncToSystem = true)

        delay(1000)

        val stored = calendarCenter.getEvent(id)
        assertNotNull(stored)
        val normalDetail =
            "normal create failed: id=$id title=$title importId=${stored?.importId} source=${stored?.source} " +
                "syncEnabled=${config.caldavSync} ids=${config.caldavSyncedCalendarIds} " +
                "lastUsed=${config.lastUsedCaldavCalendarId} pullInProgress=$pullBeforeCreate " +
                "permRead=$hasReadPermission permWrite=$hasWritePermission"

        val shouldPush = hasReadPermission && hasWritePermission && !pullBeforeCreate
        if (shouldPush) {
            assertTrue(normalDetail, !stored!!.importId.isNullOrBlank())
        } else {
            assertFalse(normalDetail, !stored!!.importId.isNullOrBlank())
        }

        calendarCenter.deleteEvent(id, deleteFromSystem = true)
    }

    @Test
    fun createRecurringEvent_duringPullSync_skipsPush() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as App
        val calendarCenter = app.calendarCenter

        val config = CalendarConfig.newInstance(context)
        calendarCenter.setSyncEnabled(true)
        if (config.caldavSyncedCalendarIds.isBlank()) {
            calendarCenter.setSyncedCalendarIds("1")
        }
        val pullBeforeCreate = SyncLoopGuard.isPullSyncInProgress()
        val hasReadPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

        val title = "SYNC_PROBE_PULL_${System.currentTimeMillis()}"
        val token = SyncLoopGuard.beginPullSync()
        val id = try {
            calendarCenter.createEvent(buildRecurringEvent(title), syncToSystem = true)
        } finally {
            SyncLoopGuard.endPullSync()
            @Suppress("UNUSED_VARIABLE")
            val ignored = token
        }

        delay(1000)

        val stored = calendarCenter.getEvent(id)
        assertNotNull(stored)
        val pullDetail =
                "pull create expected unsynced: id=$id title=$title importId=${stored?.importId} source=${stored?.source} " +
                "syncEnabled=${config.caldavSync} ids=${config.caldavSyncedCalendarIds} " +
                "lastUsed=${config.lastUsedCaldavCalendarId} pullBeforeCreate=$pullBeforeCreate " +
                "permRead=$hasReadPermission permWrite=$hasWritePermission"

        assertFalse(pullDetail, !stored!!.importId.isNullOrBlank())

        calendarCenter.deleteEvent(id, deleteFromSystem = true)
    }

    private fun buildRecurringEvent(title: String): Event {
        val nowSec = System.currentTimeMillis() / 1000L
        val start = nowSec + 3600L
        val end = start + 3600L
        return Event(
            id = null,
            startTS = start,
            endTS = end,
            title = title,
            location = "",
            description = "sync probe",
            timeZone = ZoneId.systemDefault().id,
            tag = EventTags.GENERAL,
            rrule = "FREQ=DAILY;INTERVAL=1",
            parentId = 0
        )
    }
}
