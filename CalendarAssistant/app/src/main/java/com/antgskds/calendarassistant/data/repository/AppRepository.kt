package com.antgskds.calendarassistant.data.repository

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.source.ArchiveJsonDataSource
import com.antgskds.calendarassistant.data.source.CourseJsonDataSource
import com.antgskds.calendarassistant.data.source.EventJsonDataSource
import com.antgskds.calendarassistant.data.source.SettingsDataSource
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.importer.WakeUpCourseImporter
import com.antgskds.calendarassistant.ui.theme.getRandomEventColor
import com.antgskds.calendarassistant.core.importer.ImportMode
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class AppRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 数据源
    private val eventSource = EventJsonDataSource(context)
    private val courseSource = CourseJsonDataSource(context)
    private val settingsSource = SettingsDataSource(context)
    private val archiveSource = ArchiveJsonDataSource(context)

    // StateFlows
    private val _events = MutableStateFlow<List<MyEvent>>(emptyList())
    val events: StateFlow<List<MyEvent>> = _events.asStateFlow()

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _settings = MutableStateFlow(MySettings())
    val settings: StateFlow<MySettings> = _settings.asStateFlow()

    private val _archivedEvents = MutableStateFlow<List<MyEvent>>(emptyList())
    val archivedEvents: StateFlow<List<MyEvent>> = _archivedEvents.asStateFlow()

    // 【新增】胶囊状态管理器 - ✅ 直接初始化，避免 lazy 死锁
    val capsuleStateManager: CapsuleStateManager = CapsuleStateManager(this, scope, context.applicationContext)

    // 【新增】日历同步管理器
    private val syncManager = CalendarSyncManager(context.applicationContext)

    private val eventMutex = Mutex()
    private val courseMutex = Mutex()
    private val archiveMutex = Mutex()

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
            // 🔥 修复：移除归档加载，改为懒加载（冷启动性能优化）
            // val loadedArchives = archiveSource.loadArchivedEvents()

            _events.value = loadedEvents
            _courses.value = loadedCourses
            _settings.value = loadedSettings
            // _archivedEvents 保持初始为空，直到用户查看时加载

            loadedEvents.forEach { event ->
                NotificationScheduler.scheduleReminders(context, event)
            }

            // 启动后尝试自动归档（建议延迟执行，不阻塞启动）
            launch {
                autoArchiveExpiredEvents()
            }
        }
    }

    /**
     * 🔥 修复：懒加载归档数据
     * 仅在进入归档页面时调用
     */
    fun fetchArchivedEvents() {
        scope.launch {
            archiveMutex.withLock {
                val loaded = archiveSource.loadArchivedEvents()
                _archivedEvents.value = loaded
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
            // ✅ 触发胶囊状态刷新，确保胶囊通知被注销
            capsuleStateManager.forceRefresh()
            if (triggerSync) {
                triggerAutoSync()
            }
        }
    }

    private suspend fun updateEvents(newList: List<MyEvent>) {
        _events.value = newList
        eventSource.saveEvents(newList)
    }

    /**
     * 完成取件码（直接删除事件）
     * 取件码用完即弃，点击"已取"后直接删除，不保留历史记录
     */
    suspend fun completePickupEvent(id: String) {
        val event = _events.value.find { it.id == id }
        if (event != null && event.eventType == "temp") {
            // 取消相关通知（同时取消胶囊通知和初始通知）
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(id.hashCode())  // 取消胶囊通知
            nm.cancel(id.hashCode() + NotificationScheduler.OFFSET_PICKUP_INITIAL_NOTIF)  // 取消初始通知

            // 直接删除事件
            deleteEvent(id, triggerSync = false)

            // 主动触发胶囊状态刷新
            capsuleStateManager.forceRefresh()
        }
    }

    /**
     * 根据 ID 获取事件
     */
    suspend fun getEventById(id: String): MyEvent? {
        return _events.value.find { it.id == id }
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
     * 标准化日期格式
     * 将 yyyy-M-d 格式（如 2025-9-1）转换为 ISO-8601 格式（yyyy-MM-dd，如 2025-09-01）
     * 用于处理外部导入文件中缺失前导零的日期字符串
     *
     * @param dateStr 原始日期字符串
     * @return 标准化后的日期字符串（ISO-8601 格式），如果解析失败则返回 null
     */
    private fun normalizeDateFormat(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null

        return try {
            // 先尝试直接解析（已经是标准格式的情况）
            LocalDate.parse(dateStr)
            dateStr
        } catch (e: DateTimeParseException) {
            // 如果直接解析失败，尝试使用宽松格式解析
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-M-d")
                val parsedDate = LocalDate.parse(dateStr, formatter)
                // 转换为 ISO-8601 格式字符串
                parsedDate.toString()
            } catch (e2: Exception) {
                Log.e("AppRepository", "日期格式标准化失败: $dateStr", e2)
                null
            }
        }
    }

    /**
     * 导入课程数据（支持应用备份格式和 WakeUp 课表格式）
     */
    suspend fun importCoursesData(jsonString: String): Result<Unit> {
        // 优先尝试使用 WakeUpCourseImporter 解析
        val wakeUpImporter = WakeUpCourseImporter()
        if (wakeUpImporter.supports(jsonString)) {
            Log.d("AppRepository", "检测到 WakeUp 课表格式，开始导入")
            return try {
                val result = wakeUpImporter.parse(jsonString)
                if (result.isSuccess) {
                    val importResult = result.getOrThrow()

                    // 导入课程
                    saveCourses(importResult.courses)

                    // 导入设置（如果有）
                    if (importResult.semesterStartDate != null || importResult.totalWeeks != null) {
                        val currentSettings = _settings.value
                        // 标准化日期格式
                        val normalizedDate = importResult.semesterStartDate?.let { normalizeDateFormat(it) }
                        val newSettings = currentSettings.copy(
                            semesterStartDate = normalizedDate ?: currentSettings.semesterStartDate,
                            totalWeeks = importResult.totalWeeks ?: currentSettings.totalWeeks
                        )
                        updateSettings(newSettings)
                    }

                    Log.d("AppRepository", "WakeUp 课表导入成功，共 ${importResult.courses.size} 门课程")
                    Result.success(Unit)
                } else {
                    Log.e("AppRepository", "WakeUp 课表解析失败: ${result.exceptionOrNull()?.message}")
                    Result.failure(result.exceptionOrNull() ?: Exception("解析失败"))
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "WakeUp 课表导入异常", e)
                Result.failure(e)
            }
        }

        // 如果不是 WakeUp 格式，尝试应用自己的备份格式
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
     * 导入外部课表文件（WakeUp 格式）
     * @param content 文件内容
     * @param mode 导入模式（追加/覆盖）
     * @param importSettings 是否导入设置（开学日期、总周数）
     * @return 成功导入的课程数量
     */
    suspend fun importWakeUpFile(
        content: String,
        mode: ImportMode,
        importSettings: Boolean
    ): Result<Int> {
        val importer = WakeUpCourseImporter()
        return try {
            val result = importer.parse(content)
            if (result.isSuccess) {
                val importResult = result.getOrThrow()
                val courses = importResult.courses

                // 根据模式处理课程
                if (mode == ImportMode.OVERWRITE) {
                    // 覆盖模式：清空现有课程
                    saveCourses(courses)
                    Log.d("AppRepository", "覆盖模式：清空后导入 ${courses.size} 门课程")
                } else {
                    // 追加模式：保留现有课程，添加新课程
                    val existingCourses = _courses.value
                    val mergedCourses = existingCourses + courses
                    saveCourses(mergedCourses)
                    Log.d("AppRepository", "追加模式：从 ${existingCourses.size} 门增加到 ${mergedCourses.size} 门课程")
                }

                // 导入设置（如果需要）
                if (importSettings) {
                    if (importResult.semesterStartDate != null || importResult.totalWeeks != null) {
                        val currentSettings = _settings.value
                        // 标准化日期格式
                        val normalizedDate = importResult.semesterStartDate?.let { normalizeDateFormat(it) }
                        val newSettings = currentSettings.copy(
                            semesterStartDate = normalizedDate ?: currentSettings.semesterStartDate,
                            totalWeeks = importResult.totalWeeks ?: currentSettings.totalWeeks
                        )
                        updateSettings(newSettings)
                        Log.d("AppRepository", "设置已更新，日期: $normalizedDate")
                    }
                }

                Result.success(courses.size)
            } else {
                Log.e("AppRepository", "解析失败: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("解析失败"))
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "导入异常", e)
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
     *
     * 颜色策略：
     * - 新增事件：随机分配一个 APP 内的颜色，避免统一的青灰色
     * - 更新事件：保留本地原有的颜色、提醒、重要性设置（作为 UI 防火墙）
     * - 删除事件：正常删除
     */
    suspend fun syncFromCalendar(): Result<Int> {
        return syncManager.syncFromCalendar(
            onEventAdded = { newEvent ->
                // 【场景：新增事件】
                // 策略：不信任系统传来的颜色（可能是被同步源污染的颜色）
                // 随机分配一个 APP 自己的颜色，让界面色彩更丰富
                val eventWithRandomColor = newEvent.copy(
                    color = getRandomEventColor()
                )
                addEvent(eventWithRandomColor, triggerSync = false)
            },
            onEventUpdated = { incomingEvent ->
                // 【场景：更新事件】
                // 策略：先在本地查找这个事件
                val oldEvent = _events.value.find { it.id == incomingEvent.id }

                val eventToSave = if (oldEvent != null) {
                    // 如果是老朋友：
                    // 1. 接受系统传来的 内容变更 (标题、时间、地点、描述)
                    // 2. 拒绝系统传来的 样式变更 (强制保留 App 原有的颜色、提醒、重要性)
                    // 这作为"UI 防火墙"，防止外部同步源的颜色污染我们的 UI
                    incomingEvent.copy(
                        color = oldEvent.color,
                        reminders = oldEvent.reminders,
                        isImportant = oldEvent.isImportant
                    )
                } else {
                    // 理论上只有映射存在的才会走到 onEventUpdated
                    // 但防守性编程：如果没找到旧对象，就当做新的处理，给个随机色
                    incomingEvent.copy(color = getRandomEventColor())
                }

                updateEvent(eventToSave, triggerSync = false)
            },
            onEventDeleted = { eventId ->
                // 【场景：删除事件】
                // 直接删除
                deleteEvent(eventId, triggerSync = false)
            }
        )
    }

    // ==================== 归档操作 ====================

    /**
     * 🔥 修复：归档单个事件（原子操作修正版）
     * 1. 先写入归档（持有 archiveMutex）
     * 2. 后删除原日程（复用 deleteEvent，它持有 eventMutex）
     * 3. 类型检查：课程和临时事件不可归档
     */
    suspend fun archiveEvent(eventId: String) {
        // 1. 类型安全检查与获取对象
        val event = _events.value.find { it.id == eventId } ?: return

        // 🛡️ 拦截规则：课程和临时事件不可归档
        if (event.eventType == "course" || event.eventType == "temp") {
            Log.w("AppRepository", "Attempted to archive special event type: ${event.eventType}")
            return
        }

        // 2. 先写入归档 (持有 archiveMutex)
        val archivedEvent = event.copy(archivedAt = System.currentTimeMillis())

        try {
            archiveMutex.withLock {
                // 如果内存中还没有加载归档数据，先加载（防止覆盖）
                val currentArchived = if (_archivedEvents.value.isEmpty()) {
                    archiveSource.loadArchivedEvents().toMutableList()
                } else {
                    _archivedEvents.value.toMutableList()
                }

                currentArchived.add(archivedEvent)
                // 这一步可能会抛出 IO 异常
                updateArchivedEvents(currentArchived)
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "归档失败: 写入归档文件错误", e)
            return // 归档写入失败，终止操作，原日程保留
        }

        // 3. 后删除原日程 (复用 deleteEvent，它持有 eventMutex，线程安全)
        // triggerSync = false 因为归档本质上是"移动"，不应视为系统日历的"删除"
        // 如果希望系统日历里的也删掉，可以设为 true
        deleteEvent(eventId, triggerSync = false)

        Log.d("AppRepository", "Event archived: ${event.title}")
    }

    /**
     * 🔥 修复：还原归档事件（逻辑完善版）
     * 1. 获取归档对象（持有 archiveMutex）
     * 2. 恢复到活跃列表（复用 addEvent，持有 eventMutex）
     * 3. 从归档中移除（持有 archiveMutex）
     */
    suspend fun restoreEvent(archivedEventId: String) {
        // 1. 获取归档对象 (持有 archiveMutex)
        val archivedEvent: MyEvent?

        archiveMutex.withLock {
            archivedEvent = _archivedEvents.value.find { it.id == archivedEventId }
        }

        if (archivedEvent == null) return

        // 2. 恢复到活跃列表 (复用 addEvent，持有 eventMutex)
        val activeEvent = archivedEvent.copy(archivedAt = null)

        // 检查是否已过期，如果过期太久，可能不需要 triggerSync
        addEvent(activeEvent, triggerSync = true)

        // 3. 从归档中移除 (持有 archiveMutex)
        archiveMutex.withLock {
            val currentArchived = _archivedEvents.value.toMutableList()
            currentArchived.remove(archivedEvent)
            updateArchivedEvents(currentArchived)
        }

        Log.d("AppRepository", "Event restored: ${activeEvent.title}")
    }

    /**
     * 永久删除归档事件
     */
    suspend fun deleteArchivedEvent(archivedEventId: String) = archiveMutex.withLock {
        val currentArchived = _archivedEvents.value.toMutableList()
        val event = currentArchived.find { it.id == archivedEventId }
        if (event != null) {
            currentArchived.remove(event)
            updateArchivedEvents(currentArchived)
        }
    }

    /**
     * 清空所有归档
     */
    suspend fun clearAllArchives() = archiveMutex.withLock {
        updateArchivedEvents(emptyList())
    }

    private suspend fun updateArchivedEvents(newList: List<MyEvent>) {
        _archivedEvents.value = newList
        archiveSource.saveArchivedEvents(newList)
    }

    /**
     * 🔥 修复：自动归档过期事件（使用正确的过期判断逻辑）
     * 条件：使用 DateCalculator.isEventExpired() 判断是否过期（考虑日期+时间）
     * 排除：课程和临时事件不归档
     * @return 归档的事件数量
     */
    suspend fun autoArchiveExpiredEvents(): Int {
        val settings = _settings.value
        if (!settings.autoArchiveEnabled) return 0

        // 1. 筛选需要归档的事件（使用正确的过期判断）
        val eventsSnapshot = _events.value // 获取快照
        val toArchiveEvents = eventsSnapshot.filter { event ->
            event.eventType != "course" &&
            event.eventType != "temp" && // 临时事件也不归档
            com.antgskds.calendarassistant.core.util.DateCalculator.isEventExpired(event)
        }

        if (toArchiveEvents.isEmpty()) return 0

        Log.d("AppRepository", "Auto-archiving ${toArchiveEvents.size} events...")

        // 2. 批量处理 - 归档部分
        archiveMutex.withLock {
            val currentArchived = if (_archivedEvents.value.isEmpty()) {
                archiveSource.loadArchivedEvents().toMutableList()
            } else {
                _archivedEvents.value.toMutableList()
            }

            // ✅ 修复：只添加不在归档列表中的事件，避免重复
            val existingIds = currentArchived.map { it.id }.toSet()
            val newItems = toArchiveEvents.filter { it.id !in existingIds }

            val newArchivedItems = newItems.map {
                it.copy(archivedAt = System.currentTimeMillis())
            }
            currentArchived.addAll(newArchivedItems)
            updateArchivedEvents(currentArchived)
        }

        // 3. 批量处理 - 删除部分 (使用 eventMutex)
        eventMutex.withLock {
            val currentEvents = _events.value.toMutableList()
            // 取消通知
            toArchiveEvents.forEach { NotificationScheduler.cancelReminders(context, it) }
            // 移除
            currentEvents.removeAll { event -> toArchiveEvents.any { it.id == event.id } }
            updateEvents(currentEvents)
            // 触发一次同步即可
            triggerAutoSync()
        }

        return toArchiveEvents.size
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
