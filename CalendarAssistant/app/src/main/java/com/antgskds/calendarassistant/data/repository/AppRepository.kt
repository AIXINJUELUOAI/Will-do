package com.antgskds.calendarassistant.data.repository

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.source.CourseJsonDataSource
import com.antgskds.calendarassistant.data.source.EventJsonDataSource
import com.antgskds.calendarassistant.data.source.SettingsDataSource
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager

class AppRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 数据源
    private val eventSource = EventJsonDataSource(context)
    private val courseSource = CourseJsonDataSource(context)
    private val settingsSource = SettingsDataSource(context)

    // StateFlows
    private val _events = MutableStateFlow<List<MyEvent>>(emptyList())
    val events: StateFlow<List<MyEvent>> = _events.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _settings = MutableStateFlow(MySettings())
    val settings: StateFlow<MySettings> = _settings.asStateFlow()

    // 【新增】胶囊状态管理器 - ✅ 直接初始化，避免 lazy 死锁
    val capsuleStateManager: CapsuleStateManager = CapsuleStateManager(this, scope, context.applicationContext)

    // 【新增】日历同步管理器
    private val syncManager = CalendarSyncManager(context.applicationContext)

    private val eventMutex = Mutex()
    private val courseMutex = Mutex()

    init {
        refreshData()
    }

    fun loadAndScheduleAll() {
        refreshData()
    }

    private fun refreshData() {
        scope.launch {
            val loadedEvents = eventSource.loadEvents()
            val loadedCourses = courseSource.loadCourses()
            val loadedSettings = settingsSource.loadSettings()

            _events.value = loadedEvents
            _courses.value = loadedCourses
            _settings.value = loadedSettings

            loadedEvents.forEach { event ->
                NotificationScheduler.scheduleReminders(context, event)
            }
        }
    }

    // --- Events 操作 ---

    /**
     * 添加事件
     *
     * @param event 要添加的事件
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun addEvent(event: MyEvent, triggerSync: Boolean = true) = eventMutex.withLock {
        val currentList = _events.value.toMutableList()
        currentList.add(event)
        updateEvents(currentList)
        NotificationScheduler.scheduleReminders(context, event)
        if (triggerSync) {
            triggerAutoSync()
        }
    }

    /**
     * 更新事件
     *
     * @param event 要更新的事件
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun updateEvent(event: MyEvent, triggerSync: Boolean = true) = eventMutex.withLock {
        val currentList = _events.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == event.id }
        if (index != -1) {
            val oldEvent = currentList[index]
            NotificationScheduler.cancelReminders(context, oldEvent)
            currentList[index] = event
            updateEvents(currentList)
            NotificationScheduler.scheduleReminders(context, event)
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    /**
     * 删除事件
     *
     * @param eventId 要删除的事件 ID
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun deleteEvent(eventId: String, triggerSync: Boolean = true) = eventMutex.withLock {
        val currentList = _events.value.toMutableList()
        val eventToDelete = currentList.find { it.id == eventId }

        if (eventToDelete != null) {
            NotificationScheduler.cancelReminders(context, eventToDelete)
            currentList.remove(eventToDelete)
            updateEvents(currentList)
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    private suspend fun updateEvents(newList: List<MyEvent>) {
        _events.value = newList
        eventSource.saveEvents(newList)
    }

    // --- Courses 操作 ---

    /**
     * 保存课程列表
     *
     * @param newCourses 新的课程列表
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun saveCourses(newCourses: List<Course>, triggerSync: Boolean = true) = courseMutex.withLock {
        updateCourses(newCourses)
        if (triggerSync) {
            triggerAutoSync()
        }
    }

    /**
     * 添加课程
     *
     * @param course 要添加的课程
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun addCourse(course: Course, triggerSync: Boolean = true) = courseMutex.withLock {
        val currentList = _courses.value.toMutableList()
        currentList.add(course)
        updateCourses(currentList)
        if (triggerSync) {
            triggerAutoSync()
        }
    }

    // 🔥 核心修复：级联删除逻辑
    /**
     * 删除课程
     *
     * @param course 要删除的课程
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun deleteCourse(course: Course, triggerSync: Boolean = true) = courseMutex.withLock {
        val currentList = _courses.value.toMutableList()

        // 1. 删除目标课程
        val removed = currentList.remove(course)

        // 2. 连坐：如果删除成功且不是影子课程，把它的"孩子"全删了
        if (removed && !course.isTemp) {
            val childrenToRemove = currentList.filter { it.parentCourseId == course.id }
            currentList.removeAll(childrenToRemove)
            Log.d("AppRepository", "Cascade deleted ${childrenToRemove.size} shadow courses.")
        }

        updateCourses(currentList)
        if (triggerSync) {
            triggerAutoSync()
        }
    }

    /**
     * 更新课程
     *
     * @param course 要更新的课程
     * @param triggerSync 是否触发同步到系统日历（默认 true）
     * 🔥 修复：增加 triggerSync 参数，避免反向同步时触发死循环
     */
    suspend fun updateCourse(course: Course, triggerSync: Boolean = true) = courseMutex.withLock {
        val currentList = _courses.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == course.id }
        if (index != -1) {
            currentList[index] = course
            updateCourses(currentList)
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    private suspend fun updateCourses(newList: List<Course>) {
        _courses.value = newList
        courseSource.saveCourses(newList)
    }

    // --- Settings 操作 ---
    fun updateSettings(newSettings: MySettings) {
        scope.launch {
            _settings.value = newSettings
            settingsSource.saveSettings(newSettings)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // --- 导出/导入功能 ---

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * 导出课程数据（包含课程表和作息时间配置）
     */
    suspend fun exportCoursesData(): String {
        val coursesData = CoursesBackupData(
            courses = _courses.value,
            semesterStartDate = _settings.value.semesterStartDate,
            totalWeeks = _settings.value.totalWeeks,
            timeTableJson = _settings.value.timeTableJson
        )
        return json.encodeToString(coursesData)
    }

    /**
     * 导入课程数据
     */
    suspend fun importCoursesData(jsonString: String): Result<Unit> {
        return try {
            val data = json.decodeFromString<CoursesBackupData>(jsonString)

            // 导入课程
            saveCourses(data.courses)

            // 导入设置
            val currentSettings = _settings.value
            val newSettings = currentSettings.copy(
                semesterStartDate = data.semesterStartDate,
                totalWeeks = data.totalWeeks,
                timeTableJson = data.timeTableJson
            )
            updateSettings(newSettings)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppRepository", "导入课程数据失败", e)
            Result.failure(e)
        }
    }

    /**
     * 导出日程数据
     */
    suspend fun exportEventsData(): String {
        val eventsData = EventsBackupData(
            events = _events.value
        )
        return json.encodeToString(eventsData)
    }

    /**
     * 导入日程数据
     */
    suspend fun importEventsData(jsonString: String): Result<Unit> {
        return try {
            val data = json.decodeFromString<EventsBackupData>(jsonString)

            // 清空旧日程并导入新日程
            val newEvents = data.events.map { event ->
                // 生成新ID以避免冲突
                event.copy(id = java.util.UUID.randomUUID().toString())
            }

            updateEvents(newEvents)

            // 重新设置提醒
            newEvents.forEach { event ->
                NotificationScheduler.scheduleReminders(context, event)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppRepository", "导入日程数据失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取当前事件列表（用于导出前检查）
     */
    fun getEventsCount(): Int = _events.value.size

    /**
     * 获取当前课程列表（用于导出前检查）
     */
    fun getCoursesCount(): Int = _courses.value.size

    // ==================== 日历同步相关 ====================

    /**
     * 触发自动同步（在数据变更时调用）
     * 如果同步已启用，自动将数据同步到系统日历
     */
    private suspend fun triggerAutoSync() {
        try {
            val settings = _settings.value
            val timeNodes = parseTimeTable(settings.timeTableJson)

            syncManager.syncAllToCalendar(
                events = _events.value,
                courses = _courses.value,
                semesterStart = settings.semesterStartDate,
                totalWeeks = settings.totalWeeks,
                timeNodes = timeNodes
            )
        } catch (e: Exception) {
            Log.e("AppRepository", "自动同步失败", e)
        }
    }

    /**
     * 解析作息时间 JSON 为 TimeNode 列表
     */
    private fun parseTimeTable(json: String): List<com.antgskds.calendarassistant.data.model.TimeNode> {
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<com.antgskds.calendarassistant.data.model.TimeNode>>(json)
        } catch (e: Exception) {
            Log.e("AppRepository", "解析作息时间失败", e)
            emptyList()
        }
    }

    /**
     * 手动触发同步（由 UI 调用）
     */
    suspend fun manualSync(): Result<Unit> {
        return try {
            val settings = _settings.value
            val timeNodes = parseTimeTable(settings.timeTableJson)

            syncManager.syncAllToCalendar(
                events = _events.value,
                courses = _courses.value,
                semesterStart = settings.semesterStartDate,
                totalWeeks = settings.totalWeeks,
                timeNodes = timeNodes
            )
        } catch (e: Exception) {
            Log.e("AppRepository", "手动同步失败", e)
            Result.failure(e)
        }
    }

    /**
     * 启用日历同步
     */
    suspend fun enableCalendarSync(): Result<Unit> {
        return syncManager.enableSync()
    }

    /**
     * 禁用日历同步
     */
    suspend fun disableCalendarSync(): Result<Unit> {
        return syncManager.disableSync()
    }

    /**
     * 获取同步状态
     */
    suspend fun getSyncStatus() = syncManager.getSyncStatus()

    /**
     * 从系统日历同步变更到应用
     * 由 CalendarContentObserver 在检测到系统日历变化时触发
     */
    suspend fun syncFromCalendar(): Result<Int> {
        return syncManager.syncFromCalendar(
            onEventAdded = { newEvent ->
                // 新增事件，传入 triggerSync = false 避免死循环
                addEvent(newEvent, triggerSync = false)
            },
            onEventUpdated = { updatedEvent ->
                // 更新事件，传入 triggerSync = false 避免死循环
                updateEvent(updatedEvent, triggerSync = false)
            },
            onEventDeleted = { eventId ->
                // 🔥 新增：删除事件，传入 triggerSync = false
                deleteEvent(eventId, triggerSync = false)
            }
        )
    }
}

@kotlinx.serialization.Serializable
private data class CoursesBackupData(
    val courses: List<Course>,
    val semesterStartDate: String,
    val totalWeeks: Int,
    val timeTableJson: String
)

@kotlinx.serialization.Serializable
private data class EventsBackupData(
    val events: List<MyEvent>
)
