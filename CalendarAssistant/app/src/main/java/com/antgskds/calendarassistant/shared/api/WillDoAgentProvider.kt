package com.antgskds.calendarassistant.shared.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.shared.operation.AgentAttachmentInfo
import com.antgskds.calendarassistant.shared.operation.AgentCourse
import com.antgskds.calendarassistant.shared.operation.AgentCourseDraft
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
import java.io.FileNotFoundException
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Signature-protected IPC endpoint used by the official companion Agent app. */
class WillDoAgentProvider : ContentProvider() {

    private val service: AgentDataService by lazy {
        (requireNotNull(context).applicationContext as? App)?.agentDataService
            ?: error("WillDo application is not initialized")
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val rawRequest = extras?.getString(WillDoAgentContract.REQUEST_KEY).orEmpty()
        val isThirdPartyTransport = extras?.getBoolean(WillDoAgentContract.THIRD_PARTY_TRANSPORT_KEY) == true
        val callingUid = Binder.getCallingUid()
        val caller = callingPackage ?: "uid:$callingUid"
        var requestId = "unknown"

        val response = try {
            require(rawRequest.toByteArray(Charsets.UTF_8).size <= WillDoAgentContract.MAX_REQUEST_BYTES) {
                "Request exceeds ${WillDoAgentContract.MAX_REQUEST_BYTES} bytes"
            }
            val request = AgentProtocolJson.decodeRequest(rawRequest)
            requestId = request.requestId
            require(request.requestId.isNotBlank() && request.requestId.length <= MAX_REQUEST_ID_LENGTH) {
                "requestId must contain 1-$MAX_REQUEST_ID_LENGTH characters"
            }
            require(request.protocolVersion == WillDoAgentContract.PROTOCOL_VERSION) {
                "Unsupported protocol version ${request.protocolVersion}"
            }

            val cacheKey = "$callingUid:${request.requestId}"
            if (method in MUTATING_METHODS) {
                synchronized(responseCache) {
                    responseCache[cacheKey]?.let { cached ->
                        require(cached.method == method && cached.request == rawRequest) {
                            "requestId was already used for another request"
                        }
                        return Bundle().withResponse(cached.response)
                    }
                }
            }

            Log.i(TAG, "Agent call method=$method caller=$caller requestId=${request.requestId}")
            val data = runBlocking(Dispatchers.IO) {
                dispatch(method, request.payload, isThirdPartyTransport)
            }
            AgentProtocolJson.encodeSuccess(request.requestId, data).also { encoded ->
                if (method in MUTATING_METHODS) cacheResponse(cacheKey, method, rawRequest, encoded)
            }
        } catch (error: Throwable) {
            val protocolError = error.toProtocolError()
            Log.w(
                TAG,
                "Agent call failed method=$method caller=$caller requestId=$requestId code=${protocolError.code}",
                error
            )
            AgentProtocolJson.encodeFailure(requestId, protocolError.code, protocolError.message)
        }

        return Bundle().withResponse(response)
    }

    private suspend fun dispatch(
        method: String,
        payload: JsonObject,
        isThirdPartyTransport: Boolean
    ): JsonElement {
        if (
            isThirdPartyTransport &&
            (method !in THIRD_PARTY_METHODS || payload.requestsRestrictedFileAccess(method))
        ) {
            throw AgentTransportException("File and attachment operations require the official Agent")
        }

        return dispatchAllowed(method, payload, isThirdPartyTransport)
    }

    private suspend fun dispatchAllowed(
        method: String,
        payload: JsonObject,
        isThirdPartyTransport: Boolean
    ): JsonElement = when (method) {
        WillDoAgentContract.GET_CAPABILITIES -> capabilities(isThirdPartyTransport)

        WillDoAgentContract.CREATE_EVENT -> idResult(
            service.createEvent(payload.decode("event", AgentEventDraft.serializer())).getOrThrow()
        )
        WillDoAgentContract.BATCH_CREATE_EVENTS -> buildJsonObject {
            val drafts = payload.decode("events", ListSerializer(AgentEventDraft.serializer()))
            put(
                "ids",
                AgentProtocolJson.json.encodeToJsonElement(
                    ListSerializer(Long.serializer()),
                    service.batchCreateEvents(drafts).getOrThrow()
                )
            )
        }
        WillDoAgentContract.GET_EVENT -> encode(
            service.getEvent(payload.requiredLong("id")).getOrThrow(),
            AgentEvent.serializer()
        )
        WillDoAgentContract.QUERY_EVENTS -> encode(
            service.queryEvents(AgentProtocolJson.json.decodeFromJsonElement<AgentEventQuery>(payload)).getOrThrow(),
            ListSerializer(AgentEvent.serializer())
        )
        WillDoAgentContract.UPDATE_EVENT -> completed {
            service.updateEvent(
                payload.requiredLong("id"),
                payload.decode("event", AgentEventDraft.serializer())
            ).getOrThrow()
        }
        WillDoAgentContract.DELETE_EVENT -> completed {
            service.deleteEvent(payload.requiredLong("id")).getOrThrow()
        }
        WillDoAgentContract.EDIT_RECURRING_EVENT -> nullableIdResult(
            service.editRecurringEvent(
                parentId = payload.requiredLong("parentId"),
                occurrenceTs = payload.requiredLong("occurrenceTs"),
                mode = payload.requiredEnum("mode"),
                draft = payload.decode("event", AgentEventDraft.serializer())
            ).getOrThrow()
        )
        WillDoAgentContract.DELETE_RECURRING_EVENT -> completed {
            service.deleteRecurringEvent(
                parentId = payload.requiredLong("parentId"),
                occurrenceTs = payload.requiredLong("occurrenceTs"),
                mode = payload.requiredEnum("mode")
            ).getOrThrow()
        }
        WillDoAgentContract.SET_EVENT_STATE -> idResult(
            service.setEventState(
                id = payload.requiredLong("id"),
                occurrenceTs = payload.optionalLong("occurrenceTs"),
                state = payload.requiredEnum("state")
            ).getOrThrow()
        )
        WillDoAgentContract.ARCHIVE_EVENT -> completed {
            service.archiveEvent(payload.requiredLong("id"), payload.optionalLong("occurrenceTs")).getOrThrow()
        }
        WillDoAgentContract.RESTORE_EVENT -> completed {
            service.restoreEvent(payload.requiredLong("id")).getOrThrow()
        }
        WillDoAgentContract.ADD_EVENT_ATTACHMENT -> encode(
            service.addEventAttachment(
                payload.requiredLong("eventId"),
                payload.decode("file", AgentFileInput.serializer())
            ).getOrThrow(),
            AgentAttachmentInfo.serializer()
        )
        WillDoAgentContract.LIST_EVENT_ATTACHMENTS -> encode(
            service.listEventAttachments(payload.requiredLong("eventId")).getOrThrow(),
            ListSerializer(AgentAttachmentInfo.serializer())
        )
        WillDoAgentContract.DELETE_EVENT_ATTACHMENT -> completed {
            service.deleteEventAttachment(payload.requiredLong("attachmentId")).getOrThrow()
        }

        WillDoAgentContract.CREATE_COURSE -> stringIdResult(
            service.createCourse(payload.decode("course", AgentCourseDraft.serializer())).getOrThrow()
        )
        WillDoAgentContract.BATCH_CREATE_COURSES -> buildJsonObject {
            val drafts = payload.decode("courses", ListSerializer(AgentCourseDraft.serializer()))
            put(
                "ids",
                AgentProtocolJson.json.encodeToJsonElement(
                    ListSerializer(String.serializer()),
                    service.batchCreateCourses(drafts).getOrThrow()
                )
            )
        }
        WillDoAgentContract.GET_COURSE -> encode(
            service.getCourse(payload.requiredString("id")).getOrThrow(),
            AgentCourse.serializer()
        )
        WillDoAgentContract.QUERY_COURSES -> encode(
            service.queryCourses(payload["dayOfWeek"]?.jsonPrimitive?.intOrNull).getOrThrow(),
            ListSerializer(AgentCourse.serializer())
        )
        WillDoAgentContract.UPDATE_COURSE -> completed {
            service.updateCourse(
                payload.requiredString("id"),
                payload.decode("course", AgentCourseDraft.serializer())
            ).getOrThrow()
        }
        WillDoAgentContract.DELETE_COURSE -> completed {
            service.deleteCourse(payload.requiredString("id")).getOrThrow()
        }

        WillDoAgentContract.CREATE_QUICK_MEMO -> idResult(
            service.createQuickMemo(payload.decode("memo", AgentQuickMemoDraft.serializer())).getOrThrow()
        )
        WillDoAgentContract.GET_QUICK_MEMO -> encode(
            service.getQuickMemo(payload.requiredLong("id")).getOrThrow(),
            AgentQuickMemo.serializer()
        )
        WillDoAgentContract.QUERY_QUICK_MEMOS -> encode(
            service.queryQuickMemos(
                AgentProtocolJson.json.decodeFromJsonElement<AgentQuickMemoQuery>(payload)
            ).getOrThrow(),
            ListSerializer(AgentQuickMemo.serializer())
        )
        WillDoAgentContract.UPDATE_QUICK_MEMO -> completed {
            service.updateQuickMemo(
                payload.requiredLong("id"),
                payload.decode("patch", AgentQuickMemoPatch.serializer())
            ).getOrThrow()
        }
        WillDoAgentContract.ATTACH_QUICK_MEMO_IMAGE -> completed {
            service.attachQuickMemoImage(
                payload.requiredLong("id"),
                payload.decode("file", AgentFileInput.serializer())
            ).getOrThrow()
        }
        WillDoAgentContract.REMOVE_QUICK_MEMO_IMAGE -> completed {
            service.removeQuickMemoImage(payload.requiredLong("id")).getOrThrow()
        }
        WillDoAgentContract.ATTACH_QUICK_MEMO_VOICE -> completed {
            service.attachQuickMemoVoice(
                payload.requiredLong("id"),
                payload.decode("file", AgentFileInput.serializer()),
                payload.requiredLong("durationMs")
            ).getOrThrow()
        }
        WillDoAgentContract.SET_QUICK_MEMO_PINNED -> completed {
            service.setQuickMemoPinned(
                payload.requiredLong("id"),
                payload.requiredBoolean("pinned")
            ).getOrThrow()
        }
        WillDoAgentContract.DELETE_QUICK_MEMO -> completed {
            service.deleteQuickMemo(payload.requiredLong("id")).getOrThrow()
        }

        WillDoAgentContract.GET_SETTINGS -> AgentProtocolJson.anyToJson(service.getSettings().getOrThrow())
        WillDoAgentContract.LIST_CONFIGURATIONS -> encode(
            service.listConfigurations().getOrThrow(),
            ListSerializer(AgentConfiguration.serializer())
        )
        WillDoAgentContract.UPDATE_CONFIGURATION -> encode(
            service.updateConfiguration(
                payload.requiredString("key"),
                payload.requiredInt("value"),
            ).getOrThrow(),
            AgentConfiguration.serializer()
        )
        WillDoAgentContract.GET_CONNECTION_SUMMARY -> encode(
            service.getConnectionSummary().getOrThrow(),
            AgentConnectionSummary.serializer()
        )
        WillDoAgentContract.UPDATE_MODEL_CONNECTION -> encode(
            service.updateModelConnection(
                payload.decode("connection", AgentModelConnectionInput.serializer())
            ).getOrThrow(),
            AgentConnectionSummary.serializer()
        )
        WillDoAgentContract.UPDATE_WEATHER_CONNECTION -> encode(
            service.updateWeatherConnection(
                payload.decode("connection", AgentWeatherConnectionInput.serializer())
            ).getOrThrow(),
            AgentConnectionSummary.serializer()
        )
        WillDoAgentContract.TEST_AND_SAVE_WEBDAV_CONNECTION -> encode(
            service.testAndSaveWebDavConnection(
                payload.decode("connection", AgentWebDavConnectionInput.serializer())
            ).getOrThrow(),
            AgentConnectionResult.serializer()
        )
        WillDoAgentContract.GET_SYNC_STATUS -> encode(
            service.getSyncStatus().getOrThrow(),
            AgentSyncStatus.serializer()
        )
        WillDoAgentContract.SYNC_NOW -> encode(
            service.syncNow().getOrThrow(),
            AgentSyncStatus.serializer()
        )
        WillDoAgentContract.LIST_DATABASE_TABLES -> encode(
            service.listDatabaseTables().getOrThrow(),
            ListSerializer(AgentDatabaseTable.serializer())
        )
        WillDoAgentContract.QUERY_DATABASE -> encode(
            service.queryDatabase(
                payload.decode("query", AgentDatabaseQuery.serializer())
            ).getOrThrow(),
            AgentDatabasePage.serializer()
        )
        WillDoAgentContract.UPDATE_DATABASE_ROW -> encode(
            service.updateDatabaseRow(
                payload.decode("update", AgentDatabaseUpdate.serializer())
            ).getOrThrow(),
            AgentDatabaseMutationResult.serializer()
        )
        WillDoAgentContract.DELETE_DATABASE_ROWS -> encode(
            service.deleteDatabaseRows(
                payload.decode("delete", AgentDatabaseDelete.serializer())
            ).getOrThrow(),
            AgentDatabaseMutationResult.serializer()
        )
        WillDoAgentContract.GET_WEATHER -> {
            val weather = service.getWeather(payload["forceRefresh"]?.jsonPrimitive?.boolean ?: false).getOrThrow()
            if (weather == null) buildJsonObject { put("available", false) }
            else encode(weather, AgentWeatherSnapshot.serializer())
        }
        WillDoAgentContract.EXPORT_DIAGNOSTIC_LOGS -> encode(
            service.exportDiagnosticLogs(payload.requiredInt("minutes")).getOrThrow(),
            AgentExportedFile.serializer()
        )
        WillDoAgentContract.EXPORT_BACKUP -> encode(
            service.exportBackup(payload["includeSettings"]?.jsonPrimitive?.boolean ?: false).getOrThrow(),
            AgentExportedFile.serializer()
        )
        WillDoAgentContract.GET_SYSTEM_INFO -> encode(
            service.getSystemInfo().getOrThrow(),
            AgentSystemInfo.serializer()
        )
        else -> throw NoSuchMethodException("Unknown Agent API method: $method")
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Agent media is read-only")
        val file = runBlocking(Dispatchers.IO) {
            when (URI_MATCHER.match(uri)) {
                CODE_EVENT_ATTACHMENT -> service.resolveEventAttachmentFile(uri.lastPathSegment.requiredId()).getOrThrow()
                CODE_QUICK_MEMO_IMAGE -> service.resolveQuickMemoMediaFile(uri.pathSegments[1].toLong(), "image").getOrThrow()
                CODE_QUICK_MEMO_AUDIO -> service.resolveQuickMemoMediaFile(uri.pathSegments[1].toLong(), "audio").getOrThrow()
                CODE_AGENT_EXPORT -> service.resolveAgentExportFile(uri.lastPathSegment.orEmpty()).getOrThrow()
                else -> throw FileNotFoundException("Unsupported Agent media URI")
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = when (URI_MATCHER.match(uri)) {
        CODE_EVENT_ATTACHMENT, CODE_QUICK_MEMO_IMAGE, CODE_QUICK_MEMO_AUDIO, CODE_AGENT_EXPORT -> "application/octet-stream"
        else -> null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun capabilities(isThirdPartyTransport: Boolean): JsonObject = buildJsonObject {
        val access = service.accessState()
        val methods = if (isThirdPartyTransport) THIRD_PARTY_METHODS else ALL_METHODS
        put("protocolVersion", WillDoAgentContract.PROTOCOL_VERSION)
        put("maxBatchSize", WillDoAgentContract.MAX_BATCH_SIZE)
        put("maxQueryLimit", WillDoAgentContract.MAX_QUERY_LIMIT)
        put("supportsContentUriFiles", !isThirdPartyTransport)
        put("accessEnabled", access.accessEnabled)
        put("thirdPartyAccessEnabled", access.thirdPartyAccessEnabled)
        put("connectionManagementEnabled", access.connectionManagementEnabled)
        put("databaseOperationsEnabled", access.databaseOperationsEnabled)
        put("methods", buildJsonArray { methods.forEach { add(JsonPrimitive(it)) } })
    }

    private fun JsonObject.requestsRestrictedFileAccess(method: String): Boolean = when (method) {
        WillDoAgentContract.CREATE_EVENT,
        WillDoAgentContract.UPDATE_EVENT,
        WillDoAgentContract.EDIT_RECURRING_EVENT ->
            (this["event"] as? JsonObject)?.requestsEventAttachmentAccess() == true

        WillDoAgentContract.BATCH_CREATE_EVENTS ->
            (this["events"] as? JsonArray)
                ?.any { (it as? JsonObject)?.requestsEventAttachmentAccess() == true } == true

        WillDoAgentContract.CREATE_QUICK_MEMO -> {
            val memo = this["memo"] as? JsonObject
            val type = (memo?.get("type") as? JsonPrimitive)?.contentOrNull
            val media = memo?.get("media")
            (type != null && !type.equals("TEXT", ignoreCase = true)) ||
                (media != null && media !is JsonNull)
        }

        else -> false
    }

    private fun JsonObject.requestsEventAttachmentAccess(): Boolean {
        val attachments = this["attachments"] as? JsonArray
        val replaceAttachments = (this["replaceAttachments"] as? JsonPrimitive)?.booleanOrNull == true
        return attachments?.isNotEmpty() == true || replaceAttachments
    }

    private suspend fun completed(block: suspend () -> Unit): JsonObject {
        block()
        return buildJsonObject { put("completed", true) }
    }

    private fun idResult(id: Long): JsonObject = buildJsonObject { put("id", id) }
    private fun nullableIdResult(id: Long?): JsonObject = buildJsonObject { id?.let { put("id", it) } }
    private fun stringIdResult(id: String): JsonObject = buildJsonObject { put("id", id) }

    private fun <T> encode(value: T, serializer: KSerializer<T>): JsonElement =
        AgentProtocolJson.json.encodeToJsonElement(serializer, value)

    private fun cacheResponse(key: String, method: String, request: String, response: String) {
        synchronized(responseCache) {
            responseCache[key] = CachedResponse(method, request, response)
            while (responseCache.size > MAX_CACHED_RESPONSES) {
                responseCache.remove(responseCache.entries.first().key)
            }
        }
    }

    private fun Bundle.withResponse(response: String): Bundle = apply {
        putString(WillDoAgentContract.RESPONSE_KEY, response)
    }

    private fun Throwable.toProtocolError(): AgentProtocolError = when (this) {
        is AgentTransportException -> AgentProtocolError("UNSUPPORTED_TRANSPORT", message.orEmpty())
        is SecurityException -> AgentProtocolError("FORBIDDEN", message ?: "Permission denied")
        is NoSuchElementException, is FileNotFoundException ->
            AgentProtocolError("NOT_FOUND", message ?: "Data not found")
        is NoSuchMethodException -> AgentProtocolError("UNKNOWN_METHOD", message ?: "Unknown method")
        is SerializationException, is IllegalArgumentException ->
            AgentProtocolError("INVALID_ARGUMENT", message ?: "Invalid request")
        is IllegalStateException -> {
            if (message == "Agent API is disabled") AgentProtocolError("API_DISABLED", message.orEmpty())
            else AgentProtocolError("INVALID_STATE", message ?: "Invalid state")
        }
        else -> AgentProtocolError("INTERNAL_ERROR", message ?: javaClass.simpleName)
    }

    private fun JsonObject.requiredLong(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("$name is required")

    private fun JsonObject.optionalLong(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("$name is required")

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.boolean ?: throw IllegalArgumentException("$name is required")

    private fun JsonObject.requiredInt(name: String): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("$name is required")

    private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String): T =
        runCatching { enumValueOf<T>(requiredString(name).uppercase()) }
            .getOrElse { throw IllegalArgumentException("Invalid $name") }

    private fun <T> JsonObject.decode(name: String, serializer: KSerializer<T>): T =
        AgentProtocolJson.json.decodeFromJsonElement(
            serializer,
            this[name] ?: throw IllegalArgumentException("$name is required")
        )

    private fun String?.requiredId(): Long = this?.toLongOrNull()
        ?: throw FileNotFoundException("Invalid media id")

    private data class CachedResponse(val method: String, val request: String, val response: String)

    private companion object {
        const val TAG = "WillDoAgentApi"
        const val CODE_EVENT_ATTACHMENT = 1
        const val CODE_QUICK_MEMO_IMAGE = 2
        const val CODE_QUICK_MEMO_AUDIO = 3
        const val CODE_AGENT_EXPORT = 4
        const val MAX_REQUEST_ID_LENGTH = 128
        const val MAX_CACHED_RESPONSES = 128

        val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(WillDoAgentContract.AUTHORITY, "event_attachments/#", CODE_EVENT_ATTACHMENT)
            addURI(WillDoAgentContract.AUTHORITY, "quick_memos/#/image", CODE_QUICK_MEMO_IMAGE)
            addURI(WillDoAgentContract.AUTHORITY, "quick_memos/#/audio", CODE_QUICK_MEMO_AUDIO)
            addURI(WillDoAgentContract.AUTHORITY, "exports/*", CODE_AGENT_EXPORT)
        }

        val ALL_METHODS = listOf(
            WillDoAgentContract.GET_CAPABILITIES,
            WillDoAgentContract.CREATE_EVENT,
            WillDoAgentContract.BATCH_CREATE_EVENTS,
            WillDoAgentContract.GET_EVENT,
            WillDoAgentContract.QUERY_EVENTS,
            WillDoAgentContract.UPDATE_EVENT,
            WillDoAgentContract.DELETE_EVENT,
            WillDoAgentContract.EDIT_RECURRING_EVENT,
            WillDoAgentContract.DELETE_RECURRING_EVENT,
            WillDoAgentContract.SET_EVENT_STATE,
            WillDoAgentContract.ARCHIVE_EVENT,
            WillDoAgentContract.RESTORE_EVENT,
            WillDoAgentContract.ADD_EVENT_ATTACHMENT,
            WillDoAgentContract.LIST_EVENT_ATTACHMENTS,
            WillDoAgentContract.DELETE_EVENT_ATTACHMENT,
            WillDoAgentContract.CREATE_COURSE,
            WillDoAgentContract.BATCH_CREATE_COURSES,
            WillDoAgentContract.GET_COURSE,
            WillDoAgentContract.QUERY_COURSES,
            WillDoAgentContract.UPDATE_COURSE,
            WillDoAgentContract.DELETE_COURSE,
            WillDoAgentContract.CREATE_QUICK_MEMO,
            WillDoAgentContract.GET_QUICK_MEMO,
            WillDoAgentContract.QUERY_QUICK_MEMOS,
            WillDoAgentContract.UPDATE_QUICK_MEMO,
            WillDoAgentContract.ATTACH_QUICK_MEMO_IMAGE,
            WillDoAgentContract.REMOVE_QUICK_MEMO_IMAGE,
            WillDoAgentContract.ATTACH_QUICK_MEMO_VOICE,
            WillDoAgentContract.SET_QUICK_MEMO_PINNED,
            WillDoAgentContract.DELETE_QUICK_MEMO,
            WillDoAgentContract.GET_SETTINGS,
            WillDoAgentContract.LIST_CONFIGURATIONS,
            WillDoAgentContract.UPDATE_CONFIGURATION,
            WillDoAgentContract.GET_CONNECTION_SUMMARY,
            WillDoAgentContract.UPDATE_MODEL_CONNECTION,
            WillDoAgentContract.UPDATE_WEATHER_CONNECTION,
            WillDoAgentContract.TEST_AND_SAVE_WEBDAV_CONNECTION,
            WillDoAgentContract.GET_SYNC_STATUS,
            WillDoAgentContract.SYNC_NOW,
            WillDoAgentContract.LIST_DATABASE_TABLES,
            WillDoAgentContract.QUERY_DATABASE,
            WillDoAgentContract.UPDATE_DATABASE_ROW,
            WillDoAgentContract.DELETE_DATABASE_ROWS,
            WillDoAgentContract.GET_WEATHER,
            WillDoAgentContract.EXPORT_DIAGNOSTIC_LOGS,
            WillDoAgentContract.EXPORT_BACKUP,
            WillDoAgentContract.GET_SYSTEM_INFO
        )

        val MUTATING_METHODS = ALL_METHODS.toSet() - setOf(
            WillDoAgentContract.GET_CAPABILITIES,
            WillDoAgentContract.GET_EVENT,
            WillDoAgentContract.QUERY_EVENTS,
            WillDoAgentContract.LIST_EVENT_ATTACHMENTS,
            WillDoAgentContract.GET_COURSE,
            WillDoAgentContract.QUERY_COURSES,
            WillDoAgentContract.GET_QUICK_MEMO,
            WillDoAgentContract.QUERY_QUICK_MEMOS,
            WillDoAgentContract.GET_SETTINGS,
            WillDoAgentContract.LIST_CONFIGURATIONS,
            WillDoAgentContract.GET_CONNECTION_SUMMARY,
            WillDoAgentContract.GET_SYNC_STATUS,
            WillDoAgentContract.LIST_DATABASE_TABLES,
            WillDoAgentContract.QUERY_DATABASE,
            WillDoAgentContract.GET_WEATHER,
            WillDoAgentContract.GET_SYSTEM_INFO
        )

        val THIRD_PARTY_BLOCKED_METHODS = setOf(
            WillDoAgentContract.ADD_EVENT_ATTACHMENT,
            WillDoAgentContract.LIST_EVENT_ATTACHMENTS,
            WillDoAgentContract.DELETE_EVENT_ATTACHMENT,
            WillDoAgentContract.ATTACH_QUICK_MEMO_IMAGE,
            WillDoAgentContract.REMOVE_QUICK_MEMO_IMAGE,
            WillDoAgentContract.ATTACH_QUICK_MEMO_VOICE,
            WillDoAgentContract.EXPORT_DIAGNOSTIC_LOGS,
            WillDoAgentContract.EXPORT_BACKUP
        )

        val THIRD_PARTY_METHODS = ALL_METHODS.filterNot(THIRD_PARTY_BLOCKED_METHODS::contains)

        val responseCache = LinkedHashMap<String, CachedResponse>()
    }
}

private class AgentTransportException(message: String) : IllegalStateException(message)
