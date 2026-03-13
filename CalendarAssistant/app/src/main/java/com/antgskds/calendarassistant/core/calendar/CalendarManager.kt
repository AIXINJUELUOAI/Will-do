package com.antgskds.calendarassistant.core.calendar

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.TimeNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * 日历管理器
 * 负责系统日历的底层 CRUD 操作
 * 使用 applyBatch 进行批量操作，确保性能
 */
class CalendarManager(private val context: Context) {

    companion object {
        private const val TAG = "CalendarManager"

        /**
         * 应用专用的日历名称
         */
        private const val CALENDAR_NAME = "Will-do 日程助手"

        /**
         * 用于标识应用创建事件的标记（添加到 description 末尾）
         */
        private const val MANAGED_EVENT_MARKER = "\n\n🔒 [由 CalendarAssistant 托管，请勿在此修改]"

        /**
         * 用于标识应用创建事件的扩展属性
         */
        private const val EXTENDED_PROPERTY_APP_ID = "com.antgskds.calendarassistant.event_id"
        private const val EXTENDED_PROPERTY_EVENT_TYPE = "com.antgskds.calendarassistant.event_type"
        private const val EXTENDED_PROPERTY_TAG = "com.antgskds.calendarassistant.event_tag"
    }

    private val contentResolver: ContentResolver = context.contentResolver

    // ==================== 日历管理 ====================

    /**
     * 获取用户可写的日历列表
     */
    suspend fun getWritableCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_DISPLAY_NAME
        )

        // 只获取可写入的日历（ OWNER 级别或 CONTRIBUTOR 级别）
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        try {
            contentResolver.query(
                Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Calendars._ID} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Calendars._ID)
                val nameIndex = cursor.getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME)
                val accountIndex = cursor.getColumnIndex(Calendars.ACCOUNT_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else "日历 $id"
                    val accountName = if (accountIndex >= 0) cursor.getString(accountIndex) else null

                    calendars.add(
                        CalendarInfo(
                            id = id,
                            name = displayName,
                            accountName = accountName
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "缺少日历读取权限", e)
            throw SecurityException("需要日历读取权限")
        } catch (e: Exception) {
            Log.e(TAG, "获取日历列表失败", e)
        }

        calendars
    }

    /**
     * 获取或创建应用专用日历
     * 如果找到同名日历则返回其 ID，否则返回默认日历 ID
     */
    suspend fun getOrCreateAppCalendar(): Long = withContext(Dispatchers.IO) {
        val calendars = getWritableCalendars()

        // 查找同名日历
        val existingCalendar = calendars.find { it.name == CALENDAR_NAME }
        if (existingCalendar != null) {
            Log.d(TAG, "找到现有日历: ${existingCalendar.name} (ID: ${existingCalendar.id})")
            return@withContext existingCalendar.id
        }

        // 返回第一个可写日历作为默认
        val defaultCalendar = calendars.firstOrNull()
        if (defaultCalendar != null) {
            Log.d(TAG, "使用默认日历: ${defaultCalendar.name} (ID: ${defaultCalendar.id})")
            return@withContext defaultCalendar.id
        }

        Log.e(TAG, "未找到可写日历")
        -1L
    }

    // ==================== 事件操作（单条） ====================

    /**
     * 创建单个事件
     * @return 新创建的事件 ID，失败返回 -1
     */
    suspend fun createEvent(
        event: MyEvent,
        calendarId: Long
    ): Long = withContext(Dispatchers.IO) {
        try {
            val values = buildEventContentValues(event, calendarId)
            val uri = contentResolver.insert(Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull() ?: -1L

            if (eventId != -1L) {
                if (!upsertEventMetadata(eventId, event)) {
                    Log.w(TAG, "事件元数据保存失败: $eventId - ${event.title}")
                }
                Log.d(TAG, "创建事件成功: $eventId - ${event.title}")
            } else {
                Log.e(TAG, "创建事件失败: ${event.title}")
            }

            eventId
        } catch (e: Exception) {
            Log.e(TAG, "创建事件异常: ${event.title}", e)
            -1L
        }
    }

    /**
     * 更新单个事件
     */
    suspend fun updateEvent(
        eventId: Long,
        event: MyEvent,
        calendarId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = buildEventContentValues(event, calendarId)
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
            val rowsUpdated = contentResolver.update(uri, values, null, null)

            val success = rowsUpdated > 0
            if (success) {
                if (!upsertEventMetadata(eventId, event)) {
                    Log.w(TAG, "事件元数据更新失败: $eventId - ${event.title}")
                }
                Log.d(TAG, "更新事件成功: $eventId - ${event.title}")
            } else {
                Log.w(TAG, "更新事件失败（未找到记录）: $eventId")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "更新事件异常: $eventId", e)
            false
        }
    }

    /**
     * 删除单个事件
     */
    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
            val rowsDeleted = contentResolver.delete(uri, null, null)

            val success = rowsDeleted > 0
            if (success) {
                Log.d(TAG, "删除事件成功: $eventId")
            } else {
                Log.w(TAG, "删除事件失败（未找到记录）: $eventId")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "删除事件异常: $eventId", e)
            false
        }
    }

    // ==================== 批量操作（课程同步） ====================

    /**
     * 批量创建课程事件
     * 使用 applyBatch 确保性能
     *
     * @param courseEvents 课程事件列表，包含每节课的日期
     * @param calendarId 目标日历 ID
     * @param timeNodes 作息时间表
     * @return 成功创建的事件 ID 映射 (virtualId -> calendarEventId)
     */
    suspend fun batchCreateCourseEvents(
        courseEvents: List<CourseEventInstance>,
        calendarId: Long,
        timeNodes: List<TimeNode>
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        if (courseEvents.isEmpty()) {
            Log.d(TAG, "没有课程事件需要创建")
            return@withContext emptyMap()
        }

        val operations = ArrayList<ContentProviderOperation>()
        val resultMapping = mutableMapOf<String, Long>()

        try {
            courseEvents.forEach { instance ->
                val virtualId = "course_${instance.course.id}_${instance.date}"
                val values = buildCourseEventContentValues(
                    instance.course,
                    instance.date,
                    calendarId,
                    timeNodes
                )

                // 构建 insert 操作
                val builder = ContentProviderOperation.newInsert(Events.CONTENT_URI)
                    .withValues(values)

                operations.add(builder.build())
                resultMapping[virtualId] = -1L // 占位，稍后更新
            }

            Log.d(TAG, "准备批量创建 ${operations.size} 个课程事件")

            // 执行批量操作
            val results = contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)

            // 更新结果映射
            results.forEachIndexed { index, result ->
                val uri = result.uri
                if (uri != null) {
                    val eventId = uri.lastPathSegment?.toLongOrNull() ?: -1L
                    val virtualId = courseEvents[index].let {
                        "course_${it.course.id}_${it.date}"
                    }
                    resultMapping[virtualId] = eventId
                }
            }

            Log.d(TAG, "批量创建完成，成功 ${results.size} 个")

        } catch (e: Exception) {
            Log.e(TAG, "批量创建课程事件失败", e)
            throw e
        }

        resultMapping
    }

    /**
     * 批量删除指定日历中由本应用创建的所有课程事件
     * 通过 description 中的托管标记识别
     *
     * @param calendarId 目标日历 ID
     * @return 删除的事件数量
     */
    suspend fun batchDeleteManagedCourseEvents(calendarId: Long): Int = withContext(Dispatchers.IO) {
        try {
            // 查询所有带有托管标记的事件
            val eventsToDelete = queryManagedEvents(calendarId)

            if (eventsToDelete.isEmpty()) {
                Log.d(TAG, "没有需要删除的托管课程事件")
                return@withContext 0
            }

            val operations = ArrayList<ContentProviderOperation>()

            eventsToDelete.forEach { eventId ->
                val builder = ContentProviderOperation.newDelete(
                    ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
                )
                operations.add(builder.build())
            }

            Log.d(TAG, "准备批量删除 ${operations.size} 个托管课程事件")

            // 执行批量删除
            val results = contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)

            Log.d(TAG, "批量删除完成，删除了 ${results.size} 个事件")
            results.size

        } catch (e: Exception) {
            Log.e(TAG, "批量删除课程事件失败", e)
            0
        }
    }

    /**
     * 查询指定日历中所有由本应用托管的事件
     * 通过 description 中的托管标记识别
     */
    suspend fun queryManagedEvents(calendarId: Long): List<Long> = withContext(Dispatchers.IO) {
        val eventIds = mutableListOf<Long>()

        val projection = arrayOf(Events._ID)
        val selection = "${Events.CALENDAR_ID} = ? AND ${Events.DESCRIPTION} LIKE ?"
        val selectionArgs = arrayOf(
            calendarId.toString(),
            "%$MANAGED_EVENT_MARKER%"
        )

        try {
            contentResolver.query(
                Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Events._ID)
                while (cursor.moveToNext()) {
                    eventIds.add(cursor.getLong(idIndex))
                }
            }

            Log.d(TAG, "查询到 ${eventIds.size} 个托管事件")

        } catch (e: Exception) {
            Log.e(TAG, "查询托管事件失败", e)
        }

        eventIds
    }

    // ==================== 查询操作 ====================

    /**
     * 查询指定时间范围内的事件
     */
    suspend fun queryEventsInRange(
        calendarId: Long,
        startMillis: Long,
        endMillis: Long
    ): List<SystemEventInfo> = withContext(Dispatchers.IO) {
        val selection = """
            ${Events.CALENDAR_ID} = ?
            AND ${Events.DTEND} > ?
            AND ${Events.DTSTART} < ?
            AND (${Events.RRULE} IS NULL OR ${Events.RRULE} = '')
            AND ${Events.DELETED} = 0
        """.trimIndent().replace("\n", " ")

        val selectionArgs = arrayOf(
            calendarId.toString(),
            startMillis.toString(),
            endMillis.toString()
        )

        // 复用查询逻辑
        executeEventQuery(selection, selectionArgs, "${Events.DTSTART} ASC")
    }

    /**
     * 查询指定时间窗口内的重复事件实例
     * 使用 CalendarContract.Instances 展开重复规则，返回真实 occurrence
     */
    suspend fun queryRecurringInstancesInRange(
        calendarId: Long,
        startMillis: Long,
        endMillis: Long,
        eventId: Long? = null
    ): List<SystemEventInfo> {
        return queryRecurringInstancesInRangeInternal(
            calendarId = calendarId,
            startMillis = startMillis,
            endMillis = endMillis,
            eventId = eventId,
            limit = null
        ).events
    }

    suspend fun queryRecurringInstancesInRangeLimited(
        calendarId: Long,
        startMillis: Long,
        endMillis: Long,
        eventId: Long? = null,
        limit: Int
    ): RecurringInstancesQueryResult {
        return queryRecurringInstancesInRangeInternal(
            calendarId = calendarId,
            startMillis = startMillis,
            endMillis = endMillis,
            eventId = eventId,
            limit = limit
        )
    }

    private suspend fun queryRecurringInstancesInRangeInternal(
        calendarId: Long,
        startMillis: Long,
        endMillis: Long,
        eventId: Long?,
        limit: Int?
    ): RecurringInstancesQueryResult = withContext(Dispatchers.IO) {
        val normalizedLimit = limit?.takeIf { it > 0 }
        val events = mutableListOf<SystemEventInfo>()
        var truncated = false

        val builder = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val uri = builder.build()

        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.BEGIN,
            Instances.END,
            Events.TITLE,
            Events.EVENT_LOCATION,
            Events.DESCRIPTION,
            Events.EVENT_COLOR,
            Events.ALL_DAY,
            Events.RRULE
        )

        val selection = if (eventId != null) {
            "${Events.CALENDAR_ID} = ? AND ${Instances.EVENT_ID} = ?"
        } else {
            "${Events.CALENDAR_ID} = ?"
        }
        val selectionArgs = if (eventId != null) {
            arrayOf(calendarId.toString(), eventId.toString())
        } else {
            arrayOf(calendarId.toString())
        }

        try {
            contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Instances.BEGIN} ASC"
            )?.use { cursor ->
                val eventIdIndex = cursor.getColumnIndexOrThrow(Instances.EVENT_ID)
                val beginIndex = cursor.getColumnIndexOrThrow(Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(Instances.END)
                val titleIndex = cursor.getColumnIndex(Events.TITLE)
                val locationIndex = cursor.getColumnIndex(Events.EVENT_LOCATION)
                val descIndex = cursor.getColumnIndex(Events.DESCRIPTION)
                val colorIndex = cursor.getColumnIndex(Events.EVENT_COLOR)
                val allDayIndex = cursor.getColumnIndex(Events.ALL_DAY)
                val rruleIndex = cursor.getColumnIndex(Events.RRULE)

                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(eventIdIndex)
                    val start = cursor.getLong(beginIndex)
                    val end = cursor.getLong(endIndex)
                    val description = if (descIndex >= 0) cursor.getString(descIndex) ?: "" else ""
                    val rrule = if (rruleIndex >= 0) cursor.getString(rruleIndex) else null

                    if (rrule.isNullOrBlank()) continue

                    if (normalizedLimit != null && events.size >= normalizedLimit) {
                        truncated = true
                        break
                    }

                    val isManaged = description.contains(MANAGED_EVENT_MARKER)
                    val seriesKey = RecurringEventUtils.buildSeriesKey(calendarId, eventId)
                    val instanceKey = RecurringEventUtils.buildInstanceKey(seriesKey, start)

                    events.add(
                        SystemEventInfo(
                            eventId = eventId,
                            title = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "" else "",
                            location = if (locationIndex >= 0) cursor.getString(locationIndex) ?: "" else "",
                            description = description.removeSuffix(MANAGED_EVENT_MARKER).trim(),
                            startMillis = start,
                            endMillis = end,
                            color = if (colorIndex >= 0) cursor.getInt(colorIndex) else null,
                            allDay = allDayIndex >= 0 && cursor.getInt(allDayIndex) == 1,
                            isManaged = isManaged,
                            lastModified = null,
                            recurringRule = rrule,
                            isRecurring = true,
                            seriesKey = seriesKey,
                            instanceKey = instanceKey
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询重复实例失败", e)
        }

        if (events.isEmpty()) return@withContext RecurringInstancesQueryResult(events, truncated)

        val metadataByEventId = queryEventMetadata(events.map { it.eventId })
        val enrichedEvents = events.map { event ->
            val metadata = metadataByEventId[event.eventId]
            if (metadata != null) {
                event.copy(
                    appId = metadata.appId,
                    appEventType = metadata.eventType,
                    tag = metadata.tag
                )
            } else {
                event
            }
        }

        RecurringInstancesQueryResult(enrichedEvents, truncated)
    }

    suspend fun queryRecurringSeries(calendarId: Long): List<SystemEventInfo> = withContext(Dispatchers.IO) {
        val selection = """
            ${Events.CALENDAR_ID} = ?
            AND ${Events.DELETED} = 0
            AND ${Events.RRULE} IS NOT NULL
            AND ${Events.RRULE} != ''
        """.trimIndent().replace("\n", " ")

        executeEventQuery(selection, arrayOf(calendarId.toString()), "${Events.DTSTART} ASC")
            .filter { it.isRecurring }
    }

    suspend fun queryNextRecurringInstance(
        calendarId: Long,
        eventId: Long,
        seriesKey: String,
        fromMillis: Long,
        recurringRule: String?,
        excludedInstanceKeys: Set<String>
    ): SystemEventInfo? {
        val primaryHorizonDays = getRecurringLookAheadDays(recurringRule)
        val fallbackHorizonDays = max(primaryHorizonDays, 3660L)

        val horizonEnds = listOf(primaryHorizonDays, fallbackHorizonDays)
            .distinct()
            .map { days -> fromMillis + days * 24L * 60L * 60L * 1000L }

        horizonEnds.forEach { endMillis ->
            val candidate = queryRecurringInstancesInRange(
                calendarId = calendarId,
                startMillis = fromMillis,
                endMillis = endMillis,
                eventId = eventId
            )
                .asSequence()
                .filter { it.seriesKey == seriesKey }
                .filter { it.startMillis >= fromMillis }
                .filter { it.instanceKey !in excludedInstanceKeys }
                .minByOrNull { it.startMillis }

            if (candidate != null) {
                return candidate
            }
        }

        return null
    }

    /**
     * 根据 ID 列表批量查询事件
     */
    suspend fun queryEventsByIds(
        eventIds: Collection<Long>,
        calendarId: Long
    ): List<SystemEventInfo> = withContext(Dispatchers.IO) {
        if (eventIds.isEmpty()) return@withContext emptyList()
        val result = mutableListOf<SystemEventInfo>()

        eventIds.chunked(500).forEach { batchIds ->
            val idListString = batchIds.joinToString(",")
            val selection = "${Events._ID} IN ($idListString) AND ${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0"
            val selectionArgs = arrayOf(calendarId.toString())
            result.addAll(executeEventQuery(selection, selectionArgs, null))
        }
        result
    }

    /**
     * 内部私有方法：执行通用的事件查询
     * 提取公共代码，避免重复
     */
    private fun executeEventQuery(
        selection: String,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): List<SystemEventInfo> {
        val events = mutableListOf<SystemEventInfo>()

        val projection = arrayOf(
            Events._ID,
            Events.CALENDAR_ID,
            Events.TITLE,
            Events.EVENT_LOCATION,
            Events.DESCRIPTION,
            Events.DTSTART,
            Events.DTEND,
            Events.EVENT_COLOR,
            Events.ALL_DAY,
            Events.RRULE
        )

        try {
            contentResolver.query(
                Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Events._ID)
                val calendarIdIndex = cursor.getColumnIndexOrThrow(Events.CALENDAR_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(Events.TITLE)
                val locationIndex = cursor.getColumnIndex(Events.EVENT_LOCATION)
                val descIndex = cursor.getColumnIndex(Events.DESCRIPTION)
                val startIndex = cursor.getColumnIndexOrThrow(Events.DTSTART)
                val endIndex = cursor.getColumnIndexOrThrow(Events.DTEND)
                val colorIndex = cursor.getColumnIndex(Events.EVENT_COLOR)
                val allDayIndex = cursor.getColumnIndex(Events.ALL_DAY)
                val rruleIndex = cursor.getColumnIndex(Events.RRULE)

                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(idIndex)
                    val calendarId = cursor.getLong(calendarIdIndex)
                    val description = if (descIndex >= 0) cursor.getString(descIndex) ?: "" else ""
                    val isManaged = description.contains(MANAGED_EVENT_MARKER)
                    val rrule = if (rruleIndex >= 0) cursor.getString(rruleIndex) else null

                    events.add(
                        SystemEventInfo(
                            eventId = eventId,
                            title = cursor.getString(titleIndex) ?: "",
                            location = if (locationIndex >= 0) cursor.getString(locationIndex) ?: "" else "",
                            description = description.removeSuffix(MANAGED_EVENT_MARKER).trim(),
                            startMillis = cursor.getLong(startIndex),
                            endMillis = cursor.getLong(endIndex),
                            color = if (colorIndex >= 0) cursor.getInt(colorIndex) else null,
                            allDay = allDayIndex >= 0 && cursor.getInt(allDayIndex) == 1,
                            isManaged = isManaged,
                            lastModified = null,
                            recurringRule = rrule,
                            isRecurring = !rrule.isNullOrBlank(),
                            seriesKey = if (!rrule.isNullOrBlank()) {
                                RecurringEventUtils.buildSeriesKey(
                                    calendarId = calendarId,
                                    eventId = eventId
                                )
                            } else {
                                null
                            },
                            instanceKey = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询事件失败", e)
        }
        if (events.isEmpty()) return events

        val metadataByEventId = queryEventMetadata(events.map { it.eventId })
        return events.map { event ->
            val metadata = metadataByEventId[event.eventId]
            if (metadata != null) {
                event.copy(
                    appId = metadata.appId,
                    appEventType = metadata.eventType,
                    tag = metadata.tag
                )
            } else {
                event
            }
        }
    }

    private fun upsertEventMetadata(eventId: Long, event: MyEvent): Boolean {
        return try {
            deleteEventMetadata(eventId)

            val metadataEntries = listOf(
                EXTENDED_PROPERTY_APP_ID to event.id,
                EXTENDED_PROPERTY_EVENT_TYPE to event.eventType,
                EXTENDED_PROPERTY_TAG to event.tag
            )

            metadataEntries.forEach { (name, value) ->
                val values = android.content.ContentValues().apply {
                    put(ExtendedProperties.EVENT_ID, eventId)
                    put(ExtendedProperties.NAME, name)
                    put(ExtendedProperties.VALUE, value)
                }
                contentResolver.insert(ExtendedProperties.CONTENT_URI, values)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "写入事件元数据失败: eventId=$eventId", e)
            false
        }
    }

    private fun deleteEventMetadata(eventId: Long) {
        val metadataNames = arrayOf(
            EXTENDED_PROPERTY_APP_ID,
            EXTENDED_PROPERTY_EVENT_TYPE,
            EXTENDED_PROPERTY_TAG
        )
        val namePlaceholders = metadataNames.joinToString(",") { "?" }
        val selection = "${ExtendedProperties.EVENT_ID} = ? AND ${ExtendedProperties.NAME} IN ($namePlaceholders)"
        val selectionArgs = arrayOf(eventId.toString(), *metadataNames)
        contentResolver.delete(ExtendedProperties.CONTENT_URI, selection, selectionArgs)
    }

    private fun queryEventMetadata(eventIds: Collection<Long>): Map<Long, EventMetadata> {
        if (eventIds.isEmpty()) return emptyMap()

        val metadataNames = arrayOf(
            EXTENDED_PROPERTY_APP_ID,
            EXTENDED_PROPERTY_EVENT_TYPE,
            EXTENDED_PROPERTY_TAG
        )
        val rawMetadata = mutableMapOf<Long, MutableMap<String, String>>()

        try {
            eventIds.distinct().chunked(300).forEach { batchIds ->
                val idPlaceholders = batchIds.joinToString(",") { "?" }
                val namePlaceholders = metadataNames.joinToString(",") { "?" }
                val selection = "${ExtendedProperties.EVENT_ID} IN ($idPlaceholders) AND ${ExtendedProperties.NAME} IN ($namePlaceholders)"
                val selectionArgs = batchIds.map { it.toString() } + metadataNames

                contentResolver.query(
                    ExtendedProperties.CONTENT_URI,
                    arrayOf(
                        ExtendedProperties.EVENT_ID,
                        ExtendedProperties.NAME,
                        ExtendedProperties.VALUE
                    ),
                    selection,
                    selectionArgs.toTypedArray(),
                    null
                )?.use { cursor ->
                    val eventIdIndex = cursor.getColumnIndexOrThrow(ExtendedProperties.EVENT_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(ExtendedProperties.NAME)
                    val valueIndex = cursor.getColumnIndexOrThrow(ExtendedProperties.VALUE)

                    while (cursor.moveToNext()) {
                        val eventId = cursor.getLong(eventIdIndex)
                        val name = cursor.getString(nameIndex) ?: continue
                        val value = cursor.getString(valueIndex) ?: continue
                        rawMetadata.getOrPut(eventId) { mutableMapOf() }[name] = value
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询事件元数据失败", e)
            return emptyMap()
        }

        return rawMetadata.mapValues { (_, values) ->
            EventMetadata(
                appId = values[EXTENDED_PROPERTY_APP_ID],
                eventType = values[EXTENDED_PROPERTY_EVENT_TYPE],
                tag = values[EXTENDED_PROPERTY_TAG]
            )
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建 MyEvent 的 ContentValues
     */
    private fun buildEventContentValues(
        event: MyEvent,
        calendarId: Long
    ): android.content.ContentValues {
        val values = android.content.ContentValues()

        // 基础字段
        values.put(Events.CALENDAR_ID, calendarId)
        values.put(Events.TITLE, event.title)
        values.put(Events.EVENT_LOCATION, event.location)
        values.put(Events.DESCRIPTION, event.description)

        // 时间转换
        val startMillis = getDateTimeMillis(event.startDate, event.startTime)
        val endMillis = getDateTimeMillis(event.endDate, event.endTime)
        values.put(Events.DTSTART, startMillis)
        values.put(Events.DTEND, endMillis)

        // 时区
        values.put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)

        // 不全天事件
        values.put(Events.ALL_DAY, 0)

        // 颜色
        val colorInt = event.color.hashCode()
        values.put(Events.EVENT_COLOR, colorInt)

        // 提醒（如果有）
        if (event.reminders.isNotEmpty()) {
            values.put(Events.HAS_ALARM, 1)
        }

        return values
    }

    /**
     * 构建 Course 事件实例的 ContentValues
     */
    private fun buildCourseEventContentValues(
        course: Course,
        date: LocalDate,
        calendarId: Long,
        timeNodes: List<TimeNode>
    ): android.content.ContentValues {
        val values = android.content.ContentValues()

        // 获取对应节次的时间
        val startNode = timeNodes.find { it.index == course.startNode }
        val endNode = timeNodes.find { it.index == course.endNode }

        val startTime = startNode?.startTime ?: "08:00"
        val endTime = endNode?.endTime ?: "09:00"

        // 基础字段
        values.put(Events.CALENDAR_ID, calendarId)
        values.put(Events.TITLE, course.name)
        values.put(Events.EVENT_LOCATION, course.location)

        // 描述：包含课程信息 + 托管标记
        val description = buildString {
            if (course.teacher.isNotBlank()) {
                append("教师: ${course.teacher}\n")
            }
            append("节次: 第${course.startNode}-${course.endNode}节")
            append(MANAGED_EVENT_MARKER)
        }
        values.put(Events.DESCRIPTION, description)

        // 时间
        val startDateTime = LocalDateTime.of(date, parseTime(startTime))
        val endDateTime = LocalDateTime.of(date, parseTime(endTime))
        values.put(Events.DTSTART, startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        values.put(Events.DTEND, endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

        values.put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        values.put(Events.ALL_DAY, 0)

        // 颜色
        val colorInt = course.color.hashCode()
        values.put(Events.EVENT_COLOR, colorInt)

        return values
    }

    /**
     * 将 LocalDate 和时间字符串转换为毫秒时间戳
     */
    private fun getDateTimeMillis(date: LocalDate, timeStr: String): Long {
        val time = parseTime(timeStr)
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * 解析时间字符串 (HH:mm)
     */
    private fun parseTime(timeStr: String): LocalTime {
        return try {
            LocalTime.parse(timeStr)
        } catch (e: Exception) {
            LocalTime.of(9, 0) // 默认 9:00
        }
    }

    private fun getRecurringLookAheadDays(recurringRule: String?): Long {
        if (recurringRule.isNullOrBlank()) return 120L

        val ruleMap = recurringRule
            .split(';')
            .mapNotNull { token ->
                val parts = token.split('=', limit = 2)
                if (parts.size == 2) parts[0].uppercase() to parts[1] else null
            }
            .toMap()

        val interval = ruleMap["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
        return when (ruleMap["FREQ"]?.uppercase()) {
            "DAILY" -> max(60L, interval * 3L + 7L)
            "WEEKLY" -> max(120L, interval * 21L)
            "MONTHLY" -> max(730L, interval * 93L)
            "YEARLY" -> max(3660L, interval * 730L)
            else -> 365L
        }
    }

    // ==================== 数据类 ====================

    /**
     * 日历信息
     */
    data class CalendarInfo(
        val id: Long,
        val name: String,
        val accountName: String? = null
    )

    /**
     * 课程事件实例（展开后的单次课程）
     */
    data class CourseEventInstance(
        val course: Course,
        val date: LocalDate
    )

    /**
     * 系统事件信息
     */
    data class SystemEventInfo(
        val eventId: Long,
        val title: String,
        val location: String,
        val description: String,
        val startMillis: Long,
        val endMillis: Long,
        val color: Int?,
        val allDay: Boolean,
        val isManaged: Boolean,
        val lastModified: Long? = null,
        val recurringRule: String? = null,
        val isRecurring: Boolean = false,
        val seriesKey: String? = null,
        val instanceKey: String? = null,
        val appId: String? = null,
        val appEventType: String? = null,
        val tag: String? = null
    )

    data class RecurringInstancesQueryResult(
        val events: List<SystemEventInfo>,
        val isTruncated: Boolean
    )

    private data class EventMetadata(
        val appId: String? = null,
        val eventType: String? = null,
        val tag: String? = null
    )
}
