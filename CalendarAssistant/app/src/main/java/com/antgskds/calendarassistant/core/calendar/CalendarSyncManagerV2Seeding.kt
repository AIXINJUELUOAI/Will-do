package com.antgskds.calendarassistant.core.calendar

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.antgskds.calendarassistant.core.util.EventDeduplicator
import com.antgskds.calendarassistant.data.db.dao.CalendarSyncMapDao
import com.antgskds.calendarassistant.data.db.dao.EventInstanceDao
import com.antgskds.calendarassistant.data.db.dao.EventMasterDao
import com.antgskds.calendarassistant.data.db.entity.CalendarSyncMapEntity
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.SyncData
import com.antgskds.calendarassistant.data.source.SyncJsonDataSource

internal data class CalendarSyncCalendarMeta(
    val accountName: String,
    val accountType: String,
    val displayName: String
)

internal class CalendarSyncManagerV2Seeding(
    private val context: Context,
    private val calendarManager: CalendarManager,
    private val syncDataSource: SyncJsonDataSource,
    private val syncMapDao: CalendarSyncMapDao,
    private val masterDao: EventMasterDao,
    private val instanceDao: EventInstanceDao,
    private val syncLookBackDays: Long,
    private val syncLookAheadDays: Long,
    private val logTag: String
) {
    suspend fun ensureSyncMapSeeded(
        syncData: SyncData,
        calendarId: Long,
        eventsById: Map<String, MyEvent>
    ): SyncData {
        val existing = syncMapDao.getByCalendarId(calendarId).associateBy { it.localMasterId }
        val updatedMapping = syncData.mapping.toMutableMap()
        var mappingChanged = false

        existing.values.forEach { mapping ->
            val systemId = mapping.systemEventId.toString()
            if (updatedMapping[mapping.localMasterId] != systemId) {
                updatedMapping[mapping.localMasterId] = systemId
                mappingChanged = true
            }
        }

        return if (mappingChanged) {
            syncData.copy(mapping = updatedMapping)
        } else {
            syncData
        }
    }

    suspend fun seedExistingCalendarMappings(
        syncData: SyncData,
        calendarId: Long,
        calendarMeta: CalendarSyncCalendarMeta,
        eventsById: Map<String, MyEvent>
    ): SyncData {
        val existingMappings = syncMapDao.getByCalendarId(calendarId)
        val mappedLocalIds = existingMappings.map { it.localMasterId }.toMutableSet()
        val mappedSystemIds = existingMappings.map { it.systemEventId }.toMutableSet()
        val updatedMapping = syncData.mapping.toMutableMap()
        val seededMappings = mutableListOf<CalendarSyncMapEntity>()

        val activeSingleEventsById = eventsById.values
            .filter { it.tag != EventTags.NOTE && it.tag != EventTags.COURSE && !it.isRecurring && !it.isRecurringParent }
            .associateBy { it.id }
        val activeSingleEventsByFingerprint = activeSingleEventsById.values.associateBy {
            EventDeduplicator.generateFingerprint(it)
        }

        val windowStart = System.currentTimeMillis() - syncLookBackDays * 24 * 60 * 60 * 1000
        val windowEnd = System.currentTimeMillis() + syncLookAheadDays * 24 * 60 * 60 * 1000
        val systemSingles = calendarManager.queryEventsInRange(calendarId, windowStart, windowEnd)

        systemSingles.forEach { systemEvent ->
            if (mappedSystemIds.contains(systemEvent.eventId)) return@forEach

            val localEvent = CalendarSyncManagerV2Hashing.resolveSeedSingleEvent(
                systemEvent = systemEvent,
                activeEventsById = activeSingleEventsById,
                activeEventsByFingerprint = activeSingleEventsByFingerprint
            ) ?: return@forEach

            if (!mappedLocalIds.add(localEvent.id)) return@forEach

            seededMappings.add(
                CalendarSyncMapEntity(
                    localMasterId = localEvent.id,
                    systemEventId = systemEvent.eventId,
                    calendarId = calendarId,
                    accountName = calendarMeta.accountName,
                    accountType = calendarMeta.accountType,
                    displayName = calendarMeta.displayName,
                    lastSyncHash = CalendarSyncManagerV2Hashing.computeSystemHash(systemEvent)
                )
            )
            mappedSystemIds.add(systemEvent.eventId)
            updatedMapping[localEvent.id] = systemEvent.eventId.toString()
        }

        val recurringMasters = masterDao.getRecurringMasters()
        if (recurringMasters.isNotEmpty()) {
            val recurringInstancesByMasterId = recurringMasters.mapNotNull { master ->
                instanceDao.getFirstInstanceByMasterId(master.masterId)?.let { instance ->
                    master.masterId to instance
                }
            }.toMap()
            val recurringMastersById = recurringMasters.associateBy { it.masterId }
            val recurringMastersByBaseHash = recurringMasters.mapNotNull { master ->
                val instance = recurringInstancesByMasterId[master.masterId] ?: return@mapNotNull null
                CalendarSyncManagerV2Hashing.computeRecurringBaseHash(master, instance) to master
            }.toMap()

            val systemRecurringSeries = calendarManager.queryRecurringSeries(calendarId)
            systemRecurringSeries.forEach { systemSeries ->
                if (mappedSystemIds.contains(systemSeries.eventId)) return@forEach

                val localMaster = CalendarSyncManagerV2Hashing.resolveSeedRecurringMaster(
                    systemSeries = systemSeries,
                    mastersById = recurringMastersById,
                    mastersByBaseHash = recurringMastersByBaseHash
                ) ?: return@forEach

                if (!mappedLocalIds.add(localMaster.masterId)) return@forEach

                seededMappings.add(
                    CalendarSyncMapEntity(
                        localMasterId = localMaster.masterId,
                        systemEventId = systemSeries.eventId,
                        calendarId = calendarId,
                        accountName = calendarMeta.accountName,
                        accountType = calendarMeta.accountType,
                        displayName = calendarMeta.displayName,
                        lastSyncHash = CalendarSyncManagerV2Hashing.computeSystemHash(systemSeries)
                    )
                )
                mappedSystemIds.add(systemSeries.eventId)
                updatedMapping[localMaster.masterId] = systemSeries.eventId.toString()
            }
        }

        if (seededMappings.isNotEmpty()) {
            syncMapDao.insertAll(seededMappings)
        }

        return if (seededMappings.isNotEmpty() || updatedMapping != syncData.mapping) {
            syncData.copy(mapping = updatedMapping)
        } else {
            syncData
        }
    }

    suspend fun resolveTargetCalendar(syncData: SyncData): Pair<Long, SyncData>? {
        if (syncData.targetCalendarId != -1L) {
            return syncData.targetCalendarId to syncData
        }

        val calendarId = calendarManager.getOrCreateAppCalendar()
        if (calendarId == -1L) return null

        val updated = syncData.copy(targetCalendarId = calendarId)
        syncDataSource.saveSyncData(updated)
        return calendarId to updated
    }

    suspend fun loadCalendarMeta(calendarId: Long): CalendarSyncCalendarMeta {
        var accountName = ""
        var accountType = ""
        var displayName = "Calendar $calendarId"

        val projection = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        val selection = "${CalendarContract.Calendars._ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val accountNameIndex = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                    val accountTypeIndex = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                    val displayNameIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)

                    if (accountNameIndex >= 0) {
                        accountName = cursor.getString(accountNameIndex) ?: ""
                    }
                    if (accountTypeIndex >= 0) {
                        accountType = cursor.getString(accountTypeIndex) ?: ""
                    }
                    if (displayNameIndex >= 0) {
                        displayName = cursor.getString(displayNameIndex) ?: displayName
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(logTag, "Failed to load calendar meta", e)
        }

        return CalendarSyncCalendarMeta(
            accountName = accountName,
            accountType = accountType,
            displayName = displayName
        )
    }
}
