package com.antgskds.calendarassistant.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.core.ai.AiPrompts
import com.antgskds.calendarassistant.core.ai.PromptCheckResult
import com.antgskds.calendarassistant.core.ai.PromptUpdater
import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.operation.WeatherOperationApi
import com.antgskds.calendarassistant.core.query.HomeQueryApi
import com.antgskds.calendarassistant.core.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.WeatherQueryApi
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.RemotePrompts
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.core.weather.hasWeatherConfig
import com.antgskds.calendarassistant.ui.components.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class MainUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val revealedEventId: String? = null,
    val allEvents: List<MyEvent> = emptyList(),
    val noteEvents: List<MyEvent> = emptyList(),
    val settings: MySettings = MySettings(),
    val currentDateEvents: List<MyEvent> = emptyList(),
    val tomorrowEvents: List<MyEvent> = emptyList(),
    val weatherData: WeatherData? = null
)

data class PromptUpdateDialogState(
    val localVersion: Int,
    val remoteVersion: Int
)

data class PromptCheckFeedback(
    val message: String,
    val type: ToastType
)

class MainViewModel(
    private val appContext: Context,
    private val scheduleCenter: ScheduleCenter,
    private val settingsQueryApi: SettingsQueryApi,
    private val homeQueryApi: HomeQueryApi,
    private val scheduleInsightsQueryApi: ScheduleInsightsQueryApi,
    private val weatherQueryApi: WeatherQueryApi,
    private val weatherOperationApi: WeatherOperationApi
) : ViewModel() {

    // ✅ 精确过期触发器：仅在事件实际过期时触发 UI 刷新，避免无效轮询
    private val _timeTrigger = MutableStateFlow(System.currentTimeMillis())
    private val _promptUpdateDialogState = MutableStateFlow<PromptUpdateDialogState?>(null)
    val promptUpdateDialogState: StateFlow<PromptUpdateDialogState?> = _promptUpdateDialogState.asStateFlow()
    private val _promptCheckInProgress = MutableStateFlow(false)
    val promptCheckInProgress: StateFlow<Boolean> = _promptCheckInProgress.asStateFlow()
    private val _promptLocalVersion = MutableStateFlow(AiPrompts.getLocalVersion(appContext))
    val promptLocalVersion: StateFlow<Int> = _promptLocalVersion.asStateFlow()
    private val _promptSource = MutableStateFlow(AiPrompts.getPromptSource(appContext))
    val promptSource: StateFlow<AiPrompts.PromptSource> = _promptSource.asStateFlow()
    private val _promptCheckFeedback = MutableSharedFlow<PromptCheckFeedback>(extraBufferCapacity = 1)
    val promptCheckFeedback: SharedFlow<PromptCheckFeedback> = _promptCheckFeedback.asSharedFlow()
    private var pendingPromptUpdate: RemotePrompts? = null

    init {
        // 精确定时器：等待最近的未过期事件过期时才触发刷新
        viewModelScope.launch {
            while (true) {
                val delayMs = calculateDelayToNextExpiration()
                
                if (delayMs > 0) {
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    kotlinx.coroutines.delay(60_000L) // 保底：无未过期事件时 60 秒检查一次
                }
                _timeTrigger.value = System.currentTimeMillis()
            }
        }

        // 自动归档过期事件
        viewModelScope.launch {
            val archivedCount = scheduleCenter.autoArchiveExpiredEvents()
            if (archivedCount > 0) {
                Log.d("Archive", "自动归档了 $archivedCount 条事件")
            }
        }

        viewModelScope.launch {
            settingsQueryApi.settings.collectLatest { settings ->
                weatherOperationApi.refreshIfNeeded(settings)
            }
        }

        checkPromptUpdatesSilently()
    }

    private fun calculateDelayToNextExpiration(): Long {
        return homeQueryApi.calculateDelayToNextExpiration(scheduleCenter.events.value)
    }

    // 归档事件（公开访问）
    val archivedEvents = scheduleCenter.archivedEvents

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _revealedEventId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        _selectedDate,
        _revealedEventId,
        scheduleCenter.events,
        settingsQueryApi.settings,
        weatherQueryApi.weatherData,
        _timeTrigger  // ✅ 添加时间触发器
    ) { values ->
        val date = values[0] as LocalDate
        val revealedId = values[1] as String?
        val events = values[2] as List<MyEvent>
        val settings = values[3] as MySettings
        val weatherData = values[4] as WeatherData?
        val courses = CourseEventMapper.extractCourses(events, settings)

        val snapshot = homeQueryApi.buildSnapshot(
            selectedDate = date,
            events = events,
            courses = courses,
            settings = settings
        )

        MainUiState(
            selectedDate = date,
            revealedEventId = revealedId,
            allEvents = events,
            noteEvents = snapshot.noteEvents,
            settings = settings,
            currentDateEvents = snapshot.currentDateEvents,
            tomorrowEvents = snapshot.tomorrowEvents,
            weatherData = if (settings.hasWeatherConfig()) weatherData else null
        )
    }.flowOn(Dispatchers.Default)  // ✅ 将计算移到后台线程，避免主线程 ANR
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),  // ✅ 改为 WhileSubscribed，避免不必要的计算
        initialValue = MainUiState()
    )

    fun updateSelectedDate(date: LocalDate) { _selectedDate.value = date; _revealedEventId.value = null }
    fun onRevealEvent(eventId: String?) { _revealedEventId.value = eventId }

    fun checkPromptUpdatesManually() {
        if (_promptCheckInProgress.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _promptCheckInProgress.value = true
            try {
                when (val result = PromptUpdater.check(appContext, ignoreIgnoredVersion = true)) {
                    is PromptCheckResult.UpdateAvailable -> {
                        presentPromptUpdate(result.candidate)
                    }

                    is PromptCheckResult.NoUpdate -> {
                        val message = when {
                            result.remoteVersion < result.localVersion -> "当前本地 prompt 版本较新（v${result.localVersion}）"
                            else -> "当前已是最新 prompt（v${result.localVersion}）"
                        }
                        sendPromptFeedback(message, ToastType.INFO)
                    }

                    is PromptCheckResult.Error -> {
                        sendPromptFeedback(result.message, ToastType.ERROR)
                    }
                }
            } finally {
                _promptCheckInProgress.value = false
            }
        }
    }

    fun confirmPromptUpdate() {
        val remotePrompts = pendingPromptUpdate ?: return
        viewModelScope.launch(Dispatchers.IO) {
            AiPrompts.updatePrompts(appContext, remotePrompts, AiPrompts.PromptSource.CLOUD)
            refreshPromptInfo()
            Log.d("MainViewModel", "用户确认更新 prompt，version=${remotePrompts.version}")
            pendingPromptUpdate = null
            _promptUpdateDialogState.value = null
            sendPromptFeedback("已更新到本地 v${remotePrompts.version}", ToastType.SUCCESS)
        }
    }

    fun refreshPromptInfo() {
        _promptLocalVersion.value = AiPrompts.getLocalVersion(appContext)
        _promptSource.value = AiPrompts.getPromptSource(appContext)
    }

    fun dismissPromptUpdate() {
        val remotePrompts = pendingPromptUpdate
        viewModelScope.launch(Dispatchers.IO) {
            if (remotePrompts != null) {
                AiPrompts.markVersionIgnored(appContext, remotePrompts.version)
                Log.d("MainViewModel", "用户取消更新 prompt，忽略 version=${remotePrompts.version}")
                sendPromptFeedback("已忽略 v${remotePrompts.version}，更高版本时会再次提示", ToastType.INFO)
            }
            pendingPromptUpdate = null
            _promptUpdateDialogState.value = null
        }
    }

    private fun checkPromptUpdatesSilently() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = PromptUpdater.check(appContext)) {
                is PromptCheckResult.UpdateAvailable -> presentPromptUpdate(result.candidate)
                is PromptCheckResult.NoUpdate,
                is PromptCheckResult.Error -> Unit
            }
        }
    }

    private fun presentPromptUpdate(candidate: com.antgskds.calendarassistant.core.ai.PromptUpdateCandidate) {
        pendingPromptUpdate = candidate.remotePrompts
        Log.d(
            "MainViewModel",
            "准备弹出 prompt 更新对话框: local=${candidate.localVersion}, remote=${candidate.remotePrompts.version}"
        )
        _promptUpdateDialogState.value = PromptUpdateDialogState(
            localVersion = candidate.localVersion,
            remoteVersion = candidate.remotePrompts.version
        )
    }

    private fun sendPromptFeedback(message: String, type: ToastType) {
        _promptCheckFeedback.tryEmit(PromptCheckFeedback(message, type))
    }

    // --- 普通事件操作 ---
    fun addEvent(event: MyEvent) = viewModelScope.launch { scheduleCenter.addEvent(event) }
    fun updateEvent(event: MyEvent) = viewModelScope.launch { scheduleCenter.updateEvent(event) }

    fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    ) = viewModelScope.launch {
        scheduleCenter.detachRecurringInstance(parentEventId, sourceInstanceId, sourceInstanceKey, detachedEvent)
    }

    fun findRecurringParent(event: MyEvent): MyEvent? {
        if (event.isRecurringParent) return event
        val parentId = event.parentRecurringId ?: return null
        return scheduleCenter.events.value.find { it.id == parentId && it.isRecurringParent }
    }

    fun findNextRecurringInstance(parentEvent: MyEvent): MyEvent? {
        return scheduleInsightsQueryApi.findNextRecurringInstance(
            events = scheduleCenter.events.value,
            parentEventId = parentEvent.id
        )
    }

    fun deleteEvent(event: MyEvent) {
        viewModelScope.launch {
            if (event.tag == EventTags.COURSE) {
                // 仅虚拟课程实例支持“删除本次”，课程模板不进入该入口。
                if (event.id.startsWith("course_")) {
                    excludeCourse(event.id, event.startDate)
                }
            } else {
                scheduleCenter.deleteEvent(event.id)
            }
            _revealedEventId.value = null
        }
    }

    fun toggleImportant(event: MyEvent) {
        viewModelScope.launch {
            if (event.tag != EventTags.COURSE) {
                scheduleCenter.updateEvent(event.copy(isImportant = !event.isImportant))
            }
            _revealedEventId.value = null
        }
    }

    // --- 课程管理 ---
    fun addCourse(course: Course) = viewModelScope.launch {
        scheduleCenter.addEvent(courseToTemplateEvent(course))
    }

    fun updateCourse(course: Course) = viewModelScope.launch {
        val parentEvent = findCourseParentBySeries(course.id)
        scheduleCenter.updateEvent(courseToTemplateEvent(course, parentEvent))
    }

    fun deleteCourse(course: Course) = viewModelScope.launch {
        val parentId = RecurringEventUtils.buildParentId(course.id)
        scheduleCenter.deleteEvent(parentId)
        scheduleCenter.events.value
            .filter { event ->
                event.tag == EventTags.COURSE &&
                    !event.isRecurring &&
                    CourseEventMapper.detachedParentSeriesKey(event) == course.id
            }
            .forEach { detached ->
                scheduleCenter.deleteEvent(detached.id)
            }
    }

    // 删除单次课程逻辑 (通过 ID，用于 SwipeableEventItem)
    fun excludeCourse(virtualEventId: String, date: LocalDate) {
        viewModelScope.launch {
            val parts = virtualEventId.split("_")
            if (parts.size >= 2) {
                val courseId = parts[1]
                val all = currentCourses().toMutableList()
                val target = all.find { it.id == courseId } ?: return@launch

                if (target.isTemp) {
                    // 如果本身是影子课程，直接删
                    scheduleCenter.deleteEvent(target.id)
                } else {
                    val parentEvent = findCourseParentBySeries(target.id) ?: return@launch
                    val startMillis = resolveCourseStartMillis(target, date, settingsQueryApi.settings.value)
                    val instanceKey = RecurringEventUtils.buildInstanceKey(target.id, startMillis)
                    if (!parentEvent.excludedRecurringInstances.contains(instanceKey)) {
                        scheduleCenter.updateEvent(
                            parentEvent.copy(
                                excludedRecurringInstances = (parentEvent.excludedRecurringInstances + instanceKey).distinct(),
                                lastModified = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }

    // 🔥 新增：删除单次课程逻辑 (通过对象，用于 Dialog)
    // 修复 Unresolved reference 'deleteSingleCourseInstance' 错误
    fun deleteSingleCourseInstance(course: Course, date: LocalDate) {
        viewModelScope.launch {
            if (course.isTemp) {
                // 如果是影子课程，物理删除
                scheduleCenter.deleteEvent(course.id)
            } else {
                val parentEvent = findCourseParentBySeries(course.id) ?: return@launch
                val startMillis = resolveCourseStartMillis(course, date, settingsQueryApi.settings.value)
                val instanceKey = RecurringEventUtils.buildInstanceKey(course.id, startMillis)
                if (!parentEvent.excludedRecurringInstances.contains(instanceKey)) {
                    scheduleCenter.updateEvent(
                        parentEvent.copy(
                            excludedRecurringInstances = (parentEvent.excludedRecurringInstances + instanceKey).distinct(),
                            lastModified = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    // 🔥 核心：影子课程修改逻辑
    fun updateSingleCourseInstance(
        virtualEventId: String,
        newName: String,
        newLoc: String,
        newStartNode: Int,
        newEndNode: Int,
        newDate: LocalDate
    ) {
        viewModelScope.launch {
            val parts = virtualEventId.split("_")
            // 确保 ID 格式正确：course_{id}_{originalDate}
            if (parts.size < 3) return@launch

            val originalCourseId = parts[1]
            val originalDateStr = parts[2] // 这节课原本应该发生的日期

            val allCourses = currentCourses()
            val originalCourse = allCourses.find { it.id == originalCourseId } ?: return@launch

            if (originalCourse.isTemp) {
                val parentSeriesKey = originalCourse.parentCourseId.orEmpty()
                val updatedDetached = MyEvent(
                    id = originalCourse.id,
                    title = newName,
                    startDate = newDate,
                    endDate = newDate,
                    startTime = resolveNodeTime(newStartNode, settingsQueryApi.settings.value, true),
                    endTime = resolveNodeTime(newEndNode, settingsQueryApi.settings.value, false),
                    location = newLoc,
                    description = CourseEventMapper.buildDetachedInstanceDescription(
                        teacher = originalCourse.teacher,
                        startNode = newStartNode,
                        endNode = newEndNode,
                        parentSeriesKey = parentSeriesKey
                    ),
                    color = originalCourse.color,
                    tag = EventTags.COURSE,
                    skipCalendarSync = false
                )
                scheduleCenter.updateEvent(updatedDetached)
                return@launch
            }

            val parentEvent = findCourseParentBySeries(originalCourse.id) ?: return@launch
            val originalDate = runCatching { LocalDate.parse(originalDateStr) }.getOrElse { newDate }
            val sourceStartMillis = resolveCourseStartMillis(originalCourse, originalDate, settingsQueryApi.settings.value)
            val sourceInstanceKey = RecurringEventUtils.buildInstanceKey(originalCourse.id, sourceStartMillis)

            val detachedEvent = MyEvent(
                title = newName,
                startDate = newDate,
                endDate = newDate,
                startTime = resolveNodeTime(newStartNode, settingsQueryApi.settings.value, true),
                endTime = resolveNodeTime(newEndNode, settingsQueryApi.settings.value, false),
                location = newLoc,
                description = CourseEventMapper.buildDetachedInstanceDescription(
                    teacher = originalCourse.teacher,
                    startNode = newStartNode,
                    endNode = newEndNode,
                    parentSeriesKey = originalCourse.id
                ),
                color = originalCourse.color,
                tag = EventTags.COURSE,
                skipCalendarSync = false
            )

            scheduleCenter.detachRecurringInstance(
                parentEventId = parentEvent.id,
                sourceInstanceId = "",
                sourceInstanceKey = sourceInstanceKey,
                detachedEvent = detachedEvent
            )
        }
    }

    private fun currentCourses(): List<Course> {
        return CourseEventMapper.extractCourses(scheduleCenter.events.value, settingsQueryApi.settings.value)
    }

    private fun findCourseParentBySeries(seriesKey: String): MyEvent? {
        val parentId = RecurringEventUtils.buildParentId(seriesKey)
        return scheduleCenter.events.value.firstOrNull { it.id == parentId && it.isRecurringParent }
    }

    private fun resolveCourseStartMillis(course: Course, date: LocalDate, settings: MySettings): Long {
        val startTime = resolveNodeTime(course.startNode, settings, true)
        return runCatching {
            LocalDateTime.of(date, LocalTime.parse(startTime))
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun resolveNodeTime(nodeIndex: Int, settings: MySettings, start: Boolean): String {
        val nodes = runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<com.antgskds.calendarassistant.data.model.TimeNode>>(settings.timeTableJson)
        }.getOrElse { emptyList() }
        val matched = nodes.find { it.index == nodeIndex }
        return if (start) matched?.startTime ?: "08:00" else matched?.endTime ?: "09:40"
    }

    private fun courseToTemplateEvent(course: Course, existingParent: MyEvent? = null): MyEvent {
        return CourseEventMapper.toTemplateEvent(course, settingsQueryApi.settings.value, existingParent)
    }

    // --- 归档操作 ---

    /**
     * 🔥 修复：懒加载归档数据
     * 仅在进入归档页面时调用
     */
    fun fetchArchivedEvents() {
        scheduleCenter.fetchArchivedEvents()
    }

    /**
     * 归档事件
     */
    fun archiveEvent(eventId: String) {
        viewModelScope.launch {
            scheduleCenter.archiveEvent(eventId)
            _revealedEventId.value = null
        }
    }

    /**
     * 还原归档事件
     */
    fun restoreEvent(archivedEventId: String) {
        viewModelScope.launch {
            scheduleCenter.restoreEvent(archivedEventId)
        }
    }

    /**
     * 删除归档事件
     */
    fun deleteArchivedEvent(archivedEventId: String) {
        viewModelScope.launch {
            scheduleCenter.deleteArchivedEvent(archivedEventId)
        }
    }

    /**
     * 清空所有归档
     */
    fun clearAllArchives() {
        viewModelScope.launch {
            scheduleCenter.clearAllArchives()
        }
    }

    /**
     * 刷新数据
     * 每次回到前台时调用，确保 UI 显示最新状态
     */
    fun refreshData() {
        viewModelScope.launch {
            // 1. 触发自动归档，删除过期事件
            val archivedCount = scheduleCenter.autoArchiveExpiredEvents()
            if (archivedCount > 0) {
                Log.d("Refresh", "自动归档了 $archivedCount 条事件")
            }
            // 2. 强制触发 UI 重组
            _timeTrigger.value = System.currentTimeMillis()
        }
    }
}
