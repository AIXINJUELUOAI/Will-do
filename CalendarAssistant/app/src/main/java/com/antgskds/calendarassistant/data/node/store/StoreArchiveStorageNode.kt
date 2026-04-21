package com.antgskds.calendarassistant.data.node.store

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.data.db.shadow.RoomEventShadowWriter
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.repository.ArchiveRepository
import com.antgskds.calendarassistant.data.repository.SyncMappingRepository

internal class StoreArchiveStorageNode(
    private val context: Context,
    private val archiveRepository: ArchiveRepository,
    private val roomShadowWriter: RoomEventShadowWriter,
    private val syncMappingRepository: SyncMappingRepository
) {
    suspend fun removeCalendarMappingsForEvents(events: List<MyEvent>, reason: String) {
        if (events.isEmpty()) return
        try {
            val syncData = syncMappingRepository.load()
            val calendarManager = CalendarManager(context)
            val updatedMapping = syncData.mapping.toMutableMap()

            events.forEach { event ->
                val calendarEventIdStr = syncData.mapping[event.id]
                if (calendarEventIdStr == null) {
                    Log.d("StoreNode", "$reason: 未找到映射，event.id=${event.id}")
                    return@forEach
                }

                val calendarEventId = calendarEventIdStr.toLongOrNull()
                if (calendarEventId == null) {
                    Log.w("StoreNode", "$reason: 非法日历映射，event.id=${event.id}, value=$calendarEventIdStr")
                    updatedMapping.remove(event.id)
                    return@forEach
                }

                val success = calendarManager.deleteEvent(calendarEventId)
                Log.d("StoreNode", "$reason: deleteEvent result=$success, ${event.id} -> $calendarEventId")
                updatedMapping.remove(event.id)
            }

            if (updatedMapping != syncData.mapping) {
                syncMappingRepository.save(syncData.copy(mapping = updatedMapping))
            }
        } catch (e: Exception) {
            Log.e("StoreNode", "$reason: 同步删除系统日历事件失败", e)
        }
    }

    suspend fun persistArchivedEvents(events: List<MyEvent>, roomMainEnabled: Boolean) {
        if (roomMainEnabled) {
            archiveRepository.saveArchivedEventsBackup(events)
        } else {
            archiveRepository.saveArchivedEvents(events)
        }
        try {
            roomShadowWriter.syncEvents(events, RoomEventShadowWriter.SyncMode.ARCHIVED)
        } catch (e: Exception) {
            Log.e("StoreNode", "Room 归档影子写入失败", e)
        }
    }

    suspend fun loadCurrentArchivedMutableList(currentArchived: List<MyEvent>): MutableList<MyEvent> {
        return if (currentArchived.isEmpty()) {
            archiveRepository.loadArchivedEvents().toMutableList()
        } else {
            currentArchived.toMutableList()
        }
    }
}
