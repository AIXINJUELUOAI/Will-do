package com.antgskds.calendarassistant.core.calendar

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
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
            AND ${Events.DTSTART} >= ?
            AND ${Events.DTEND} <= ?
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
     * 根据 ID 列表批量查询事件
     */
    suspend fun queryEventsByIds(
        eventIds: Collection<Long>
    ): List<SystemEventInfo> = withContext(Dispatchers.IO) {
        if (eventIds.isEmpty()) return@withContext emptyList()
        val result = mutableListOf<SystemEventInfo>()

        eventIds.chunked(500).forEach { batchIds ->
            val idListString = batchIds.joinToString(",")
            val selection = "${Events._ID} IN ($idListString) AND ${Events.DELETED} = 0"
            result.addAll(executeEventQuery(selection, null, null))
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
            Events.TITLE,
            Events.EVENT_LOCATION,
            Events.DESCRIPTION,
            Events.DTSTART,
            Events.DTEND,
            Events.EVENT_COLOR,
            Events.ALL_DAY
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
                val titleIndex = cursor.getColumnIndexOrThrow(Events.TITLE)
                val locationIndex = cursor.getColumnIndex(Events.EVENT_LOCATION)
                val descIndex = cursor.getColumnIndex(Events.DESCRIPTION)
                val startIndex = cursor.getColumnIndexOrThrow(Events.DTSTART)
                val endIndex = cursor.getColumnIndexOrThrow(Events.DTEND)
                val colorIndex = cursor.getColumnIndex(Events.EVENT_COLOR)
                val allDayIndex = cursor.getColumnIndex(Events.ALL_DAY)

                while (cursor.moveToNext()) {
                    val description = if (descIndex >= 0) cursor.getString(descIndex) else ""
                    val isManaged = description.contains(MANAGED_EVENT_MARKER)

                    events.add(
                        SystemEventInfo(
                            eventId = cursor.getLong(idIndex),
                            title = cursor.getString(titleIndex) ?: "",
                            location = if (locationIndex >= 0) cursor.getString(locationIndex) else "",
                            description = description.removeSuffix(MANAGED_EVENT_MARKER).trim(),
                            startMillis = cursor.getLong(startIndex),
                            endMillis = cursor.getLong(endIndex),
                            color = if (colorIndex >= 0) cursor.getInt(colorIndex) else null,
                            allDay = cursor.getInt(allDayIndex) == 1,
                            isManaged = isManaged
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询事件失败", e)
        }
        return events
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
        val isManaged: Boolean
    )
}
