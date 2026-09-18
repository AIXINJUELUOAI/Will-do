package com.antgskds.calendarassistant.feature.recognition.application.ai

import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft

object RecognitionJsonParser {
    data class Content(val events: List<RecognitionDraft>, val bills: List<AccountingDraft>, val issues: List<String>)

    fun parseContent(cleanJson: String): Content {
        val root = jsonParser.parseToJsonElement(cleanJson)
        val issues = mutableListOf<String>()
        val rawBills = (root as? JsonObject)?.get("bills")
        if (rawBills != null && rawBills !is JsonArray) issues += "账单列表格式错误，请重试识别"
        val bills = (rawBills as? JsonArray).orEmpty().mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject
            if (obj == null) {
                issues += "第 ${index + 1} 条账单格式错误，已跳过"
                return@mapIndexedNotNull null
            }
            fun field(name: String) = (obj[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val status = field("paymentStatus").uppercase()
            if (status in setOf("UNPAID", "CANCELLED", "FAILED", "REFUND_PENDING")) {
                issues += "第 ${index + 1} 条交易尚未完成，未生成账单"
                return@mapIndexedNotNull null
            }
            if (listOf("amount", "merchant", "transactionId").all { field(it).isBlank() }) {
                issues += "第 ${index + 1} 条账单缺少可识别内容，已跳过"
                return@mapIndexedNotNull null
            }
            val direction = field("direction").uppercase().takeIf { it in setOf("EXPENSE", "INCOME", "TRANSFER") }.orEmpty()
            AccountingDraft(amount = field("amount"), direction = direction, currency = field("currency").uppercase(),
                merchant = field("merchant"), category = field("category"), note = field("note"),
                occurredAt = field("occurredAt"), zoneId = java.time.ZoneId.systemDefault().id,
                channel = field("channel"), transactionId = field("transactionId"),
                transactionIdType = field("transactionIdType").uppercase().takeIf { it in setOf("PAYMENT", "MERCHANT_ORDER") } ?: "UNKNOWN",
                paymentStatus = status.takeIf { it in setOf("COMPLETED", "REFUNDED") } ?: "REVIEW")
        }
        return Content(parseCalendarEvents(cleanJson).filter { it.title.isNotBlank() }, bills, issues)
    }
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
        if ("bills" in jsonObject || "events" in jsonObject) return emptyList()
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
