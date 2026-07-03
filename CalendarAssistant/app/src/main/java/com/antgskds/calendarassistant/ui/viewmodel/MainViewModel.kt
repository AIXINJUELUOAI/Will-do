package com.antgskds.calendarassistant.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.core.ai.AiPrompts
import com.antgskds.calendarassistant.core.ai.convertDraftToEvent
import com.antgskds.calendarassistant.core.ai.PromptCheckResult
import com.antgskds.calendarassistant.core.ai.PromptUpdater
import com.antgskds.calendarassistant.core.update.AppUpdateChecker
import com.antgskds.calendarassistant.core.update.AppUpdateCheckResult
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.center.NoteCenter
import com.antgskds.calendarassistant.core.center.QuickMemoCenter
import com.antgskds.calendarassistant.core.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.core.query.CapsuleQueryApi
import com.antgskds.calendarassistant.feature.weather.api.WeatherOperationApi
import com.antgskds.calendarassistant.core.query.HomeQueryApi
import com.antgskds.calendarassistant.core.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.feature.weather.api.WeatherQueryApi
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.RemotePrompts
import com.antgskds.calendarassistant.data.model.RemoteAppUpdateInfo
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.core.note.NoteDocument
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.note.NoteTransferManager
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionEntity
import com.antgskds.calendarassistant.core.quickmemo.audio.AudioPlaybackCenter
import com.antgskds.calendarassistant.core.quickmemo.audio.AudioPlaybackState
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.course.CourseMeta
import com.antgskds.calendarassistant.core.course.calculateSemesterWeek
import com.antgskds.calendarassistant.core.course.resolveSemesterAnchor
import com.antgskds.calendarassistant.core.model.RecurringMode
import com.antgskds.calendarassistant.feature.weather.domain.hasWeatherConfig
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.data.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class MainUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val timeRefreshToken: Long = System.currentTimeMillis(),
    val revealedItemKey: String? = null,
    val rawEvents: List<Event> = emptyList(),
    val allScheduleItems: List<ScheduleDisplayItem> = emptyList(),
    val allEventsFutureDays: Int = 7,
    val allEventsFutureLimit: LocalDate = LocalDate.now().plusDays(7),
    val courseScheduleItems: List<ScheduleDisplayItem> = emptyList(),
    val settings: MySettings = MySettings(),
    val currentDateEvents: List<ScheduleDisplayItem> = emptyList(),
    val tomorrowEvents: List<ScheduleDisplayItem> = emptyList(),
    val weatherData: WeatherData? = null,
    val rawEventCount: Int = 0
)

data class PromptUpdateDialogState(
    val localVersion: Int,
    val remoteVersion: Int
)

data class PromptCheckFeedback(
    val message: String,
    val type: ToastType
)

data class AppUpdateUiState(
    val info: RemoteAppUpdateInfo? = null,
    val hasUpdate: Boolean = false,
    val localVersionCode: Int = com.antgskds.calendarassistant.BuildConfig.VERSION_CODE,
    val isChecking: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(
    private val appContext: Context,
    private val scheduleCenter: ScheduleCenter,
    private val noteCenter: NoteCenter,
    private val quickMemoCenter: QuickMemoCenter,
    private val audioPlaybackCenter: AudioPlaybackCenter,
    private val capsuleQueryApi: CapsuleQueryApi,
    private val settingsQueryApi: SettingsQueryApi,
    private val homeQueryApi: HomeQueryApi,
    private val scheduleInsightsQueryApi: ScheduleInsightsQueryApi,
    private val weatherQueryApi: WeatherQueryApi,
    private val weatherOperationApi: WeatherOperationApi,
    private val attachmentManager: EventAttachmentManager
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
    private val _appUpdateUiState = MutableStateFlow(AppUpdateUiState())
    val appUpdateUiState: StateFlow<AppUpdateUiState> = _appUpdateUiState.asStateFlow()
    private val noteTransferManager = NoteTransferManager(appContext)

    init {
        // 精确定时器：等待最近的未过期事件过期或跨天时触发刷新
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(calculateDelayToNextTimeRefresh())
                publishTimeRefresh()
            }
        }

        // 自动归档过期事件（仅用户开启后执行）
        viewModelScope.launch {
            runAutoArchiveIfEnabled("Archive")
        }

        viewModelScope.launch {
            settingsQueryApi.settings.collectLatest { settings ->
                weatherOperationApi.refreshIfNeeded(settings)
            }
        }

        checkPromptUpdatesSilently()
        checkAppUpdatesSilently()
    }

    private fun calculateDelayToNextExpiration(): Long {
        return homeQueryApi.calculateDelayToNextExpiration(scheduleCenter.events.value)
    }

    private fun calculateDelayToNextTimeRefresh(now: LocalDateTime = LocalDateTime.now()): Long {
        val expirationDelayMs = calculateDelayToNextExpiration().takeIf { it > 0 }
        val midnightDelayMs = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
            .coerceAtLeast(1L)
        return listOfNotNull(expirationDelayMs, midnightDelayMs, TIME_REFRESH_FALLBACK_CHECK_MS).minOrNull()
            ?: TIME_REFRESH_FALLBACK_CHECK_MS
    }

    private fun publishTimeRefresh() {
        val previousToday = _today.value
        val currentToday = LocalDate.now()
        if (previousToday != currentToday) {
            if (_selectedDate.value == previousToday) {
                _selectedDate.value = currentToday
            }
            _today.value = currentToday
        }
        _timeTrigger.value = System.currentTimeMillis()
    }

    // 归档事件（公开访问）
    val archivedEvents = scheduleCenter.archivedEvents
    val notes: StateFlow<List<NoteEntity>> = noteCenter.notes
    val quickMemos: StateFlow<List<QuickMemoEntity>> = quickMemoCenter.quickMemos
    val quickMemoSuggestions: StateFlow<List<QuickMemoSuggestionEntity>> = quickMemoCenter.suggestions
    val audioPlaybackState: StateFlow<AudioPlaybackState> = audioPlaybackCenter.playbackState
    val capsuleUiState: StateFlow<CapsuleUiState> = capsuleQueryApi.uiState

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _today = MutableStateFlow(LocalDate.now())
    private val _revealedItemKey = MutableStateFlow<String?>(null)
    private val _allEventsFutureDays = MutableStateFlow(INITIAL_ALL_EVENTS_FUTURE_DAYS)

    val uiState: StateFlow<MainUiState> = combine(
        _selectedDate,
        _revealedItemKey,
        scheduleCenter.events,
        settingsQueryApi.settings,
        weatherQueryApi.weatherData,
        _timeTrigger,
        _today,
        _allEventsFutureDays
    ) { values ->
        val date = values[0] as LocalDate
        val revealedKey = values[1] as String?
        val events = values[2] as List<Event>
        val activeEvents = events.filter { it.archivedAt == null }
        val settings = values[3] as MySettings
        val weatherData = values[4] as WeatherData?
        val timeRefreshToken = values[5] as Long
        val today = values[6] as LocalDate
        val allEventsFutureDays = values[7] as Int
        val snapshot = homeQueryApi.buildSnapshot(
            selectedDate = date,
            events = activeEvents,
            settings = settings
        )

        // 为"全部日程"页展开所有历史日程到未来 7 天
        val futureLimit = today.plusDays(allEventsFutureDays.toLong())
        val allItemsStart = activeEvents.minOfOrNull { it.startDate } ?: today
        val allItems = ScheduleDisplayHelper.buildDisplayItems(activeEvents, allItemsStart, futureLimit)
        val semesterStart = resolveSemesterAnchor(settings.semesterStartDate)
        val semesterEnd = semesterStart.plusWeeks(settings.totalWeeks.coerceAtLeast(1).toLong()).minusDays(1)
        val courseItems = ScheduleDisplayHelper.buildDisplayItems(
            activeEvents.filter { it.tag == EventTags.COURSE },
            semesterStart,
            semesterEnd
        )

        MainUiState(
            selectedDate = date,
            today = today,
            timeRefreshToken = timeRefreshToken,
            revealedItemKey = revealedKey,
            rawEvents = activeEvents,
            allScheduleItems = allItems,
            allEventsFutureDays = allEventsFutureDays,
            allEventsFutureLimit = futureLimit,
            courseScheduleItems = courseItems,
            settings = settings,
            currentDateEvents = snapshot.currentDateEvents,
            tomorrowEvents = snapshot.tomorrowEvents,
            weatherData = if (settings.hasWeatherConfig()) weatherData else null,
            rawEventCount = activeEvents.size
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun updateSelectedDate(date: LocalDate) { _selectedDate.value = date; _revealedItemKey.value = null }
    fun onRevealItem(key: String?) { _revealedItemKey.value = key }
    fun loadMoreFutureAllEvents() {
        _allEventsFutureDays.value += ALL_EVENTS_LOAD_MORE_DAYS
        _revealedItemKey.value = null
    }

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

    fun checkAppUpdatesManually() {
        checkAppUpdates(silent = false)
    }

    private fun checkAppUpdatesSilently() {
        checkAppUpdates(silent = true)
    }

    private fun checkAppUpdates(silent: Boolean) {
        if (_appUpdateUiState.value.isChecking) return

        viewModelScope.launch(Dispatchers.IO) {
            _appUpdateUiState.update { it.copy(isChecking = true, errorMessage = null) }
            val nextState = when (val result = AppUpdateChecker.check()) {
                is AppUpdateCheckResult.Success -> AppUpdateUiState(
                    info = result.info,
                    hasUpdate = result.hasUpdate,
                    localVersionCode = result.localVersionCode,
                    isChecking = false,
                    errorMessage = null
                )
                is AppUpdateCheckResult.Error -> _appUpdateUiState.value.copy(
                    isChecking = false,
                    errorMessage = if (silent) null else result.message
                )
            }
            _appUpdateUiState.value = nextState
        }
    }

    // --- 普通事件操作 ---
    fun addEvent(event: Event) = viewModelScope.launch { scheduleCenter.addEvent(event) }
    fun addEvent(event: Event, pendingAttachmentKeys: List<String>) = viewModelScope.launch {
        val eventId = scheduleCenter.addEvent(event)
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            pendingAttachmentKeys.distinct().forEach { pendingKey ->
                attachmentManager.bindPendingAttachments(eventId, pendingKey)
            }
        }
    }
    fun updateEvent(event: Event) = viewModelScope.launch { scheduleCenter.updateEvent(event) }

    suspend fun getNoteById(noteId: Long): NoteEntity? = noteCenter.getNote(noteId)

    fun saveNote(
        id: Long?,
        title: String,
        document: NoteDocument,
        createdAt: Long? = null,
        onSaved: (Long) -> Unit = {}
    ) = viewModelScope.launch {
        val savedId = noteCenter.saveNote(id, title, document, createdAt)
        onSaved(savedId)
    }

    fun deleteNote(noteId: Long, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        noteCenter.deleteNote(noteId)
        onDeleted()
    }

    fun toggleNoteTodo(noteId: Long, paragraphId: String) = viewModelScope.launch {
        noteCenter.toggleTodo(noteId, paragraphId)
    }

    fun setNotePinned(noteId: Long, pinned: Boolean) = viewModelScope.launch {
        noteCenter.setPinned(noteId, pinned)
    }

    fun createTextQuickMemo(bodyText: String, asTodo: Boolean = false, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = quickMemoCenter.createTextMemo(bodyText, asTodo)
        onCreated(id)
    }

    fun createImageQuickMemo(imagePath: String, bodyText: String = "", asTodo: Boolean = false, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = quickMemoCenter.createImageMemo(imagePath, bodyText, asTodo)
        onCreated(id)
    }

    fun updateQuickMemoBody(memoId: Long, bodyText: String) = viewModelScope.launch {
        quickMemoCenter.updateBody(memoId, bodyText)
    }

    fun attachImageToQuickMemo(memoId: Long, imagePath: String, onResult: (Result<Unit>) -> Unit = {}) = viewModelScope.launch {
        val result = runCatching { quickMemoCenter.attachImageToMemo(memoId, imagePath) }
        onResult(result)
    }

    fun attachVoiceToQuickMemo(memoId: Long, audioPath: String, durationMs: Long, onResult: (Result<Unit>) -> Unit = {}) = viewModelScope.launch {
        val result = runCatching {
            if (!quickMemoCenter.attachVoiceToMemo(memoId, audioPath, durationMs)) error("随口记不存在")
        }
        onResult(result)
    }

    fun deleteQuickMemo(memoId: Long, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        quickMemoCenter.deleteQuickMemo(memoId)
        onDeleted()
    }

    fun toggleQuickMemoTodoCompletion(memoId: Long) = viewModelScope.launch {
        quickMemoCenter.toggleTodoCompletion(memoId)
    }

    fun markQuickMemoTodo(memoId: Long) = viewModelScope.launch {
        quickMemoCenter.markTodoActive(memoId)
    }

    fun retryQuickMemoTranscription(memoId: Long) {
        quickMemoCenter.retryTranscription(memoId)
    }

    fun pinQuickMemo(memoId: Long, onResult: (Result<Unit>) -> Unit = {}) = viewModelScope.launch {
        val result = runCatching {
            if (!quickMemoCenter.pinQuickMemo(memoId)) error("随口记不可挂起")
        }
        onResult(result)
    }

    fun clearPinnedQuickMemo(memoId: Long, onResult: (Result<Unit>) -> Unit = {}) = viewModelScope.launch {
        val result = runCatching {
            if (!quickMemoCenter.clearPinnedTextQuickMemo(memoId)) error("当前随口记未挂起")
        }
        onResult(result)
    }

    fun toggleAudioPlayback(audioPath: String?) {
        audioPlaybackCenter.toggle(audioPath)
    }

    fun stopAudioPlayback() {
        audioPlaybackCenter.stop()
    }

    fun createEventFromQuickMemoSuggestion(suggestionId: Long, onResult: (Result<Long>) -> Unit = {}) = viewModelScope.launch {
        val result = runCatching {
            val suggestion = quickMemoCenter.getSuggestion(suggestionId) ?: error("候选不存在")
            val draft = QuickMemoSuggestionCodec.decode(suggestion.candidateJson) ?: error("候选解析失败")
            val settings = settingsQueryApi.settings.value
            val event = convertDraftToEvent(
                draft = draft,
                defaultDurationMinutes = settings.defaultEventDurationMinutes,
                forceInstantCodeTimeToNow = settings.forceInstantCodeTimeToNow,
                eventColorPaletteHex = settings.eventColorPaletteHex
            )
            val eventId = scheduleCenter.addEvent(event)
            quickMemoCenter.markSuggestionCreated(suggestionId, eventId)
            eventId
        }
        onResult(result)
    }

    fun exportNote(noteId: Long, uri: Uri, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            val note = noteCenter.getNote(noteId) ?: error("便签不存在")
            kotlinx.coroutines.withContext(Dispatchers.IO) { noteTransferManager.exportNote(note, uri) }
        }
        onResult(result)
    }

    fun exportMarkdownNote(noteId: Long, uri: Uri, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            val note = noteCenter.getNote(noteId) ?: error("便签不存在")
            kotlinx.coroutines.withContext(Dispatchers.IO) { noteTransferManager.exportMarkdownNote(note, uri) }
        }
        onResult(result)
    }

    fun importNote(uri: Uri, onResult: (Result<Long>) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            val data = kotlinx.coroutines.withContext(Dispatchers.IO) { noteTransferManager.importNote(uri) }
            noteCenter.saveNote(
                id = null,
                title = data.title,
                document = data.document,
                createdAt = data.createdAt.takeIf { it > 0L }
            )
        }
        onResult(result)
    }

    suspend fun addEventWithResult(event: Event): Long {
        return scheduleCenter.addEvent(event)
    }

    fun updateEventAndRefreshAttachments(event: Event) = viewModelScope.launch {
        scheduleCenter.updateEvent(event)
        event.id?.let { eventId ->
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                attachmentManager.updateEventKey(eventId)
            }
        }
    }

    fun detachRecurringInstance(
        parentEventId: Long,
        occurrenceTs: Long,
        detachedEvent: Event
    ) = viewModelScope.launch {
        scheduleCenter.editRecurringEvent(
            parentEventId, detachedEvent,
            com.antgskds.calendarassistant.core.model.RecurringMode.THIS,
            occurrenceTs
        )
    }

    fun findRecurringParent(event: Event): Event? {
        if (event.isRecurring) return event
        val pId = event.parentId
        if (pId == 0L) return null
        return scheduleCenter.events.value.find { it.id == pId && it.isRecurring }
    }

    fun getEventById(eventId: Long): Event? {
        return scheduleCenter.events.value.find { it.id == eventId }
    }

    // ── 新 API：EditDraft / EventPatch 操作 ──

    fun prepareEditSingle(eventId: Long): com.antgskds.calendarassistant.data.model.EditDraft? {
        return scheduleCenter.prepareEditSingle(eventId)
    }

    fun prepareEditRecurringOccurrence(parentId: Long, occurrenceTs: Long): com.antgskds.calendarassistant.data.model.EditDraft? {
        return scheduleCenter.prepareEditRecurringOccurrence(parentId, occurrenceTs)
    }

    fun prepareNewEvent(): com.antgskds.calendarassistant.data.model.EditDraft {
        return scheduleCenter.prepareNewEvent()
    }

    fun addEventFromPatch(patch: com.antgskds.calendarassistant.data.model.EventPatch) = viewModelScope.launch {
        val eventId = scheduleCenter.addEventFromPatchWithResult(patch)
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            patch.pendingAttachmentUris.forEach { uri ->
                runCatching { attachmentManager.addManualAttachment(eventId, uri) }
            }
        }
    }

    suspend fun addEventFromPatchWithResult(patch: com.antgskds.calendarassistant.data.model.EventPatch): Long {
        return scheduleCenter.addEventFromPatchWithResult(patch)
    }

    fun updateSingleFromPatch(eventId: Long, patch: com.antgskds.calendarassistant.data.model.EventPatch) = viewModelScope.launch {
        scheduleCenter.updateSingleFromPatch(eventId, patch)
    }

    fun editRecurringFromPatch(
        parentId: Long,
        occurrenceTs: Long,
        mode: RecurringMode,
        patch: com.antgskds.calendarassistant.data.model.EventPatch
    ) = viewModelScope.launch {
        val editedId = scheduleCenter.editRecurringFromPatch(
            parentId, occurrenceTs,
            mode,
            patch
        )
        editedId?.let { refreshAttachmentKey(it) }
    }

    suspend fun editRecurringFromPatchWithResult(
        parentId: Long,
        occurrenceTs: Long,
        mode: RecurringMode,
        patch: com.antgskds.calendarassistant.data.model.EventPatch
    ): Long? {
        val editedId = scheduleCenter.editRecurringFromPatch(parentId, occurrenceTs, mode, patch)
        editedId?.let { refreshAttachmentKey(it) }
        return editedId
    }

    suspend fun getEventAttachments(eventId: Long): List<EventAttachment> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.getAttachments(eventId)
        }
    }

    suspend fun getAttachmentsByIds(ids: List<Long>): List<EventAttachment> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.getAttachmentsByIds(ids)
        }
    }

    suspend fun addAttachmentToEvent(eventId: Long, uri: Uri): EventAttachment {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.addManualAttachment(eventId, uri)
        }
    }

    suspend fun addPendingAttachment(eventKey: String, uri: Uri): EventAttachment {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.addPendingManualAttachment(eventKey, uri)
        }
    }

    suspend fun bindPendingAttachmentsToEvent(eventId: Long, pendingEventKey: String) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.bindPendingAttachments(eventId, pendingEventKey)
        }
    }

    suspend fun deletePendingAttachments(eventKey: String) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.deleteAttachmentsForEventKey(eventKey)
        }
    }

    suspend fun refreshAttachmentKey(eventId: Long) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.updateEventKey(eventId)
        }
    }

    suspend fun deleteAttachment(attachment: EventAttachment) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.deleteAttachment(attachment)
        }
    }

    private suspend fun deleteAttachmentsForEventOnIo(eventId: Long) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            attachmentManager.deleteAttachmentsForEvent(eventId)
        }
    }

    suspend fun openAttachment(attachment: EventAttachment): Boolean {
        return kotlinx.coroutines.withContext(Dispatchers.Main) {
            val intent = attachmentManager.openAttachmentIntent(attachment) ?: return@withContext false
            runCatching { appContext.startActivity(Intent.createChooser(intent, "打开附件")) }.isSuccess
        }
    }

    fun findNextRecurringInstance(parentEvent: Event): Event? {
        val pid = parentEvent.id ?: return null
        return scheduleInsightsQueryApi.findNextRecurringInstance(
            events = scheduleCenter.events.value,
            parentEventId = pid
        )
    }

    // --- 课程操作 ---

    fun currentCourses(): List<Course> {
        return CourseEventMapper.extractParentCourses(scheduleCenter.events.value, settingsQueryApi.settings.value)
    }

    fun addCourse(course: Course) = viewModelScope.launch {
        scheduleCenter.addEvent(CourseEventMapper.toParentEvent(course, settingsQueryApi.settings.value))
    }

    fun updateCourse(course: Course) = viewModelScope.launch {
        val events = scheduleCenter.events.value
        val settings = settingsQueryApi.settings.value
        val existingParent = CourseEventMapper.findParentByCourseId(events, course.id)
        val parentId = existingParent?.id
        val excludedTs = if (parentId != null) {
            events.filter { it.parentId == parentId && CourseEventMapper.isCourseEvent(it) }
                .mapNotNull { child -> CourseEventMapper.childOriginalWeek(child, settings) }
                .distinct()
                .mapNotNull { week -> CourseEventMapper.occurrenceTsForWeek(course, settings, week) }
        } else {
            emptyList()
        }
        val updated = CourseEventMapper.toParentEvent(course, settings, existingParent, excludedTs)
        if (existingParent == null) scheduleCenter.addEvent(updated) else scheduleCenter.updateEvent(updated)
    }

    fun deleteCourse(course: Course) = viewModelScope.launch {
        val parent = CourseEventMapper.findParentByCourseId(scheduleCenter.events.value, course.id) ?: return@launch
        parent.id?.let { scheduleCenter.deleteEvent(it) }
    }

    fun clearAllCourses() = viewModelScope.launch {
        val events = scheduleCenter.events.value
        val courses = CourseEventMapper.extractParentCourses(events, settingsQueryApi.settings.value)
        courses.forEach { course ->
            CourseEventMapper.findParentByCourseId(scheduleCenter.events.value, course.id)
                ?.id
                ?.let { scheduleCenter.deleteEvent(it) }
        }
    }

    fun updateCourseOccurrence(
        item: ScheduleDisplayItem,
        newName: String,
        newLocation: String,
        newStartNode: Int,
        newEndNode: Int,
        newDate: LocalDate
    ) = viewModelScope.launch {
        val settings = settingsQueryApi.settings.value
        val startTime = CourseEventMapper.nodeStartTime(settings, newStartNode)
        val endTime = CourseEventMapper.nodeEndTime(settings, newEndNode, startTime)
        val startTs = toEpochSeconds(newDate, startTime)
        val endTs = toEpochSeconds(newDate, endTime).let { if (it > startTs) it else startTs + 45 * 60 }

        when (val target = item.action) {
            is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                val parent = getEventById(target.parentId) ?: return@launch
                val parentMeta = CourseEventMapper.parseMeta(parent.description) ?: CourseMeta(uid = parent.id?.toString().orEmpty())
                val originalDate = java.time.Instant.ofEpochSecond(target.occurrenceTs)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                val originalWeek = calculateSemesterWeek(settings.semesterStartDate, originalDate)
                val detached = parent.copy(
                    id = null,
                    title = newName,
                    location = newLocation,
                    startTS = startTs,
                    endTS = endTs,
                    description = CourseEventMapper.buildDetachedInstanceDescription(
                        parentMeta = parentMeta,
                        startNode = newStartNode,
                        endNode = newEndNode,
                        originalOccurrenceTs = target.occurrenceTs,
                        originalWeek = originalWeek,
                        originalDate = originalDate
                    ),
                    parentId = target.parentId,
                    rrule = "",
                    exdates = emptyList(),
                    importId = ""
                )
                scheduleCenter.editRecurringEvent(
                    target.parentId,
                    detached,
                    com.antgskds.calendarassistant.core.model.RecurringMode.THIS,
                    target.occurrenceTs
                )
            }

            is ScheduleDisplayItem.ActionTarget.Single -> {
                val existing = getEventById(target.eventId) ?: return@launch
                val meta = CourseEventMapper.parseMeta(existing.description)
                val parentMeta = meta?.parentCourseUid
                    ?.let { parentUid -> CourseEventMapper.findParentByCourseId(scheduleCenter.events.value, parentUid) }
                    ?.let { CourseEventMapper.parseMeta(it.description) }
                    ?: meta
                    ?: CourseMeta(uid = existing.id?.toString().orEmpty())
                val originalTs = meta?.originalOccurrenceTs ?: existing.startTS
                val originalDate = meta?.originalDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: java.time.Instant.ofEpochSecond(originalTs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val originalWeek = meta?.originalWeek ?: calculateSemesterWeek(settings.semesterStartDate, originalDate)
                scheduleCenter.updateEvent(
                    existing.copy(
                        title = newName,
                        location = newLocation,
                        startTS = startTs,
                        endTS = endTs,
                        description = CourseEventMapper.buildDetachedInstanceDescription(
                            parentMeta = parentMeta,
                            startNode = newStartNode,
                            endNode = newEndNode,
                            originalOccurrenceTs = originalTs,
                            originalWeek = originalWeek,
                            originalDate = originalDate
                        )
                    )
                )
            }
        }
    }

    fun deleteCourseOccurrence(item: ScheduleDisplayItem) = viewModelScope.launch {
        when (val target = item.action) {
            is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                scheduleCenter.deleteRecurringFromUi(
                    target.parentId,
                    target.occurrenceTs,
                    com.antgskds.calendarassistant.core.model.RecurringMode.THIS
                )
            }
            is ScheduleDisplayItem.ActionTarget.Single -> {
                deleteAttachmentsForEventOnIo(target.eventId)
                scheduleCenter.deleteEvent(target.eventId)
            }
        }
        _revealedItemKey.value = null
    }

    fun deleteRecurringItem(item: ScheduleDisplayItem, mode: RecurringMode) = viewModelScope.launch {
        when (val target = item.action) {
            is ScheduleDisplayItem.ActionTarget.RecurringOccurrence -> {
                scheduleCenter.deleteRecurringFromUi(target.parentId, target.occurrenceTs, mode)
            }
            is ScheduleDisplayItem.ActionTarget.Single -> {
                deleteAttachmentsForEventOnIo(target.eventId)
                scheduleCenter.deleteEvent(target.eventId)
            }
        }
        _revealedItemKey.value = null
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            val eid = event.id ?: return@launch
            deleteAttachmentsForEventOnIo(eid)
            scheduleCenter.deleteEvent(eid)
            _revealedItemKey.value = null
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            deleteAttachmentsForEventOnIo(eventId)
            scheduleCenter.deleteEvent(eventId)
            _revealedItemKey.value = null
        }
    }

    fun clearDeveloperTestEvents(): Int {
        val testEvents = scheduleCenter.events.value.filter { it.title.startsWith("[DEV]") }
        viewModelScope.launch {
            testEvents.mapNotNull { it.id }.forEach { eventId ->
                scheduleCenter.deleteEvent(eventId)
            }
            _revealedItemKey.value = null
        }
        return testEvents.size
    }

    fun toggleImportant(event: Event) {
        viewModelScope.launch {
            scheduleCenter.updateEvent(event.copy())
            _revealedItemKey.value = null
        }
    }

    // --- 归档操作 ---

    /**
     * 🔥 修复：懒加载归档数据
     * 仅在进入归档页面时调用
     */
    fun refreshArchivedEvents() {
        scheduleCenter.refreshArchivedEvents()
    }

    /**
     * 归档事件
     */
    fun archiveEvent(eventId: Long) {
        viewModelScope.launch {
            scheduleCenter.archiveEvent(eventId)
            _revealedItemKey.value = null
        }
    }

    /**
     * 还原归档事件
     */
    fun restoreEvent(archivedEventId: Long) {
        viewModelScope.launch {
            scheduleCenter.restoreEvent(archivedEventId)
        }
    }

    /**
     * 删除归档事件
     */
    fun deleteArchivedEvent(archivedEventId: Long) {
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

    fun fetchArchivedEvents() {
        viewModelScope.launch {
            scheduleCenter.refreshArchivedEvents()  // 修复：之前错误调用了 refreshEvents()
        }
    }

    // ── 新 API：面向 ActionTarget 的操作 ──

    fun archiveItem(target: com.antgskds.calendarassistant.data.model.ScheduleDisplayItem.ActionTarget) {
        viewModelScope.launch {
            scheduleCenter.archiveItem(target)
            _revealedItemKey.value = null
        }
    }

    fun completeItem(target: com.antgskds.calendarassistant.data.model.ScheduleDisplayItem.ActionTarget) {
        scheduleCenter.completeItem(target)
        _revealedItemKey.value = null
    }

    fun checkInItem(target: com.antgskds.calendarassistant.data.model.ScheduleDisplayItem.ActionTarget) {
        scheduleCenter.checkInItem(target)
        _revealedItemKey.value = null
    }

    fun performPrimaryActionOnItem(item: com.antgskds.calendarassistant.data.model.ScheduleDisplayItem) {
        scheduleCenter.performPrimaryActionOnItem(item)
        _revealedItemKey.value = null
    }

    /**
     * 刷新数据
     * 每次回到前台时调用，确保 UI 显示最新状态
     */
    fun refreshData() {
        viewModelScope.launch {
            // 1. 用户开启自动归档时，归档所有已过期且未归档的日程
            runAutoArchiveIfEnabled("Refresh")
            // 2. 强制触发 UI 重组
            publishTimeRefresh()
        }
    }

    private suspend fun runAutoArchiveIfEnabled(logTag: String) {
        if (!settingsQueryApi.settings.value.autoArchiveEnabled) return
        val archivedCount = scheduleCenter.autoArchiveExpiredEvents()
        if (archivedCount > 0) {
            Log.d(logTag, "自动归档了 $archivedCount 条事件")
        }
    }

    private companion object {
        private const val INITIAL_ALL_EVENTS_FUTURE_DAYS = 7
        private const val ALL_EVENTS_LOAD_MORE_DAYS = 15
        private const val TIME_REFRESH_FALLBACK_CHECK_MS = 60_000L
    }
}
