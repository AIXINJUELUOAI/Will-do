package com.antgskds.calendarassistant.feature.recognition.application.ai

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.shared.util.ImageCompressionUtils
import com.antgskds.calendarassistant.feature.schedule.domain.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.feature.recognition.application.ai.RulePatchProvider
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeQrSupport
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.recognition.application.ai.model.ModelMessage
import com.antgskds.calendarassistant.feature.recognition.application.ai.model.ModelRequest
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * AI 返回的原始 JSON 事件 DTO —— 字段名与 prompt 约定一致。
 * 仅用于反序列化，之后立即转成 RecognitionDraft。
 */
@Serializable
data class AiEventDto(
    val title: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val location: String = "",
    val description: String = "",
    val tag: String = "general",
    val type: String = "event"       // 兼容旧 prompt，不再传播到 RecognitionDraft
) {
    fun toRecognitionDraft(): RecognitionDraft {
        val resolvedTag = if (tag.isBlank() || tag == "general") {
            if (type == "pickup") "pickup" else "general"
        } else tag
        return RecognitionDraft(
            title = title,
            startTS = parseDateTimeToEpoch(startTime),
            endTS = parseDateTimeToEpoch(endTime),
            location = location,
            description = description,
            tag = resolvedTag
        )
    }
    companion object {
        private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        fun parseDateTimeToEpoch(value: String): Long {
            val text = value.trim()
            if (text.isBlank()) return 0L
            return try {
                LocalDateTime.parse(text, dtf)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toEpochSecond()
            } catch (_: Exception) { 0L }
        }
    }
}

@Serializable
data class AiResponse(
    val events: List<AiEventDto> = emptyList()
)

data class AnalysisFailure(
    val title: String,
    val detail: String,
    val errorCode: String? = null,
    val retryable: Boolean = true
) {
    fun fullMessage(): String {
        return if (detail.isBlank()) title else "$title：$detail"
    }
}

sealed class AnalysisResult<out T> {
    data class Success<T>(
        val data: T,
        val bills: List<com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft> = emptyList(),
        val billIssues: List<String> = emptyList(),
        val accountingResult: com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionResult? = null,
    ) : AnalysisResult<T>() {
        fun feedback(): String = buildList {
            val count = (data as? Collection<*>)?.size ?: 1
            if (count > 0) add("已识别 $count 个日程，正在保存")
            accountingResult?.let {
                if (it.saved.isNotEmpty()) add("已记 ${it.saved.size} 笔账单")
                if (it.duplicates > 0) add("账单重复 ${it.duplicates} 笔，暂不入库")
                if (it.suspectedDuplicates > 0) add("疑似重复 ${it.suspectedDuplicates} 笔，暂不入库")
                if (it.pending > 0) add("${it.pending} 笔待核对，暂不入库")
            }
            if (bills.isNotEmpty() && accountingResult == null) add("${bills.size} 条账单待处理")
            addAll(billIssues)
        }.joinToString("；")
    }
    data class Empty(val message: String = "未识别到有效日程") : AnalysisResult<Nothing>()
    data class Failure(val failure: AnalysisFailure) : AnalysisResult<Nothing>()
}

private data class AiEventFeature(
    val event: RecognitionDraft,
    val normalizedTitle: String,
    val startTime: LocalDateTime?,
    val pickupCode: String?,
    val pickupPlatform: String?,
    val ridePlate: String?,
    val trainNo: String?,
    val hasMicroFormat: Boolean,
    val isPickup: Boolean
)

object RecognitionProcessor {
    private const val TAG = "CALENDAR_AI_DEBUG"

    suspend fun parseUserText(text: String, settings: MySettings, context: Context): AnalysisResult<RecognitionDraft> {
        val appContext = context.applicationContext
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeStr = now.format(dtfFull)
        val dateToday = now.format(dtfDate)
        val dayOfWeek = getDayOfWeek(now)

        val rulePatch = RulePatchProvider.loadSchedulePatch(appContext)

        val prompt = AiPrompts.getUserTextPrompt(
            context = appContext,
            timeStr = timeStr,
            dateToday = dateToday,
            dayOfWeek = dayOfWeek,
            rulePatch = rulePatch,
            defaultDurationMinutes = settings.defaultEventDurationMinutes
        )

        Log.d(TAG, "========== [AI 自然语言输入] ==========")
        Log.d(TAG, "用户输入: $text")

        val modelConfig = settings.activeAiConfig()
        if (!modelConfig.isConfigured()) {
            Log.e(TAG, "AI 配置缺失，无法解析文本输入")
            return AnalysisResult.Failure(AnalysisFailure("分析失败", "AI 配置缺失"))
        }
        val modelName = modelConfig.name.ifBlank { "deepseek-chat" }
        val request = ModelRequest(
            model = modelName,
            messages = listOf(
                ModelMessage("system", prompt),
                ModelMessage("user", text)
            ),
            temperature = 0.3
        )

        return try {
            when (val response = ApiModelProvider.generate(
                request = request,
                apiKey = modelConfig.key,
                baseUrl = modelConfig.url,
                modelName = modelName,
                disableThinking = settings.disableThinking
            )) {
                is ApiCallResult.Success -> {
                    Log.d(TAG, "[AI文本输入] 原始响应(${response.content.length} chars): ${response.content}")
                    val cleanJson = cleanJsonString(response.content)
                    Log.d(TAG, "[AI文本输入] 清洗后 JSON(${cleanJson.length} chars): $cleanJson")
                    val parsedEvent = parseCalendarEvents(cleanJson).firstOrNull()
                    if (parsedEvent == null || parsedEvent.title.isBlank()) {
                        Log.d(TAG, "[AI文本输入] 解析结果为空")
                        AnalysisResult.Empty()
                    } else {
                        val normalized = enforceRuleHeaders(listOf(parsedEvent)).first()
                        AnalysisResult.Success(normalized)
                    }
                }
                is ApiCallResult.Failure -> {
                    val failure = mapApiFailure(response)
                    AnalysisResult.Failure(failure)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI 解析异常", e)
            AnalysisResult.Failure(AnalysisFailure("分析失败", "返回格式错误"))
        }
    }

    suspend fun analyzeTextEvents(text: String, settings: MySettings, context: Context): AnalysisResult<List<RecognitionDraft>> {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return AnalysisResult.Empty()
        return analyzeSchedule(normalizedText, settings, context.applicationContext)
    }

    /** 图片直接交给多模态模型，二维码仍由本地条码扫描补全。 */
    suspend fun analyzeImage(bitmap: Bitmap, settings: MySettings, context: Context): AnalysisResult<List<RecognitionDraft>> {
        val result = analyzeImageWithMultimodal(bitmap, settings, context.applicationContext)
        return attachQrPayloads(result, scanQrPayloadsSafely(bitmap))
    }

    private suspend fun analyzeSchedule(
        extractedText: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<RecognitionDraft>> {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val rulePatch = RulePatchProvider.loadSchedulePatch(context)

        val schedulePrompt = AiPrompts.getUserTextPrompt(
            context = context.applicationContext,
            timeStr = now.format(dtfFull),
            dateToday = now.format(dtfDate),
            dayOfWeek = getDayOfWeek(now),
            rulePatch = rulePatch,
            defaultDurationMinutes = settings.defaultEventDurationMinutes
        )

        return executeAiRequest(schedulePrompt, extractedText, settings, "日程识别")
    }

    private suspend fun executeAiRequest(
        systemPrompt: String,
        userText: String,
        settings: MySettings,
        debugTag: String
    ): AnalysisResult<List<RecognitionDraft>> {
        val modelConfig = settings.activeAiConfig()
        if (!modelConfig.isConfigured()) {
            Log.e(TAG, "[$debugTag] AI 配置缺失，无法请求")
            return AnalysisResult.Failure(AnalysisFailure("分析失败", "AI 配置缺失"))
        }
        val modelName = modelConfig.name.ifBlank { "deepseek-chat" }
        val userPrompt = "[输入文本开始]\n$userText\n[输入文本结束]"

        val request = ModelRequest(
            model = modelName,
            temperature = 0.1,
            messages = listOf(
                ModelMessage("system", systemPrompt),
                ModelMessage("user", userPrompt)
            )
        )

        return try {
            when (val response = ApiModelProvider.generate(
                request = request,
                apiKey = modelConfig.key,
                baseUrl = modelConfig.url,
                modelName = modelName,
                disableThinking = settings.disableThinking
            )) {
                is ApiCallResult.Success -> {
                    val cleanJson = cleanJsonString(response.content)
                    val content = RecognitionJsonParser.parseContent(cleanJson)
                    val parsedEvents = content.events

                    Log.d(TAG, "[$debugTag] AI 解析完成，生成 ${parsedEvents.size} 个事件")
                    if (parsedEvents.isEmpty() && content.bills.isEmpty()) {
                        AnalysisResult.Empty(content.issues.joinToString("；").ifBlank { "未识别到有效日程或账单" })
                    } else {
                        AnalysisResult.Success(enforceRuleHeaders(parsedEvents), content.bills, content.issues)
                    }
                }
                is ApiCallResult.Failure -> {
                    val failure = mapApiFailure(response)
                    AnalysisResult.Failure(failure)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$debugTag] 解析失败", e)
            AnalysisResult.Failure(AnalysisFailure("分析失败", "返回格式错误"))
        }
    }

    private suspend fun analyzeImageWithMultimodal(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<RecognitionDraft>> {
        val modelConfig = settings.activeAiConfig()
        if (!modelConfig.isConfigured()) {
            Log.e(TAG, "多模态 AI 配置缺失，跳过识别")
            return AnalysisResult.Failure(AnalysisFailure("分析失败", "AI 配置缺失"))
        }

        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dtfTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        val rulePatch = RulePatchProvider.loadSchedulePatch(context)

        val prompt = AiPrompts.getMultimodalUnifiedPrompt(
            context = context.applicationContext,
            timeStr = now.format(dtfFull),
            dateToday = now.format(dtfDate),
            dateYesterday = now.minusDays(1).format(dtfDate),
            dateBeforeYesterday = now.minusDays(2).format(dtfDate),
            nowTime = now.format(dtfTime),
            nowPlusHourTime = now.plusHours(1).format(dtfTime),
            dayOfWeek = getDayOfWeek(now),
            rulePatch = rulePatch,
            defaultDurationMinutes = settings.defaultEventDurationMinutes
        )

        val imageBytes = bitmapToJpegBytes(bitmap)

        return try {
            when (val response = ApiModelProvider.generateWithImage(
                prompt = prompt,
                imageBytes = imageBytes,
                mimeType = "image/jpeg",
                apiKey = modelConfig.key,
                baseUrl = modelConfig.url,
                modelName = modelConfig.name,
                disableThinking = settings.disableThinking
            )) {
                is ApiCallResult.Success -> {
                    val cleanJson = cleanJsonString(response.content)
                        Log.d(TAG, "[多模态识别] 清洗后内容(${cleanJson.length} chars): $cleanJson")
                        val content = RecognitionJsonParser.parseContent(cleanJson)
                        val events = content.events
                        val normalizedEvents = enforceRuleHeaders(events)

                        val pickupEvents = normalizedEvents.filter(::isInstantCodeDraft)
                        val scheduleEvents = normalizedEvents.filterNot(::isInstantCodeDraft)
                    val refinedScheduleEvents = filterRedundantSchedules(scheduleEvents, pickupEvents)

                        val mergedEvents = refinedScheduleEvents + pickupEvents
                        val finalEvents = deduplicateAiEvents(mergedEvents)

                    if (finalEvents.isEmpty() && content.bills.isEmpty()) {
                        AnalysisResult.Empty(content.issues.joinToString("；").ifBlank { "未识别到有效日程或账单" })
                    } else {
                        AnalysisResult.Success(finalEvents, content.bills, content.issues)
                    }
                }
                is ApiCallResult.Failure -> {
                    Log.e(
                        TAG,
                        "[多模态识别] API 失败: kind=${response.kind}, status=${response.statusCode}, message=${response.message}, rawBody=${response.rawBody}"
                    )
                    val mapped = AiFailureMapper.mapImage(response)
                    val failure = AnalysisFailure(mapped.title, mapped.detail, mapped.errorCode, mapped.retryable)
                    AnalysisResult.Failure(failure)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[多模态识别] 解析失败", e)
            AnalysisResult.Failure(AnalysisFailure("分析失败", "返回格式错误"))
        }
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        return ImageCompressionUtils.compressForAiRecognition(bitmap)
    }

    private fun cleanJsonString(response: String): String {
        return RecognitionJsonParser.cleanJsonString(response.removePrefix("\uFEFF"))
    }

    private fun parseCalendarEvents(cleanJson: String): List<RecognitionDraft> {
        return RecognitionJsonParser.parseCalendarEvents(cleanJson)
    }

    private fun mapApiFailure(failure: ApiCallResult.Failure): AnalysisFailure {
        val mapped = AiFailureMapper.map(failure)
        return AnalysisFailure(mapped.title, mapped.detail)
    }

    private fun enforceRuleHeaders(events: List<RecognitionDraft>): List<RecognitionDraft> {
        return events.map { event ->
            val ruleId = normalizeRuleId(event)
            val description = normalizeDescriptionHeader(event.description, ruleId)
            event.copy(
                description = description,
                tag = ruleId
            )
        }
    }

    private suspend fun scanQrPayloadsSafely(bitmap: Bitmap): List<String> {
        return runCatching { InstantCodeQrSupport.scanQrPayloads(bitmap) }
            .onFailure { Log.w(TAG, "二维码内容识别失败", it) }
            .getOrDefault(emptyList())
    }

    private fun attachQrPayloads(
        result: AnalysisResult<List<RecognitionDraft>>,
        qrPayloads: List<String>
    ): AnalysisResult<List<RecognitionDraft>> {
        return when (result) {
            is AnalysisResult.Success -> result.copy(data = attachQrPayloads(result.data, qrPayloads))
            else -> result
        }
    }

    private fun attachQrPayloads(
        events: List<RecognitionDraft>,
        qrPayloads: List<String>
    ): List<RecognitionDraft> {
        val cleanPayloads = qrPayloads.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanPayloads.isEmpty()) return events

        val instantIndices = events.indices.filter { index ->
            isInstantCodeDraft(events[index]) && events[index].qrPayload.isBlank()
        }
        if (instantIndices.isEmpty()) return events

        var payloadIndex = 0
        return events.mapIndexed { index, event ->
            if (index !in instantIndices) return@mapIndexed event
            val payload = if (cleanPayloads.size == 1) {
                cleanPayloads.first()
            } else {
                cleanPayloads.getOrNull(payloadIndex++) ?: cleanPayloads.first()
            }
            event.copy(qrPayload = payload)
        }
    }

    private fun isInstantCodeDraft(event: RecognitionDraft): Boolean {
        val ruleId = RuleMatchingEngine.resolvePayload(event.description, event.tag)?.ruleId
            ?: event.tag.trim().lowercase()
        return RuleMatchingEngine.isInstantCodeRule(ruleId)
    }

    private fun normalizeRuleId(event: RecognitionDraft): String {
        val fromHeader = RuleMatchingEngine.resolvePayload(event.description, event.tag)?.ruleId
        if (!fromHeader.isNullOrBlank()) return fromHeader

        val raw = event.tag.trim().lowercase()
        if (raw == RuleMatchingEngine.RULE_TRAIN ||
            raw == RuleMatchingEngine.RULE_TAXI ||
            raw == RuleMatchingEngine.RULE_PICKUP ||
            raw == RuleMatchingEngine.RULE_FLIGHT ||
            raw.matches(Regex("[a-z0-9_]+"))) {
            return raw
        }

        // type field removed from RecognitionDraft; tag already resolved in AiEventDto
        return when (event.tag.trim().lowercase()) {
            "pickup" -> RuleMatchingEngine.RULE_PICKUP
            else -> RuleMatchingEngine.RULE_GENERAL
        }
    }

    private fun normalizeDescriptionHeader(description: String, ruleId: String): String {
        val displayName = when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> "列车"
            RuleMatchingEngine.RULE_TAXI -> "用车"
            RuleMatchingEngine.RULE_FLIGHT -> "航班"
            RuleMatchingEngine.RULE_PICKUP -> "取件"
            RuleMatchingEngine.RULE_FOOD -> "取餐"
            RuleMatchingEngine.RULE_TICKET -> "取票"
            RuleMatchingEngine.RULE_SENDER -> "寄件"
            else -> "日程"
        }
        val clean = description.trim()
        if (clean.isBlank()) return "【$displayName】"

        val payload = RuleMatchingEngine.resolvePayload(clean, ruleId)
        if (payload != null) {
            return "【$displayName】${payload.payload}".trim()
        }

        return "【$displayName】$clean".trim()
    }

    /**
     * 低信息量日程清洗：过滤被 pickup 覆盖的 general 事件
     * 
     * 逻辑：
     * 1. 如果 pickupEvents 为空，不做处理
     * 2. 如果 general 事件的标题只包含取件关键词（如"取件"），直接删除
     * 3. 如果 general 事件包含"取件+其他内容"，检查其他内容是否已被 pickup 覆盖
     * 
     * 示例：
     * - "取件" + "取件码123" → 删除 "取件"
     * - "蜜雪冰城取件" + "蜜雪冰城 123" → 删除 "蜜雪冰城取件"
     * - "开会后取件" + "顺丰 123" → 保留 "开会后取件"
     */
    private fun filterRedundantSchedules(
        scheduleEvents: List<RecognitionDraft>,
        pickupEvents: List<RecognitionDraft>
    ): List<RecognitionDraft> {
        if (pickupEvents.isEmpty()) return scheduleEvents

        val pickupKeywordsRegex = Regex("(取|拿|收)(件|快递|餐|外卖|货)")

        return scheduleEvents.filter { schedule ->
            // 1. 如果标题不包含取件关键词，保留
            if (!schedule.title.contains(pickupKeywordsRegex)) {
                return@filter true
            }

            // 2. 提取"剩余信息"（去掉取件关键词）
            val subjectInfo = schedule.title.replace(pickupKeywordsRegex, "").trim()

            // 3. 如果剩余信息为空（纯动作如"取件"），删除
            if (subjectInfo.isEmpty()) {
                Log.d(TAG, "过滤纯取件标题: ${schedule.title}")
                return@filter false
            }

            // 4. 检查剩余信息是否已被 pickup 覆盖
            val isCoveredByPickup = pickupEvents.any { pickup ->
                pickup.title.contains(subjectInfo, ignoreCase = true)
            }

            if (isCoveredByPickup) {
                Log.d(TAG, "过滤被覆盖的日程: ${schedule.title} (被 ${pickupEvents.find { it.title.contains(subjectInfo, ignoreCase = true) }?.title} 覆盖)")
            }

            // 如果被覆盖则删除，否则保留
            !isCoveredByPickup
        }
    }

    private fun getDayOfWeek(now: LocalDateTime): String {
        return when(now.dayOfWeek) {
            DayOfWeek.MONDAY -> "星期一"
            DayOfWeek.TUESDAY -> "星期二"
            DayOfWeek.WEDNESDAY -> "星期三"
            DayOfWeek.THURSDAY -> "星期四"
            DayOfWeek.FRIDAY -> "星期五"
            DayOfWeek.SATURDAY -> "星期六"
            DayOfWeek.SUNDAY -> "星期日"
        }
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val licensePlateRegex = Regex("[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][A-Z0-9·•\\.]{4,6}")
    private val trainNoRegex = Regex("[GCDZTKSYL]\\d{1,4}")
    private const val DUPLICATE_TIME_TOLERANCE_MINUTES = 10L

    private fun deduplicateAiEvents(events: List<RecognitionDraft>): List<RecognitionDraft> {
        if (events.size <= 1) return events
        val features = events.map { buildFeature(it) }
        val consumed = BooleanArray(events.size)

        val pickupGroups = mutableMapOf<String, MutableList<Int>>()
        val rideGroups = mutableMapOf<String, MutableList<Int>>()
        val trainGroups = mutableMapOf<String, MutableList<Int>>()

        features.forEachIndexed { index, feature ->
            feature.pickupCode?.takeIf { it.isNotBlank() }?.let {
                pickupGroups.getOrPut(it) { mutableListOf() }.add(index)
            }
            feature.ridePlate?.takeIf { it.isNotBlank() }?.let {
                rideGroups.getOrPut(it) { mutableListOf() }.add(index)
            }
            feature.trainNo?.takeIf { it.isNotBlank() }?.let {
                trainGroups.getOrPut(it) { mutableListOf() }.add(index)
            }
        }

        fun consumeGroup(group: List<Int>) {
            val active = group.filterNot { consumed[it] }
            if (active.size <= 1) return
            val best = active.maxByOrNull { scoreFeature(features[it]) } ?: return
            active.forEach { if (it != best) consumed[it] = true }
        }

        pickupGroups.values.forEach { consumeGroup(it) }
        rideGroups.values.forEach { consumeGroup(it) }
        trainGroups.values.forEach { consumeGroup(it) }

        val pickupIndices = features.indices.filter { !consumed[it] && features[it].isPickup }
        val pickupFeatures = pickupIndices.map { it to features[it] }
        features.forEachIndexed { index, feature ->
            if (consumed[index] || feature.isPickup) return@forEachIndexed
            val matchedPickup = pickupFeatures.firstOrNull { (_, pickup) ->
                isPickupDuplicate(feature, pickup)
            }
            if (matchedPickup != null) {
                consumed[index] = true
            }
        }

        val remaining = features.indices.filterNot { consumed[it] }
        val groupedByTitle = remaining.groupBy { features[it].normalizedTitle }.filterKeys { it.isNotBlank() }
        groupedByTitle.values.forEach { indices ->
            if (indices.size <= 1) return@forEach
            val sorted = indices.sortedBy { features[it].startTime ?: LocalDateTime.MAX }
            var anchor = sorted.first()
            for (i in 1 until sorted.size) {
                val candidate = sorted[i]
                if (isWeakDuplicate(features[anchor], features[candidate])) {
                    val winner = if (scoreFeature(features[anchor]) >= scoreFeature(features[candidate])) anchor else candidate
                    val loser = if (winner == anchor) candidate else anchor
                    consumed[loser] = true
                    anchor = winner
                } else {
                    anchor = candidate
                }
            }
        }

        return features.indices.filterNot { consumed[it] }.map { features[it].event }
    }

    private fun buildFeature(event: RecognitionDraft): AiEventFeature {
        val title = event.title.trim()
        val description = event.description.trim()
        val normalizedTitle = normalizeText(title)
        val startTime = if (event.startTS > 0L) {
            java.time.Instant.ofEpochSecond(event.startTS)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
        } else null

        val hasPickupMicro = description.startsWith("【取件】") ||
            description.startsWith("【取餐】") ||
            description.startsWith("【取票】") ||
            description.startsWith("【寄件】") ||
            description.startsWith("【pickup】")
        val hasRideMicro = description.startsWith("【用车】") || description.startsWith("【taxi】")
        val hasTrainMicro = description.startsWith("【列车】") || description.startsWith("【train】")
        val hasMicroFormat = hasPickupMicro || hasRideMicro || hasTrainMicro

        var pickupCode: String? = null
        var pickupPlatform: String? = null
        if (hasPickupMicro) {
            val payload = RuleMatchingEngine.resolvePayload(description, event.tag)
            if (payload != null && RuleMatchingEngine.isInstantCodeRule(payload.ruleId)) {
                val fields = RuleMatchingEngine.splitFields(payload.payload, 3)
                pickupCode = fields[0].trim().ifBlank { null }
                pickupPlatform = fields[1].trim().ifBlank { null }
            }
        }

        val ridePlate = extractLicensePlate(description).ifBlankOrNull()
            ?: extractLicensePlate(title).ifBlankOrNull()

        val trainNo = extractTrainNo(description).ifBlankOrNull()
            ?: extractTrainNo(title).ifBlankOrNull()

        val isPickup = isInstantCodeDraft(event) || hasPickupMicro

        return AiEventFeature(
            event = event,
            normalizedTitle = normalizedTitle,
            startTime = startTime,
            pickupCode = pickupCode,
            pickupPlatform = pickupPlatform,
            ridePlate = ridePlate,
            trainNo = trainNo,
            hasMicroFormat = hasMicroFormat,
            isPickup = isPickup
        )
    }

    private fun scoreFeature(feature: AiEventFeature): Int {
        var score = 0
        if (feature.hasMicroFormat) score += 5
        if (!feature.pickupCode.isNullOrBlank() || !feature.ridePlate.isNullOrBlank() || !feature.trainNo.isNullOrBlank()) score += 3
        if (feature.startTime != null) score += 2
        val descLen = feature.event.description.trim().length
        val titleLen = feature.event.title.trim().length
        if (descLen > titleLen) score += 1
        if (descLen >= 12) score += 1
        return score
    }

    private fun isPickupDuplicate(candidate: AiEventFeature, pickup: AiEventFeature): Boolean {
        val platform = pickup.pickupPlatform?.let { normalizeText(it) }.orEmpty()
        val pickupTitle = pickup.normalizedTitle
        val candidateTitle = candidate.normalizedTitle

        val platformMatch = platform.isNotBlank() && (candidateTitle.contains(platform) || platform.contains(candidateTitle))
        val titleMatch = pickupTitle.isNotBlank() && candidateTitle.isNotBlank() && (candidateTitle.contains(pickupTitle) || pickupTitle.contains(candidateTitle))
        if (!platformMatch && !titleMatch) return false

        val timeDiff = minutesDiff(candidate.startTime, pickup.startTime)
        return timeDiff == null || timeDiff <= DUPLICATE_TIME_TOLERANCE_MINUTES
    }

    private fun isWeakDuplicate(a: AiEventFeature, b: AiEventFeature): Boolean {
        if (a.normalizedTitle.isBlank() || b.normalizedTitle.isBlank()) return false
        if (a.normalizedTitle != b.normalizedTitle) return false
        val timeDiff = minutesDiff(a.startTime, b.startTime) ?: return false
        return timeDiff <= DUPLICATE_TIME_TOLERANCE_MINUTES
    }

    private fun minutesDiff(a: LocalDateTime?, b: LocalDateTime?): Long? {
        if (a == null || b == null) return null
        return kotlin.math.abs(ChronoUnit.MINUTES.between(a, b))
    }

    private fun parseDateTime(value: String?): LocalDateTime? {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return null
        return try {
            LocalDateTime.parse(text, timeFormatter)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractLicensePlate(text: String): String? {
        val match = licensePlateRegex.find(text)
        return match?.value
    }

    private fun extractTrainNo(text: String): String? {
        val match = trainNoRegex.find(text)
        return match?.value
    }

    private fun normalizeText(text: String): String {
        val cleaned = text.trim().lowercase()
        if (cleaned.isBlank()) return ""
        return cleaned.replace(Regex("[\\s\\p{Punct}·•、，。；：！!？?（）()\\[\\]【】{}<>《》“”\"'`~|/_\\-]+"), "")
    }

    private fun String?.ifBlankOrNull(): String? {
        val value = this?.trim().orEmpty()
        return value.ifBlank { null }
    }
}
