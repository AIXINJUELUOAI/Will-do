package com.antgskds.calendarassistant.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.antgskds.calendarassistant.core.util.ImageCompressionUtils
import com.antgskds.calendarassistant.core.util.LayoutAnalyzer
import com.antgskds.calendarassistant.core.util.OcrElement
import com.antgskds.calendarassistant.core.util.ScreenMetrics
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

        val prompt = AiPrompts.getUserTextPrompt(
            context = appContext,
            timeStr = timeStr,
            dateToday = dateToday,
            dayOfWeek = dayOfWeek
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
                        AnalysisResult.Success(parsedEvent)
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
                val finalEvents = allEvents
                    .groupBy { "${it.title}|${it.startTime}" }
                    .map { (_, events) ->
                        events.maxByOrNull { if (it.tag == "pickup") 1 else 0 }!!
                    }

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

        val schedulePrompt = AiPrompts.getSchedulePrompt(
            context = context.applicationContext,
            timeStr = now.format(dtfFull),
            dateToday = now.format(dtfDate),
            dateYesterday = now.minusDays(1).format(dtfDate),
            dateBeforeYesterday = now.minusDays(2).format(dtfDate),
            dayOfWeek = getDayOfWeek(now)
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
                        AnalysisResult.Success(aiResponse.events)
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

        val prompt = AiPrompts.getMultimodalUnifiedPrompt(
            context = context.applicationContext,
            timeStr = now.format(dtfFull),
            dateToday = now.format(dtfDate),
            dateYesterday = now.minusDays(1).format(dtfDate),
            dateBeforeYesterday = now.minusDays(2).format(dtfDate),
            nowTime = now.format(dtfTime),
            nowPlusHourTime = now.plusHours(1).format(dtfTime),
            dayOfWeek = getDayOfWeek(now)
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

                    val pickupEvents = events.filter {
                        it.tag == EventTags.PICKUP || it.type == "pickup"
                    }
                    val scheduleEvents = events.filter {
                        it.tag != EventTags.PICKUP && it.type != "pickup"
                    }
                    val refinedScheduleEvents = filterRedundantSchedules(scheduleEvents, pickupEvents)

                    val mergedEvents = refinedScheduleEvents + pickupEvents
                    val finalEvents = mergedEvents
                        .groupBy { "${it.title}|${it.startTime}" }
                        .mapNotNull { (_, events) ->
                            events.maxByOrNull { if (it.tag == EventTags.PICKUP) 1 else 0 }
                        }

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
}
