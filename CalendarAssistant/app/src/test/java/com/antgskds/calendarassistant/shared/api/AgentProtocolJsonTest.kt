package com.antgskds.calendarassistant.shared.api

import com.antgskds.calendarassistant.shared.operation.AgentAttendee
import com.antgskds.calendarassistant.shared.operation.AgentConnectionSummary
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseQuery
import com.antgskds.calendarassistant.shared.operation.AgentModelConnectionSummary
import com.antgskds.calendarassistant.shared.operation.AgentWeatherConnectionSummary
import com.antgskds.calendarassistant.shared.operation.AgentWebDavConnectionSummary
import com.antgskds.calendarassistant.shared.operation.AgentEventDraft
import com.antgskds.calendarassistant.shared.operation.AgentFileInput
import com.antgskds.calendarassistant.shared.operation.WillDoAgentContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProtocolJsonTest {

    @Test
    fun requestEnvelopeRoundTrips() {
        val request = AgentRequestEnvelope(
            requestId = "request-1",
            payload = buildJsonObject { put("id", 42L) }
        )

        val decoded = AgentProtocolJson.decodeRequest(AgentProtocolJson.encodeRequest(request))

        assertEquals(WillDoAgentContract.PROTOCOL_VERSION, decoded.protocolVersion)
        assertEquals("request-1", decoded.requestId)
        assertEquals(42L, decoded.payload.getValue("id").jsonPrimitive.content.toLong())
    }

    @Test
    fun eventDraftKeepsFilesAndRecurringFields() {
        val draft = AgentEventDraft(
            title = "发送合同",
            startTs = 1_800_000_000L,
            endTs = 1_800_003_600L,
            reminderMinutes = listOf(10, 30),
            rrule = "FREQ=WEEKLY;COUNT=4",
            exdates = listOf("20260820T070000Z"),
            attendees = listOf(AgentAttendee(name = "客户", email = "client@example.com")),
            replaceAttachments = true,
            attachments = listOf(
                AgentFileInput(
                    contentUri = "content://agent.files/contracts/1",
                    displayName = "contract.pdf",
                    mimeType = "application/pdf"
                )
            )
        )

        val element = AgentProtocolJson.json.encodeToJsonElement(AgentEventDraft.serializer(), draft)
        val decoded = AgentProtocolJson.json.decodeFromJsonElement(AgentEventDraft.serializer(), element)

        assertEquals(draft, decoded)
        assertFalse(element.jsonObject.containsKey("audioBase64"))
        assertFalse(element.jsonObject.containsKey("imageBase64"))
    }

    @Test
    fun failureResponseHasStableErrorShape() {
        val decoded = AgentProtocolJson.decodeResponse(
            AgentProtocolJson.encodeFailure("request-2", "NOT_FOUND", "Event not found")
        )

        assertFalse(decoded.success)
        assertNull(decoded.data)
        assertEquals("request-2", decoded.requestId)
        assertEquals("NOT_FOUND", decoded.error?.code)
    }

    @Test
    fun dynamicSettingsMapBecomesJsonObject() {
        val value = AgentProtocolJson.anyToJson(
            mapOf("enabled" to true, "minutes" to 15, "modes" to listOf("A", "B"))
        ).jsonObject

        assertTrue(value.getValue("enabled").jsonPrimitive.content.toBoolean())
        assertEquals("15", value.getValue("minutes").jsonPrimitive.content)
        assertEquals(2, value.getValue("modes").jsonArray.size)
    }

    @Test
    fun connectionSummaryDoesNotHaveCredentialValueFields() {
        val summary = AgentConnectionSummary(
            textModel = AgentModelConnectionSummary("TEXT", "model", "https://example.com", true),
            multimodalModel = AgentModelConnectionSummary("MULTIMODAL", "", "", false),
            weather = AgentWeatherConnectionSummary("qweather", true, "https://weather.example", true, "manual", "北京"),
            webDav = AgentWebDavConnectionSummary("https://dav.example", true, true, true, true, false),
        )

        val encoded = AgentProtocolJson.json.encodeToString(AgentConnectionSummary.serializer(), summary)

        assertFalse(encoded.contains("\"apiKey\":", ignoreCase = true))
        assertFalse(encoded.contains("\"password\":", ignoreCase = true))
        assertFalse(encoded.contains("\"token\":", ignoreCase = true))
        assertFalse(encoded.contains("\"syncPassphrase\":", ignoreCase = true))
    }

    @Test
    fun databaseQueryRoundTripsFiltersAndLimits() {
        val query = AgentDatabaseQuery(
            table = "events",
            filters = mapOf("state" to "0", "archived_at" to null),
            limit = 25,
            offset = 50,
            maxCellChars = 8_000,
        )

        val element = AgentProtocolJson.json.encodeToJsonElement(AgentDatabaseQuery.serializer(), query)
        val decoded = AgentProtocolJson.json.decodeFromJsonElement(AgentDatabaseQuery.serializer(), element)

        assertEquals(query, decoded)
    }
}
