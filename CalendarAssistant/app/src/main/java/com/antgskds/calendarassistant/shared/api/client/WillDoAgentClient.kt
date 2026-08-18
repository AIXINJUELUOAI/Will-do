package com.antgskds.calendarassistant.shared.api.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.antgskds.calendarassistant.shared.api.AgentProtocolJson
import com.antgskds.calendarassistant.shared.api.AgentRequestEnvelope
import com.antgskds.calendarassistant.shared.operation.AgentAttachmentInfo
import com.antgskds.calendarassistant.shared.operation.AgentCapabilities
import com.antgskds.calendarassistant.shared.operation.AgentConfiguration
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
import com.antgskds.calendarassistant.shared.operation.AgentSyncStatus
import com.antgskds.calendarassistant.shared.operation.AgentWeatherConnectionInput
import com.antgskds.calendarassistant.shared.operation.AgentWeatherSnapshot
import com.antgskds.calendarassistant.shared.operation.AgentWebDavConnectionInput
import com.antgskds.calendarassistant.shared.operation.AgentCourse
import com.antgskds.calendarassistant.shared.operation.AgentCourseDraft
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
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Client-side wrapper intended to be extracted into the official Agent app. */
class WillDoAgentClient(private val context: Context) {

    fun isWillDoInstalled(): Boolean = context.packageManager.resolveContentProvider(
        WillDoAgentContract.AUTHORITY,
        0
    ) != null

    suspend fun getCapabilities(): AgentCapabilities = decode(
        call(WillDoAgentContract.GET_CAPABILITIES),
        AgentCapabilities.serializer()
    )

    suspend fun createEvent(draft: AgentEventDraft): Long {
        grantFiles(draft.attachments)
        return call(
            WillDoAgentContract.CREATE_EVENT,
            buildJsonObject { put("event", encode(draft, AgentEventDraft.serializer())) }
        ).requiredLong("id")
    }

    suspend fun batchCreateEvents(drafts: List<AgentEventDraft>): List<Long> {
        require(drafts.size <= WillDoAgentContract.MAX_BATCH_SIZE)
        drafts.flatMap { it.attachments }.let(::grantFiles)
        val data = call(
            WillDoAgentContract.BATCH_CREATE_EVENTS,
            buildJsonObject {
                put("events", encode(drafts, ListSerializer(AgentEventDraft.serializer())))
            }
        ).jsonObject
        return decode(data.getValue("ids"), ListSerializer(Long.serializer()))
    }

    suspend fun getEvent(id: Long): AgentEvent = decode(
        call(WillDoAgentContract.GET_EVENT, idPayload(id)),
        AgentEvent.serializer()
    )

    suspend fun queryEvents(query: AgentEventQuery = AgentEventQuery()): List<AgentEvent> = decode(
        call(WillDoAgentContract.QUERY_EVENTS, encode(query, AgentEventQuery.serializer()).jsonObject),
        ListSerializer(AgentEvent.serializer())
    )

    suspend fun updateEvent(id: Long, draft: AgentEventDraft) {
        grantFiles(draft.attachments)
        call(
            WillDoAgentContract.UPDATE_EVENT,
            buildJsonObject {
                put("id", id)
                put("event", encode(draft, AgentEventDraft.serializer()))
            }
        )
    }

    suspend fun deleteEvent(id: Long) {
        call(WillDoAgentContract.DELETE_EVENT, idPayload(id))
    }

    suspend fun editRecurringEvent(
        parentId: Long,
        occurrenceTs: Long,
        mode: AgentRecurringMode,
        draft: AgentEventDraft
    ): Long? {
        grantFiles(draft.attachments)
        return call(
            WillDoAgentContract.EDIT_RECURRING_EVENT,
            buildJsonObject {
                put("parentId", parentId)
                put("occurrenceTs", occurrenceTs)
                put("mode", mode.name)
                put("event", encode(draft, AgentEventDraft.serializer()))
            }
        ).jsonObject["id"]?.jsonPrimitive?.long
    }

    suspend fun deleteRecurringEvent(parentId: Long, occurrenceTs: Long, mode: AgentRecurringMode) {
        call(
            WillDoAgentContract.DELETE_RECURRING_EVENT,
            buildJsonObject {
                put("parentId", parentId)
                put("occurrenceTs", occurrenceTs)
                put("mode", mode.name)
            }
        )
    }

    suspend fun setEventState(id: Long, occurrenceTs: Long? = null, state: AgentEventState): Long =
        call(
            WillDoAgentContract.SET_EVENT_STATE,
            buildJsonObject {
                put("id", id)
                occurrenceTs?.let { put("occurrenceTs", it) }
                put("state", state.name)
            }
        ).requiredLong("id")

    suspend fun archiveEvent(id: Long, occurrenceTs: Long? = null) {
        call(
            WillDoAgentContract.ARCHIVE_EVENT,
            buildJsonObject {
                put("id", id)
                occurrenceTs?.let { put("occurrenceTs", it) }
            }
        )
    }

    suspend fun restoreEvent(id: Long) {
        call(WillDoAgentContract.RESTORE_EVENT, idPayload(id))
    }

    suspend fun addEventAttachment(eventId: Long, file: AgentFileInput): AgentAttachmentInfo {
        grantFiles(listOf(file))
        return decode(
            call(
                WillDoAgentContract.ADD_EVENT_ATTACHMENT,
                buildJsonObject {
                    put("eventId", eventId)
                    put("file", encode(file, AgentFileInput.serializer()))
                }
            ),
            AgentAttachmentInfo.serializer()
        )
    }

    suspend fun listEventAttachments(eventId: Long): List<AgentAttachmentInfo> = decode(
        call(
            WillDoAgentContract.LIST_EVENT_ATTACHMENTS,
            buildJsonObject { put("eventId", eventId) }
        ),
        ListSerializer(AgentAttachmentInfo.serializer())
    )

    suspend fun deleteEventAttachment(attachmentId: Long) {
        call(
            WillDoAgentContract.DELETE_EVENT_ATTACHMENT,
            buildJsonObject { put("attachmentId", attachmentId) }
        )
    }

    suspend fun createCourse(draft: AgentCourseDraft): String = call(
        WillDoAgentContract.CREATE_COURSE,
        buildJsonObject { put("course", encode(draft, AgentCourseDraft.serializer())) }
    ).requiredString("id")

    suspend fun batchCreateCourses(drafts: List<AgentCourseDraft>): List<String> {
        require(drafts.size <= WillDoAgentContract.MAX_BATCH_SIZE)
        val data = call(
            WillDoAgentContract.BATCH_CREATE_COURSES,
            buildJsonObject {
                put("courses", encode(drafts, ListSerializer(AgentCourseDraft.serializer())))
            }
        ).jsonObject
        return decode(data.getValue("ids"), ListSerializer(String.serializer()))
    }

    suspend fun getCourse(id: String): AgentCourse = decode(
        call(WillDoAgentContract.GET_COURSE, stringIdPayload(id)),
        AgentCourse.serializer()
    )

    suspend fun queryCourses(dayOfWeek: Int? = null): List<AgentCourse> = decode(
        call(
            WillDoAgentContract.QUERY_COURSES,
            buildJsonObject { dayOfWeek?.let { put("dayOfWeek", it) } }
        ),
        ListSerializer(AgentCourse.serializer())
    )

    suspend fun updateCourse(id: String, draft: AgentCourseDraft) {
        call(
            WillDoAgentContract.UPDATE_COURSE,
            buildJsonObject {
                put("id", id)
                put("course", encode(draft, AgentCourseDraft.serializer()))
            }
        )
    }

    suspend fun deleteCourse(id: String) {
        call(WillDoAgentContract.DELETE_COURSE, stringIdPayload(id))
    }

    suspend fun createQuickMemo(draft: AgentQuickMemoDraft): Long {
        draft.media?.let { grantFiles(listOf(it)) }
        return call(
            WillDoAgentContract.CREATE_QUICK_MEMO,
            buildJsonObject { put("memo", encode(draft, AgentQuickMemoDraft.serializer())) }
        ).requiredLong("id")
    }

    suspend fun getQuickMemo(id: Long): AgentQuickMemo = decode(
        call(WillDoAgentContract.GET_QUICK_MEMO, idPayload(id)),
        AgentQuickMemo.serializer()
    )

    suspend fun queryQuickMemos(query: AgentQuickMemoQuery = AgentQuickMemoQuery()): List<AgentQuickMemo> = decode(
        call(
            WillDoAgentContract.QUERY_QUICK_MEMOS,
            encode(query, AgentQuickMemoQuery.serializer()).jsonObject
        ),
        ListSerializer(AgentQuickMemo.serializer())
    )

    suspend fun updateQuickMemo(id: Long, patch: AgentQuickMemoPatch) {
        call(
            WillDoAgentContract.UPDATE_QUICK_MEMO,
            buildJsonObject {
                put("id", id)
                put("patch", encode(patch, AgentQuickMemoPatch.serializer()))
            }
        )
    }

    suspend fun attachQuickMemoImage(id: Long, file: AgentFileInput) {
        grantFiles(listOf(file))
        call(WillDoAgentContract.ATTACH_QUICK_MEMO_IMAGE, memoFilePayload(id, file))
    }

    suspend fun removeQuickMemoImage(id: Long) {
        call(WillDoAgentContract.REMOVE_QUICK_MEMO_IMAGE, idPayload(id))
    }

    suspend fun attachQuickMemoVoice(id: Long, file: AgentFileInput, durationMs: Long) {
        grantFiles(listOf(file))
        call(
            WillDoAgentContract.ATTACH_QUICK_MEMO_VOICE,
            buildJsonObject {
                put("id", id)
                put("file", encode(file, AgentFileInput.serializer()))
                put("durationMs", durationMs)
            }
        )
    }

    suspend fun setQuickMemoPinned(id: Long, pinned: Boolean) {
        call(
            WillDoAgentContract.SET_QUICK_MEMO_PINNED,
            buildJsonObject {
                put("id", id)
                put("pinned", pinned)
            }
        )
    }

    suspend fun deleteQuickMemo(id: Long) {
        call(WillDoAgentContract.DELETE_QUICK_MEMO, idPayload(id))
    }

    suspend fun getSettings(): JsonObject = call(WillDoAgentContract.GET_SETTINGS).jsonObject

    suspend fun listConfigurations(): List<AgentConfiguration> = decode(
        call(WillDoAgentContract.LIST_CONFIGURATIONS),
        ListSerializer(AgentConfiguration.serializer())
    )

    suspend fun updateConfiguration(key: String, value: Int): AgentConfiguration = decode(
        call(
            WillDoAgentContract.UPDATE_CONFIGURATION,
            buildJsonObject {
                put("key", key)
                put("value", value)
            }
        ),
        AgentConfiguration.serializer()
    )

    suspend fun getConnectionSummary(): AgentConnectionSummary = decode(
        call(WillDoAgentContract.GET_CONNECTION_SUMMARY),
        AgentConnectionSummary.serializer()
    )

    suspend fun updateModelConnection(input: AgentModelConnectionInput): AgentConnectionSummary = decode(
        call(
            WillDoAgentContract.UPDATE_MODEL_CONNECTION,
            buildJsonObject { put("connection", encode(input, AgentModelConnectionInput.serializer())) }
        ),
        AgentConnectionSummary.serializer()
    )

    suspend fun updateWeatherConnection(input: AgentWeatherConnectionInput): AgentConnectionSummary = decode(
        call(
            WillDoAgentContract.UPDATE_WEATHER_CONNECTION,
            buildJsonObject { put("connection", encode(input, AgentWeatherConnectionInput.serializer())) }
        ),
        AgentConnectionSummary.serializer()
    )

    suspend fun testAndSaveWebDavConnection(input: AgentWebDavConnectionInput): AgentConnectionResult = decode(
        call(
            WillDoAgentContract.TEST_AND_SAVE_WEBDAV_CONNECTION,
            buildJsonObject { put("connection", encode(input, AgentWebDavConnectionInput.serializer())) }
        ),
        AgentConnectionResult.serializer()
    )

    suspend fun getSyncStatus(): AgentSyncStatus = decode(
        call(WillDoAgentContract.GET_SYNC_STATUS),
        AgentSyncStatus.serializer()
    )

    suspend fun syncNow(): AgentSyncStatus = decode(
        call(WillDoAgentContract.SYNC_NOW),
        AgentSyncStatus.serializer()
    )

    suspend fun listDatabaseTables(): List<AgentDatabaseTable> = decode(
        call(WillDoAgentContract.LIST_DATABASE_TABLES),
        ListSerializer(AgentDatabaseTable.serializer())
    )

    suspend fun queryDatabase(input: AgentDatabaseQuery): AgentDatabasePage = decode(
        call(
            WillDoAgentContract.QUERY_DATABASE,
            buildJsonObject { put("query", encode(input, AgentDatabaseQuery.serializer())) }
        ),
        AgentDatabasePage.serializer()
    )

    suspend fun updateDatabaseRow(input: AgentDatabaseUpdate): AgentDatabaseMutationResult = decode(
        call(
            WillDoAgentContract.UPDATE_DATABASE_ROW,
            buildJsonObject { put("update", encode(input, AgentDatabaseUpdate.serializer())) }
        ),
        AgentDatabaseMutationResult.serializer()
    )

    suspend fun deleteDatabaseRows(input: AgentDatabaseDelete): AgentDatabaseMutationResult = decode(
        call(
            WillDoAgentContract.DELETE_DATABASE_ROWS,
            buildJsonObject { put("delete", encode(input, AgentDatabaseDelete.serializer())) }
        ),
        AgentDatabaseMutationResult.serializer()
    )

    suspend fun getWeather(forceRefresh: Boolean = false): AgentWeatherSnapshot? {
        val result = call(
            WillDoAgentContract.GET_WEATHER,
            buildJsonObject { put("forceRefresh", forceRefresh) }
        )
        if (result.jsonObject["available"]?.jsonPrimitive?.content == "false") return null
        return decode(result, AgentWeatherSnapshot.serializer())
    }

    suspend fun exportDiagnosticLogs(minutes: Int): AgentExportedFile = decode(
        call(
            WillDoAgentContract.EXPORT_DIAGNOSTIC_LOGS,
            buildJsonObject { put("minutes", minutes) }
        ),
        AgentExportedFile.serializer()
    )

    suspend fun exportBackup(includeSettings: Boolean = false): AgentExportedFile = decode(
        call(
            WillDoAgentContract.EXPORT_BACKUP,
            buildJsonObject { put("includeSettings", includeSettings) }
        ),
        AgentExportedFile.serializer()
    )

    suspend fun getSystemInfo(): AgentSystemInfo = decode(
        call(WillDoAgentContract.GET_SYSTEM_INFO),
        AgentSystemInfo.serializer()
    )

    fun openMedia(contentUri: String): InputStream = requireNotNull(
        context.contentResolver.openInputStream(Uri.parse(contentUri))
    ) { "Unable to open Agent media URI" }

    private suspend fun call(method: String, payload: JsonObject = JsonObject(emptyMap())): JsonElement =
        withContext(Dispatchers.IO) {
            check(isWillDoInstalled()) { "WillDo is not installed" }
            val requestId = UUID.randomUUID().toString()
            val request = AgentProtocolJson.encodeRequest(
                AgentRequestEnvelope(requestId = requestId, payload = payload)
            )
            val result = context.contentResolver.call(
                Uri.parse(WillDoAgentContract.BASE_URI),
                method,
                null,
                Bundle().apply { putString(WillDoAgentContract.REQUEST_KEY, request) }
            ) ?: throw AgentApiException("NO_RESPONSE", "WillDo returned no response")
            val rawResponse = result.getString(WillDoAgentContract.RESPONSE_KEY)
                ?: throw AgentApiException("INVALID_RESPONSE", "WillDo returned an empty response")
            val response = AgentProtocolJson.decodeResponse(rawResponse)
            check(response.protocolVersion == WillDoAgentContract.PROTOCOL_VERSION) {
                "Unsupported WillDo Agent API version ${response.protocolVersion}"
            }
            check(response.requestId == requestId) { "Agent response requestId mismatch" }
            if (!response.success) {
                val error = response.error
                throw AgentApiException(error?.code ?: "UNKNOWN", error?.message ?: "Agent request failed")
            }
            response.data ?: JsonObject(emptyMap())
        }

    private fun grantFiles(files: List<AgentFileInput>) {
        files.forEach { file ->
            val uri = Uri.parse(file.contentUri)
            require(uri.scheme == "content") { "Only content:// file URIs are accepted" }
            context.grantUriPermission(
                WillDoAgentContract.WILLDO_PACKAGE,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun idPayload(id: Long): JsonObject = buildJsonObject { put("id", id) }
    private fun stringIdPayload(id: String): JsonObject = buildJsonObject { put("id", id) }

    private fun memoFilePayload(id: Long, file: AgentFileInput): JsonObject = buildJsonObject {
        put("id", id)
        put("file", encode(file, AgentFileInput.serializer()))
    }

    private fun <T> encode(value: T, serializer: KSerializer<T>): JsonElement =
        AgentProtocolJson.json.encodeToJsonElement(serializer, value)

    private fun <T> decode(value: JsonElement, serializer: KSerializer<T>): T =
        AgentProtocolJson.json.decodeFromJsonElement(serializer, value)

    private fun JsonElement.requiredLong(name: String): Long =
        jsonObject[name]?.jsonPrimitive?.long ?: error("Missing $name in Agent response")

    private fun JsonElement.requiredString(name: String): String =
        jsonObject[name]?.jsonPrimitive?.content ?: error("Missing $name in Agent response")
}

class AgentApiException(val code: String, message: String) : IllegalStateException(message)
