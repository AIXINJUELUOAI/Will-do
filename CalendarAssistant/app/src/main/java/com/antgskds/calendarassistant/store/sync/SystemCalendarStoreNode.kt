package com.antgskds.calendarassistant.store.sync

import android.content.Context
import com.antgskds.calendarassistant.calendar.jobs.CalDAVUpdateListener
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.sync.SystemCalendarSyncManager

class SystemCalendarStoreNode(context: Context) {
    private val appContext = context.applicationContext
    private val syncManager = SystemCalendarSyncManager(appContext)

    fun insertToSystem(event: Event): Event = syncManager.insertCalDAVEvent(event)

    fun updateToSystem(event: Event): Event = syncManager.updateCalDAVEvent(event)

    fun deleteFromSystem(event: Event) = syncManager.deleteCalDAVEvent(event)

    fun insertRepeatException(parentEvent: Event, occurrenceTs: Long) {
        syncManager.insertEventRepeatException(parentEvent, occurrenceTs)
    }

    fun refreshCalendars(ids: String, manual: Boolean) {
        syncManager.refreshCalDAVCalendars(ids, manual)
    }

    fun recheckCalendars(scheduleNextCalDAVSync: Boolean) {
        syncManager.recheckCalDAVCalendars(scheduleNextCalDAVSync)
    }

    fun scheduleSync(activate: Boolean) {
        syncManager.scheduleCalDAVSync(activate)
        // 同时注册/取消 CalDAVUpdateListener 内容观察 job
        try {
            if (activate) {
                CalDAVUpdateListener().scheduleJob(appContext)
            }
        } catch (_: Exception) { }
    }
}
