package com.antgskds.calendarassistant.shared.api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.antgskds.calendarassistant.BuildConfig
import com.antgskds.calendarassistant.feature.backup.application.BackupCoordinator
import com.antgskds.calendarassistant.feature.backup.data.model.AppBackupOptions
import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoFacade
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTodoState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType
import com.antgskds.calendarassistant.feature.schedule.application.ScheduleFacade
import com.antgskds.calendarassistant.feature.schedule.data.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.FLAG_ALL_DAY
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.REMINDER_OFF
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.STATE_CHECKED_IN
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.STATE_COMPLETED
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.STATE_PENDING
import com.antgskds.calendarassistant.feature.schedule.domain.course.Course
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.feature.schedule.domain.model.Attendee
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventAttachment
import com.antgskds.calendarassistant.feature.schedule.domain.model.RecurringMode
import com.antgskds.calendarassistant.feature.cloudsync.application.WebDavConnectionCoordinator
import com.antgskds.calendarassistant.feature.cloudsync.application.WebDavSyncV2Coordinator
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionInput
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2RuntimeStatus
import com.antgskds.calendarassistant.feature.weather.domain.WeatherApiAdapter
import com.antgskds.calendarassistant.feature.weather.api.WeatherOperationApi
import com.antgskds.calendarassistant.feature.weather.api.WeatherQueryApi
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import com.antgskds.calendarassistant.feature.settings.diagnostics.application.DiagnosticLogExporter
import com.antgskds.calendarassistant.shared.operation.AgentAttachmentInfo
import com.antgskds.calendarassistant.shared.operation.AgentConfiguration
import com.antgskds.calendarassistant.shared.operation.AgentConfigurationOption
import com.antgskds.calendarassistant.shared.operation.AgentConnectionResult
import com.antgskds.calendarassistant.shared.operation.AgentConnectionSummary
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseDelete
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseMutationResult
import com.antgskds.calendarassistant.shared.operation.AgentDatabasePage
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseQuery
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseTable
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseUpdate
import com.antgskds.calendarassistant.shared.operation.AgentExportedFile
import com.antgskds.calendarassistant.shared.operation.AgentModelConnectionInput
import com.antgskds.calendarassistant.shared.operation.AgentModelConnectionSummary
import com.antgskds.calendarassistant.shared.operation.AgentSyncStatus
import com.antgskds.calendarassistant.shared.operation.AgentWeatherConnectionInput
import com.antgskds.calendarassistant.shared.operation.AgentWeatherConnectionSummary
import com.antgskds.calendarassistant.shared.operation.AgentWeatherDay
import com.antgskds.calendarassistant.shared.operation.AgentWeatherHour
import com.antgskds.calendarassistant.shared.operation.AgentWeatherSnapshot
import com.antgskds.calendarassistant.shared.operation.AgentWebDavConnectionInput
import com.antgskds.calendarassistant.shared.operation.AgentWebDavConnectionSummary
import com.antgskds.calendarassistant.shared.operation.AgentAttendee
import com.antgskds.calendarassistant.shared.operation.AgentCourse
import com.antgskds.calendarassistant.shared.operation.AgentCourseDraft
import com.antgskds.calendarassistant.shared.operation.AgentDataApi
import com.antgskds.calendarassistant.shared.operation.AgentEvent
import com.antgskds.calendarassistant.shared.operation.AgentEventDraft
import com.antgskds.calendarassistant.shared.operation.AgentEventQuery
import com.antgskds.calendarassistant.shared.operation.AgentEventState
import com.antgskds.calendarassistant.shared.operation.AgentFileInput
import com.antgskds.calendarassistant.shared.operation.AgentQuickMemo
import com.antgskds.calendarassistant.shared.operation.AgentQuickMemoDraft
import com.antgskds.calendarassistant.shared.operation.AgentQuickMemoPatch
import com.antgskds.calendarassistant.shared.operation.AgentQuickMemoQuery
import com.antgskds.calendarassistant.shared.operation.AgentRecurringMode
import com.antgskds.calendarassistant.shared.operation.AgentSystemInfo
import com.antgskds.calendarassistant.shared.operation.WillDoAgentContract
import com.antgskds.calendarassistant.shared.operation.SettingsOperationApi
import com.antgskds.calendarassistant.shared.management.catalog.AgentConfigAccess
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.management.catalog.ConfigControl
import com.antgskds.calendarassistant.shared.management.catalog.ConfigItem
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import java.io.File
import java.net.URI
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first

class AgentDataService(
    private val appContext: Context,
    private val scheduleFacade: ScheduleFacade,
    private val quickMemoFacade: QuickMemoFacade,
    private val eventAttachmentManager: EventAttachmentManager,
    private val settingsQueryApi: SettingsQueryApi,
    private val settingsOperationApi: SettingsOperationApi,
    private val webDavConnectionCoordinator: WebDavConnectionCoordinator,
    private val webDavSyncCoordinator: WebDavSyncV2Coordinator,
    private val databaseService: AgentDatabaseService,
    private val weatherQueryApi: WeatherQueryApi,
    private val weatherOperationApi: WeatherOperationApi,
    private val diagnosticLogExporter: DiagnosticLogExporter,
    private val backupCoordinator: BackupCoordinator,
) : AgentDataApi {

    fun accessState(): AgentAccessState {
        val settings = settingsQueryApi.settings.value
        return AgentAccessState(
            accessEnabled = settings.agentApiEnabled,
            connectionManagementEnabled = settings.agentApiEnabled && settings.agentConnectionManagementEnabled,
            databaseOperationsEnabled = settings.agentApiEnabled && settings.agentDatabaseOperationsEnabled,
        )
    }

    private suspend fun <T> withPermissionCheck(block: suspend () -> T): Result<T> = try {
        check(settingsQueryApi.settings.value.agentApiEnabled) { "Agent API is disabled" }
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun createEvent(draft: AgentEventDraft): Result<Long> = withPermissionCheck {
        createEventInternal(draft)
    }

    override suspend fun batchCreateEvents(drafts: List<AgentEventDraft>): Result<List<Long>> = withPermissionCheck {
        require(drafts.size <= WillDoAgentContract.MAX_BATCH_SIZE) { "Too many events in one batch" }
        drafts.forEach(::validateEventDraft)
        val created = mutableListOf<Long>()
        try {
            drafts.forEach { created += createEventInternal(it) }
            created
        } catch (error: Throwable) {
            created.asReversed().forEach { id ->
                runCatching { eventAttachmentManager.deleteAttachmentsForEvent(id) }
                runCatching { scheduleFacade.deleteEvent(id) }
            }
            throw error
        }
    }

    override suspend fun getEvent(id: Long): Result<AgentEvent> = withPermissionCheck {
        val event = scheduleFacade.getEventById(id) ?: throw NoSuchElementException("Event $id not found")
        eventToAgentEvent(event)
    }

    override suspend fun queryEvents(query: AgentEventQuery): Result<List<AgentEvent>> = withPermissionCheck {
        val active = scheduleFacade.getLatestActiveEvents()
        val source = if (query.includeArchived) active + scheduleFacade.getLatestArchivedEvents() else active
        AgentEventQueryResolver.resolve(source, query)
            .asSequence()
            .map(::eventToAgentEvent)
            .toList()
    }

    override suspend fun updateEvent(id: Long, draft: AgentEventDraft): Result<Unit> = withPermissionCheck {
        validateEventDraft(draft)
        val existing = scheduleFacade.getEventById(id) ?: throw NoSuchElementException("Event $id not found")
        scheduleFacade.updateEvent(applyDraft(existing, draft))
        replaceAttachmentsIfRequested(id, draft.attachments, draft.replaceAttachments)
    }

    override suspend fun deleteEvent(id: Long): Result<Unit> = withPermissionCheck {
        requireNotNull(scheduleFacade.getEventById(id)) { "Event $id not found" }
        scheduleFacade.deleteEvent(id)
    }

    override suspend fun editRecurringEvent(
        parentId: Long,
        occurrenceTs: Long,
        mode: AgentRecurringMode,
        draft: AgentEventDraft
    ): Result<Long?> = withPermissionCheck {
        validateEventDraft(draft)
        val parent = scheduleFacade.getEventById(parentId)
            ?: throw NoSuchElementException("Recurring event $parentId not found")
        require(parent.isRecurring) { "Event $parentId is not recurring" }
        val result = scheduleFacade.editRecurringEvent(
            parentEventId = parentId,
            editedEvent = applyDraft(parent.copy(id = null), draft),
            mode = mode.toDomain(),
            occurrenceTs = occurrenceTs
        )
        val attachmentTarget = when (mode) {
            AgentRecurringMode.THIS -> result
            AgentRecurringMode.THIS_AND_FUTURE -> result
            AgentRecurringMode.ALL -> parentId
        }
        if (attachmentTarget != null) {
            replaceAttachmentsIfRequested(
                attachmentTarget,
                draft.attachments,
                draft.replaceAttachments
            )
        }
        result
    }

    override suspend fun deleteRecurringEvent(
        parentId: Long,
        occurrenceTs: Long,
        mode: AgentRecurringMode
    ): Result<Unit> = withPermissionCheck {
        val parent = scheduleFacade.getEventById(parentId)
            ?: throw NoSuchElementException("Recurring event $parentId not found")
        require(parent.isRecurring) { "Event $parentId is not recurring" }
        scheduleFacade.deleteRecurringEvent(
            parentId,
            mode.toDomain(),
            occurrenceTs
        )
    }

    override suspend fun setEventState(
        id: Long,
        occurrenceTs: Long?,
        state: AgentEventState
    ): Result<Long> = withPermissionCheck {
        requireNotNull(scheduleFacade.getEventById(id)) { "Event $id not found" }
        scheduleFacade.setEventState(
            id,
            occurrenceTs,
            state.toDomainState()
        )
    }

    override suspend fun archiveEvent(id: Long, occurrenceTs: Long?): Result<Unit> = withPermissionCheck {
        val event = scheduleFacade.getEventById(id) ?: throw NoSuchElementException("Event $id not found")
        if (occurrenceTs != null && event.isRecurring) {
            scheduleFacade.archiveItem(
                com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem.ActionTarget
                    .RecurringOccurrence(
                        id,
                        occurrenceTs
                    )
            )
        } else {
            scheduleFacade.archiveEvent(id)
        }
    }

    override suspend fun restoreEvent(id: Long): Result<Unit> = withPermissionCheck {
        scheduleFacade.restoreEvent(id)
    }

    override suspend fun addEventAttachment(
        eventId: Long,
        input: AgentFileInput
    ): Result<AgentAttachmentInfo> = withPermissionCheck {
        requireNotNull(scheduleFacade.getEventById(eventId)) { "Event $eventId not found" }
        validateContentUri(input.contentUri)
        eventAttachmentManager.addManualAttachment(eventId, Uri.parse(input.contentUri)).toAgentAttachment()
    }

    override suspend fun listEventAttachments(eventId: Long): Result<List<AgentAttachmentInfo>> = withPermissionCheck {
        requireNotNull(scheduleFacade.getEventById(eventId)) { "Event $eventId not found" }
        eventAttachmentManager.getAttachments(eventId).map { it.toAgentAttachment() }
    }

    override suspend fun deleteEventAttachment(attachmentId: Long): Result<Unit> = withPermissionCheck {
        val attachment = eventAttachmentManager.getAttachmentsByIds(listOf(attachmentId)).firstOrNull()
            ?: throw NoSuchElementException("Attachment $attachmentId not found")
        eventAttachmentManager.deleteAttachment(attachment)
    }

    override suspend fun createCourse(draft: AgentCourseDraft): Result<String> = withPermissionCheck {
        createCourseInternal(draft)
    }

    override suspend fun batchCreateCourses(drafts: List<AgentCourseDraft>): Result<List<String>> = withPermissionCheck {
        require(drafts.size <= WillDoAgentContract.MAX_BATCH_SIZE) { "Too many courses in one batch" }
        drafts.forEach(::validateCourseDraft)
        val created = mutableListOf<String>()
        try {
            drafts.forEach { created += createCourseInternal(it) }
            created
        } catch (error: Throwable) {
            created.asReversed().forEach { id -> runCatching { deleteCourseInternal(id) } }
            throw error
        }
    }

    override suspend fun getCourse(id: String): Result<AgentCourse> = withPermissionCheck {
        findCourse(id) ?: throw NoSuchElementException("Course $id not found")
    }

    override suspend fun queryCourses(dayOfWeek: Int?): Result<List<AgentCourse>> = withPermissionCheck {
        require(dayOfWeek == null || dayOfWeek in 1..7) { "dayOfWeek must be 1..7" }
        val settings = settingsQueryApi.settings.first()
        CourseEventMapper.extractParentCourses(scheduleFacade.getLatestActiveEvents(), settings)
            .filter { dayOfWeek == null || it.dayOfWeek == dayOfWeek }
            .map(::courseToAgentCourse)
    }

    override suspend fun updateCourse(id: String, draft: AgentCourseDraft): Result<Unit> = withPermissionCheck {
        validateCourseDraft(draft)
        val allEvents = scheduleFacade.getLatestActiveEvents()
        val existing = CourseEventMapper.findParentByCourseId(allEvents, id)
            ?: throw NoSuchElementException("Course $id not found")
        val settings = settingsQueryApi.settings.first()
        scheduleFacade.updateEvent(CourseEventMapper.toParentEvent(draftToCourse(draft).copy(id = id), settings, existing))
    }

    override suspend fun deleteCourse(id: String): Result<Unit> = withPermissionCheck {
        deleteCourseInternal(id)
    }

    override suspend fun createQuickMemo(draft: AgentQuickMemoDraft): Result<Long> = withPermissionCheck {
        when (draft.type.uppercase()) {
            QuickMemoType.TEXT -> quickMemoFacade.createTextMemo(draft.bodyText, draft.asTodo)
            QuickMemoType.IMAGE -> {
                val input = requireNotNull(draft.media) { "Image memo requires media.contentUri" }
                val file = copyInputToQuickMemoStore(input, "image")
                runCatching { quickMemoFacade.createImageMemo(file.absolutePath, draft.bodyText, draft.asTodo) }
                    .getOrElse { file.delete(); throw it }
            }
            QuickMemoType.VOICE -> {
                val input = requireNotNull(draft.media) { "Voice memo requires media.contentUri" }
                val file = copyInputToQuickMemoStore(input, "voice")
                runCatching {
                    quickMemoFacade.createVoiceMemo(file.absolutePath, draft.durationMs, draft.bodyText, draft.asTodo)
                }.getOrElse { file.delete(); throw it }
            }
            else -> throw IllegalArgumentException("Unsupported quick memo type: ${draft.type}")
        }
    }

    override suspend fun getQuickMemo(id: Long): Result<AgentQuickMemo> = withPermissionCheck {
        val memo = quickMemoFacade.getQuickMemo(id) ?: throw NoSuchElementException("Quick memo $id not found")
        memo.toAgentQuickMemo()
    }

    override suspend fun queryQuickMemos(query: AgentQuickMemoQuery): Result<List<AgentQuickMemo>> = withPermissionCheck {
        val needle = query.text?.trim()?.takeIf { it.isNotEmpty() }
        quickMemoFacade.quickMemos.first().asSequence()
            .filter { query.type == null || it.type.equals(query.type, ignoreCase = true) }
            .filter { query.todoState == null || it.todoState.equals(query.todoState, ignoreCase = true) }
            .filter { needle == null || it.bodyText.contains(needle, ignoreCase = true) }
            .take(query.limit.coerceIn(1, WillDoAgentContract.MAX_QUERY_LIMIT))
            .map { it.toAgentQuickMemo() }
            .toList()
    }

    override suspend fun updateQuickMemo(id: Long, patch: AgentQuickMemoPatch): Result<Unit> = withPermissionCheck {
        requireNotNull(quickMemoFacade.getQuickMemo(id)) { "Quick memo $id not found" }
        patch.bodyText?.let { quickMemoFacade.updateBody(id, it) }
        patch.todoState?.let { state ->
            when (state.uppercase()) {
                QuickMemoTodoState.NONE -> quickMemoFacade.removeTodo(id)
                QuickMemoTodoState.ACTIVE -> quickMemoFacade.markTodoActive(id)
                QuickMemoTodoState.COMPLETED -> {
                    val current = quickMemoFacade.getQuickMemo(id)
                    if (current?.todoState != QuickMemoTodoState.COMPLETED) quickMemoFacade.toggleTodoCompletion(id)
                }
                else -> throw IllegalArgumentException("Unsupported todoState: $state")
            }
        }
    }

    override suspend fun attachQuickMemoImage(id: Long, input: AgentFileInput): Result<Unit> = withPermissionCheck {
        requireNotNull(quickMemoFacade.getQuickMemo(id)) { "Quick memo $id not found" }
        val file = copyInputToQuickMemoStore(input, "image")
        runCatching { quickMemoFacade.attachImageToMemo(id, file.absolutePath) }
            .getOrElse { file.delete(); throw it }
    }

    override suspend fun removeQuickMemoImage(id: Long): Result<Unit> = withPermissionCheck {
        check(quickMemoFacade.removeImageFromMemo(id)) { "Quick memo $id has no image" }
    }

    override suspend fun attachQuickMemoVoice(
        id: Long,
        input: AgentFileInput,
        durationMs: Long
    ): Result<Unit> = withPermissionCheck {
        requireNotNull(quickMemoFacade.getQuickMemo(id)) { "Quick memo $id not found" }
        val file = copyInputToQuickMemoStore(input, "voice")
        val attached = runCatching { quickMemoFacade.attachVoiceToMemo(id, file.absolutePath, durationMs) }
            .getOrElse { file.delete(); throw it }
        if (!attached) {
            file.delete()
            error("Unable to attach voice to quick memo $id")
        }
    }

    override suspend fun setQuickMemoPinned(id: Long, pinned: Boolean): Result<Unit> = withPermissionCheck {
        val changed = if (pinned) quickMemoFacade.pinQuickMemo(id) else quickMemoFacade.clearPinnedTextQuickMemo(id)
        check(changed) { "Unable to ${if (pinned) "pin" else "unpin"} quick memo $id" }
    }

    override suspend fun deleteQuickMemo(id: Long): Result<Unit> = withPermissionCheck {
        requireNotNull(quickMemoFacade.getQuickMemo(id)) { "Quick memo $id not found" }
        quickMemoFacade.deleteQuickMemo(id)
    }

    override suspend fun listConfigurations(): Result<List<AgentConfiguration>> = withPermissionCheck {
        val settings = settingsQueryApi.settings.value
        ConfigCatalog.editableItems()
            .asSequence()
            .filter { it.agentAccess != AgentConfigAccess.NONE && it.visible(settings) }
            .map { it.toAgentConfiguration(settings) }
            .toList()
    }

    override suspend fun updateConfiguration(key: String, value: Int): Result<AgentConfiguration> = withPermissionCheck {
        val current = settingsQueryApi.settings.value
        val item = ConfigCatalog.editableItems().firstOrNull { it.key == key }
            ?: throw NoSuchElementException("Configuration $key not found")
        if (item.agentAccess != AgentConfigAccess.READ_WRITE) {
            throw SecurityException("Configuration $key is read-only")
        }
        check(item.visible(current)) { "Configuration $key is not currently available" }
        item.validateAgentValue(value)
        val updated = item.set(current, value)
        settingsOperationApi.updateSettings(updated)
        item.toAgentConfiguration(updated)
    }

    override suspend fun getConnectionSummary(): Result<AgentConnectionSummary> = withPermissionCheck {
        buildConnectionSummary()
    }

    override suspend fun updateModelConnection(
        input: AgentModelConnectionInput,
    ): Result<AgentConnectionSummary> = withPermissionCheck {
        requireConnectionManagement()
        val current = settingsQueryApi.settings.value
        val mode = input.mode.trim().uppercase()
        require(mode == "TEXT" || mode == "MULTIMODAL") { "mode must be TEXT or MULTIMODAL" }
        val apiUrl = input.apiUrl.trim()
        val modelName = input.modelName.trim()
        require(apiUrl.isNotBlank()) { "API URL is required" }
        require(modelName.isNotBlank()) { "Model name is required" }
        requireHttpUrl(apiUrl, "API URL")
        val existingKey = if (mode == "MULTIMODAL") current.mmModelKey else current.modelKey
        val apiKey = input.apiKey.trim().ifBlank { existingKey }
        require(apiKey.isNotBlank()) { "API Key is required" }
        val updated = if (mode == "MULTIMODAL") {
            current.copy(mmModelUrl = apiUrl, mmModelName = modelName, mmModelKey = apiKey)
        } else {
            current.copy(modelUrl = apiUrl, modelName = modelName, modelKey = apiKey)
        }
        settingsOperationApi.updateSettings(updated)
        buildConnectionSummary(updated)
    }

    override suspend fun updateWeatherConnection(
        input: AgentWeatherConnectionInput,
    ): Result<AgentConnectionSummary> = withPermissionCheck {
        requireConnectionManagement()
        val current = settingsQueryApi.settings.value
        val provider = WeatherApiAdapter.normalizeProvider(input.provider.trim())
        val existingUrl = when (provider) {
            WeatherApiAdapter.PROVIDER_CAIYUN -> current.weatherCaiyunApiUrl.ifBlank {
                current.weatherApiUrl.takeIf {
                    WeatherApiAdapter.normalizeProvider(current.weatherProvider) == WeatherApiAdapter.PROVIDER_CAIYUN
                }.orEmpty()
            }
            else -> current.weatherQWeatherApiUrl.ifBlank {
                current.weatherApiUrl.takeIf {
                    WeatherApiAdapter.normalizeProvider(current.weatherProvider) == WeatherApiAdapter.PROVIDER_QWEATHER
                }.orEmpty()
            }
        }
        val existingCredential = when (provider) {
            WeatherApiAdapter.PROVIDER_CAIYUN -> current.weatherCaiyunToken.ifBlank {
                current.weatherApiKey.takeIf {
                    WeatherApiAdapter.normalizeProvider(current.weatherProvider) == WeatherApiAdapter.PROVIDER_CAIYUN
                }.orEmpty()
            }
            else -> current.weatherQWeatherApiKey.ifBlank {
                current.weatherApiKey.takeIf {
                    WeatherApiAdapter.normalizeProvider(current.weatherProvider) == WeatherApiAdapter.PROVIDER_QWEATHER
                }.orEmpty()
            }
        }
        val apiUrl = input.apiUrl.trim().ifBlank { existingUrl }
        val credential = input.credential.trim().ifBlank { existingCredential }
        require(apiUrl.isNotBlank()) { "Weather API URL is required" }
        require(credential.isNotBlank()) { "Weather credential is required" }
        requireHttpUrl(apiUrl, "Weather API URL")
        val updated = current.copy(
            weatherEnabled = input.enabled,
            weatherProvider = provider,
            weatherApiUrl = apiUrl,
            weatherApiKey = credential,
            weatherQWeatherApiUrl = if (provider == WeatherApiAdapter.PROVIDER_QWEATHER) apiUrl else current.weatherQWeatherApiUrl,
            weatherQWeatherApiKey = if (provider == WeatherApiAdapter.PROVIDER_QWEATHER) credential else current.weatherQWeatherApiKey,
            weatherCaiyunApiUrl = if (provider == WeatherApiAdapter.PROVIDER_CAIYUN) apiUrl else current.weatherCaiyunApiUrl,
            weatherCaiyunToken = if (provider == WeatherApiAdapter.PROVIDER_CAIYUN) credential else current.weatherCaiyunToken,
        )
        settingsOperationApi.updateSettings(updated)
        buildConnectionSummary(updated)
    }

    override suspend fun testAndSaveWebDavConnection(
        input: AgentWebDavConnectionInput,
    ): Result<AgentConnectionResult> = withPermissionCheck {
        requireConnectionManagement()
        requireHttpUrl(input.baseUrl.trim(), "WebDAV URL")
        val result = webDavConnectionCoordinator.testAndSave(
            WebDavConnectionInput(
                baseUrl = input.baseUrl,
                username = input.username,
                password = input.password,
                syncPassphrase = input.syncPassphrase,
            )
        )
        AgentConnectionResult(
            success = result.success,
            message = result.message,
            summary = buildConnectionSummary(),
        )
    }

    override suspend fun getSyncStatus(): Result<AgentSyncStatus> = withPermissionCheck {
        webDavSyncCoordinator.status.value.toAgentSyncStatus()
    }

    override suspend fun syncNow(): Result<AgentSyncStatus> = withPermissionCheck {
        webDavSyncCoordinator.syncNow(force = true).getOrThrow()
        webDavSyncCoordinator.status.value.toAgentSyncStatus()
    }

    override suspend fun listDatabaseTables(): Result<List<AgentDatabaseTable>> = withPermissionCheck {
        databaseService.listTables()
    }

    override suspend fun queryDatabase(input: AgentDatabaseQuery): Result<AgentDatabasePage> = withPermissionCheck {
        databaseService.query(input)
    }

    override suspend fun updateDatabaseRow(
        input: AgentDatabaseUpdate,
    ): Result<AgentDatabaseMutationResult> = withPermissionCheck {
        databaseService.update(input)
    }

    override suspend fun deleteDatabaseRows(
        input: AgentDatabaseDelete,
    ): Result<AgentDatabaseMutationResult> = withPermissionCheck {
        databaseService.delete(input)
    }

    override suspend fun getWeather(forceRefresh: Boolean): Result<AgentWeatherSnapshot?> = withPermissionCheck {
        val settings = settingsQueryApi.settings.value
        val weather = when {
            forceRefresh -> weatherOperationApi.forceRefresh(settings).getOrThrow()
            weatherQueryApi.weatherData.value != null -> weatherQueryApi.weatherData.value
            else -> weatherOperationApi.refreshIfNeeded(settings).getOrThrow()
        }
        weather?.toAgentWeatherSnapshot()
    }

    override suspend fun exportDiagnosticLogs(minutes: Int): Result<AgentExportedFile> = withPermissionCheck {
        val id = UUID.randomUUID().toString()
        val target = prepareAgentExportFile(id, "txt")
        diagnosticLogExporter.exportAgentLogBundle(target, minutes).getOrThrow()
        target.toAgentExportedFile(
            id = id,
            displayName = "willdo_diagnostics_${minutes}min.txt",
            mimeType = "text/plain",
        )
    }

    override suspend fun exportBackup(includeSettings: Boolean): Result<AgentExportedFile> = withPermissionCheck {
        if (includeSettings) requireConnectionManagement()
        val id = UUID.randomUUID().toString()
        val target = prepareAgentExportFile(id, "zip")
        val fileProviderUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            target,
        )
        backupCoordinator.exportBackupZip(
            uri = fileProviderUri,
            options = AppBackupOptions(
                includeEvents = true,
                includeSettings = includeSettings,
                includeAttachments = true,
                includePrompts = includeSettings,
                includeQuickMemos = true,
            ),
        )
        require(target.isFile && target.length() > 0L) { "Backup export did not produce a file" }
        target.toAgentExportedFile(
            id = id,
            displayName = "willdo_backup.zip",
            mimeType = "application/zip",
        )
    }

    override suspend fun getSettings(): Result<Map<String, Any>> = withPermissionCheck {
        val settings = settingsQueryApi.settings.first()
        mapOf(
            "showTomorrowEvents" to settings.showTomorrowEvents,
            "isDailySummaryEnabled" to settings.isDailySummaryEnabled,
            "isAdvanceReminderEnabled" to settings.isAdvanceReminderEnabled,
            "advanceReminderMinutes" to settings.advanceReminderMinutes,
            "courseFeatureEnabled" to settings.courseFeatureEnabled,
            "isDarkMode" to settings.isDarkMode,
            "themeMode" to settings.themeMode
        )
    }

    override suspend fun getSystemInfo(): Result<AgentSystemInfo> = withPermissionCheck {
        AgentSystemInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            agentApiVersion = WillDoAgentContract.PROTOCOL_VERSION,
            hasCalendarPermission = hasPermission(Manifest.permission.READ_CALENDAR) &&
                hasPermission(Manifest.permission.WRITE_CALENDAR),
            hasNotificationPermission = hasPermission(Manifest.permission.POST_NOTIFICATIONS),
            enabled = true
        )
    }

    suspend fun resolveEventAttachmentFile(id: Long): Result<File> = withPermissionCheck {
        val attachment = eventAttachmentManager.getAttachmentsByIds(listOf(id)).firstOrNull()
            ?: throw NoSuchElementException("Attachment $id not found")
        File(attachment.localPath).also { require(it.isFile) { "Attachment file is missing" } }
    }

    suspend fun resolveQuickMemoMediaFile(id: Long, kind: String): Result<File> = withPermissionCheck {
        val memo = quickMemoFacade.getQuickMemo(id) ?: throw NoSuchElementException("Quick memo $id not found")
        val path = when (kind) {
            "image" -> memo.imagePath
            "audio" -> memo.audioPath
            else -> null
        } ?: throw NoSuchElementException("Quick memo $id has no $kind")
        File(path).also { require(it.isFile) { "Quick memo media file is missing" } }
    }

    fun resolveAgentExportFile(id: String): Result<File> = runCatching {
        check(settingsQueryApi.settings.value.agentApiEnabled) { "Agent API is disabled" }
        UUID.fromString(id)
        val directory = agentExportDirectory()
        listOf("zip", "txt")
            .asSequence()
            .map { extension -> File(directory, "$id.$extension") }
            .firstOrNull { it.isFile }
            ?: throw NoSuchElementException("Agent export $id not found")
    }

    private fun ConfigItem.validateAgentValue(value: Int) {
        when (val itemControl = control) {
            ConfigControl.Toggle -> require(value == 0 || value == 1) { "$key only accepts 0 or 1" }
            is ConfigControl.IntOptions -> require(itemControl.options.any { it.value == value }) {
                "$key does not accept value $value"
            }
            is ConfigControl.IntInput -> {
                require(value in itemControl.min..itemControl.max) {
                    "$key must be between ${itemControl.min} and ${itemControl.max}"
                }
                require((value - itemControl.min) % itemControl.step == 0) {
                    "$key must use step ${itemControl.step}"
                }
            }
        }
    }

    private fun ConfigItem.toAgentConfiguration(
        settings: com.antgskds.calendarassistant.feature.settings.data.model.MySettings,
    ): AgentConfiguration {
        val itemControl = control
        return AgentConfiguration(
            key = key,
            label = label,
            description = description,
            domain = domain.name,
            domainLabel = domain.label,
            kind = kind.name,
            exposure = exposure.name,
            control = when (itemControl) {
                ConfigControl.Toggle -> "TOGGLE"
                is ConfigControl.IntOptions -> "INT_OPTIONS"
                is ConfigControl.IntInput -> "INT_INPUT"
            },
            value = get(settings),
            options = (itemControl as? ConfigControl.IntOptions)?.options.orEmpty().map {
                AgentConfigurationOption(it.value, it.label)
            },
            min = (itemControl as? ConfigControl.IntInput)?.min,
            max = (itemControl as? ConfigControl.IntInput)?.max,
            step = (itemControl as? ConfigControl.IntInput)?.step,
            unitLabel = (itemControl as? ConfigControl.IntInput)?.unitLabel.orEmpty(),
            writable = agentAccess == AgentConfigAccess.READ_WRITE,
        )
    }

    private fun requireConnectionManagement() {
        if (!settingsQueryApi.settings.value.agentConnectionManagementEnabled) {
            throw SecurityException("Agent connection management is disabled")
        }
    }

    private fun buildConnectionSummary(
        settings: com.antgskds.calendarassistant.feature.settings.data.model.MySettings = settingsQueryApi.settings.value,
    ): AgentConnectionSummary {
        val weatherProvider = WeatherApiAdapter.normalizeProvider(settings.weatherProvider)
        val weatherUrl = when (weatherProvider) {
            WeatherApiAdapter.PROVIDER_CAIYUN -> settings.weatherCaiyunApiUrl.ifBlank { settings.weatherApiUrl }
            else -> settings.weatherQWeatherApiUrl.ifBlank { settings.weatherApiUrl }
        }
        val weatherCredential = when (weatherProvider) {
            WeatherApiAdapter.PROVIDER_CAIYUN -> settings.weatherCaiyunToken.ifBlank { settings.weatherApiKey }
            else -> settings.weatherQWeatherApiKey.ifBlank { settings.weatherApiKey }
        }
        return AgentConnectionSummary(
            textModel = AgentModelConnectionSummary(
                mode = "TEXT",
                modelName = settings.modelName,
                endpoint = safeEndpoint(settings.modelUrl),
                credentialConfigured = settings.modelKey.isNotBlank(),
            ),
            multimodalModel = AgentModelConnectionSummary(
                mode = "MULTIMODAL",
                modelName = settings.mmModelName,
                endpoint = safeEndpoint(settings.mmModelUrl),
                credentialConfigured = settings.mmModelKey.isNotBlank(),
            ),
            weather = AgentWeatherConnectionSummary(
                provider = weatherProvider,
                enabled = settings.weatherEnabled,
                endpoint = safeEndpoint(weatherUrl),
                credentialConfigured = weatherCredential.isNotBlank(),
                locationMode = settings.weatherLocationMode,
                locationName = settings.weatherManualLocationName.ifBlank { settings.weatherCity },
            ),
            webDav = AgentWebDavConnectionSummary(
                endpoint = safeEndpoint(settings.webDavBaseUrl),
                usernameConfigured = settings.webDavUsername.isNotBlank(),
                passwordConfigured = webDavConnectionCoordinator.hasStoredPassword(),
                syncPassphraseConfigured = webDavConnectionCoordinator.hasStoredSyncPassphrase(),
                syncEnabled = settings.webDavSyncEnabled,
                wifiOnly = settings.webDavWifiOnly,
            ),
        )
    }

    private fun requireHttpUrl(value: String, label: String) {
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("$label is invalid") }
        require((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) {
            "$label must use HTTP or HTTPS"
        }
    }

    private fun safeEndpoint(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val uri = URI(value.trim())
            if ((uri.scheme != "http" && uri.scheme != "https") || uri.host.isNullOrBlank()) return@runCatching "已配置"
            buildString {
                append(uri.scheme)
                append("://")
                append(uri.host)
                if (uri.port >= 0) append(":${uri.port}")
            }
        }.getOrDefault("已配置")
    }

    private fun SyncV2RuntimeStatus.toAgentSyncStatus(): AgentSyncStatus = AgentSyncStatus(
        phase = phase.name,
        message = message,
        lastSuccessAt = lastSuccessAt,
        pendingAssetCount = pendingAssetCount,
        conflictCount = conflictCount,
    )

    private fun WeatherData.toAgentWeatherSnapshot(): AgentWeatherSnapshot = AgentWeatherSnapshot(
        temperature = temperature,
        feelsLike = feelsLike,
        text = text,
        windDirection = windDir,
        windScale = windScale,
        humidity = humidity,
        precipitation = precip,
        observationTime = obsTime,
        locationName = locationName.ifBlank { city },
        provider = provider,
        updateTime = updateTime,
        hourly = hourlyForecast.take(24).map {
            AgentWeatherHour(
                time = it.fxTime,
                temperature = it.temp,
                text = it.text,
                precipitationProbability = it.pop,
            )
        },
        daily = dailyForecast.take(10).map {
            AgentWeatherDay(
                date = it.fxDate,
                minimumTemperature = it.tempMin,
                maximumTemperature = it.tempMax,
                dayText = it.textDay,
                nightText = it.textNight,
            )
        },
        alerts = alerts.map { it.headline.ifBlank { it.eventName } } +
            riskAlerts.map { it.message.ifBlank { it.title } },
    )

    private fun prepareAgentExportFile(id: String, extension: String): File {
        val directory = agentExportDirectory()
        directory.mkdirs()
        directory.listFiles().orEmpty()
            .filter { System.currentTimeMillis() - it.lastModified() > AGENT_EXPORT_RETENTION_MS }
            .forEach { it.delete() }
        return File(directory, "$id.$extension").also { file ->
            require(file.canonicalFile.parentFile == directory.canonicalFile) { "Invalid export path" }
        }
    }

    private fun agentExportDirectory(): File = File(appContext.filesDir, "agent_exports")

    private fun File.toAgentExportedFile(
        id: String,
        displayName: String,
        mimeType: String,
    ): AgentExportedFile = AgentExportedFile(
        id = id,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = length(),
        contentUri = "${WillDoAgentContract.BASE_URI}/exports/$id",
    )

    private suspend fun createEventInternal(draft: AgentEventDraft): Long {
        validateEventDraft(draft)
        val id = scheduleFacade.addEvent(draftToEvent(draft))
        check(id > 0L && scheduleFacade.getEventById(id) != null) {
            "WillDo did not persist the created event"
        }
        try {
            draft.attachments.forEach { input ->
                validateContentUri(input.contentUri)
                eventAttachmentManager.addManualAttachment(id, Uri.parse(input.contentUri))
            }
        } catch (error: Throwable) {
            runCatching { eventAttachmentManager.deleteAttachmentsForEvent(id) }
            runCatching { scheduleFacade.deleteEvent(id) }
            throw error
        }
        return id
    }

    private fun replaceAttachmentsIfRequested(
        eventId: Long,
        inputs: List<AgentFileInput>,
        replaceAttachments: Boolean
    ) {
        if (!replaceAttachments && inputs.isEmpty()) return
        inputs.forEach { validateContentUri(it.contentUri) }
        val oldAttachments = eventAttachmentManager.getAttachments(eventId)
        val newAttachments = mutableListOf<EventAttachment>()
        try {
            inputs.forEach { input ->
                newAttachments += eventAttachmentManager.addManualAttachment(eventId, Uri.parse(input.contentUri))
            }
        } catch (error: Throwable) {
            newAttachments.forEach { attachment ->
                runCatching { eventAttachmentManager.deleteAttachment(attachment) }
            }
            throw error
        }
        oldAttachments.forEach(eventAttachmentManager::deleteAttachment)
    }

    private suspend fun createCourseInternal(draft: AgentCourseDraft): String {
        validateCourseDraft(draft)
        val settings = settingsQueryApi.settings.first()
        val course = draftToCourse(draft)
        val parent = CourseEventMapper.toParentEvent(course, settings)
        scheduleFacade.addEvent(parent)
        return CourseEventMapper.parseMeta(parent.description)?.uid ?: course.id
    }

    private suspend fun deleteCourseInternal(id: String) {
        val parent = CourseEventMapper.findParentByCourseId(scheduleFacade.getLatestActiveEvents(), id)
            ?: throw NoSuchElementException("Course $id not found")
        scheduleFacade.deleteEvent(requireNotNull(parent.id))
    }

    private suspend fun findCourse(id: String): AgentCourse? {
        val settings = settingsQueryApi.settings.first()
        val parent = CourseEventMapper.findParentByCourseId(scheduleFacade.getLatestActiveEvents(), id) ?: return null
        return CourseEventMapper.toCourse(parent, settings)?.let(::courseToAgentCourse)
    }

    private fun validateEventDraft(draft: AgentEventDraft) {
        require(draft.title.isNotBlank()) { "Event title is required" }
        require(draft.startTs >= 0L && draft.endTs >= draft.startTs) { "Invalid event time range" }
        require(draft.reminderMinutes.size <= 3) { "At most three reminders are supported" }
        require(draft.reminderMinutes.all { it >= 0 }) { "Reminder minutes must be non-negative" }
        if (draft.timeZone.isNotBlank()) ZoneId.of(draft.timeZone)
        draft.attachments.forEach { validateContentUri(it.contentUri) }
    }

    private fun validateCourseDraft(draft: AgentCourseDraft) {
        require(draft.name.isNotBlank()) { "Course name is required" }
        require(draft.dayOfWeek in 1..7) { "dayOfWeek must be 1..7" }
        require(draft.startNode >= 1 && draft.endNode >= draft.startNode) { "Invalid course node range" }
        require(draft.startWeek >= 1 && draft.endWeek >= draft.startWeek) { "Invalid course week range" }
        require(draft.weekType in 0..2) { "weekType must be 0, 1 or 2" }
    }

    private fun validateContentUri(value: String) {
        val uri = Uri.parse(value)
        require(uri.scheme == "content") { "Only content:// file URIs are accepted" }
        require(uri.authority?.isNotBlank() == true) { "File URI authority is required" }
    }

    private fun draftToEvent(draft: AgentEventDraft): Event = Event(
        id = null,
        title = draft.title.trim(),
        startTS = draft.startTs,
        endTS = draft.endTs,
        location = draft.location,
        description = draft.description,
        reminder1Minutes = reminderAt(draft.reminderMinutes, 0),
        reminder2Minutes = reminderAt(draft.reminderMinutes, 1),
        reminder3Minutes = reminderAt(draft.reminderMinutes, 2),
        rrule = draft.rrule,
        exdates = draft.exdates,
        attendees = draft.attendees.map { Attendee(name = it.name, email = it.email, status = it.status, isMe = it.isMe) },
        timeZone = draft.timeZone,
        flags = if (draft.isAllDay) FLAG_ALL_DAY else 0,
        tag = draft.tag,
        color = if (draft.color != 0) draft.color else DEFAULT_EVENT_COLOR,
        source = AGENT_SOURCE
    )

    private fun applyDraft(existing: Event, draft: AgentEventDraft): Event = existing.copy(
        title = draft.title.trim(),
        startTS = draft.startTs,
        endTS = draft.endTs,
        location = draft.location,
        description = draft.description,
        reminder1Minutes = reminderAt(draft.reminderMinutes, 0),
        reminder2Minutes = reminderAt(draft.reminderMinutes, 1),
        reminder3Minutes = reminderAt(draft.reminderMinutes, 2),
        rrule = draft.rrule,
        exdates = draft.exdates,
        attendees = draft.attendees.map { Attendee(name = it.name, email = it.email, status = it.status, isMe = it.isMe) },
        timeZone = draft.timeZone,
        flags = if (draft.isAllDay) existing.flags or FLAG_ALL_DAY else existing.flags and FLAG_ALL_DAY.inv(),
        tag = draft.tag,
        color = if (draft.color != 0) draft.color else existing.color
    )

    private fun eventToAgentEvent(event: Event): AgentEvent = AgentEvent(
        id = event.id ?: 0L,
        title = event.title,
        startTs = event.startTS,
        endTs = event.endTS,
        location = event.location,
        description = event.description,
        reminderMinutes = event.getReminders().map { it.minutes },
        rrule = event.rrule,
        exdates = event.exdates,
        timeZone = event.getTimeZoneString(),
        isAllDay = event.getIsAllDay(),
        tag = event.tag,
        color = event.color,
        state = when (event.state) {
            STATE_COMPLETED -> AgentEventState.COMPLETED.name
            STATE_CHECKED_IN -> AgentEventState.CHECKED_IN.name
            else -> AgentEventState.PENDING.name
        },
        parentId = event.parentId,
        isRecurring = event.isRecurring,
        isException = event.isException,
        archivedAt = event.archivedAt,
        attendees = event.attendees.map { AgentAttendee(it.name, it.email, it.status, it.isMe) },
        attachments = event.id?.let(eventAttachmentManager::getAttachments).orEmpty().map { it.toAgentAttachment() }
    )

    private fun EventAttachment.toAgentAttachment(): AgentAttachmentInfo = AgentAttachmentInfo(
        id = id ?: 0L,
        eventId = eventId ?: 0L,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        source = source,
        contentUri = "${WillDoAgentContract.BASE_URI}/event_attachments/${id ?: 0L}"
    )

    private fun QuickMemoEntity.toAgentQuickMemo(): AgentQuickMemo {
        val memoId = id ?: 0L
        return AgentQuickMemo(
            id = memoId,
            type = type,
            bodyText = bodyText,
            createdAtMs = createdAt,
            updatedAtMs = updatedAt,
            todoState = todoState,
            isPinned = quickMemoFacade.isQuickMemoPinned(memoId),
            audioDurationMs = audioDurationMs,
            audioContentUri = audioPath?.takeIf { it.isNotBlank() }
                ?.let { "${WillDoAgentContract.BASE_URI}/quick_memos/$memoId/audio" },
            imageContentUri = imagePath?.takeIf { it.isNotBlank() }
                ?.let { "${WillDoAgentContract.BASE_URI}/quick_memos/$memoId/image" }
        )
    }

    private fun draftToCourse(draft: AgentCourseDraft): Course = Course(
        id = "",
        name = draft.name.trim(),
        dayOfWeek = draft.dayOfWeek,
        startNode = draft.startNode,
        endNode = draft.endNode,
        startWeek = draft.startWeek,
        endWeek = draft.endWeek,
        weekType = draft.weekType,
        location = draft.location,
        teacher = draft.teacher,
        color = draft.color
    )

    private fun courseToAgentCourse(course: Course): AgentCourse = AgentCourse(
        id = course.id,
        name = course.name,
        dayOfWeek = course.dayOfWeek,
        startNode = course.startNode,
        endNode = course.endNode,
        startWeek = course.startWeek,
        endWeek = course.endWeek,
        weekType = course.weekType,
        location = course.location,
        teacher = course.teacher,
        color = course.color
    )

    private fun copyInputToQuickMemoStore(input: AgentFileInput, fallbackName: String): File {
        validateContentUri(input.contentUri)
        val uri = Uri.parse(input.contentUri)
        val sourceName = input.displayName.ifBlank { queryDisplayName(uri) ?: fallbackName }
        val safeName = sourceName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifBlank { fallbackName }
        val target = File(appContext.filesDir, "quick_memos/${System.currentTimeMillis()}_$safeName")
        target.parentFile?.mkdirs()
        appContext.contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream) { "Unable to open $uri" }
            target.outputStream().use { output -> inputStream.copyTo(output) }
        }
        return target
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }
    }.getOrNull()

    private fun reminderAt(minutes: List<Int>, index: Int): Int = minutes.getOrNull(index) ?: REMINDER_OFF
    private fun AgentRecurringMode.toDomain(): RecurringMode = RecurringMode.valueOf(name)
    private fun AgentEventState.toDomainState(): Int = when (this) {
        AgentEventState.PENDING -> STATE_PENDING
        AgentEventState.COMPLETED -> STATE_COMPLETED
        AgentEventState.CHECKED_IN -> STATE_CHECKED_IN
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val AGENT_SOURCE = "agent"
        const val DEFAULT_EVENT_COLOR = 0xFF91A3B0.toInt()
        const val AGENT_EXPORT_RETENTION_MS = 24L * 60L * 60L * 1000L
    }
}

data class AgentAccessState(
    val accessEnabled: Boolean,
    val connectionManagementEnabled: Boolean,
    val databaseOperationsEnabled: Boolean,
)
