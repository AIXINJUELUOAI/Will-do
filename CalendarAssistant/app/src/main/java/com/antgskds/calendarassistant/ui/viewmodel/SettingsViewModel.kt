package com.antgskds.calendarassistant.ui.viewmodel

import android.net.Uri
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.stubs.CalendarSyncManager
import com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager
import com.antgskds.calendarassistant.calendar.models.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.core.center.BackupCenter
import com.antgskds.calendarassistant.core.center.DiagnosticLogCenter
import com.antgskds.calendarassistant.core.center.DuplicateEventCleanupCenter
import com.antgskds.calendarassistant.core.center.DuplicateEventCleanupResult
import com.antgskds.calendarassistant.core.center.ParsedCourseImport
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.center.SyncCenter
import com.antgskds.calendarassistant.core.center.ImportMode
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.note.LegacyNoteMigrationCenter
import com.antgskds.calendarassistant.core.note.LegacyNoteMigrationResult
import com.antgskds.calendarassistant.core.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsTransformApi
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.data.model.AppBackupOptions
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
    private val diagnosticLogCenter: DiagnosticLogCenter,
    private val settingsOperationApi: SettingsOperationApi,
    private val settingsQueryApi: SettingsQueryApi,
    private val settingsTransformApi: SettingsTransformApi,
    private val scheduleInsightsQueryApi: ScheduleInsightsQueryApi,
    private val legacyNoteMigrationCenter: LegacyNoteMigrationCenter,
    private val duplicateEventCleanupCenter: DuplicateEventCleanupCenter
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
        hapticFeedbackEnabled: Boolean? = null,
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
        predictiveBackEnabled: Boolean? = null,
        clipboardCodeRecognitionEnabled: Boolean? = null,
        widgetThemeMode: Int? = null,
        widgetBackgroundAlpha: Float? = null,
        developerOptionsUnlocked: Boolean? = null,
        developerOptionsEnabled: Boolean? = null,
        developerOptionsDisabledAtMillis: Long? = null,
        homeBottomItems: List<String>? = null,
        homeStartPageKey: String? = null,
        weatherLocationStabilityRequiredHits: Int? = null,
        liveNotificationTemplateMode: String? = null,
        courseFeatureEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            val updated = settingsTransformApi.applyPreferenceUpdate(
                current = settings.value,
                showTomorrow = showTomorrow,
                dailySummary = dailySummary,
                liveCapsule = liveCapsule,
                pickupAggregation = pickupAggregation,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
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
                predictiveBackEnabled = predictiveBackEnabled,
                clipboardCodeRecognitionEnabled = clipboardCodeRecognitionEnabled,
                widgetThemeMode = widgetThemeMode,
                widgetBackgroundAlpha = widgetBackgroundAlpha,
                developerOptionsUnlocked = developerOptionsUnlocked,
                developerOptionsEnabled = developerOptionsEnabled,
                developerOptionsDisabledAtMillis = developerOptionsDisabledAtMillis,
                homeBottomItems = homeBottomItems,
                homeStartPageKey = homeStartPageKey,
                weatherLocationStabilityRequiredHits = weatherLocationStabilityRequiredHits,
                liveNotificationTemplateMode = liveNotificationTemplateMode,
                courseFeatureEnabled = courseFeatureEnabled
            )
            settingsOperationApi.updateSettings(updated)
        }
    }

    fun unlockDeveloperOptions() {
        updatePreference(
            developerOptionsUnlocked = true,
            developerOptionsDisabledAtMillis = System.currentTimeMillis()
        )
    }

    fun setDeveloperOptionsEnabled(enabled: Boolean) {
        updatePreference(
            developerOptionsEnabled = enabled,
            developerOptionsDisabledAtMillis = if (enabled) 0L else System.currentTimeMillis()
        )
    }

    fun expireDeveloperOptionsUnlock() {
        updatePreference(
            developerOptionsUnlocked = false,
            developerOptionsEnabled = false,
            developerOptionsDisabledAtMillis = 0L
        )
    }

    fun updateWeatherSettings(
        enabled: Boolean,
        provider: String,
        apiUrl: String,
        apiKey: String,
        refreshInterval: Int,
        showInFloating: Boolean,
        locationMode: String,
        manualLocationId: String,
        manualLocationName: String,
        manualAdm1: String,
        manualAdm2: String,
        manualCountry: String,
        manualLat: Double,
        manualLon: Double,
        warningEnabled: Boolean,
        riskWarningEnabled: Boolean,
        warningLookaheadHours: Int,
        floatingWeatherForecastRange: Int
    ) {
        settingsOperationApi.updateSettings(
            settings.value.copy(
                weatherEnabled = enabled,
                weatherProvider = provider,
                weatherApiUrl = apiUrl,
                weatherApiKey = apiKey,
                weatherCity = manualLocationName,
                weatherLocationMode = locationMode,
                weatherManualLocationId = manualLocationId,
                weatherManualLocationName = manualLocationName,
                weatherManualAdm1 = manualAdm1,
                weatherManualAdm2 = manualAdm2,
                weatherManualCountry = manualCountry,
                weatherManualLat = manualLat,
                weatherManualLon = manualLon,
                weatherWarningEnabled = warningEnabled,
                weatherRiskWarningEnabled = riskWarningEnabled,
                weatherWarningLookaheadHours = warningLookaheadHours,
                weatherRefreshInterval = refreshInterval,
                showWeatherInFloating = showInFloating,
                floatingWeatherForecastRange = floatingWeatherForecastRange
            )
        )
    }

    // 更新截图延迟
    fun updateScreenshotDelay(delay: Long) {
        val normalizedDelay = MySettings.normalizeScreenshotDelayMs(delay)
        val current = settings.value
        if (current.screenshotDelayMs != normalizedDelay) {
            viewModelScope.launch {
                settingsOperationApi.updateSettings(current.copy(screenshotDelayMs = normalizedDelay))
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

    suspend fun exportBackupData(options: AppBackupOptions): String {
        return backupCenter.exportBackupData(options)
    }

    suspend fun exportBackupZip(uri: Uri, options: AppBackupOptions) {
        backupCenter.exportBackupZip(uri, options)
    }

    suspend fun importEventsData(jsonString: String): Result<ImportResult> {
        return backupCenter.importEventsData(jsonString)
    }

    suspend fun importBackupJson(jsonString: String, options: AppBackupOptions): Result<AppBackupImportResult> {
        val result = backupCenter.importBackupJson(jsonString, options)
        if (result.isSuccess) scheduleCenter.refreshAll()
        return result
    }

    suspend fun importBackupZip(uri: Uri, options: AppBackupOptions): Result<AppBackupImportResult> {
        val result = backupCenter.importBackupZip(uri, options)
        if (result.isSuccess) scheduleCenter.refreshAll()
        return result
    }

    fun getEventsCount(): Int = scheduleCenter.getEventsCount()
    fun getTotalEventsCount(): Int = scheduleCenter.getTotalEventsCount()
    fun getAttachmentCount(): Int = backupCenter.getAttachmentCount()
    fun estimateAttachmentBytes(): Long = backupCenter.estimateAttachmentBytes()
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

    fun migrateLegacyLogs(onResult: (List<String>, String) -> Unit) {
        viewModelScope.launch {
            val result = diagnosticLogCenter.migrateLegacyLogs()
            onResult(result, diagnosticLogCenter.logDirectoryHint())
        }
    }

    fun exportDiagnosticLogs(minutes: Int?, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(diagnosticLogCenter.exportLogBundle(minutes))
        }
    }

    fun migrateLegacyNotes(onResult: (LegacyNoteMigrationResult) -> Unit) {
        viewModelScope.launch {
            onResult(legacyNoteMigrationCenter.migrateLegacyNotes(force = true))
        }
    }

    fun cleanLegacyNotes(onResult: (LegacyNoteMigrationResult) -> Unit) {
        viewModelScope.launch {
            onResult(legacyNoteMigrationCenter.cleanLegacyNoteEvents())
        }
    }

    fun cleanDuplicateEvents(onResult: (DuplicateEventCleanupResult) -> Unit) {
        viewModelScope.launch {
            val result = duplicateEventCleanupCenter.cleanupExactDuplicates()
            scheduleCenter.refreshAll()
            onResult(result)
        }
    }
}
