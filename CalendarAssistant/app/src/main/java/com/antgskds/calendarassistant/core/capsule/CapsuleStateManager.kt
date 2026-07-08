package com.antgskds.calendarassistant.core.capsule

import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

import com.antgskds.calendarassistant.calendar.helpers.FLAG_ALL_DAY
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.util.FlymeUtils
import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.data.state.CapsuleType
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import com.antgskds.calendarassistant.service.capsule.CapsuleDispatcher
import com.antgskds.calendarassistant.service.capsule.CapsuleMessageComposer
import com.antgskds.calendarassistant.service.capsule.IconUtils
import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import com.antgskds.calendarassistant.feature.weather.domain.WeatherWarningText
import com.antgskds.calendarassistant.service.capsule.miui.MiuiIslandManager
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.platform.xposed.XposedModuleStatus
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow // ✅ 改用 StateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * 胶囊状态管理器 - 主动唤醒模式
 */
class CapsuleStateManager(
    private val scheduleCenter: ScheduleCenter,
    private val settingsQueryApi: SettingsQueryApi,
    private val appScope: CoroutineScope,
    private val context: Context
) {
    companion object {
        private const val TAG = "CapsuleStateManager"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        const val AGGREGATE_PICKUP_ID = "AGGREGATE_PICKUP"
        const val AGGREGATE_NOTIF_ID = 99999

        // 胶囊类型常量（值的事实源已迁至 data.state.CapsuleType，此处保留为别名向后兼容）
        const val TYPE_SCHEDULE = CapsuleType.SCHEDULE
        const val TYPE_PICKUP = CapsuleType.PICKUP
        const val TYPE_PICKUP_EXPIRED = CapsuleType.PICKUP_EXPIRED
        const val TYPE_NETWORK_SPEED = CapsuleType.NETWORK_SPEED
        const val TYPE_OCR_PROGRESS = CapsuleType.OCR_PROGRESS
        const val TYPE_OCR_RESULT = CapsuleType.OCR_RESULT
        const val TYPE_MODEL_LOADING = CapsuleType.MODEL_LOADING
        const val TYPE_WEATHER_ALERT = CapsuleType.WEATHER_ALERT
        const val TYPE_VOICE_TRANSCRIPTION = CapsuleType.VOICE_TRANSCRIPTION
        const val TYPE_TEXT_QUICK_MEMO = CapsuleType.TEXT_QUICK_MEMO
        const val TYPE_QUICK_MEMO_RECORDING = CapsuleType.QUICK_MEMO_RECORDING

        private const val OCR_PROGRESS_ID = "OCR_PROGRESS"
        private const val OCR_RESULT_ID = "OCR_RESULT"
        private const val MODEL_LOADING_ID = "MODEL_LOADING"
        private const val VOICE_TRANSCRIPTION_ID_PREFIX = "VOICE_TRANSCRIPTION_"
        private const val TEXT_QUICK_MEMO_ID_PREFIX = "TEXT_QUICK_MEMO_"
        private const val QUICK_MEMO_RECORDING_ID = "QUICK_MEMO_RECORDING"
        private const val WEATHER_CAPSULE_ID_PREFIX = "WEATHER_"
        private const val OCR_NOTIF_ID = 88886
        private const val MODEL_LOADING_NOTIF_ID = 88885
        private const val OCR_UPDATE_THROTTLE_MS = 600L
        private const val EVENT_TYPE_OCR_PROGRESS = "ocr_progress"
        private const val EVENT_TYPE_OCR_RESULT = "ocr_result"
        private const val EVENT_TYPE_MODEL_LOADING = "model_loading"
        private const val EVENT_TYPE_VOICE_TRANSCRIPTION = "voice_transcription"
        private const val EVENT_TYPE_TEXT_QUICK_MEMO = "text_quick_memo"
        private const val EVENT_TYPE_QUICK_MEMO_RECORDING = "quick_memo_recording"
        private const val EVENT_TYPE_WEATHER_ALERT = "weather_alert"
        private const val EVENT_TYPE_WEATHER_RISK = "weather_risk"
        private val DEFAULT_PICKUP_CAPSULE_COLOR = android.graphics.Color.rgb(180, 195, 161)

        // ✅ 核心修复 1：改用 MutableStateFlow(0)
        // StateFlow 总是持有最新值，保证 combine 永远不会因为等待信号而卡死或丢状态
        private val forceRefreshTrigger = MutableStateFlow(0)

        // 网速胶囊的动态状态（每次更新都触发状态重新计算）
        private val networkSpeedState = MutableStateFlow<NetworkSpeedMonitor.NetworkSpeed?>(null)
    }

    private data class OcrCapsuleState(
        val id: String,
        val notifId: Int,
        val type: Int,
        val eventType: String,
        val title: String,
        val content: String,
        val description: String,
        val color: Int,
        val startMillis: Long,
        val endMillis: Long,
        val display: CapsuleDisplayModel,
        val expiresAt: Long?,
        val locationName: String = "",
        val weatherAlert: WeatherAlertData? = null,
        val weatherRisk: WeatherRiskAlert? = null
    )

    private data class TransientCapsules(
        val ocr: OcrCapsuleState?,
        val modelLoading: OcrCapsuleState?,
        val weather: List<OcrCapsuleState>,
        val voiceTranscription: OcrCapsuleState?,
        val textQuickMemo: OcrCapsuleState?,
        val quickMemoRecording: OcrCapsuleState?
    )

    private val ocrCapsuleState = MutableStateFlow<OcrCapsuleState?>(null)
    private val modelLoadingCapsuleState = MutableStateFlow<OcrCapsuleState?>(null)
    private val weatherCapsuleState = MutableStateFlow<List<OcrCapsuleState>>(emptyList())
    private val voiceTranscriptionCapsuleState = MutableStateFlow<OcrCapsuleState?>(null)
    private val textQuickMemoCapsuleState = MutableStateFlow<OcrCapsuleState?>(null)
    private val quickMemoRecordingCapsuleState = MutableStateFlow<OcrCapsuleState?>(null)
    private var ocrAutoClearJob: Job? = null
    private var modelLoadingAutoClearJob: Job? = null
    private var voiceTranscriptionAutoClearJob: Job? = null
    private var textQuickMemoAutoClearJob: Job? = null
    private val weatherAutoClearJobs = ConcurrentHashMap<String, Job>()
    private var lastOcrUpdateAt = 0L

    // 通知发布站（胶囊「一条线」的发布分支）：CapsuleStateManager 只算状态，发布交给它。
    private val dispatcher = CapsuleDispatcher(context, appScope, settingsQueryApi) { uiState.value }

    /**
     * 【修复问题3】强制刷新胶囊状态
     * ✅ 改为同步执行，确保调用后立即生效
     */
    fun forceRefresh() {
        // ✅ 直接在调用线程更新值，不使用协程
        val newValue = forceRefreshTrigger.value + 1
        forceRefreshTrigger.value = newValue
        Log.d(TAG, "forceRefresh: 主动触发胶囊状态刷新 (Counter: $newValue)")
    }

    fun updateNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed?) {
        networkSpeedState.value = speed
    }

    fun showOcrProgress(title: String, content: String) {
        val now = System.currentTimeMillis()
        val display = CapsuleMessageComposer.composeOcrProgress(title, content)
        updateOcrCapsule(
            OcrCapsuleState(
                id = OCR_PROGRESS_ID,
                notifId = OCR_NOTIF_ID,
                type = TYPE_OCR_PROGRESS,
                eventType = EVENT_TYPE_OCR_PROGRESS,
                title = display.shortText,
                content = display.secondaryText ?: content,
                description = "",
                color = android.graphics.Color.parseColor("#2979FF"),
                startMillis = now,
                endMillis = now + ocrProgressTimeoutMs(),
                display = display,
                expiresAt = now + ocrProgressTimeoutMs()
            ),
            ocrProgressTimeoutMs()
        )
    }

    private fun weatherCapsuleTimeoutMs(): Long =
        settingsQueryApi.settings.value.weatherNotificationTimeoutMs.toLong().coerceIn(1000L, 1_800_000L)

    private fun ocrResultTimeoutMs(): Long =
        settingsQueryApi.settings.value.resultNotificationTimeoutMs.toLong().coerceIn(1000L, 120_000L)

    private fun ocrProgressTimeoutMs(): Long =
        settingsQueryApi.settings.value.ocrProgressTimeoutMs.toLong().coerceIn(1000L, 1_800_000L)

    private fun modelLoadingTimeoutMs(): Long =
        settingsQueryApi.settings.value.modelLoadingTimeoutMs.toLong().coerceIn(1000L, 1_800_000L)

    private fun quickMemoCapsuleTimeoutMs(): Long {
        val durationMinutes = settingsQueryApi.settings.value.defaultEventDurationMinutes
        if (durationMinutes == -1) {
            val endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.of(23, 59))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            return (endOfDay - System.currentTimeMillis()).coerceIn(60_000L, 24 * 60 * 60_000L)
        }
        return durationMinutes.toLong().coerceIn(1L, 24 * 60L) * 60_000L
    }

    fun showOcrResult(
        title: String,
        content: String,
        durationMs: Long = ocrResultTimeoutMs(),
        actions: List<CapsuleActionSpec> = emptyList()
    ) {
        val now = System.currentTimeMillis()
        val display = CapsuleMessageComposer.composeOcrResult(title, content).copy(actions = actions)
        updateOcrCapsule(
            OcrCapsuleState(
                id = OCR_RESULT_ID,
                notifId = OCR_NOTIF_ID,
                type = TYPE_OCR_RESULT,
                eventType = EVENT_TYPE_OCR_RESULT,
                title = display.shortText,
                content = display.secondaryText ?: content,
                description = "",
                color = android.graphics.Color.parseColor("#4CAF50"),
                startMillis = now,
                endMillis = now + durationMs,
                display = display,
                expiresAt = now + durationMs
            ),
            durationMs
        )
    }

    fun clearOcrCapsule() {
        ocrAutoClearJob?.cancel()
        ocrAutoClearJob = null
        ocrCapsuleState.value = null
    }

    fun showVoiceTranscription(memoId: Long, title: String, durationMs: Long = 0L) {
        val cleanTitle = title.trim().takeIf { it.isNotEmpty() } ?: return
        val effectiveDurationMs = if (durationMs > 0L) durationMs else quickMemoCapsuleTimeoutMs()
        val now = System.currentTimeMillis()
        val display = CapsuleMessageComposer.composeVoiceTranscription(cleanTitle)
        voiceTranscriptionCapsuleState.value = OcrCapsuleState(
            id = "$VOICE_TRANSCRIPTION_ID_PREFIX$memoId",
            notifId = NotificationIds.quickMemoVoiceTranscription(memoId),
            type = TYPE_VOICE_TRANSCRIPTION,
            eventType = EVENT_TYPE_VOICE_TRANSCRIPTION,
            title = display.primaryText,
            content = cleanTitle,
            description = cleanTitle,
            color = android.graphics.Color.parseColor("#2979FF"),
            startMillis = now,
            endMillis = now + effectiveDurationMs,
            display = display,
            expiresAt = now + effectiveDurationMs
        )
        scheduleVoiceTranscriptionAutoClear(effectiveDurationMs)
    }

    fun showTextQuickMemo(memoId: Long, title: String, durationMs: Long = 0L) {
        val cleanTitle = title.trim().takeIf { it.isNotEmpty() } ?: return
        val effectiveDurationMs = if (durationMs > 0L) durationMs else quickMemoCapsuleTimeoutMs()
        val now = System.currentTimeMillis()
        val display = CapsuleMessageComposer.composeTextQuickMemo(cleanTitle, memoId)
        textQuickMemoCapsuleState.value = OcrCapsuleState(
            id = "$TEXT_QUICK_MEMO_ID_PREFIX$memoId",
            notifId = NotificationIds.quickMemoText(memoId),
            type = TYPE_TEXT_QUICK_MEMO,
            eventType = EVENT_TYPE_TEXT_QUICK_MEMO,
            title = display.primaryText,
            content = cleanTitle,
            description = cleanTitle,
            color = android.graphics.Color.parseColor("#7C4DFF"),
            startMillis = now,
            endMillis = now + effectiveDurationMs,
            display = display,
            expiresAt = now + effectiveDurationMs
        )
        scheduleTextQuickMemoAutoClear(effectiveDurationMs)
    }

    fun clearVoiceTranscription() {
        voiceTranscriptionAutoClearJob?.cancel()
        voiceTranscriptionAutoClearJob = null
        voiceTranscriptionCapsuleState.value = null
    }

    fun clearTextQuickMemo() {
        textQuickMemoAutoClearJob?.cancel()
        textQuickMemoAutoClearJob = null
        textQuickMemoCapsuleState.value = null
    }

    fun showQuickMemoRecording(title: String, content: String = "松开保存") {
        val cleanTitle = title.trim().takeIf { it.isNotEmpty() } ?: "录音中：00:00"
        val cleanContent = content.trim().takeIf { it.isNotEmpty() } ?: "松开保存"
        val now = System.currentTimeMillis()
        val display = CapsuleMessageComposer.composeQuickMemoRecording(cleanTitle, cleanContent)
        quickMemoRecordingCapsuleState.value = OcrCapsuleState(
            id = QUICK_MEMO_RECORDING_ID,
            notifId = NotificationIds.QUICK_MEMO_RECORDING_CAPSULE,
            type = TYPE_QUICK_MEMO_RECORDING,
            eventType = EVENT_TYPE_QUICK_MEMO_RECORDING,
            title = display.primaryText,
            content = cleanContent,
            description = cleanContent,
            color = android.graphics.Color.parseColor("#7C4DFF"),
            startMillis = now,
            endMillis = now + 60_000L,
            display = display,
            expiresAt = null
        )
    }

    fun clearQuickMemoRecording() {
        quickMemoRecordingCapsuleState.value = null
    }

    fun showModelLoading(title: String, content: String) {
        val now = System.currentTimeMillis()
        val display = CapsuleMessageComposer.composeModelLoading(title, content)
        modelLoadingCapsuleState.value = OcrCapsuleState(
            id = MODEL_LOADING_ID,
            notifId = MODEL_LOADING_NOTIF_ID,
            type = TYPE_MODEL_LOADING,
            eventType = EVENT_TYPE_MODEL_LOADING,
            title = display.shortText,
            content = display.secondaryText ?: content,
            description = "",
            color = android.graphics.Color.parseColor("#2979FF"),
            startMillis = now,
            endMillis = now + modelLoadingTimeoutMs(),
            display = display,
            expiresAt = now + modelLoadingTimeoutMs()
        )
        scheduleModelLoadingAutoClear(modelLoadingTimeoutMs())
    }

    fun clearModelLoading() {
        modelLoadingAutoClearJob?.cancel()
        modelLoadingAutoClearJob = null
        modelLoadingCapsuleState.value = null
    }

    fun showWeatherAlert(locationName: String, alert: WeatherAlertData) {
        val now = System.currentTimeMillis()
        val title = WeatherWarningText.officialTitle(alert)
        val content = alert.description.ifBlank { alert.instruction.ifBlank { alert.headline.ifBlank { alert.eventName } } }
        val templateMode = settingsQueryApi.settings.value.liveNotificationTemplateMode
        val display = CapsuleMessageComposer.composeWeatherAlert(locationName, alert, templateMode)
        val id = "$WEATHER_CAPSULE_ID_PREFIX${EVENT_TYPE_WEATHER_ALERT}_${alert.id.ifBlank { title }}"
        updateWeatherCapsule(
            OcrCapsuleState(
                id = id,
                notifId = NotificationIds.weatherWarning(id),
                type = TYPE_WEATHER_ALERT,
                eventType = EVENT_TYPE_WEATHER_ALERT,
                title = title,
                content = content,
                description = content,
                color = resolveWeatherAlertColor(alert),
                startMillis = now,
                endMillis = now + weatherCapsuleTimeoutMs(),
                display = display,
                expiresAt = now + weatherCapsuleTimeoutMs(),
                locationName = locationName,
                weatherAlert = alert
            )
        )
    }

    fun showWeatherRisk(locationName: String, risk: WeatherRiskAlert) {
        val now = System.currentTimeMillis()
        val title = risk.title.ifBlank { "天气风险提醒" }
        val content = risk.message.ifBlank { risk.weatherText }
        val templateMode = settingsQueryApi.settings.value.liveNotificationTemplateMode
        val display = CapsuleMessageComposer.composeWeatherRisk(locationName, risk, templateMode)
        val id = "$WEATHER_CAPSULE_ID_PREFIX${EVENT_TYPE_WEATHER_RISK}_${risk.id.ifBlank { title }}"
        updateWeatherCapsule(
            OcrCapsuleState(
                id = id,
                notifId = NotificationIds.weatherWarning(id),
                type = TYPE_WEATHER_ALERT,
                eventType = EVENT_TYPE_WEATHER_RISK,
                title = display.primaryText,
                content = content,
                description = content,
                color = resolveWeatherRiskColor(risk),
                startMillis = now,
                endMillis = now + weatherCapsuleTimeoutMs(),
                display = display,
                expiresAt = now + weatherCapsuleTimeoutMs(),
                locationName = locationName,
                weatherRisk = risk
            )
        )
    }

    fun clearWeatherCapsules() {
        weatherAutoClearJobs.values.forEach { it.cancel() }
        weatherAutoClearJobs.clear()
        weatherCapsuleState.value = emptyList()
    }

    private fun updateOcrCapsule(state: OcrCapsuleState, autoClearMs: Long?) {
        val now = System.currentTimeMillis()
        val current = ocrCapsuleState.value
        if (current != null && current.type == state.type && current.display == state.display) {
            if (now - lastOcrUpdateAt < OCR_UPDATE_THROTTLE_MS) {
                return
            }
        }
        lastOcrUpdateAt = now
        ocrCapsuleState.value = state
        if (autoClearMs != null) {
            scheduleOcrAutoClear(autoClearMs)
        }
    }

    private fun scheduleOcrAutoClear(delayMs: Long) {
        ocrAutoClearJob?.cancel()
        ocrAutoClearJob = appScope.launch {
            kotlinx.coroutines.delay(delayMs)
            clearOcrCapsule()
        }
    }

    private fun updateWeatherCapsule(state: OcrCapsuleState) {
        weatherCapsuleState.value = (weatherCapsuleState.value.filterNot { it.id == state.id } + state)
        scheduleWeatherAutoClear(state.id, weatherCapsuleTimeoutMs())
    }

    private fun scheduleModelLoadingAutoClear(delayMs: Long) {
        modelLoadingAutoClearJob?.cancel()
        modelLoadingAutoClearJob = appScope.launch {
            kotlinx.coroutines.delay(delayMs)
            clearModelLoading()
        }
    }

    private fun scheduleVoiceTranscriptionAutoClear(delayMs: Long) {
        voiceTranscriptionAutoClearJob?.cancel()
        voiceTranscriptionAutoClearJob = appScope.launch {
            kotlinx.coroutines.delay(delayMs)
            clearVoiceTranscription()
        }
    }

    private fun scheduleTextQuickMemoAutoClear(delayMs: Long) {
        textQuickMemoAutoClearJob?.cancel()
        textQuickMemoAutoClearJob = appScope.launch {
            kotlinx.coroutines.delay(delayMs)
            clearTextQuickMemo()
        }
    }

    private fun scheduleWeatherAutoClear(id: String, delayMs: Long) {
        weatherAutoClearJobs.remove(id)?.cancel()
        weatherAutoClearJobs[id] = appScope.launch {
            kotlinx.coroutines.delay(delayMs)
            weatherCapsuleState.value = weatherCapsuleState.value.filterNot { it.id == id }
            weatherAutoClearJobs.remove(id)
        }
    }

    val uiState: StateFlow<CapsuleUiState> = createCapsuleStateFlow()

    init {
        startNotificationManager()
    }

    /**
     * 启动通知管理机制
     * 当胶囊状态变化时自动发布/更新/取消通知
     */
    private fun startNotificationManager() {
        appScope.launch {
            uiState.collect { state ->
                dispatcher.dispatch(state)
            }
        }
    }

    private fun createCapsuleStateFlow(): StateFlow<CapsuleUiState> {
        // ✅ 改用 MutableStateFlow，确保立即有值且 combine 能正常工作
        val tickerTrigger = MutableStateFlow(System.currentTimeMillis())

        // 启动定时器，每 10 秒更新一次（快速检测过期）
        appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                tickerTrigger.value = System.currentTimeMillis()
                Log.d(TAG, "Ticker fired: 检查过期状态")
            }
        }

        // combine 只支持最多 5 个流，需要嵌套 combine
        val baseCombine = combine(
            scheduleCenter.events,
            settingsQueryApi.settings,
            tickerTrigger,
            forceRefreshTrigger
        ) { events, settings, _, _ ->
            Pair(events, settings)
        }

        val baseTransientCombine = combine(
            ocrCapsuleState,
            modelLoadingCapsuleState,
            weatherCapsuleState,
            voiceTranscriptionCapsuleState,
            textQuickMemoCapsuleState
        ) { ocrCapsule, modelLoadingCapsule, weatherCapsules, voiceTranscriptionCapsule, textQuickMemoCapsule ->
            TransientCapsules(
                ocr = ocrCapsule,
                modelLoading = modelLoadingCapsule,
                weather = weatherCapsules,
                voiceTranscription = voiceTranscriptionCapsule,
                textQuickMemo = textQuickMemoCapsule,
                quickMemoRecording = null
            )
        }

        val capsuleTransientCombine = combine(
            baseTransientCombine,
            quickMemoRecordingCapsuleState
        ) { transient, quickMemoRecording ->
            transient.copy(quickMemoRecording = quickMemoRecording)
        }

        return combine(baseCombine, networkSpeedState, capsuleTransientCombine) { (events, settings), networkSpeed, transient ->
            Log.d(TAG, "=== computeCapsuleState 被调用 ===")
            computeCapsuleState(events, settings, networkSpeed, transient)
        }.flowOn(Dispatchers.Default)  // ✅ 将胶囊计算移到后台线程，避免主线程 ANR
        .stateIn(
            scope = appScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = CapsuleUiState.None
        )
    }

    private fun computeCapsuleState(
        events: List<Event>,
        settings: MySettings,
        networkSpeed: NetworkSpeedMonitor.NetworkSpeed?,
        transient: TransientCapsules
    ): CapsuleUiState {
        Log.d(TAG, ">>> computeCapsuleState 开始执行")

        val nowMillis = System.currentTimeMillis()
        val activeOcrCapsule = transient.ocr?.let { state ->
            if (state.expiresAt != null && nowMillis >= state.expiresAt) {
                clearOcrCapsule()
                null
            } else {
                state
            }
        }

        val activeModelLoadingCapsule = transient.modelLoading?.let { state ->
            if (state.expiresAt != null && nowMillis >= state.expiresAt) {
                clearModelLoading()
                null
            } else {
                state
            }
        }

        val activeVoiceTranscriptionCapsule = transient.voiceTranscription?.let { state ->
            if (state.expiresAt != null && nowMillis >= state.expiresAt) {
                clearVoiceTranscription()
                null
            } else {
                state
            }
        }

        val activeTextQuickMemoCapsule = transient.textQuickMemo?.let { state ->
            if (state.expiresAt != null && nowMillis >= state.expiresAt) {
                clearTextQuickMemo()
                null
            } else {
                state
            }
        }

        val activeQuickMemoRecordingCapsule = transient.quickMemoRecording?.let { state ->
            if (state.expiresAt != null && nowMillis >= state.expiresAt) {
                clearQuickMemoRecording()
                null
            } else {
                state
            }
        }

        val activeWeatherCapsules = transient.weather.filter { state ->
            state.expiresAt == null || nowMillis < state.expiresAt
        }
        if (activeWeatherCapsules.size != transient.weather.size) {
            weatherCapsuleState.value = activeWeatherCapsules
        }

        if ((activeOcrCapsule != null || activeModelLoadingCapsule != null || activeWeatherCapsules.isNotEmpty() || activeVoiceTranscriptionCapsule != null || activeTextQuickMemoCapsule != null || activeQuickMemoRecordingCapsule != null) && settings.isLiveCapsuleEnabled) {
            val transientItems = buildList {
                activeQuickMemoRecordingCapsule?.let { state ->
                    add(createTransientCapsuleItem(state))
                }
                activeOcrCapsule?.let { state ->
                    add(createTransientCapsuleItem(state))
                }
                activeModelLoadingCapsule?.let { state ->
                    add(createTransientCapsuleItem(state))
                }
                activeWeatherCapsules.forEach { state ->
                    add(createTransientCapsuleItem(state.withTemplateMode(settings.liveNotificationTemplateMode)))
                }
                activeVoiceTranscriptionCapsule?.let { state ->
                    add(createTransientCapsuleItem(state))
                }
                activeTextQuickMemoCapsule?.let { state ->
                    add(createTransientCapsuleItem(state))
                }
            }
            val scheduleCapsules = computeScheduleCapsules(events, settings)
            return CapsuleUiState.Active(transientItems + scheduleCapsules)
        }

        // 【实验室】网速胶囊：若未触发 OCR 胶囊则覆盖其他胶囊
        if (settings.isLiveCapsuleEnabled && settings.isNetworkSpeedCapsuleEnabled && networkSpeed != null) {
            Log.d(TAG, "网速胶囊模式: ${networkSpeed.formattedSpeed}")
            val display = CapsuleMessageComposer.composeNetworkSpeed(networkSpeed)
            val capsules = listOf(
                createCapsuleItem(
                    id = "network_speed",
                    notifId = 88888,
                    type = TYPE_NETWORK_SPEED,
                    eventType = "network_speed",
                    description = "",
                    color = android.graphics.Color.parseColor("#4CAF50"),
                    startMillis = System.currentTimeMillis(),
                    endMillis = System.currentTimeMillis() + 60 * 60 * 1000, // 1小时有效
                    display = display
                )
            )
            return CapsuleUiState.Active(capsules)
        }

        if (!settings.isLiveCapsuleEnabled) {
            return CapsuleUiState.None
        }

        val capsules = computeScheduleCapsules(events, settings)
        return if (capsules.isEmpty()) CapsuleUiState.None else CapsuleUiState.Active(capsules)
    }

    private data class CapsuleScheduleEntry(
        val id: String,
        val notifId: Int,
        val event: Event
    )

    private fun computeScheduleCapsules(
        events: List<Event>,
        settings: MySettings
    ): List<CapsuleUiState.Active.CapsuleItem> {
        val now = LocalDateTime.now()
        val activeEntries = buildCapsuleScheduleEntries(events, settings).filter { entry ->
            isActiveCapsuleEntry(entry.event, settings, now)
        }

        if (activeEntries.isEmpty()) {
            Log.d(TAG, "无活跃事件 (Active list empty)")
            return emptyList()
        }

        val (pickupEntries, scheduleEntries) = activeEntries.partition { isPickupRule(it.event) }
        val capsules = mutableListOf<CapsuleUiState.Active.CapsuleItem>()

        scheduleEntries.forEach { entry ->
            val event = entry.event
            val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
            val isExpired = now.isAfter(endDateTime)
            val display = CapsuleMessageComposer.composeSchedule(context, event, isExpired, settings.liveNotificationTemplateMode)

            capsules.add(createCapsuleItem(
                id = entry.id,
                notifId = entry.notifId,
                type = TYPE_SCHEDULE,
                eventType = resolveCapsuleEventType(event),
                description = stripSourceImageMarkers(event.description),
                color = event.color,
                state = event.state,
                startMillis = toMillis(event, event.startTime),
                endMillis = toMillis(event, event.endTime),
                display = display
            ))
        }

        val pickupEvents = pickupEntries.map { it.event }
        val aggregateMode = settings.isPickupAggregationEnabled && pickupEvents.size > 1

        if (aggregateMode) {
            Log.d(TAG, "聚合模式: ${pickupEvents.size} 个取件码")
            val latestEndMillis = pickupEvents.mapNotNull {
                try {
                    LocalDateTime.of(it.endDate, LocalTime.parse(it.endTime, TIME_FORMATTER))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) { null }
            }.maxOrNull() ?: (System.currentTimeMillis() + 2 * 60 * 60 * 1000)

            val isAnyExpired = pickupEvents.any { event ->
                val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
                now.isAfter(endDateTime)
            }
            val capsuleType = if (isAnyExpired) TYPE_PICKUP_EXPIRED else TYPE_PICKUP
            val display = CapsuleMessageComposer.composeAggregatePickup(context, pickupEvents)

            capsules.add(createCapsuleItem(
                id = AGGREGATE_PICKUP_ID,
                notifId = AGGREGATE_NOTIF_ID,
                type = capsuleType,
                eventType = RuleMatchingEngine.RULE_PICKUP,
                description = stripSourceImageMarkers(pickupEvents.firstOrNull()?.description),
                color = resolveAggregatePickupCapsuleColor(pickupEvents),
                startMillis = System.currentTimeMillis(),
                endMillis = latestEndMillis,
                display = display
            ))
        } else {
            Log.d(TAG, "非聚合模式: ${pickupEvents.size} 个取件码")
            pickupEntries.forEach { entry ->
                val event = entry.event
                val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
                val isExpired = now.isAfter(endDateTime)
                val capsuleType = if (isExpired) TYPE_PICKUP_EXPIRED else TYPE_PICKUP
                val display = CapsuleMessageComposer.composePickup(context, event, isExpired)

                Log.d(TAG, "生成胶囊: id=${entry.id}, type=$capsuleType, notifId=${entry.notifId}, title=${display.shortText}")

                capsules.add(createCapsuleItem(
                    id = entry.id,
                    notifId = entry.notifId,
                    type = capsuleType,
                    eventType = RuleMatchingEngine.RULE_PICKUP,
                    description = stripSourceImageMarkers(event.description),
                    color = resolvePickupCapsuleColor(event),
                    state = event.state,
                    startMillis = toMillis(event, event.startTime),
                    endMillis = toMillis(event, event.endTime),
                    display = display
                ))
            }
        }

        Log.d(TAG, "最终胶囊数量: ${capsules.size}")
        return capsules
    }

    private fun createTransientCapsuleItem(state: OcrCapsuleState): CapsuleUiState.Active.CapsuleItem {
        return createCapsuleItem(
            id = state.id,
            notifId = state.notifId,
            type = state.type,
            eventType = state.eventType,
            description = state.description,
            color = state.color,
            startMillis = state.startMillis,
            endMillis = state.endMillis,
            display = state.display
        )
    }

    private fun OcrCapsuleState.withTemplateMode(templateMode: String): OcrCapsuleState {
        val updatedDisplay = when {
            weatherAlert != null -> CapsuleMessageComposer.composeWeatherAlert(locationName, weatherAlert, templateMode)
            weatherRisk != null -> CapsuleMessageComposer.composeWeatherRisk(locationName, weatherRisk, templateMode)
            else -> display
        }
        return if (updatedDisplay == display) this else copy(display = updatedDisplay)
    }

    private fun resolveWeatherAlertColor(alert: WeatherAlertData): Int {
        return when (alert.colorCode.ifBlank { alert.severity }.lowercase()) {
            "red" -> android.graphics.Color.rgb(214, 48, 49)
            "orange" -> android.graphics.Color.rgb(230, 126, 34)
            "yellow" -> android.graphics.Color.rgb(241, 196, 15)
            "blue" -> android.graphics.Color.rgb(52, 152, 219)
            else -> android.graphics.Color.rgb(230, 126, 34)
        }
    }

    private fun resolveWeatherRiskColor(risk: WeatherRiskAlert): Int {
        return when (risk.level) {
            "high" -> android.graphics.Color.rgb(214, 48, 49)
            "medium" -> android.graphics.Color.rgb(230, 126, 34)
            else -> android.graphics.Color.rgb(241, 196, 15)
        }
    }

    private fun buildCapsuleScheduleEntries(events: List<Event>, settings: MySettings): List<CapsuleScheduleEntry> {
        val today = LocalDate.now()
        val advanceDays = if (settings.isAdvanceReminderEnabled && settings.advanceReminderMinutes > 0) {
            settings.advanceReminderMinutes / (24 * 60) + 1
        } else {
            1
        }
        val from = today.minusDays(1)
        val to = today.plusDays(advanceDays.toLong() + 1)
        val activeEvents = events.filter { it.archivedAt == null }
        val eventsById = activeEvents.mapNotNull { event -> event.id?.let { id -> id to event } }.toMap()

        return ScheduleDisplayHelper.buildDisplayItems(activeEvents, from, to).mapNotNull { item ->
            when (val target = item.action) {
                is ScheduleDisplayItem.ActionTarget.Single -> {
                    val event = eventsById[target.eventId]
                        ?.copy(tag = item.tag, state = item.state)
                        ?: item.toCapsuleEvent(id = target.eventId, parentId = 0L)
                    CapsuleScheduleEntry(
                        id = target.eventId.toString(),
                        notifId = NotificationIds.liveCapsule(target.eventId),
                        event = event
                    )
                }
                is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                    val parent = eventsById[target.parentId] ?: return@mapNotNull null
                    val rawVirtualId = item.stableKey.hashCode().toLong()
                    val virtualId = if (rawVirtualId < 0L) -rawVirtualId else rawVirtualId
                    val event = item.toCapsuleEvent(id = virtualId, parentId = target.parentId, parent = parent)
                    CapsuleScheduleEntry(
                        id = item.stableKey,
                        notifId = NotificationIds.liveCapsule(item.stableKey),
                        event = event
                    )
                }
            }
        }
    }

    private fun ScheduleDisplayItem.toCapsuleEvent(
        id: Long,
        parentId: Long,
        parent: Event? = null
    ): Event {
        return Event(
            id = id,
            startTS = startTS,
            endTS = endTS,
            title = title,
            location = location,
            description = description,
            timeZone = timeZone,
            flags = if (isAllDay) FLAG_ALL_DAY else 0,
            color = color,
            state = state,
            tag = tag,
            parentId = parentId,
            eventType = parent?.eventType ?: 0L,
            type = parent?.type ?: 0
        )
    }

    private fun isActiveCapsuleEntry(event: Event, settings: MySettings, now: LocalDateTime): Boolean {
        return try {
            val endDateTime = LocalDateTime.of(event.endDate, LocalTime.parse(event.endTime, TIME_FORMATTER))
            val startDateTime = LocalDateTime.of(event.startDate, LocalTime.parse(event.startTime, TIME_FORMATTER))
            val effectiveStartTime = if (settings.isAdvanceReminderEnabled && settings.advanceReminderMinutes > 0) {
                startDateTime.minusMinutes(settings.advanceReminderMinutes.toLong())
            } else {
                startDateTime.minusMinutes(1)
            }

            !event.isCompleted && now.isBefore(endDateTime) && !now.isBefore(effectiveStartTime)
        } catch (e: Exception) {
            Log.e(TAG, "解析事件时间失败: ${event.title}", e)
            false
        }
    }

    private fun createCapsuleItem(
        id: String,
        notifId: Int,
        type: Int,
        eventType: String,
        description: String,
        color: Int,
        state: Int = 0,
        startMillis: Long,
        endMillis: Long,
        display: CapsuleDisplayModel
    ): CapsuleUiState.Active.CapsuleItem {
        return CapsuleUiState.Active.CapsuleItem(
            id = id,
            notifId = notifId,
            type = type,
            eventType = eventType,
            title = display.shortText,
            content = display.expandedText
                ?: listOfNotNull(display.secondaryText, display.tertiaryText).joinToString("\n"),
            description = description,
            color = color,
            state = state,
            startMillis = startMillis,
            endMillis = endMillis,
            display = display
        )
    }

    private fun resolveCapsuleEventType(event: Event): String {
        return if (event.tag == EventTags.COURSE || event.tag == "__removed_course__") {
            RuleMatchingEngine.RULE_COURSE
        } else {
            resolveRuleId(event)
        }
    }

    private fun resolveRuleId(event: Event): String {
        val parsedRuleId = RuleMatchingEngine.resolvePayload(event)?.ruleId
        if (!parsedRuleId.isNullOrBlank()) {
            return parsedRuleId
        }
        return when (event.tag) {
            EventTags.PICKUP -> RuleMatchingEngine.RULE_PICKUP
            EventTags.TRAIN -> RuleMatchingEngine.RULE_TRAIN
            EventTags.TAXI -> RuleMatchingEngine.RULE_TAXI
            EventTags.GENERAL -> RuleMatchingEngine.RULE_GENERAL
            else -> if (event.tag.isNotBlank()) event.tag else RuleMatchingEngine.RULE_GENERAL
        }
    }

    private fun isPickupRule(event: Event): Boolean {
        return resolveRuleId(event) == RuleMatchingEngine.RULE_PICKUP
    }

    private fun resolveAggregatePickupCapsuleColor(events: List<Event>): Int {
        return events.firstNotNullOfOrNull { event -> event.color.takeIf { it != 0 } }
            ?: DEFAULT_PICKUP_CAPSULE_COLOR
    }

    private fun resolvePickupCapsuleColor(event: Event): Int {
        return event.color.takeIf { it != 0 } ?: DEFAULT_PICKUP_CAPSULE_COLOR
    }

    private fun toMillis(event: Event, timeStr: String): Long {
        return try {
            // 修复：时间必须对应正确的日期
            // startTime 对应 startDate，endTime 对应 endDate
            val date = if (timeStr == event.startTime) event.startDate else event.endDate
            val localDateTime = LocalDateTime.of(date, LocalTime.parse(timeStr, TIME_FORMATTER))
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.e(TAG, "时间转换失败: $timeStr", e)
            System.currentTimeMillis()
        }
    }
}
