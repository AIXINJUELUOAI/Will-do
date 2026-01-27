package com.antgskds.calendarassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.core.course.CourseManager
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

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _revealedEventId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        _selectedDate,
        _revealedEventId,
        repository.events,
        repository.courses,
        repository.settings
    ) { date, revealedId, events, courses, settings ->

        val todayNormal = events.filter { it.startDate == date }
        val todayCourses = CourseManager.getDailyCourses(date, courses, settings)
        val todayMerged = (todayNormal + todayCourses).sortedBy { it.startTime }

        val tomorrowMerged = if (settings.showTomorrowEvents) {
            val tomorrow = date.plusDays(1)
            val tomorrowNormal = events.filter { it.startDate == tomorrow }
            val tomorrowCourses = CourseManager.getDailyCourses(tomorrow, courses, settings)
            (tomorrowNormal + tomorrowCourses).sortedBy { it.startTime }
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
        started = SharingStarted.WhileSubscribed(5000),
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
}