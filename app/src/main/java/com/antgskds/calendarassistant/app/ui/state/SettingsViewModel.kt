package com.antgskds.calendarassistant.app.ui.state

import android.content.Context
import android.net.Uri
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.api.model.CalendarSyncManager
import com.antgskds.calendarassistant.feature.schedule.api.model.CalendarManager
import com.antgskds.calendarassistant.feature.schedule.domain.model.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.feature.backup.application.BackupCoordinator
import com.antgskds.calendarassistant.feature.cloudsync.application.WebDavConnectionCoordinator
import com.antgskds.calendarassistant.feature.cloudsync.application.WebDavSyncV2Coordinator
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionInput
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionTestResult
import com.antgskds.calendarassistant.feature.settings.diagnostics.application.DiagnosticLogExporter
import com.antgskds.calendarassistant.feature.schedule.data.maintenance.DuplicateEventCleaner
import com.antgskds.calendarassistant.feature.schedule.data.maintenance.DuplicateEventCleanupResult
import com.antgskds.calendarassistant.feature.backup.courseimport.ParsedCourseImport
import com.antgskds.calendarassistant.feature.schedule.application.ScheduleFacade
import com.antgskds.calendarassistant.feature.schedule.application.sync.CalendarSyncService
import com.antgskds.calendarassistant.feature.backup.courseimport.ImportMode
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.shared.operation.SettingsOperationApi
import com.antgskds.calendarassistant.feature.note.data.migration.LegacyNoteMigrator
import com.antgskds.calendarassistant.feature.note.data.migration.LegacyNoteMigrationResult
import com.antgskds.calendarassistant.shared.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.shared.query.SettingsTransformApi
import com.antgskds.calendarassistant.feature.backup.data.model.ImportResult
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupOptions
import com.antgskds.calendarassistant.feature.settings.data.model.DEFAULT_EVENT_COLOR_PALETTE_HEX
import com.antgskds.calendarassistant.feature.settings.data.model.FloatingBallGestureAction
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.sanitizeEventColorPaletteHex
import com.antgskds.calendarassistant.feature.appearance.domain.AppBackgroundImageStore
import com.antgskds.calendarassistant.app.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.app.ui.theme.material.normalizeThemeHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class SettingsViewModel(
    private val appContext: Context,
    private val scheduleCenter: ScheduleFacade,
    private val backupCenter: BackupCoordinator,
    private val syncCenter: CalendarSyncService,
    private val diagnosticLogCenter: DiagnosticLogExporter,
    private val settingsOperationApi: SettingsOperationApi,
    private val settingsQueryApi: SettingsQueryApi,
    private val settingsTransformApi: SettingsTransformApi,
    private val scheduleInsightsQueryApi: ScheduleInsightsQueryApi,
    private val legacyNoteMigrationCenter: LegacyNoteMigrator,
    private val duplicateEventCleanupCenter: DuplicateEventCleaner,
    private val webDavConnectionCenter: WebDavConnectionCoordinator,
    private val webDavSyncV2Center: WebDavSyncV2Coordinator,
) : ViewModel() {
    private val backgroundImageStore = AppBackgroundImageStore(appContext)

    // 直接观察 QueryApi 的数据源
    val settings = settingsQueryApi.settings
    val webDavSyncStatus = webDavSyncV2Center.status

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

    fun updateAgentAccessOptions(
        accessEnabled: Boolean? = null,
        thirdPartyAccessEnabled: Boolean? = null,
        connectionManagementEnabled: Boolean? = null,
    ) {
        val current = settings.value
        settingsOperationApi.updateSettings(
            current.copy(
                agentApiEnabled = accessEnabled ?: current.agentApiEnabled,
                agentThirdPartyAccessEnabled = thirdPartyAccessEnabled
                    ?: current.agentThirdPartyAccessEnabled,
                agentConnectionManagementEnabled = connectionManagementEnabled
                    ?: current.agentConnectionManagementEnabled,
            )
        )
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
    fun updateAccountingMessages(enabled: Boolean) {
        viewModelScope.launch {
            val allowed = !enabled || (settings.value.automaticAccountingEnabled &&
                com.antgskds.calendarassistant.platform.receiver.AccountingMessageAccessPolicy.permissionsGranted(appContext))
            if (!allowed) return@launch
            settingsOperationApi.updateSettings(settings.value.copy(accountingMessagesEnabled = enabled))
            (appContext.applicationContext as? com.antgskds.calendarassistant.App)?.refreshSmsObserver()
            if (enabled) com.antgskds.calendarassistant.platform.receiver.SmsNotificationListenerService.rebind(appContext)
        }
    }

    fun updateAutomaticAccounting(enabled: Boolean) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(automaticAccountingEnabled = enabled,
                accountingMessagesEnabled = enabled && settings.value.accountingMessagesEnabled))
            (appContext.applicationContext as? com.antgskds.calendarassistant.App)?.refreshSmsObserver()
        }
    }

    // 【修改】增加了 pickupAggregation、advanceReminder 和 autoArchive 参数
    fun updatePreference(
        showTomorrow: Boolean? = null,
        dailySummary: Boolean? = null,
        dailySummaryMorningMinuteOfDay: Int? = null,
        dailySummaryEveningMinuteOfDay: Int? = null,
        liveCapsule: Boolean? = null,
        braceletModeEnabled: Boolean? = null,
        pickupAggregation: Boolean? = null,
        hapticFeedbackEnabled: Boolean? = null,
        edgeBarEnabled: Boolean? = null,
        networkSpeedCapsule: Boolean? = null,
        floatingWindow: Boolean? = null,
        advanceReminderEnabled: Boolean? = null,
        advanceReminderMinutes: Int? = null,
        transitAutoCheckInEnabled: Boolean? = null,
        transitAutoCheckInMinutes: Int? = null,
        autoArchive: Boolean? = null,
        recognitionMode: Int? = null,
        defaultEventDurationMinutes: Int? = null,
        useMultimodalAi: Boolean? = null,
        disableThinking: Boolean? = null,
        localSemanticEnabled: Boolean? = null,
        selectedLocalModelId: String? = null,
        floatingEventRange: Int? = null,
        floatingExpandSide: String? = null,
        quickMemoRecordingDisplayMode: Int? = null,
        floatingEntryStyle: Int? = null,
        floatingBallEnabled: Boolean? = null,
        floatingBallXPercent: Float? = null,
        floatingBallYPercent: Float? = null,
        floatingBallSizeDp: Int? = null,
        floatingBallAlpha: Float? = null,
        floatingBallSingleTapAction: Int? = null,
        floatingBallDoubleTapAction: Int? = null,
        floatingBallLongPressAction: Int? = null,
        edgeBarSingleTapAction: Int? = null,
        edgeBarDoubleTapAction: Int? = null,
        edgeBarLongPressAction: Int? = null,
        volumeUpLongPressEnabled: Boolean? = null,
        volumeUpLongPressAction: Int? = null,
        smsMonitoring: Boolean? = null,
        forceInstantCodeTimeToNow: Boolean? = null,
        predictiveBackEnabled: Boolean? = null,
        clipboardCodeRecognitionEnabled: Boolean? = null,
        voiceInputEnabled: Boolean? = null,
        floatingVoiceLongPressEnabled: Boolean? = null,
        floatingTextQuickMemoAutoPinEnabled: Boolean? = null,
        voiceQuickMemoAutoPinEnabled: Boolean? = null,
        appBackgroundCardAlphaPercent: Int? = null,
        widgetThemeMode: Int? = null,
        widgetBackgroundAlpha: Float? = null,
        developerOptionsUnlocked: Boolean? = null,
        developerOptionsEnabled: Boolean? = null,
        developerOptionsDisabledAtMillis: Long? = null,
        developerSimulateRootEnabled: Boolean? = null,
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
                dailySummaryMorningMinuteOfDay = dailySummaryMorningMinuteOfDay,
                dailySummaryEveningMinuteOfDay = dailySummaryEveningMinuteOfDay,
                liveCapsule = liveCapsule,
                braceletModeEnabled = braceletModeEnabled,
                pickupAggregation = pickupAggregation,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                edgeBarEnabled = edgeBarEnabled,
                networkSpeedCapsule = networkSpeedCapsule,
                floatingWindow = floatingWindow,
                advanceReminderEnabled = advanceReminderEnabled,
                advanceReminderMinutes = advanceReminderMinutes,
                transitAutoCheckInEnabled = transitAutoCheckInEnabled,
                transitAutoCheckInMinutes = transitAutoCheckInMinutes,
                autoArchive = autoArchive,
                recognitionMode = recognitionMode,
                defaultEventDurationMinutes = defaultEventDurationMinutes,
                useMultimodalAi = useMultimodalAi,
                disableThinking = disableThinking,
                localSemanticEnabled = localSemanticEnabled,
                selectedLocalModelId = selectedLocalModelId,
                floatingEventRange = floatingEventRange,
                floatingExpandSide = floatingExpandSide,
                quickMemoRecordingDisplayMode = quickMemoRecordingDisplayMode,
                floatingEntryStyle = floatingEntryStyle,
                floatingBallEnabled = floatingBallEnabled,
                floatingBallXPercent = floatingBallXPercent,
                floatingBallYPercent = floatingBallYPercent,
                floatingBallSizeDp = floatingBallSizeDp,
                floatingBallAlpha = floatingBallAlpha,
                floatingBallSingleTapAction = floatingBallSingleTapAction,
                floatingBallDoubleTapAction = floatingBallDoubleTapAction,
                floatingBallLongPressAction = floatingBallLongPressAction,
                edgeBarSingleTapAction = edgeBarSingleTapAction,
                edgeBarDoubleTapAction = edgeBarDoubleTapAction,
                edgeBarLongPressAction = edgeBarLongPressAction,
                volumeUpLongPressEnabled = volumeUpLongPressEnabled,
                volumeUpLongPressAction = volumeUpLongPressAction,
                smsMonitoring = smsMonitoring,
                forceInstantCodeTimeToNow = forceInstantCodeTimeToNow,
                predictiveBackEnabled = predictiveBackEnabled,
                clipboardCodeRecognitionEnabled = clipboardCodeRecognitionEnabled,
                voiceInputEnabled = voiceInputEnabled,
                floatingVoiceLongPressEnabled = floatingVoiceLongPressEnabled,
                floatingTextQuickMemoAutoPinEnabled = floatingTextQuickMemoAutoPinEnabled,
                voiceQuickMemoAutoPinEnabled = voiceQuickMemoAutoPinEnabled,
                appBackgroundCardAlphaPercent = appBackgroundCardAlphaPercent,
                widgetThemeMode = widgetThemeMode,
                widgetBackgroundAlpha = widgetBackgroundAlpha,
                developerOptionsUnlocked = developerOptionsUnlocked,
                developerOptionsEnabled = developerOptionsEnabled,
                developerOptionsDisabledAtMillis = developerOptionsDisabledAtMillis,
                developerSimulateRootEnabled = developerSimulateRootEnabled,
                homeBottomItems = homeBottomItems,
                homeStartPageKey = homeStartPageKey,
                weatherLocationStabilityRequiredHits = weatherLocationStabilityRequiredHits,
                liveNotificationTemplateMode = liveNotificationTemplateMode,
                courseFeatureEnabled = courseFeatureEnabled
            )
            settingsOperationApi.updateSettings(updated)
        }
    }

    /**
     * 更新列表排序方向开关（首页 / 全部日程 / 悬浮窗 / 归档）。
     * 直接走 copy，不经 transform 层；传 null 表示该项不变。
     */
    fun updateListSortOrder(
        homeListReverseOrder: Boolean? = null,
        allEventsListReverseOrder: Boolean? = null,
        homeAgendaReverseOrder: Boolean? = null,
        floatingListReverseOrder: Boolean? = null,
        archivesListReverseOrder: Boolean? = null
    ) {
        viewModelScope.launch {
            val current = settings.value
            settingsOperationApi.updateSettings(
                current.copy(
                    homeListReverseOrder = homeListReverseOrder ?: current.homeListReverseOrder,
                    allEventsListReverseOrder = allEventsListReverseOrder ?: current.allEventsListReverseOrder,
                    homeAgendaReverseOrder = homeAgendaReverseOrder ?: current.homeAgendaReverseOrder,
                    floatingListReverseOrder = floatingListReverseOrder ?: current.floatingListReverseOrder,
                    archivesListReverseOrder = archivesListReverseOrder ?: current.archivesListReverseOrder
                )
            )
        }
    }

    fun updateFloatingDragTextOptions(
        includeTitle: Boolean? = null,
        includeTime: Boolean? = null,
        includeLocation: Boolean? = null,
        includeDescription: Boolean? = null
    ) {
        viewModelScope.launch {
            val current = settings.value
            settingsOperationApi.updateSettings(
                current.copy(
                    floatingDragTextIncludeTitle = includeTitle ?: current.floatingDragTextIncludeTitle,
                    floatingDragTextIncludeTime = includeTime ?: current.floatingDragTextIncludeTime,
                    floatingDragTextIncludeLocation = includeLocation ?: current.floatingDragTextIncludeLocation,
                    floatingDragTextIncludeDescription = includeDescription ?: current.floatingDragTextIncludeDescription
                )
            )
        }
    }

    fun updateFloatingDragHotZonePercent(percent: Int) {
        viewModelScope.launch {
            val current = settings.value
            settingsOperationApi.updateSettings(
                current.copy(
                    floatingDragHotZonePercent = com.antgskds.calendarassistant.feature.settings.data.model.MySettings
                        .normalizeFloatingDragHotZonePercent(percent)
                )
            )
        }
    }

    fun updateDailySummaryTimes(
        morningMinuteOfDay: Int? = null,
        eveningMinuteOfDay: Int? = null,
        onUpdated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(
                dailySummaryMorningMinuteOfDay = morningMinuteOfDay
                    ?.let(MySettings::normalizeDailySummaryMinuteOfDay)
                    ?: current.dailySummaryMorningMinuteOfDay,
                dailySummaryEveningMinuteOfDay = eveningMinuteOfDay
                    ?.let(MySettings::normalizeDailySummaryMinuteOfDay)
                    ?: current.dailySummaryEveningMinuteOfDay
            )
            settingsOperationApi.updateSettings(updated)
            onUpdated()
        }
    }

    /**
     * 把列表排序方向恢复为出厂默认（首页/全部=正序，悬浮窗/归档=倒序）。
     * 取 MySettings() 的默认值，避免硬编码漂移。
     */
    fun resetListSortOrderToDefault() {
        viewModelScope.launch {
            val current = settings.value
            val defaults = MySettings()
            settingsOperationApi.updateSettings(
                current.copy(
                    homeListReverseOrder = defaults.homeListReverseOrder,
                    allEventsListReverseOrder = defaults.allEventsListReverseOrder,
                    homeAgendaReverseOrder = defaults.homeAgendaReverseOrder,
                    floatingListReverseOrder = defaults.floatingListReverseOrder,
                    archivesListReverseOrder = defaults.archivesListReverseOrder
                )
            )
        }
    }

    fun updateEventColorPalette(hexColors: List<String>) {
        val sanitized = sanitizeEventColorPaletteHex(hexColors)
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(eventColorPaletteHex = sanitized))
        }
    }

    fun resetEventColorPalette() {
        updateEventColorPalette(DEFAULT_EVENT_COLOR_PALETTE_HEX)
    }

    fun unlockDeveloperOptions() {
        updatePreference(
            developerOptionsUnlocked = true,
            developerOptionsDisabledAtMillis = System.currentTimeMillis()
        )
    }

    fun setDeveloperOptionsEnabled(enabled: Boolean) = viewModelScope.launch {
        val current = settings.value
        settingsOperationApi.updateSettings(
            current.copy(
                developerOptionsEnabled = enabled,
                developerOptionsDisabledAtMillis = if (enabled) 0L else System.currentTimeMillis(),
                developerDemoModeEnabled = current.developerDemoModeEnabled && enabled,
            )
        )
    }

    fun setQuickMemoPinnedFixedTitleEnabled(enabled: Boolean, onUpdated: () -> Unit = {}) = viewModelScope.launch {
        settingsOperationApi.updateSettings(
            settings.value.copy(quickMemoPinnedFixedTitleEnabled = enabled)
        )
        onUpdated()
    }

    fun setAutoRecordLogs(enabled: Boolean) = viewModelScope.launch {
        settingsOperationApi.updateSettings(settings.value.copy(autoRecordLogs = enabled))
    }

    fun setCourseModuleEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsOperationApi.updateSettings(settings.value.copy(courseModuleEnabled = enabled))
    }

    fun setHomeAgendaOnlyScheduled(enabled: Boolean) = viewModelScope.launch {
        settingsOperationApi.updateSettings(settings.value.copy(homeAgendaOnlyScheduled = enabled))
    }

    fun setDemoModeEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsOperationApi.updateSettings(settings.value.copy(developerDemoModeEnabled = enabled))
    }

    fun setScheduleIngestDedupEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsOperationApi.updateSettings(
            settings.value.copy(scheduleIngestDedupEnabled = enabled)
        )
    }

    fun expireDeveloperOptionsUnlock() = viewModelScope.launch {
        settingsOperationApi.updateSettings(
            settings.value.copy(
                developerOptionsUnlocked = false,
                developerOptionsEnabled = false,
                developerOptionsDisabledAtMillis = 0L,
                developerDemoModeEnabled = false,
            )
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
        val current = settings.value
        val normalizedProvider = if (provider == "caiyun") "caiyun" else "qweather"
        val previousQWeatherUrl = current.weatherQWeatherApiUrl.ifBlank {
            current.weatherApiUrl.takeIf { current.weatherProvider != "caiyun" }.orEmpty()
        }
        val previousQWeatherKey = current.weatherQWeatherApiKey.ifBlank {
            current.weatherApiKey.takeIf { current.weatherProvider != "caiyun" }.orEmpty()
        }
        val previousCaiyunUrl = current.weatherCaiyunApiUrl.ifBlank {
            current.weatherApiUrl.takeIf { current.weatherProvider == "caiyun" }.orEmpty()
        }
        val previousCaiyunToken = current.weatherCaiyunToken.ifBlank {
            current.weatherApiKey.takeIf { current.weatherProvider == "caiyun" }.orEmpty()
        }
        settingsOperationApi.updateSettings(
            current.copy(
                weatherEnabled = enabled,
                weatherProvider = normalizedProvider,
                weatherApiUrl = apiUrl,
                weatherApiKey = apiKey,
                weatherQWeatherApiUrl = if (normalizedProvider == "qweather") apiUrl else previousQWeatherUrl,
                weatherQWeatherApiKey = if (normalizedProvider == "qweather") apiKey else previousQWeatherKey,
                weatherCaiyunApiUrl = if (normalizedProvider == "caiyun") apiUrl else previousCaiyunUrl,
                weatherCaiyunToken = if (normalizedProvider == "caiyun") apiKey else previousCaiyunToken,
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
            settingsOperationApi.updateSettings(
                settings.value.copy(
                    themeColorScheme = scheme,
                    appBackgroundImageColorEnabled = false
                )
            )
        }
    }

    fun updateCustomThemeColorHex(hex: String) {
        val normalized = normalizeThemeHexColor(hex) ?: return
        viewModelScope.launch {
            settingsOperationApi.updateSettings(
                settings.value.copy(
                    themeColorScheme = ThemeColorScheme.CUSTOM.name,
                    customThemeColorHex = normalized,
                    appBackgroundImageColorEnabled = false
                )
            )
        }
    }

    fun importAppBackground(
        uri: Uri,
        imageScale: Float = MySettings.APP_BACKGROUND_IMAGE_SCALE_DEFAULT,
        imageOffsetX: Float = 0f,
        imageOffsetY: Float = 0f,
        averageLuminance: Float? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val current = settings.value
            runCatching {
                withContext(Dispatchers.IO) {
                    backgroundImageStore.importBackground(uri, current.appBackgroundImagePath)
                }
            }.onSuccess { result ->
                val resetImageColor = current.appBackgroundImageColorEnabled
                settingsOperationApi.updateSettings(
                    current.copy(
                        appBackgroundEnabled = true,
                        appBackgroundImagePath = result.path,
                        appBackgroundImageScale = MySettings.normalizeAppBackgroundImageScale(imageScale),
                        appBackgroundImageOffsetX = MySettings.normalizeAppBackgroundImageOffset(imageOffsetX),
                        appBackgroundImageOffsetY = MySettings.normalizeAppBackgroundImageOffset(imageOffsetY),
                        appBackgroundSeedColorHex = "",
                        appBackgroundImageColorEnabled = false,
                        appBackgroundScrimAlphaPercent = 0,
                        appBackgroundAverageLuminance = averageLuminance
                            ?.coerceIn(0f, 1f)
                            ?: result.averageLuminance,
                        themeColorScheme = if (resetImageColor) ThemeColorScheme.DEFAULT.name else current.themeColorScheme
                    )
                )
                onResult(true, "主界面壁纸已设置")
            }.onFailure { error ->
                onResult(false, error.message ?: "背景图片导入失败")
            }
        }
    }

    fun clearAppBackground() {
        viewModelScope.launch {
            val current = settings.value
            withContext(Dispatchers.IO) {
                backgroundImageStore.clearBackground(current.appBackgroundImagePath)
            }
            settingsOperationApi.updateSettings(
                current.copy(
                    appBackgroundEnabled = false,
                        appBackgroundImagePath = "",
                        appBackgroundImageScale = MySettings.APP_BACKGROUND_IMAGE_SCALE_DEFAULT,
                        appBackgroundImageOffsetX = 0f,
                        appBackgroundImageOffsetY = 0f,
                        appBackgroundSeedColorHex = "",
                        appBackgroundImageColorEnabled = false,
                        appBackgroundWallpaperBlurEnabled = false,
                        appBackgroundScrimAlphaPercent = 0,
                        appBackgroundAverageLuminance = -1f,
                        themeColorScheme = if (current.appBackgroundImageColorEnabled) ThemeColorScheme.DEFAULT.name else current.themeColorScheme
                    )
                )
        }
    }

    fun hasStoredWebDavPassword(): Boolean = webDavConnectionCenter.hasStoredPassword()

    fun hasStoredSyncPassphrase(): Boolean = webDavConnectionCenter.hasStoredSyncPassphrase()

    fun readStoredWebDavPassword(): String = webDavConnectionCenter.readStoredPassword()

    fun readStoredSyncPassphrase(): String = webDavConnectionCenter.readStoredSyncPassphrase()

    suspend fun testAndSaveWebDavConnection(input: WebDavConnectionInput): WebDavConnectionTestResult {
        return withContext(Dispatchers.IO) {
            webDavConnectionCenter.testAndSave(input)
        }
    }

    fun updateWebDavSyncOptions(
        enabled: Boolean? = null,
        wifiOnly: Boolean? = null,
        foregroundIntervalSeconds: Int? = null,
    ) {
        val current = settings.value
        settingsOperationApi.updateSettings(
            current.copy(
                webDavSyncEnabled = enabled ?: current.webDavSyncEnabled,
                webDavWifiOnly = wifiOnly ?: current.webDavWifiOnly,
                webDavForegroundSyncIntervalSeconds = foregroundIntervalSeconds
                    ?.coerceIn(1, 300)
                    ?: current.webDavForegroundSyncIntervalSeconds,
            )
        )
    }

    fun syncWebDavNow() {
        viewModelScope.launch(Dispatchers.IO) { webDavSyncV2Center.syncNow(force = true) }
    }

    fun updateAppBackgroundImageColorEnabled(enabled: Boolean, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val current = settings.value
            if (!enabled) {
                settingsOperationApi.updateSettings(
                    current.copy(
                        appBackgroundImageColorEnabled = false,
                        themeColorScheme = ThemeColorScheme.DEFAULT.name
                    )
                )
                onResult(true, "已切换为系统取色")
                return@launch
            }

            if (current.appBackgroundImagePath.isBlank()) {
                onResult(false, "请先选择背景图片")
                return@launch
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    current.appBackgroundSeedColorHex.takeIf { normalizeThemeHexColor(it) != null }
                        ?: backgroundImageStore.extractSeedColorHex(current.appBackgroundImagePath)
                }
            }.onSuccess { seedHex ->
                val normalized = normalizeThemeHexColor(seedHex) ?: "#6750A4"
                settingsOperationApi.updateSettings(
                    current.copy(
                        appBackgroundSeedColorHex = normalized,
                        appBackgroundImageColorEnabled = true,
                        themeColorScheme = ThemeColorScheme.CUSTOM.name,
                        customThemeColorHex = normalized
                    )
                )
                onResult(true, "已切换为图片取色")
            }.onFailure { error ->
                onResult(false, error.message ?: "图片取色失败")
            }
        }
    }

    fun updateAppBackgroundMiuiBlurTestEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsOperationApi.updateSettings(settings.value.copy(appBackgroundMiuiBlurTestEnabled = enabled))
        }
    }

    fun updateAppBackgroundWallpaperBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            settingsOperationApi.updateSettings(
                current.copy(appBackgroundWallpaperBlurEnabled = enabled && current.appBackgroundImagePath.isNotBlank())
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

    fun updateQuickMemoAutoStop(enabled: Boolean? = null, seconds: Int? = null) {
        viewModelScope.launch {
            val current = settings.value
            settingsOperationApi.updateSettings(
                current.copy(
                    quickMemoAutoStopEnabled = enabled ?: current.quickMemoAutoStopEnabled,
                    quickMemoAutoStopSeconds = seconds
                        ?.let(MySettings::normalizeQuickMemoAutoStopSeconds)
                        ?: current.quickMemoAutoStopSeconds
                )
            )
        }
    }

    fun updateUiScaleFactors(small: Float, medium: Float, large: Float, onUpdated: () -> Unit = {}) {
        viewModelScope.launch {
            val normalizedSmall = MySettings.normalizeUiScale(small)
            val normalizedMedium = MySettings.normalizeUiScale(medium).coerceAtLeast(normalizedSmall)
            val normalizedLarge = MySettings.normalizeUiScale(large).coerceAtLeast(normalizedMedium)
            settingsOperationApi.updateSettings(
                settings.value.copy(
                    uiScaleSmall = normalizedSmall,
                    uiScaleMedium = normalizedMedium,
                    uiScaleLarge = normalizedLarge
                )
            )
            onUpdated()
        }
    }

    fun resetUiScaleFactors(onUpdated: () -> Unit = {}) {
        updateUiScaleFactors(
            small = MySettings.UI_SCALE_SMALL_DEFAULT,
            medium = MySettings.UI_SCALE_MEDIUM_DEFAULT,
            large = MySettings.UI_SCALE_LARGE_DEFAULT,
            onUpdated = onUpdated
        )
    }

    fun updateEdgeBarSettings(
        enabled: Boolean? = null,
        side: String? = null,
        yPercent: Float? = null,
        widthDp: Int? = null,
        heightDp: Int? = null,
        alpha: Float? = null,
        singleTapAction: Int? = null,
        doubleTapAction: Int? = null,
        longPressAction: Int? = null
    ) {
        viewModelScope.launch {
            var current = settings.value
            if (enabled != null) current = current.copy(edgeBarEnabled = enabled)
            if (side != null) current = current.copy(edgeBarSide = side)
            if (yPercent != null) current = current.copy(edgeBarYPercent = yPercent)
            if (widthDp != null) current = current.copy(edgeBarWidthDp = widthDp)
            if (heightDp != null) current = current.copy(edgeBarHeightDp = heightDp)
            if (alpha != null) current = current.copy(edgeBarAlpha = alpha)
            if (singleTapAction != null) current = current.copy(edgeBarSingleTapAction = FloatingBallGestureAction.normalize(singleTapAction))
            if (doubleTapAction != null) current = current.copy(edgeBarDoubleTapAction = FloatingBallGestureAction.normalize(doubleTapAction))
            if (longPressAction != null) current = current.copy(edgeBarLongPressAction = FloatingBallGestureAction.normalize(longPressAction))
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
