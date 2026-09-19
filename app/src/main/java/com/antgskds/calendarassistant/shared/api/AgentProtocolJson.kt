package com.antgskds.calendarassistant.shared.api

import com.antgskds.calendarassistant.shared.operation.WillDoAgentContract
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class AgentRequestEnvelope(
    val protocolVersion: Int = WillDoAgentContract.PROTOCOL_VERSION,
    val requestId: String,
    val payload: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class AgentProtocolError(
    val code: String,
    val message: String
)

@Serializable
data class AgentResponseEnvelope(
    val protocolVersion: Int = WillDoAgentContract.PROTOCOL_VERSION,
    val requestId: String,
    val success: Boolean,
    val data: JsonElement? = null,
    val error: AgentProtocolError? = null
)

object AgentProtocolJson {
    val json: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun decodeRequest(value: String): AgentRequestEnvelope = json.decodeFromString(value)
    fun encodeRequest(request: AgentRequestEnvelope): String = json.encodeToString(request)
    fun decodeResponse(value: String): AgentResponseEnvelope = json.decodeFromString(value)

    fun encodeSuccess(requestId: String, data: JsonElement = JsonObject(emptyMap())): String =
        json.encodeToString(AgentResponseEnvelope(requestId = requestId, success = true, data = data))

    fun encodeFailure(requestId: String, code: String, message: String): String =
        json.encodeToString(
            AgentResponseEnvelope(
                requestId = requestId,
                success = false,
                error = AgentProtocolError(code, message)
            )
        )

    fun anyToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (key, item) -> key.toString() to anyToJson(item) }
        )
        is Iterable<*> -> JsonArray(value.map(::anyToJson))
        is Array<*> -> JsonArray(value.map(::anyToJson))
        else -> JsonPrimitive(value.toString())
    }
}
