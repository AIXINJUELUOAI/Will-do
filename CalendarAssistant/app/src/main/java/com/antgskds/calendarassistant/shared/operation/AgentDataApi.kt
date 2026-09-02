package com.antgskds.calendarassistant.shared.operation

import kotlinx.serialization.Serializable

/**
 * Transport-neutral operations exposed to an official companion Agent app.
 *
 * Android IPC details stay in WillDoAgentProvider. Implementations must route
 * writes through the existing feature facades so reminders, UI refresh and
 * WebDAV change detection continue to work. All event timestamps use Unix
 * seconds, matching WillDo's internal event model.
 */
interface AgentDataApi {
    suspend fun createEvent(draft: AgentEventDraft): Result<Long>
    suspend fun batchCreateEvents(drafts: List<AgentEventDraft>): Result<List<Long>>
    suspend fun getEvent(id: Long): Result<AgentEvent>
    suspend fun queryEvents(query: AgentEventQuery): Result<List<AgentEvent>>
    suspend fun updateEvent(id: Long, draft: AgentEventDraft): Result<Unit>
    suspend fun deleteEvent(id: Long): Result<Unit>
    suspend fun editRecurringEvent(
        parentId: Long,
        occurrenceTs: Long,
        mode: AgentRecurringMode,
        draft: AgentEventDraft
    ): Result<Long?>
    suspend fun deleteRecurringEvent(
        parentId: Long,
        occurrenceTs: Long,
        mode: AgentRecurringMode
    ): Result<Unit>
    suspend fun setEventState(id: Long, occurrenceTs: Long?, state: AgentEventState): Result<Long>
    suspend fun archiveEvent(id: Long, occurrenceTs: Long? = null): Result<Unit>
    suspend fun restoreEvent(id: Long): Result<Unit>

    suspend fun addEventAttachment(eventId: Long, input: AgentFileInput): Result<AgentAttachmentInfo>
    suspend fun listEventAttachments(eventId: Long): Result<List<AgentAttachmentInfo>>
    suspend fun deleteEventAttachment(attachmentId: Long): Result<Unit>

    suspend fun createCourse(draft: AgentCourseDraft): Result<String>
    suspend fun batchCreateCourses(drafts: List<AgentCourseDraft>): Result<List<String>>
    suspend fun getCourse(id: String): Result<AgentCourse>
    suspend fun queryCourses(dayOfWeek: Int? = null): Result<List<AgentCourse>>
    suspend fun updateCourse(id: String, draft: AgentCourseDraft): Result<Unit>
    suspend fun deleteCourse(id: String): Result<Unit>

    suspend fun createQuickMemo(draft: AgentQuickMemoDraft): Result<Long>
    suspend fun getQuickMemo(id: Long): Result<AgentQuickMemo>
    suspend fun queryQuickMemos(query: AgentQuickMemoQuery): Result<List<AgentQuickMemo>>
    suspend fun updateQuickMemo(id: Long, patch: AgentQuickMemoPatch): Result<Unit>
    suspend fun attachQuickMemoImage(id: Long, input: AgentFileInput): Result<Unit>
    suspend fun removeQuickMemoImage(id: Long): Result<Unit>
    suspend fun attachQuickMemoVoice(id: Long, input: AgentFileInput, durationMs: Long): Result<Unit>
    suspend fun setQuickMemoPinned(id: Long, pinned: Boolean): Result<Unit>
    suspend fun deleteQuickMemo(id: Long): Result<Unit>

    suspend fun listConfigurations(): Result<List<AgentConfiguration>>
    suspend fun updateConfiguration(key: String, value: Int): Result<AgentConfiguration>
    suspend fun getConnectionSummary(): Result<AgentConnectionSummary>
    suspend fun updateModelConnection(input: AgentModelConnectionInput): Result<AgentConnectionSummary>
    suspend fun updateWeatherConnection(input: AgentWeatherConnectionInput): Result<AgentConnectionSummary>
    suspend fun testAndSaveWebDavConnection(input: AgentWebDavConnectionInput): Result<AgentConnectionResult>
    suspend fun getSyncStatus(): Result<AgentSyncStatus>
    suspend fun syncNow(): Result<AgentSyncStatus>
    suspend fun listDatabaseTables(): Result<List<AgentDatabaseTable>>
    suspend fun queryDatabase(input: AgentDatabaseQuery): Result<AgentDatabasePage>
    suspend fun updateDatabaseRow(input: AgentDatabaseUpdate): Result<AgentDatabaseMutationResult>
    suspend fun deleteDatabaseRows(input: AgentDatabaseDelete): Result<AgentDatabaseMutationResult>
    suspend fun getWeather(forceRefresh: Boolean): Result<AgentWeatherSnapshot?>
    suspend fun exportDiagnosticLogs(minutes: Int): Result<AgentExportedFile>
    suspend fun exportBackup(includeSettings: Boolean): Result<AgentExportedFile>
    suspend fun getSettings(): Result<Map<String, Any>>
    suspend fun getSystemInfo(): Result<AgentSystemInfo>
}

object WillDoAgentContract {
    const val WILLDO_PACKAGE = "com.antgskds.calendarassistant"
    const val AUTHORITY = "com.antgskds.calendarassistant.agent"
    const val BASE_URI = "content://$AUTHORITY"
    const val PERMISSION = "com.antgskds.calendarassistant.permission.AGENT_API"
    const val PROTOCOL_VERSION = 2
    const val REQUEST_KEY = "request"
    const val RESPONSE_KEY = "response"
    const val THIRD_PARTY_TRANSPORT_KEY = "thirdPartyTransport"
    const val MAX_BATCH_SIZE = 200
    const val MAX_QUERY_LIMIT = 200
    const val MAX_REQUEST_BYTES = 1_000_000

    const val GET_CAPABILITIES = "getCapabilities"
    const val CREATE_EVENT = "createEvent"
    const val BATCH_CREATE_EVENTS = "batchCreateEvents"
    const val GET_EVENT = "getEvent"
    const val QUERY_EVENTS = "queryEvents"
    const val UPDATE_EVENT = "updateEvent"
    const val DELETE_EVENT = "deleteEvent"
    const val EDIT_RECURRING_EVENT = "editRecurringEvent"
    const val DELETE_RECURRING_EVENT = "deleteRecurringEvent"
    const val SET_EVENT_STATE = "setEventState"
    const val ARCHIVE_EVENT = "archiveEvent"
    const val RESTORE_EVENT = "restoreEvent"
    const val ADD_EVENT_ATTACHMENT = "addEventAttachment"
    const val LIST_EVENT_ATTACHMENTS = "listEventAttachments"
    const val DELETE_EVENT_ATTACHMENT = "deleteEventAttachment"

    const val CREATE_COURSE = "createCourse"
    const val BATCH_CREATE_COURSES = "batchCreateCourses"
    const val GET_COURSE = "getCourse"
    const val QUERY_COURSES = "queryCourses"
    const val UPDATE_COURSE = "updateCourse"
    const val DELETE_COURSE = "deleteCourse"

    const val CREATE_QUICK_MEMO = "createQuickMemo"
    const val GET_QUICK_MEMO = "getQuickMemo"
    const val QUERY_QUICK_MEMOS = "queryQuickMemos"
    const val UPDATE_QUICK_MEMO = "updateQuickMemo"
    const val ATTACH_QUICK_MEMO_IMAGE = "attachQuickMemoImage"
    const val REMOVE_QUICK_MEMO_IMAGE = "removeQuickMemoImage"
    const val ATTACH_QUICK_MEMO_VOICE = "attachQuickMemoVoice"
    const val SET_QUICK_MEMO_PINNED = "setQuickMemoPinned"
    const val DELETE_QUICK_MEMO = "deleteQuickMemo"

    const val GET_SETTINGS = "getSettings"
    const val LIST_CONFIGURATIONS = "listConfigurations"
    const val UPDATE_CONFIGURATION = "updateConfiguration"
    const val GET_CONNECTION_SUMMARY = "getConnectionSummary"
    const val UPDATE_MODEL_CONNECTION = "updateModelConnection"
    const val UPDATE_WEATHER_CONNECTION = "updateWeatherConnection"
    const val TEST_AND_SAVE_WEBDAV_CONNECTION = "testAndSaveWebDavConnection"
    const val GET_SYNC_STATUS = "getSyncStatus"
    const val SYNC_NOW = "syncNow"
    const val LIST_DATABASE_TABLES = "listDatabaseTables"
    const val QUERY_DATABASE = "queryDatabase"
    const val UPDATE_DATABASE_ROW = "updateDatabaseRow"
    const val DELETE_DATABASE_ROWS = "deleteDatabaseRows"
    const val GET_WEATHER = "getWeather"
    const val EXPORT_DIAGNOSTIC_LOGS = "exportDiagnosticLogs"
    const val EXPORT_BACKUP = "exportBackup"
    const val GET_SYSTEM_INFO = "getSystemInfo"
}

@Serializable
data class AgentConfigurationOption(
    val value: Int,
    val label: String,
)

@Serializable
data class AgentConfiguration(
    val key: String,
    val label: String,
    val description: String,
    val domain: String,
    val domainLabel: String,
    val kind: String,
    val exposure: String,
    val control: String,
    val value: Int,
    val options: List<AgentConfigurationOption> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null,
    val unitLabel: String = "",
    val writable: Boolean,
)

@Serializable
data class AgentModelConnectionInput(
    val mode: String,
    val modelName: String,
    val apiUrl: String,
    val apiKey: String = "",
)

@Serializable
data class AgentWeatherConnectionInput(
    val provider: String,
    val apiUrl: String,
    val credential: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class AgentWebDavConnectionInput(
    val baseUrl: String,
    val username: String,
    val password: String = "",
    val syncPassphrase: String = "",
)

@Serializable
data class AgentModelConnectionSummary(
    val mode: String,
    val modelName: String,
    val endpoint: String,
    val credentialConfigured: Boolean,
)

@Serializable
data class AgentWeatherConnectionSummary(
    val provider: String,
    val enabled: Boolean,
    val endpoint: String,
    val credentialConfigured: Boolean,
    val locationMode: String,
    val locationName: String,
)

@Serializable
data class AgentWebDavConnectionSummary(
    val endpoint: String,
    val usernameConfigured: Boolean,
    val passwordConfigured: Boolean,
    val syncPassphraseConfigured: Boolean,
    val syncEnabled: Boolean,
    val wifiOnly: Boolean,
)

@Serializable
data class AgentConnectionSummary(
    val textModel: AgentModelConnectionSummary,
    val multimodalModel: AgentModelConnectionSummary,
    val weather: AgentWeatherConnectionSummary,
    val webDav: AgentWebDavConnectionSummary,
)

@Serializable
data class AgentConnectionResult(
    val success: Boolean,
    val message: String,
    val summary: AgentConnectionSummary,
)

@Serializable
data class AgentSyncStatus(
    val phase: String,
    val message: String,
    val lastSuccessAt: Long,
    val pendingAssetCount: Int,
    val conflictCount: Int,
)

@Serializable
data class AgentDatabaseColumn(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKeyPosition: Int,
)

@Serializable
data class AgentDatabaseTable(
    val name: String,
    val columns: List<AgentDatabaseColumn>,
    val primaryKeyColumns: List<String>,
    val rowCount: Long,
)

@Serializable
data class AgentDatabaseQuery(
    val table: String,
    val filters: Map<String, String?> = emptyMap(),
    val limit: Int = 50,
    val offset: Int = 0,
    val maxCellChars: Int = 16_000,
)

@Serializable
data class AgentDatabasePage(
    val table: String,
    val columns: List<AgentDatabaseColumn>,
    val primaryKeyColumns: List<String>,
    val rows: List<Map<String, String?>>,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val truncatedCellCount: Int,
)

@Serializable
data class AgentDatabaseUpdate(
    val table: String,
    val key: Map<String, String>,
    val values: Map<String, String?>,
)

@Serializable
data class AgentDatabaseDelete(
    val table: String,
    val keys: List<Map<String, String>>,
)

@Serializable
data class AgentDatabaseMutationResult(
    val table: String,
    val affectedRows: Int,
)

@Serializable
data class AgentWeatherHour(
    val time: String,
    val temperature: String,
    val text: String,
    val precipitationProbability: String,
)

@Serializable
data class AgentWeatherDay(
    val date: String,
    val minimumTemperature: String,
    val maximumTemperature: String,
    val dayText: String,
    val nightText: String,
)

@Serializable
data class AgentWeatherSnapshot(
    val temperature: String,
    val feelsLike: String,
    val text: String,
    val windDirection: String,
    val windScale: String,
    val humidity: String,
    val precipitation: String,
    val observationTime: String,
    val locationName: String,
    val provider: String,
    val updateTime: Long,
    val hourly: List<AgentWeatherHour>,
    val daily: List<AgentWeatherDay>,
    val alerts: List<String>,
)

@Serializable
data class AgentExportedFile(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentUri: String,
)

@Serializable
data class AgentFileInput(
    val contentUri: String,
    val displayName: String = "",
    val mimeType: String = ""
)

@Serializable
data class AgentAttachmentInfo(
    val id: Long,
    val eventId: Long,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: String,
    val contentUri: String
)

@Serializable
data class AgentEventDraft(
    val title: String,
    val startTs: Long,
    val endTs: Long,
    val location: String = "",
    val description: String = "",
    val reminderMinutes: List<Int> = emptyList(),
    val rrule: String = "",
    val exdates: List<String> = emptyList(),
    val timeZone: String = "",
    val isAllDay: Boolean = false,
    val tag: String = "general",
    val color: Int = 0,
    val attendees: List<AgentAttendee> = emptyList(),
    val attachments: List<AgentFileInput> = emptyList(),
    val replaceAttachments: Boolean = false
)

@Serializable
data class AgentAttendee(
    val name: String = "",
    val email: String = "",
    val status: Int = 0,
    val isMe: Boolean = false
)

@Serializable
data class AgentEventQuery(
    val startTs: Long? = null,
    val endTs: Long? = null,
    val tag: String? = null,
    val text: String? = null,
    val includeArchived: Boolean = false,
    val limit: Int = 100
)

@Serializable
data class AgentEvent(
    val id: Long,
    val title: String,
    val startTs: Long,
    val endTs: Long,
    val location: String,
    val description: String,
    val reminderMinutes: List<Int>,
    val rrule: String,
    val exdates: List<String>,
    val timeZone: String,
    val isAllDay: Boolean,
    val tag: String,
    val color: Int,
    val state: String,
    val parentId: Long,
    val isRecurring: Boolean,
    val isException: Boolean,
    val archivedAt: Long?,
    val attendees: List<AgentAttendee>,
    val attachments: List<AgentAttachmentInfo>
)

@Serializable
enum class AgentRecurringMode { THIS, THIS_AND_FUTURE, ALL }

@Serializable
enum class AgentEventState { PENDING, COMPLETED, CHECKED_IN }

@Serializable
data class AgentCourseDraft(
    val name: String,
    val dayOfWeek: Int,
    val startNode: Int,
    val endNode: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int = 0,
    val location: String = "",
    val teacher: String = "",
    val color: Int
)

@Serializable
data class AgentCourse(
    val id: String,
    val name: String,
    val dayOfWeek: Int,
    val startNode: Int,
    val endNode: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int,
    val location: String,
    val teacher: String,
    val color: Int
)

@Serializable
data class AgentQuickMemoDraft(
    val type: String,
    val bodyText: String = "",
    val media: AgentFileInput? = null,
    val durationMs: Long = 0,
    val asTodo: Boolean = false
)

@Serializable
data class AgentQuickMemoQuery(
    val type: String? = null,
    val text: String? = null,
    val todoState: String? = null,
    val limit: Int = 50
)

@Serializable
data class AgentQuickMemoPatch(
    val bodyText: String? = null,
    val todoState: String? = null
)

@Serializable
data class AgentQuickMemo(
    val id: Long,
    val type: String,
    val bodyText: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val todoState: String,
    val isPinned: Boolean,
    val audioDurationMs: Long,
    val audioContentUri: String?,
    val imageContentUri: String?
)

@Serializable
data class AgentSystemInfo(
    val versionName: String,
    val versionCode: Int,
    val agentApiVersion: Int,
    val hasCalendarPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val enabled: Boolean
)

@Serializable
data class AgentCapabilities(
    val protocolVersion: Int,
    val maxBatchSize: Int,
    val maxQueryLimit: Int,
    val supportsContentUriFiles: Boolean,
    val methods: List<String>,
    val accessEnabled: Boolean = false,
    val thirdPartyAccessEnabled: Boolean = false,
    val connectionManagementEnabled: Boolean = false,
    val databaseOperationsEnabled: Boolean = false,
)
