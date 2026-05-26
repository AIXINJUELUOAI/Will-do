package com.antgskds.calendarassistant.ui.viewmodel

import android.net.Uri
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.stubs.CalendarSyncManager
import com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager
import com.antgskds.calendarassistant.calendar.models.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.core.center.BackupCenter
import com.antgskds.calendarassistant.core.center.ParsedCourseImport
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.center.SyncCenter
import com.antgskds.calendarassistant.core.center.ImportMode
import com.antgskds.calendarassistant.core.localmodel.LocalModelImportProgress
import com.antgskds.calendarassistant.core.localmodel.LocalModelManager
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsTransformApi
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.UiStyle
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.ui.theme.normalizeThemeHexColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class SettingsViewModel(
    private val scheduleCenter: ScheduleCenter,
    private val backupCenter: BackupCenter,
    private val syncCenter: SyncCenter,
    private val settingsOperationApi: SettingsOperationApi,
    private val settingsQueryApi: SettingsQueryApi,
    private val settingsTransformApi: SettingsTransformApi,
    private val scheduleInsightsQueryApi: ScheduleInsightsQueryApi,
    private val localModelManager: LocalModelManager
) : ViewModel() {
    // 直接观察 QueryApi 的数据源
    val settings = settingsQueryApi.settings

    // 日历同步状态
    private val _syncStatus = MutableStateFlow(CalendarSyncManager.SyncStatus(
        isEnabled = false,
        hasPermission = false,
        targetCalendarId = -1L,
        sourceCalendarIds = emptyList(),
        syncIntervalSeconds = 60,
        lastSyncTime = 0L,
        mappedEventCount = 0
    ))
    val syncStatus: StateFlow<CalendarSyncManager.SyncStatus> = _syncStatus.asStateFlow()

    private val _availableSyncCalendars = MutableStateFlow<List<CalendarManager.CalendarInfo>>(emptyList())
    val availableSyncCalendars: StateFlow<List<CalendarManager.CalendarInfo>> = _availableSyncCalendars.asStateFlow()

    val localModels = localModelManager.models
    private val _localModelImportProgress = MutableStateFlow<LocalModelImportProgress?>(null)
    val localModelImportProgress: StateFlow<LocalModelImportProgress?> = _localModelImportProgress.asStateFlow()

    init {
        refreshSyncStatus()
        refreshSyncCalendars()
    }

    // 更新 AI 设置
    fun updateAiSettings(key: String, name: String, url: String) {
        viewModelScope.launch {
            val current = settings.value
            settingsOperationApi.updateSettings(
                current.copy(
                    modelKey = key,
                    modelName = name,
                    modelUrl = url
                )
            )
        }
    }

    fun updateMultimodalAiSettings(key: String, name: String, url: String) {
        viewModelScope.launch {
            val current = settings.value
            settingsOperationApi.updateSettings(
                current.copy(
                    mmModelKey = key,
                    mmModelName = name,
                    mmModelUrl = url
                )
            )
        }
    }

    // 更新学期开始日期
    fun updateSemesterStartDate(date: String) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(semesterStartDate = date))
        }
    }

    // 更新总周数
    fun updateTotalWeeks(weeks: Int) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(totalWeeks = weeks))
        }
    }

    // 更新作息时间 JSON
    fun updateTimeTable(json: String, configJson: String? = null) {
        viewModelScope.launch {
            val current = settings.value
            val updated = if (configJson != null) {
                current.copy(timeTableJson = json, timeTableConfigJson = configJson)
            } else {
                current.copy(timeTableJson = json)
            }
            settingsOperationApi.updateSettings(updated)
            remapCourseEventsForSettings(updated)
        }
    }

    fun updateTimeTableConfig(json: String) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(timeTableConfigJson = json))
        }
    }

    // 更新偏好设置（支持单独更新某一项）
    // 【修改】增加了 pickupAggregation、advanceReminder 和 autoArchive 参数
    fun updatePreference(
        showTomorrow: Boolean? = null,
        dailySummary: Boolean? = null,
        liveCapsule: Boolean? = null,
        pickupAggregation: Boolean? = null,
        edgeBarEnabled: Boolean? = null,
        networkSpeedCapsule: Boolean? = null,
        floatingWindow: Boolean? = null,
        advanceReminderEnabled: Boolean? = null,
        advanceReminderMinutes: Int? = null,
        autoArchive: Boolean? = null,
        defaultEventDurationMinutes: Int? = null,
        useMultimodalAi: Boolean? = null,
        disableThinking: Boolean? = null,
        localSemanticEnabled: Boolean? = null,
        selectedLocalModelId: String? = null,
        floatingEventRange: Int? = null,
        floatingExpandSide: String? = null,
        volumeUpLongPressEnabled: Boolean? = null,
        volumeUpLongPressAction: Int? = null,
        smsMonitoring: Boolean? = null,
        forceInstantCodeTimeToNow: Boolean? = null,
        noteEnabled: Boolean? = null,
        homeBottomItems: List<String>? = null,
        homeStartPageKey: String? = null
    ) {
        viewModelScope.launch {
            localSemanticEnabled?.let { localModelManager.setLocalModelLoggingEnabled(it) }
            val updated = settingsTransformApi.applyPreferenceUpdate(
                current = settings.value,
                showTomorrow = showTomorrow,
                dailySummary = dailySummary,
                liveCapsule = liveCapsule,
                pickupAggregation = pickupAggregation,
                edgeBarEnabled = edgeBarEnabled,
                networkSpeedCapsule = networkSpeedCapsule,
                floatingWindow = floatingWindow,
                advanceReminderEnabled = advanceReminderEnabled,
                advanceReminderMinutes = advanceReminderMinutes,
                autoArchive = autoArchive,
                defaultEventDurationMinutes = defaultEventDurationMinutes,
                useMultimodalAi = useMultimodalAi,
                disableThinking = disableThinking,
                localSemanticEnabled = localSemanticEnabled,
                selectedLocalModelId = selectedLocalModelId,
                floatingEventRange = floatingEventRange,
                floatingExpandSide = floatingExpandSide,
                volumeUpLongPressEnabled = volumeUpLongPressEnabled,
                volumeUpLongPressAction = volumeUpLongPressAction,
                smsMonitoring = smsMonitoring,
                forceInstantCodeTimeToNow = forceInstantCodeTimeToNow,
                noteEnabled = noteEnabled,
                homeBottomItems = homeBottomItems,
                homeStartPageKey = homeStartPageKey
            )
            settingsOperationApi.updateSettings(updated)
        }
    }

    fun updateWeatherSettings(
        enabled: Boolean,
        provider: String,
        apiUrl: String,
        apiKey: String,
        refreshInterval: Int,
        showInFloating: Boolean
    ) {
        settingsOperationApi.updateSettings(
            settings.value.copy(
                weatherEnabled = enabled,
                weatherProvider = provider,
                weatherApiUrl = apiUrl,
                weatherApiKey = apiKey,
                weatherCity = "",
                weatherRefreshInterval = refreshInterval,
                showWeatherInFloating = showInFloating
            )
        )
    }

    // 更新截图延迟
    fun updateScreenshotDelay(delay: Long) {
        val current = settings.value
        if (current.screenshotDelayMs != delay) {
            viewModelScope.launch {
                settingsOperationApi.updateSettings(current.copy(screenshotDelayMs = delay))
            }
        }
    }

    // 更新主题模式（1=跟随系统, 2=浅色, 3=深色）
    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(themeMode = mode))
        }
    }

    // 更新主题配色方案
    fun updateThemeColorScheme(scheme: String) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(themeColorScheme = scheme))
        }
    }

    fun updateCustomThemeColorHex(hex: String) {
        val normalized = normalizeThemeHexColor(hex) ?: return
        viewModelScope.launch {
            settingsOperationApi.updateSettings(
                settings.value.copy(
                    themeColorScheme = ThemeColorScheme.CUSTOM.name,
                    customThemeColorHex = normalized
                )
            )
        }
    }

    // 更新深色模式（兼容旧接口）
    fun updateDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(
                isDarkMode = isDark,
                themeMode = if (isDark) 3 else 2
            ))
        }
    }

    // 更新 UI 大小
    fun updateUiSize(size: Int) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(uiSize = size))
        }
    }

    fun updateUiStyle(style: String) {
        val normalizedStyle = UiStyle.fromName(style).name
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(uiStyle = normalizedStyle))
        }
    }

    fun updateEdgeBarSettings(
        enabled: Boolean? = null,
        side: String? = null,
        yPercent: Float? = null,
        widthDp: Int? = null,
        heightDp: Int? = null,
        alpha: Float? = null
    ) {
        viewModelScope.launch {
            var current = settings.value
            if (enabled != null) current = current.copy(edgeBarEnabled = enabled)
            if (side != null) current = current.copy(edgeBarSide = side)
            if (yPercent != null) current = current.copy(edgeBarYPercent = yPercent)
            if (widthDp != null) current = current.copy(edgeBarWidthDp = widthDp)
            if (heightDp != null) current = current.copy(edgeBarHeightDp = heightDp)
            if (alpha != null) current = current.copy(edgeBarAlpha = alpha)
            settingsOperationApi.updateSettings(current)
        }
    }

    // 导出数据
    fun exportData(onSuccess: () -> Unit) {
        // TODO: 实现具体的导出逻辑
    }

    // --- 导出/导入功能 ---

    suspend fun exportCoursesData(): String {
        return backupCenter.exportCoursesData()
    }

    suspend fun importCoursesData(jsonString: String): Result<Unit> {
        return backupCenter.importCoursesData(jsonString)
    }

    suspend fun exportEventsData(): String {
        return backupCenter.exportEventsData()
    }

    suspend fun importEventsData(jsonString: String): Result<ImportResult> {
        return backupCenter.importEventsData(jsonString)
    }

    fun getEventsCount(): Int = scheduleCenter.getEventsCount()
    fun getTotalEventsCount(): Int = scheduleCenter.getTotalEventsCount()
    fun getCoursesCount(): Int = CourseEventMapper.extractParentCourses(
        scheduleCenter.events.value,
        settingsQueryApi.settings.value
    ).size

    private suspend fun remapCourseEventsForSettings(updatedSettings: MySettings) {
        val events = scheduleCenter.events.value
        val courseParents = events.filter { CourseEventMapper.isCourseParent(it) }
        if (courseParents.isEmpty()) return

        courseParents.forEach { parent ->
            val parentId = parent.id ?: return@forEach
            val course = CourseEventMapper.toCourse(parent, updatedSettings) ?: return@forEach
            val detachedWeeks = events
                .filter { it.parentId == parentId && CourseEventMapper.isCourseEvent(it) }
                .mapNotNull { child -> CourseEventMapper.childOriginalWeek(child, updatedSettings) }
                .distinct()
            val detachedOccurrenceTs = detachedWeeks.mapNotNull { week ->
                CourseEventMapper.occurrenceTsForWeek(course, updatedSettings, week)
            }
            val rebasedParent = CourseEventMapper.toParentEvent(
                course = course,
                settings = updatedSettings,
                existingParent = parent.copy(exdates = emptyList()),
                additionalExcludedOccurrenceTs = detachedOccurrenceTs
            )
            if (rebasedParent.startTS != parent.startTS ||
                rebasedParent.endTS != parent.endTS ||
                rebasedParent.exdates != parent.exdates
            ) {
                scheduleCenter.updateEvent(rebasedParent)
            }
        }

        events.filter { CourseEventMapper.isCourseException(it) }.forEach { child ->
            val meta = CourseEventMapper.parseMeta(child.description) ?: return@forEach
            val actualDate = Instant.ofEpochSecond(child.startTS)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val (startTs, endTs) = CourseEventMapper.mapNodesToEpochRange(
                settings = updatedSettings,
                date = actualDate,
                startNode = meta.startNode,
                endNode = meta.endNode
            )
            if (startTs != child.startTS || endTs != child.endTS) {
                scheduleCenter.updateEvent(child.copy(startTS = startTs, endTS = endTs))
            }
        }
    }

    fun hasDuplicateAdvanceReminder(minutes: Int): Boolean {
        return scheduleInsightsQueryApi.hasDuplicateAdvanceReminder(
            events = scheduleCenter.events.value,
            minutes = minutes
        )
    }

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
            val result = backupCenter.importWakeUpFile(content, mode, importSettings)
            callback(result)
        }
    }

    fun importLocalModel(uri: Uri, onComplete: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            _localModelImportProgress.value = LocalModelImportProgress("", 0L, 0L)
            try {
                val result = localModelManager.importModel(uri) { progress ->
                    _localModelImportProgress.value = progress
                }
                val current = settings.value
                settingsOperationApi.updateSettings(
                    current.copy(
                        selectedLocalModelId = result.model.id,
                        isLocalSemanticEnabled = true
                    )
                )
                onComplete(Result.success(result.model.displayName))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            } finally {
                _localModelImportProgress.value = null
            }
        }
    }

    fun updateSelectedLocalModel(modelId: String) {
        updatePreference(selectedLocalModelId = modelId)
    }

    fun deleteLocalModel(modelId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val deleted = localModelManager.deleteModel(modelId)
            if (deleted && settings.value.selectedLocalModelId == modelId) {
                val nextId = localModelManager.models.value.firstOrNull()?.id.orEmpty()
                settingsOperationApi.updateSettings(settings.value.copy(selectedLocalModelId = nextId))
            }
            onComplete(deleted)
        }
    }

    suspend fun parseExternalCourseImport(content: String): Result<ParsedCourseImport> {
        return backupCenter.parseExternalCourseImport(content)
    }

    suspend fun fetchWakeUpShareImport(shareText: String): Result<ParsedCourseImport> {
        return backupCenter.fetchWakeUpShareImport(shareText)
    }

    fun importParsedCourseImport(
        parsed: ParsedCourseImport,
        mode: ImportMode,
        importSettings: Boolean,
        callback: suspend (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            val result = backupCenter.importParsedCourseImport(parsed, mode, importSettings)
            callback(result)
        }
    }

    // ==================== 日历同步相关 ====================

    /**
     * 刷新同步状态
     */
    fun refreshSyncStatus() {
        viewModelScope.launch {
            _syncStatus.value = syncCenter.getSyncStatus()
        }
    }

    fun refreshSyncCalendars() {
        viewModelScope.launch {
            _availableSyncCalendars.value = syncCenter.getSelectableSyncCalendars()
        }
    }

    /**
     * 切换日历同步开关
     */
    fun toggleCalendarSync(enabled: Boolean) {
        viewModelScope.launch {
            val result = if (enabled) {
                syncCenter.enableCalendarSync()
            } else {
                syncCenter.disableCalendarSync()
            }

            if (result.isSuccess) {
                refreshSyncStatus()
            }
        }
    }

    fun enableCalendarSyncAndSyncNow(callback: suspend (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = syncCenter.enableCalendarSyncAndSyncNow()
            refreshSyncStatus()
            refreshSyncCalendars()
            callback(result)
        }
    }

    fun updateSourceCalendars(calendarIds: List<Long>, callback: suspend (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = syncCenter.updateSourceCalendars(calendarIds)
            refreshSyncStatus()
            refreshSyncCalendars()
            callback(result)
        }
    }

    /**
     * 手动触发同步
     */
    suspend fun manualSync(): Result<Unit> {
        val result = syncCenter.manualSync()
        if (result.isSuccess) {
            refreshSyncStatus()
        }
        return result
    }

    fun updateSyncIntervalSeconds(seconds: Int, callback: suspend (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = syncCenter.updateSyncIntervalSeconds(seconds)
            refreshSyncStatus()
            callback(result)
        }
    }

    /**
     * 更新捐赠状态
     */
    fun updateHasDonated(hasDonated: Boolean) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(
                settings.value.copy(hasDonated = hasDonated)
            )
        }
    }
}
