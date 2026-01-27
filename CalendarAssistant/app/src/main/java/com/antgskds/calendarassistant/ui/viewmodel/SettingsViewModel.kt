package com.antgskds.calendarassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.data.repository.AppRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: AppRepository
) : ViewModel() {

    // 直接观察 Repository 的数据源
    val settings = repository.settings

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
    // 【修改】增加了 pickupAggregation 参数
    fun updatePreference(
        showTomorrow: Boolean? = null,
        dailySummary: Boolean? = null,
        liveCapsule: Boolean? = null,
        pickupAggregation: Boolean? = null
    ) {
        viewModelScope.launch {
            var current = settings.value
            if (showTomorrow != null) current = current.copy(showTomorrowEvents = showTomorrow)
            if (dailySummary != null) current = current.copy(isDailySummaryEnabled = dailySummary)
            if (liveCapsule != null) current = current.copy(isLiveCapsuleEnabled = liveCapsule)
            if (pickupAggregation != null) current = current.copy(isPickupAggregationEnabled = pickupAggregation)

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

    suspend fun importEventsData(jsonString: String): Result<Unit> {
        return repository.importEventsData(jsonString)
    }

    fun getCoursesCount(): Int = repository.getCoursesCount()
    fun getEventsCount(): Int = repository.getEventsCount()
}