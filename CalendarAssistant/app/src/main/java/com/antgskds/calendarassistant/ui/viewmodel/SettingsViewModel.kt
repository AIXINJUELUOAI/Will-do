package com.antgskds.calendarassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.core.importer.WakeUpCourseImporter
import com.antgskds.calendarassistant.data.model.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: AppRepository
) : ViewModel() {

    // 直接观察 Repository 的数据源
    val settings = repository.settings

    // 日历同步状态
    private val _syncStatus = MutableStateFlow(CalendarSyncManager.SyncStatus(
        isEnabled = false,
        hasPermission = false,
        targetCalendarId = -1L,
        lastSyncTime = 0L,
        mappedEventCount = 0
    ))
    val syncStatus: StateFlow<CalendarSyncManager.SyncStatus> = _syncStatus.asStateFlow()

    init {
        refreshSyncStatus()
    }

    // 更新 AI 设置
    fun updateAiSettings(key: String, name: String, url: String) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                current.copy(
                    modelKey = key,
                    modelName = name,
                    modelUrl = url
                )
            )
        }
    }

    // 更新学期开始日期
    fun updateSemesterStartDate(date: String) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(semesterStartDate = date))
        }
    }

    // 更新总周数
    fun updateTotalWeeks(weeks: Int) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(totalWeeks = weeks))
        }
    }

    // 更新作息时间 JSON
    fun updateTimeTable(json: String) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(timeTableJson = json))
        }
    }

    // 更新偏好设置（支持单独更新某一项）
    // 【修改】增加了 pickupAggregation、advanceReminder 和 autoArchive 参数
    fun updatePreference(
        showTomorrow: Boolean? = null,
        dailySummary: Boolean? = null,
        liveCapsule: Boolean? = null,
        pickupAggregation: Boolean? = null,
        advanceReminderEnabled: Boolean? = null,
        advanceReminderMinutes: Int? = null,
        autoArchive: Boolean? = null
    ) {
        viewModelScope.launch {
            var current = settings.value
            if (showTomorrow != null) current = current.copy(showTomorrowEvents = showTomorrow)
            if (dailySummary != null) current = current.copy(isDailySummaryEnabled = dailySummary)
            if (liveCapsule != null) current = current.copy(isLiveCapsuleEnabled = liveCapsule)
            if (pickupAggregation != null) current = current.copy(isPickupAggregationEnabled = pickupAggregation)
            if (advanceReminderEnabled != null) current = current.copy(isAdvanceReminderEnabled = advanceReminderEnabled)
            if (advanceReminderMinutes != null) current = current.copy(advanceReminderMinutes = advanceReminderMinutes)
            if (autoArchive != null) current = current.copy(autoArchiveEnabled = autoArchive)

            repository.updateSettings(current)
        }
    }

    // 更新截图延迟
    fun updateScreenshotDelay(delay: Long) {
        val current = settings.value
        if (current.screenshotDelayMs != delay) {
            viewModelScope.launch {
                repository.updateSettings(current.copy(screenshotDelayMs = delay))
            }
        }
    }

    // 更新主题模式
    fun updateDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(isDarkMode = isDark))
        }
    }

    // 更新 UI 大小
    fun updateUiSize(size: Int) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(uiSize = size))
        }
    }

    // 更新智能推荐开关
    fun updateSmartRecommendation(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(enableSmartRecommendation = enabled))
        }
    }

    // 导出数据
    fun exportData(onSuccess: () -> Unit) {
        // TODO: 实现具体的导出逻辑
    }

    // --- 导出/导入功能 ---

    suspend fun exportCoursesData(): String {
        return repository.exportCoursesData()
    }

    suspend fun importCoursesData(jsonString: String): Result<Unit> {
        return repository.importCoursesData(jsonString)
    }

    suspend fun exportEventsData(): String {
        return repository.exportEventsData()
    }

    suspend fun importEventsData(jsonString: String): Result<ImportResult> {
        return repository.importEventsData(jsonString)
    }

    fun getCoursesCount(): Int = repository.getCoursesCount()
    fun getEventsCount(): Int = repository.getEventsCount()
    fun getTotalEventsCount(): Int = repository.getTotalEventsCount()

    /**
     * 导入外部课表文件（WakeUp 格式）
     * @param content 文件内容
     * @param mode 导入模式（追加/覆盖）
     * @param importSettings 是否导入设置（开学日期、总周数）
     * @param callback 导入完成回调，返回成功导入的课程数量
     */
    fun importWakeUpFile(
        content: String,
        mode: ImportMode,
        importSettings: Boolean,
        callback: suspend (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.importWakeUpFile(content, mode, importSettings)
            callback(result)
        }
    }

    // ==================== 日历同步相关 ====================

    /**
     * 刷新同步状态
     */
    fun refreshSyncStatus() {
        viewModelScope.launch {
            _syncStatus.value = repository.getSyncStatus()
        }
    }

    /**
     * 切换日历同步开关
     */
    fun toggleCalendarSync(enabled: Boolean) {
        viewModelScope.launch {
            val result = if (enabled) {
                repository.enableCalendarSync()
            } else {
                repository.disableCalendarSync()
            }

            if (result.isSuccess) {
                refreshSyncStatus()
            }
        }
    }

    /**
     * 手动触发同步
     */
    suspend fun manualSync(): Result<Unit> {
        val result = repository.manualSync()
        if (result.isSuccess) {
            refreshSyncStatus()
        }
        return result
    }
}
