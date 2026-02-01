package com.antgskds.calendarassistant.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.core.course.CourseManager
import com.antgskds.calendarassistant.core.util.DateCalculator
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class MainUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val revealedEventId: String? = null,
    val allEvents: List<MyEvent> = emptyList(),
    val courses: List<Course> = emptyList(),
    val settings: MySettings = MySettings(),
    val currentDateEvents: List<MyEvent> = emptyList(),
    val tomorrowEvents: List<MyEvent> = emptyList()
)

class MainViewModel(
    private val repository: AppRepository
) : ViewModel() {

    // ✅ 时间触发器：每 10 秒触发一次，确保过期状态能及时更新
    private val _timeTrigger = MutableStateFlow(System.currentTimeMillis())

    init {
        // 启动定时器
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)  // 10 秒
                _timeTrigger.value = System.currentTimeMillis()
            }
        }

        // 自动归档过期事件
        viewModelScope.launch {
            val archivedCount = repository.autoArchiveExpiredEvents()
            if (archivedCount > 0) {
                Log.d("Archive", "自动归档了 $archivedCount 条事件")
            }
        }
    }

    // 归档事件（公开访问）
    val archivedEvents = repository.archivedEvents

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _revealedEventId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        _selectedDate,
        _revealedEventId,
        repository.events,
        repository.courses,
        repository.settings,
        _timeTrigger  // ✅ 添加时间触发器
    ) { values ->
        val date = values[0] as LocalDate
        val revealedId = values[1] as String?
        val events = values[2] as List<MyEvent>
        val courses = values[3] as List<Course>
        val settings = values[4] as MySettings
        // values[5] 是 _timeTrigger，不需要使用

        val todayNormal = events.filter { event ->
            date >= event.startDate && date <= event.endDate
        }.distinctBy { it.id }
        val todayCourses = CourseManager.getDailyCourses(date, courses, settings)
        val todayMerged = (todayNormal + todayCourses).sortedWith(compareBy(
            // 8级优先级：过期状态 > 重要性 > 单多日
            { event ->
                val isExpired = DateCalculator.isEventExpired(event)
                val isImportant = event.isImportant
                val isMultiDay = event.startDate != event.endDate
                when {
                    !isExpired && isImportant && isMultiDay -> 0
                    !isExpired && isImportant && !isMultiDay -> 1
                    !isExpired && !isImportant && isMultiDay -> 2
                    !isExpired && !isImportant && !isMultiDay -> 3
                    isExpired && isImportant && isMultiDay -> 4
                    isExpired && isImportant && !isMultiDay -> 5
                    isExpired && !isImportant && isMultiDay -> 6
                    else -> 7
                }
            },
            // 同优先级内按开始时间排序
            { it.startTime }
        ))

        val tomorrowMerged = if (settings.showTomorrowEvents) {
            val tomorrow = date.plusDays(1)
            val tomorrowNormal = events.filter { event ->
                tomorrow >= event.startDate && tomorrow <= event.endDate
            }.distinctBy { it.id }
            val tomorrowCourses = CourseManager.getDailyCourses(tomorrow, courses, settings)
            (tomorrowNormal + tomorrowCourses).sortedWith(compareBy(
                // 8级优先级：过期状态 > 重要性 > 单多日
                { event ->
                    val isExpired = DateCalculator.isEventExpired(event)
                    val isImportant = event.isImportant
                    val isMultiDay = event.startDate != event.endDate
                    when {
                        !isExpired && isImportant && isMultiDay -> 0
                        !isExpired && isImportant && !isMultiDay -> 1
                        !isExpired && !isImportant && isMultiDay -> 2
                        !isExpired && !isImportant && !isMultiDay -> 3
                        isExpired && isImportant && isMultiDay -> 4
                        isExpired && isImportant && !isMultiDay -> 5
                        isExpired && !isImportant && isMultiDay -> 6
                        else -> 7
                    }
                },
                { it.startTime }
            ))
        } else { emptyList() }

        MainUiState(
            selectedDate = date,
            revealedEventId = revealedId,
            allEvents = events,
            courses = courses,
            settings = settings,
            currentDateEvents = todayMerged,
            tomorrowEvents = tomorrowMerged
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,  // ✅ 改为 Eagerly，确保 init 中的归档操作能被捕获
        initialValue = MainUiState()
    )

    fun updateSelectedDate(date: LocalDate) { _selectedDate.value = date; _revealedEventId.value = null }
    fun onRevealEvent(eventId: String?) { _revealedEventId.value = eventId }

    // --- 普通事件操作 ---
    fun addEvent(event: MyEvent) = viewModelScope.launch { repository.addEvent(event) }
    fun updateEvent(event: MyEvent) = viewModelScope.launch { repository.updateEvent(event) }

    fun deleteEvent(event: MyEvent) {
        viewModelScope.launch {
            if (event.eventType == "course") {
                // 如果是课程，走排除逻辑
                excludeCourse(event.id, event.startDate)
            } else {
                repository.deleteEvent(event.id)
            }
            _revealedEventId.value = null
        }
    }

    fun toggleImportant(event: MyEvent) {
        viewModelScope.launch {
            if (event.eventType != "course") repository.updateEvent(event.copy(isImportant = !event.isImportant))
            _revealedEventId.value = null
        }
    }

    // --- 课程管理 ---
    fun addCourse(course: Course) = viewModelScope.launch { repository.addCourse(course) }
    fun updateCourse(course: Course) = viewModelScope.launch { repository.updateCourse(course) }
    fun deleteCourse(course: Course) = viewModelScope.launch { repository.deleteCourse(course) }

    // 删除单次课程逻辑 (通过 ID，用于 SwipeableEventItem)
    fun excludeCourse(virtualEventId: String, date: LocalDate) {
        viewModelScope.launch {
            val parts = virtualEventId.split("_")
            if (parts.size >= 2) {
                val courseId = parts[1]
                val all = repository.courses.value.toMutableList()
                val target = all.find { it.id == courseId } ?: return@launch

                if (target.isTemp) {
                    // 如果本身是影子课程，直接删
                    repository.deleteCourse(target)
                } else {
                    // 主课程，加入排除列表
                    val dateStr = date.toString()
                    if (!target.excludedDates.contains(dateStr)) {
                        repository.updateCourse(target.copy(excludedDates = target.excludedDates + dateStr))
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
                repository.deleteCourse(course)
            } else {
                // 如果是主课程，逻辑删除（排除该日）
                val dateStr = date.toString()
                if (!course.excludedDates.contains(dateStr)) {
                    val newExcluded = course.excludedDates + dateStr
                    repository.updateCourse(course.copy(excludedDates = newExcluded))
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

            val allCourses = repository.courses.value
            val originalCourse = allCourses.find { it.id == originalCourseId } ?: return@launch

            // 1. 计算目标周次
            val settings = repository.settings.value
            val semesterStart = try {
                if(settings.semesterStartDate.isNotBlank()) LocalDate.parse(settings.semesterStartDate) else LocalDate.now()
            } catch (e: Exception) { LocalDate.now() }

            // 目标日期是第几周
            val daysDiff = ChronoUnit.DAYS.between(semesterStart, newDate)
            val targetWeek = (daysDiff / 7).toInt() + 1

            if (originalCourse.isTemp) {
                // --- 场景 A：本身就是影子课程 ---
                // 直接更新属性
                val updatedShadow = originalCourse.copy(
                    name = newName,
                    location = newLoc,
                    dayOfWeek = newDate.dayOfWeek.value, // 支持改到另一天
                    startNode = newStartNode,
                    endNode = newEndNode,
                    startWeek = targetWeek,
                    endWeek = targetWeek
                )
                repository.updateCourse(updatedShadow)
            } else {
                // --- 场景 B：这是主课程 ---
                // 1. 先把主课程在那天屏蔽掉
                if (!originalCourse.excludedDates.contains(originalDateStr)) {
                    val newExcluded = originalCourse.excludedDates + originalDateStr
                    repository.updateCourse(originalCourse.copy(excludedDates = newExcluded))
                }

                // 2. 创建一个新的影子课程
                val shadowCourse = Course(
                    id = UUID.randomUUID().toString(),
                    name = newName,
                    location = newLoc,
                    teacher = originalCourse.teacher,
                    color = originalCourse.color,      // 继承颜色
                    dayOfWeek = newDate.dayOfWeek.value,
                    startNode = newStartNode,
                    endNode = newEndNode,
                    startWeek = targetWeek,            // 🔒 锁定只在这一周生效
                    endWeek = targetWeek,
                    weekType = 0,                      // 0=每周
                    isTemp = true,                     // ⚠️ 标记为影子
                    parentCourseId = originalCourse.id // 🔗 认父，用于级联删除
                )
                repository.addCourse(shadowCourse)
            }
        }
    }

    // --- 归档操作 ---

    /**
     * 🔥 修复：懒加载归档数据
     * 仅在进入归档页面时调用
     */
    fun fetchArchivedEvents() {
        repository.fetchArchivedEvents()
    }

    /**
     * 归档事件
     */
    fun archiveEvent(eventId: String) {
        viewModelScope.launch {
            repository.archiveEvent(eventId)
            _revealedEventId.value = null
        }
    }

    /**
     * 还原归档事件
     */
    fun restoreEvent(archivedEventId: String) {
        viewModelScope.launch {
            repository.restoreEvent(archivedEventId)
        }
    }

    /**
     * 删除归档事件
     */
    fun deleteArchivedEvent(archivedEventId: String) {
        viewModelScope.launch {
            repository.deleteArchivedEvent(archivedEventId)
        }
    }

    /**
     * 清空所有归档
     */
    fun clearAllArchives() {
        viewModelScope.launch {
            repository.clearAllArchives()
        }
    }

    /**
     * 刷新数据
     * 每次回到前台时调用，确保 UI 显示最新状态
     */
    fun refreshData() {
        viewModelScope.launch {
            // 1. 触发自动归档，删除过期事件
            val archivedCount = repository.autoArchiveExpiredEvents()
            if (archivedCount > 0) {
                Log.d("Refresh", "自动归档了 $archivedCount 条事件")
            }
            // 2. 强制触发 UI 重组
            _timeTrigger.value = System.currentTimeMillis()
        }
    }
}