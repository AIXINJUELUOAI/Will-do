package com.antgskds.calendarassistant.core.ai

import android.graphics.Bitmap
import android.util.Log
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.ModelMessage
import com.antgskds.calendarassistant.data.model.ModelRequest
import com.antgskds.calendarassistant.data.model.MySettings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object RecognitionProcessor {
    private const val TAG = "CALENDAR_OCR_DEBUG"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    // --- 自然语言输入 ---
    suspend fun parseUserText(text: String, settings: MySettings): CalendarEventData? {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val timeStr = now.format(dtfFull)

        // ✅ 使用 AiPrompts
        val prompt = AiPrompts.getUserTextPrompt(timeStr)

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

            var cleanJson = response.trim()
            if (cleanJson.contains("```")) {
                cleanJson = cleanJson.substringAfter("json").substringAfter("\n").substringBeforeLast("```")
            }

            jsonParser.decodeFromString<CalendarEventData>(cleanJson)

        } catch (e: Exception) {
            Log.e(TAG, "AI 解析异常", e)
            null
        }
    }

    // --- 截图识别：同时识别日程和取件码 ---
    suspend fun analyzeImage(bitmap: Bitmap, settings: MySettings): List<CalendarEventData> {
        Log.i(TAG, ">>> 开始处理图片 (尺寸: ${bitmap.width} x ${bitmap.height})")

        val extractedText = try {
            extractTextFromBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "OCR 过程发生异常", e)
            return emptyList()
        }

        if (extractedText.isBlank()) {
            Log.w(TAG, "OCR 结果为空！")
            return emptyList()
        }

        // 并发执行两次识别请求
        return coroutineScope {
            try {
                val scheduleDeferred = async(Dispatchers.IO) { analyzeSchedule(extractedText, settings) }
                val pickupDeferred = async(Dispatchers.IO) { analyzePickup(extractedText, settings) }
                val scheduleEvents = scheduleDeferred.await()
                val pickupEvents = pickupDeferred.await()
                scheduleEvents + pickupEvents
            } catch (e: Exception) {
                Log.e(TAG, "AI 分析严重错误", e)
                emptyList()
            }
        }
    }

    // --- 识别日程事件 ---
    private suspend fun analyzeSchedule(extractedText: String, settings: MySettings): List<CalendarEventData> {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val dtfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dtfTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        val timeStr = now.format(dtfFull)
        val dateToday = now.format(dtfDate)
        val dateYesterday = now.minusDays(1).format(dtfDate)
        val dateBeforeYesterday = now.minusDays(2).format(dtfDate)

        val schedulePrompt = AiPrompts.getSchedulePrompt(timeStr, dateToday, dateYesterday, dateBeforeYesterday)

        val userPrompt = "[OCR文本开始]\n$extractedText\n[OCR文本结束]"

        val modelName = settings.modelName.ifBlank { "deepseek-chat" }

        return try {
            val request = ModelRequest(
                model = modelName,
                temperature = 0.1,
                messages = listOf(
                    ModelMessage("system", schedulePrompt),
                    ModelMessage("user", userPrompt)
                )
            )
            executeAiRequest(request, "日程识别", settings)
        } catch (e: Exception) {
            Log.e(TAG, "日程识别错误", e)
            emptyList()
        }
    }

    // --- 识别取件码事件 ---
    private suspend fun analyzePickup(extractedText: String, settings: MySettings): List<CalendarEventData> {
        val now = LocalDateTime.now()
        val dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE")
        val dtfTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        val timeStr = now.format(dtfFull)
        val dateToday = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val tempTimeInstruction = if (settings.tempEventsUseRecognitionTime) {
            AiPrompts.getForceTimeInstruction(now.format(dtfTime), now.plusHours(1).format(dtfTime))
        } else {
            AiPrompts.getSmartTimeInstruction(dateToday, now.format(dtfTime))
        }

        val pickupPrompt = AiPrompts.getPickupCodePrompt(timeStr, tempTimeInstruction)

        val userPrompt = "[OCR文本开始]\n$extractedText\n[OCR文本结束]"

        val modelName = settings.modelName.ifBlank { "deepseek-chat" }

        return try {
            val request = ModelRequest(
                model = modelName,
                temperature = 0.1,
                messages = listOf(
                    ModelMessage("system", pickupPrompt),
                    ModelMessage("user", userPrompt)
                )
            )
            executeAiRequest(request, "取件码识别", settings)
        } catch (e: Exception) {
            Log.e(TAG, "取件码识别错误", e)
            emptyList()
        }
    }

    private suspend fun executeAiRequest(
        request: ModelRequest,
        debugTag: String,
        settings: MySettings
    ): List<CalendarEventData> {
        return try {
            val modelName = settings.modelName.ifBlank { "deepseek-chat" }

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

            var cleanJson = responseText.trim()
            if (cleanJson.contains("```")) {
                cleanJson = cleanJson.substringAfter("json").substringAfter("\n").substringBeforeLast("```")
            }

            Log.d(TAG, "AI 原始响应: $cleanJson")

            val rootObject = JSONObject(cleanJson)
            val eventsArray = rootObject.optJSONArray("events") ?: JSONArray()

            if (eventsArray.length() > 0) {
                jsonParser.decodeFromString<List<CalendarEventData>>(eventsArray.toString())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$debugTag] JSON 解析失败", e)
            emptyList()
        }
    }

    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val allLines = visionText.textBlocks.flatMap { it.lines }
                    val sortedLines = allLines.sortedBy { it.boundingBox?.top ?: 0 }
                    val resultText = sortedLines.joinToString("\n") { it.text }
                    continuation.resume(resultText)
                }
                .addOnFailureListener { continuation.resumeWithException(it) }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
}