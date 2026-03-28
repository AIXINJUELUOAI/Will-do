package com.antgskds.calendarassistant.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.antgskds.calendarassistant.core.util.ImageCompressionUtils
import com.antgskds.calendarassistant.core.util.LayoutAnalyzer
import com.antgskds.calendarassistant.core.util.OcrElement
import com.antgskds.calendarassistant.core.util.ScreenMetrics
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.ai.RulePatchProvider
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.ModelMessage
import com.antgskds.calendarassistant.data.model.ModelRequest
import com.antgskds.calendarassistant.data.model.MySettings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class AiResponse(
    val events: List<CalendarEventData> = emptyList()
)

data class AnalysisFailure(
    val title: String,
    val detail: String
) {
    fun fullMessage(): String {
        return if (detail.isBlank()) title else "$title：$detail"
    }
}

sealed class AnalysisResult<out T> {
    data class Success<T>(val data: T) : AnalysisResult<T>()
    data class Empty(val message: String = "未识别到有效日程") : AnalysisResult<Nothing>()
    data class Failure(val failure: AnalysisFailure) : AnalysisResult<Nothing>()
}

data class OcrResult(
    val rawText: String,
    val reconstructedText: String,
    val screenWidth: Int,
    val screenHeight: Int
)

private data class AiEventFeature(
    val event: CalendarEventData,
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
    private const val TAG = "CALENDAR_OCR_DEBUG"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * Lightweight OCR helper for manual image import.
     * Returns raw recognized text (no layout reconstruction).
     */
    suspend fun recognizeText(bitmap: Bitmap): String {
        return try {
            processImageWithMlKit(bitmap).text
        } catch (e: Exception) {
            Log.e(TAG, "OCR 识别失败", e)
            ""
        }
    }

    suspend fun parseUserText(text: String, settings: MySettings, context: Context): AnalysisResult<CalendarEventData> {
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
            rulePatch = rulePatch
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
                    val hasEventsField = Regex("\"events\"\\s*:").containsMatchIn(cleanJson)
                    val parsedEvent = if (hasEventsField) {
                        val wrapper = jsonParser.decodeFromString<AiResponse>(cleanJson)
                        Log.d(TAG, "[AI文本输入] 解析为事件列表: size=${wrapper.events.size}")
                        wrapper.events.firstOrNull()
                    } else {
                        val event = jsonParser.decodeFromString<CalendarEventData>(cleanJson)
                        Log.d(
                            TAG,
                            "[AI文本输入] 解析为单事件: title=${event.title}, start=${event.startTime}, end=${event.endTime}, type=${event.type}, tag=${event.tag}"
                        )
                        event
                    }
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

    suspend fun analyzeImage(bitmap: Bitmap, settings: MySettings, context: Context): AnalysisResult<List<CalendarEventData>> {
        val appContext = context.applicationContext
        Log.i(TAG, ">>> 开始处理图片 (尺寸: ${bitmap.width} x ${bitmap.height})")

        if (settings.useMultimodalAi) {
            return analyzeImageWithMultimodal(bitmap, settings, appContext)
        }

        val ocrResult = try {
            val visionText = processImageWithMlKit(bitmap)

            withContext(Dispatchers.Default) {
                val screenWidth = bitmap.width
                val screenHeight = bitmap.height

                val ocrElements = visionText.textBlocks
                    .flatMap { it.lines }
                    .flatMap { it.elements }
                    .filter { it.text.isNotBlank() }
                    .map { element ->
                        OcrElement(
                            text = element.text,
                            boundingBox = element.boundingBox ?: Rect(),
                            confidence = element.confidence ?: 0f
                        )
                    }

                val filteredElements = LayoutAnalyzer.filterNoise(
                    ocrElements,
                    ScreenMetrics.getStatusBarHeight(appContext),
                    ScreenMetrics.getNavigationBarHeight(appContext),
                    screenHeight
                )

                val reconstructedText = LayoutAnalyzer.reconstructLayout(filteredElements, screenWidth)

                val rawText = filteredElements
                    .sortedBy { it.boundingBox.top }
                    .joinToString("\n") { it.text }

                OcrResult(rawText, reconstructedText, screenWidth, screenHeight)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR 过程发生异常", e)
            return AnalysisResult.Empty()
        }

        if (ocrResult.reconstructedText.isBlank()) {
            Log.w(TAG, "OCR 结果为空！")
            return AnalysisResult.Empty()
        }

        val anchoredText = injectDateAnchors(ocrResult.reconstructedText, LocalDate.now())

        Log.d(TAG, "========== [OCR 重构文本 (SSORS)] ==========")
        Log.d(TAG, anchoredText)
        Log.d(TAG, "============================================")

        return coroutineScope {
            try {
                val scheduleDeferred = async { analyzeSchedule(anchoredText, settings, appContext) }
                val pickupDeferred = async { analyzePickup(anchoredText, settings, appContext) }

                val scheduleResult = scheduleDeferred.await()
                val pickupResult = pickupDeferred.await()

                val scheduleEvents = (scheduleResult as? AnalysisResult.Success)?.data ?: emptyList()
                val pickupEvents = (pickupResult as? AnalysisResult.Success)?.data ?: emptyList()

                Log.d(TAG, "识别结果: 日程=${scheduleEvents.size}, 取件=${pickupEvents.size}")

                // 低信息量日程清洗：过滤被 pickup 覆盖的 general 事件
                val refinedScheduleEvents = filterRedundantSchedules(scheduleEvents, pickupEvents)
                if (refinedScheduleEvents.size < scheduleEvents.size) {
                    Log.d(TAG, "已过滤 ${scheduleEvents.size - refinedScheduleEvents.size} 个冗余日程")
                }

                val allEvents = refinedScheduleEvents + pickupEvents
                val finalEvents = deduplicateAiEvents(allEvents)

                if (finalEvents.isNotEmpty()) {
                    AnalysisResult.Success(finalEvents)
                } else {
                    val failure = listOf(scheduleResult, pickupResult)
                        .filterIsInstance<AnalysisResult.Failure>()
                        .firstOrNull()
                    if (failure != null) {
                        AnalysisResult.Failure(failure.failure)
                    } else {
                        AnalysisResult.Empty()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI 分析过程出错", e)
                AnalysisResult.Failure(AnalysisFailure("分析失败", "返回格式错误"))
            }
        }
    }

    private suspend fun analyzeSchedule(
        extractedText: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<CalendarEventData>> {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val rulePatch = RulePatchProvider.loadSchedulePatch(context)

        val schedulePrompt = AiPrompts.getSchedulePrompt(
            context = context.applicationContext,
            timeStr = now.format(dtfFull),
            dateToday = now.format(dtfDate),
            dateYesterday = now.minusDays(1).format(dtfDate),
            dateBeforeYesterday = now.minusDays(2).format(dtfDate),
            dayOfWeek = getDayOfWeek(now),
            rulePatch = rulePatch
        )

        return executeAiRequest(schedulePrompt, extractedText, settings, "日程识别")
    }

    private suspend fun analyzePickup(
        extractedText: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<CalendarEventData>> {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val dtfTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        val pickupPrompt = AiPrompts.getPickupPrompt(
            context = context.applicationContext,
            timeStr = now.format(dtfFull),
            nowTime = now.format(dtfTime),
            nowPlusHourTime = now.plusHours(1).format(dtfTime)
        )

        return executeAiRequest(pickupPrompt, extractedText, settings, "取件码识别")
    }

    private suspend fun executeAiRequest(
        systemPrompt: String,
        userText: String,
        settings: MySettings,
        debugTag: String
    ): AnalysisResult<List<CalendarEventData>> {
        val modelConfig = settings.activeAiConfig()
        if (!modelConfig.isConfigured()) {
            Log.e(TAG, "[$debugTag] AI 配置缺失，无法请求")
            return AnalysisResult.Failure(AnalysisFailure("分析失败", "AI 配置缺失"))
        }
        val modelName = modelConfig.name.ifBlank { "deepseek-chat" }
        val userPrompt = "[OCR文本开始]\n$userText\n[OCR文本结束]"

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
                    val aiResponse = jsonParser.decodeFromString<AiResponse>(cleanJson)

                    Log.d(TAG, "[$debugTag] AI 解析完成，生成 ${aiResponse.events.size} 个事件")
                    if (aiResponse.events.isEmpty()) {
                        AnalysisResult.Empty()
                    } else {
                        AnalysisResult.Success(enforceRuleHeaders(aiResponse.events))
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

    private suspend fun processImageWithMlKit(bitmap: Bitmap): Text =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    private suspend fun analyzeImageWithMultimodal(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<CalendarEventData>> {
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
            rulePatch = rulePatch
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
                        val events = try {
                            jsonParser.decodeFromString<AiResponse>(cleanJson).events
                        } catch (_: Exception) {
                            val single = jsonParser.decodeFromString<CalendarEventData>(cleanJson)
                            listOf(single)
                        }
                        val normalizedEvents = enforceRuleHeaders(events)

                        val pickupEvents = normalizedEvents.filter {
                            it.tag == EventTags.PICKUP || it.type == "pickup"
                        }
                        val scheduleEvents = normalizedEvents.filter {
                            it.tag != EventTags.PICKUP && it.type != "pickup"
                        }
                    val refinedScheduleEvents = filterRedundantSchedules(scheduleEvents, pickupEvents)

                        val mergedEvents = refinedScheduleEvents + pickupEvents
                        val finalEvents = deduplicateAiEvents(mergedEvents)

                    if (finalEvents.isEmpty()) {
                        AnalysisResult.Empty()
                    } else {
                        AnalysisResult.Success(finalEvents)
                    }
                }
                is ApiCallResult.Failure -> {
                    val failure = mapApiFailure(response)
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
        var json = response.trim()
        if (json.contains("```")) {
            json = json.substringAfter("json").substringAfter("\n").substringBeforeLast("```")
        }
        return json
    }

    private fun mapApiFailure(failure: ApiCallResult.Failure): AnalysisFailure {
        val title = "分析失败"
        return when (failure.kind) {
            ApiErrorKind.CONFIG -> AnalysisFailure(title, "AI 配置缺失")
            ApiErrorKind.PARSE -> AnalysisFailure(title, "返回格式错误")
            ApiErrorKind.NETWORK -> {
                val detail = buildNetworkDetail(failure.message)
                AnalysisFailure(title, detail)
            }
            ApiErrorKind.HTTP -> {
                val code = failure.statusCode
                val detail = when (code) {
                    401, 403 -> "API Key 无效或无权限 (${code})"
                    404 -> "服务地址(URL)无效或模型不存在 (404)"
                    400, 422 -> "请求参数错误/模型不支持该参数 (${code})"
                    null -> "请求失败"
                    else -> "HTTP $code"
                }
                AnalysisFailure(title, detail)
            }
            ApiErrorKind.UNKNOWN -> AnalysisFailure(title, failure.message.ifBlank { "请求失败" })
        }
    }

    private fun buildNetworkDetail(message: String): String {
        val lower = message.lowercase()
        val tag = when {
            lower.contains("timeout") || lower.contains("timed out") || lower.contains("sockettimeoutexception") -> "timeout"
            lower.contains("unknownhost") -> "unknown host"
            lower.contains("unreachable") -> "unreachable"
            lower.contains("network") -> "network"
            else -> ""
        }
        return if (tag.isBlank()) "网络连接失败" else "网络连接失败 ($tag)"
    }

    private fun enforceRuleHeaders(events: List<CalendarEventData>): List<CalendarEventData> {
        return events.map { event ->
            val ruleId = normalizeRuleId(event)
            val description = normalizeDescriptionHeader(event.description, ruleId)
            event.copy(
                description = description,
                tag = ruleId
            )
        }
    }

    private fun normalizeRuleId(event: CalendarEventData): String {
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

        val type = event.type.trim().lowercase()
        return when (type) {
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

    private val fullDateRegex = Regex("(\\d{4})[年/\\-.](\\d{1,2})[月/\\-.](\\d{1,2})(?:日|号)?")
    private val monthDayRegex = Regex("(\\d{1,2})[月/\\-.](\\d{1,2})(?:日|号)?")
    private val dayOnlyRegex = Regex("(?<!\\d)(\\d{1,2})(?:日|号)(?!\\d)")
    private val dayOfWeekRegex = Regex("(?:周|星期|礼拜)([一二三四五六日天])")

    private fun injectDateAnchors(text: String, now: LocalDate): String {
        if (text.isBlank()) return text
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val result = StringBuilder()
        var lastAnchor: LocalDate? = null

        text.lines().forEach { line ->
            result.appendLine(line)
            val anchor = parseBaseDateFromSystemLine(line, now) ?: return@forEach
            if (anchor != lastAnchor) {
                result.appendLine("[@date=${anchor.format(formatter)}]")
                lastAnchor = anchor
            }
        }

        return result.toString().trimEnd()
    }

    private fun parseBaseDateFromSystemLine(line: String, now: LocalDate): LocalDate? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("[C]")) return null
        val content = trimmed.removePrefix("[C]").trim()
        if (content.isBlank()) return null

        parseRelativeDateKeyword(content, now)?.let { return it }
        parseFullDate(content)?.let { return it }
        parseMonthDay(content, now)?.let { return it }
        parseDayOnly(content, now)?.let { return it }
        parseDayOfWeekOnly(content, now)?.let { return it }

        return null
    }

    private fun parseRelativeDateKeyword(text: String, now: LocalDate): LocalDate? {
        return when {
            text.contains("今天") -> now
            text.contains("昨日") || text.contains("昨天") -> now.minusDays(1)
            text.contains("前天") -> now.minusDays(2)
            else -> null
        }
    }

    private fun parseFullDate(text: String): LocalDate? {
        val match = fullDateRegex.find(text) ?: return null
        val year = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val month = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val day = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
        return safeDate(year, month, day)
    }

    private fun parseMonthDay(text: String, now: LocalDate): LocalDate? {
        val match = monthDayRegex.find(text) ?: return null
        val month = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val day = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        var candidate = safeDate(now.year, month, day) ?: return null
        if (candidate.isAfter(now.plusDays(7))) {
            candidate = candidate.minusYears(1)
        }
        return candidate
    }

    private fun parseDayOnly(text: String, now: LocalDate): LocalDate? {
        if (text.contains("月") || text.contains("-") || text.contains("/") || text.contains(".")) return null
        val match = dayOnlyRegex.find(text) ?: return null
        val day = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val currentMonthCandidate = safeDate(now.year, now.monthValue, day)
        val previousMonth = now.minusMonths(1)
        var candidate = currentMonthCandidate ?: safeDate(previousMonth.year, previousMonth.monthValue, day) ?: return null
        if (candidate.isAfter(now.plusDays(7))) {
            candidate = candidate.minusMonths(1)
        }
        return candidate
    }

    private fun parseDayOfWeekOnly(text: String, now: LocalDate): LocalDate? {
        val match = dayOfWeekRegex.find(text) ?: return null
        val dayChar = match.groupValues.getOrNull(1)?.firstOrNull() ?: return null
        val targetDay = resolveDayOfWeek(dayChar) ?: return null
        return now.with(TemporalAdjusters.previousOrSame(targetDay))
    }

    private fun resolveDayOfWeek(ch: Char): DayOfWeek? {
        return when (ch) {
            '一' -> DayOfWeek.MONDAY
            '二' -> DayOfWeek.TUESDAY
            '三' -> DayOfWeek.WEDNESDAY
            '四' -> DayOfWeek.THURSDAY
            '五' -> DayOfWeek.FRIDAY
            '六' -> DayOfWeek.SATURDAY
            '日', '天' -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? {
        return try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
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
        scheduleEvents: List<CalendarEventData>,
        pickupEvents: List<CalendarEventData>
    ): List<CalendarEventData> {
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

    private fun deduplicateAiEvents(events: List<CalendarEventData>): List<CalendarEventData> {
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

    private fun buildFeature(event: CalendarEventData): AiEventFeature {
        val title = event.title.trim()
        val description = event.description.trim()
        val normalizedTitle = normalizeText(title)
        val startTime = parseDateTime(event.startTime)

        val hasPickupMicro = description.startsWith("【取件】") || description.startsWith("【取餐】") || description.startsWith("【pickup】")
        val hasRideMicro = description.startsWith("【用车】") || description.startsWith("【taxi】")
        val hasTrainMicro = description.startsWith("【列车】") || description.startsWith("【train】")
        val hasMicroFormat = hasPickupMicro || hasRideMicro || hasTrainMicro

        var pickupCode: String? = null
        var pickupPlatform: String? = null
        if (hasPickupMicro) {
            val payload = RuleMatchingEngine.resolvePayload(description, RuleMatchingEngine.RULE_PICKUP)
            if (payload?.ruleId == RuleMatchingEngine.RULE_PICKUP) {
                val fields = RuleMatchingEngine.splitFields(payload.payload, 3)
                pickupCode = fields[0].trim().ifBlank { null }
                pickupPlatform = fields[1].trim().ifBlank { null }
            }
        }

        val ridePlate = extractLicensePlate(description).ifBlankOrNull()
            ?: extractLicensePlate(title).ifBlankOrNull()

        val trainNo = extractTrainNo(description).ifBlankOrNull()
            ?: extractTrainNo(title).ifBlankOrNull()

        val isPickup = event.tag == EventTags.PICKUP || event.type == "pickup" || hasPickupMicro

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
