package com.antgskds.calendarassistant.core.ai

import com.antgskds.calendarassistant.core.model.RecognitionDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

object RecognitionJsonParser {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun cleanJsonString(response: String): String {
        var cleaned = response.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").removeSuffix("```").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").removeSuffix("```").trim()
        }
        val startObj = cleaned.indexOf('{')
        val startArr = cleaned.indexOf('[')
        val start = listOf(startObj, startArr).filter { it >= 0 }.minOrNull() ?: -1
        if (start > 0) cleaned = cleaned.substring(start)
        val endObj = cleaned.lastIndexOf('}')
        val endArr = cleaned.lastIndexOf(']')
        val end = maxOf(endObj, endArr)
        if (end >= 0 && end < cleaned.lastIndex) cleaned = cleaned.substring(0, end + 1)
        return cleaned
    }

    fun parseCalendarEvents(cleanJson: String): List<RecognitionDraft> {
        if (cleanJson.isBlank()) return emptyList()
        return try {
            val element = jsonParser.parseToJsonElement(cleanJson)
            when (element) {
                is JsonObject -> parseCalendarEventsFromObject(element)
                is JsonArray -> parseCalendarEventsFromArray(element)
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun enforceRuleHeaders(events: List<RecognitionDraft>): List<RecognitionDraft> {
        return RuleDescriptionNormalizer.normalize(events)
    }

    private fun parseCalendarEventsFromObject(jsonObject: JsonObject): List<RecognitionDraft> {
        val eventsArray = jsonObject["events"] as? JsonArray
        if (eventsArray != null) return parseCalendarEventsFromArray(eventsArray)
        return runCatching {
            val dto = jsonParser.decodeFromString<AiEventDto>(jsonObject.toString())
            listOf(dto.toRecognitionDraft())
        }.getOrDefault(emptyList())
    }

    private fun parseCalendarEventsFromArray(jsonArray: JsonArray): List<RecognitionDraft> {
        return jsonArray.mapNotNull { element ->
            runCatching {
                when (element) {
                    is JsonObject -> jsonParser.decodeFromString<AiEventDto>(element.toString()).toRecognitionDraft()
                    else -> null
                }
            }.getOrNull()
        }
    }

}
