package com.antgskds.calendarassistant.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.antgskds.calendarassistant.core.util.LayoutAnalyzer
import com.antgskds.calendarassistant.core.util.OcrElement
import com.antgskds.calendarassistant.core.util.ScreenMetrics
import com.antgskds.calendarassistant.data.model.CalendarEventData
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class AiResponse(
    val events: List<CalendarEventData> = emptyList()
)

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

    suspend fun parseUserText(text: String, settings: MySettings): CalendarEventData? {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeStr = now.format(dtfFull)
        val dateToday = now.format(dtfDate)
        val dayOfWeek = getDayOfWeek(now)

        val prompt = AiPrompts.getUserTextPrompt(
            timeStr = timeStr,
            dateToday = dateToday,
            dayOfWeek = dayOfWeek
        )

        Log.d(TAG, "========== [AI 自然语言输入] ==========")
        Log.d(TAG, "用户输入: $text")

        val modelName = settings.modelName.ifBlank { "deepseek-chat" }
        val request = ModelRequest(
            model = modelName,
            messages = listOf(
                ModelMessage("system", prompt),
                ModelMessage("user", text)
            ),
            temperature = 0.3
        )

        return try {
            val response = ApiModelProvider.generate(
                request = request,
                apiKey = settings.modelKey,
                baseUrl = settings.modelUrl,
                modelName = modelName
            )

            if (response.startsWith("Error:")) {
                Log.e(TAG, "API 返回错误: $response")
                return null
            }

            val cleanJson = cleanJsonString(response)
            try {
                jsonParser.decodeFromString<CalendarEventData>(cleanJson)
            } catch (e: Exception) {
                val wrapper = jsonParser.decodeFromString<AiResponse>(cleanJson)
                wrapper.events.firstOrNull()
            }

        } catch (e: Exception) {
            Log.e(TAG, "AI 解析异常", e)
            null
        }
    }

    suspend fun analyzeImage(bitmap: Bitmap, settings: MySettings, context: Context): List<CalendarEventData> {
        Log.i(TAG, ">>> 开始处理图片 (尺寸: ${bitmap.width} x ${bitmap.height})")

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
                    ScreenMetrics.getStatusBarHeight(context),
                    ScreenMetrics.getNavigationBarHeight(context),
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
            return emptyList()
        }

        if (ocrResult.reconstructedText.isBlank()) {
            Log.w(TAG, "OCR 结果为空！")
            return emptyList()
        }

        Log.d(TAG, "========== [OCR 重构文本 (SSORS)] ==========")
        Log.d(TAG, ocrResult.reconstructedText)
        Log.d(TAG, "============================================")

        return coroutineScope {
            try {
                val scheduleDeferred = async { analyzeSchedule(ocrResult.reconstructedText, settings) }
                val pickupDeferred = async { analyzePickup(ocrResult.reconstructedText, settings) }

                val scheduleEvents = scheduleDeferred.await()
                val pickupEvents = pickupDeferred.await()

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

                finalEvents
            } catch (e: Exception) {
                Log.e(TAG, "AI 分析过程出错", e)
                emptyList()
            }
        }
    }

    private suspend fun analyzeSchedule(extractedText: String, settings: MySettings): List<CalendarEventData> {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val schedulePrompt = AiPrompts.getSchedulePrompt(
            timeStr = now.format(dtfFull),
            dateToday = now.format(dtfDate),
            dateYesterday = now.minusDays(1).format(dtfDate),
            dateBeforeYesterday = now.minusDays(2).format(dtfDate),
            dayOfWeek = getDayOfWeek(now)
        )

        return executeAiRequest(schedulePrompt, extractedText, settings, "日程识别")
    }

    private suspend fun analyzePickup(extractedText: String, settings: MySettings): List<CalendarEventData> {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val dtfTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        val pickupPrompt = AiPrompts.getPickupPrompt(
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
    ): List<CalendarEventData> {
        val modelName = settings.modelName.ifBlank { "deepseek-chat" }
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
            val responseText = ApiModelProvider.generate(
                request = request,
                apiKey = settings.modelKey,
                baseUrl = settings.modelUrl,
                modelName = modelName
            )

            if (responseText.startsWith("Error:")) {
                Log.e(TAG, "[$debugTag] API 请求失败: $responseText")
                return emptyList()
            }

            val cleanJson = cleanJsonString(responseText)
            val aiResponse = jsonParser.decodeFromString<AiResponse>(cleanJson)

            Log.d(TAG, "[$debugTag] AI 解析完成，生成 ${aiResponse.events.size} 个事件")
            aiResponse.events

        } catch (e: Exception) {
            Log.e(TAG, "[$debugTag] 解析失败", e)
            emptyList()
        }
    }

    private suspend fun processImageWithMlKit(bitmap: Bitmap): Text =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    private fun cleanJsonString(response: String): String {
        var json = response.trim()
        if (json.contains("```")) {
            json = json.substringAfter("json").substringAfter("\n").substringBeforeLast("```")
        }
        return json
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
