package com.antgskds.calendarassistant.core.ai.provider

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.aiengine.AiEngineException
import com.antgskds.calendarassistant.aiengine.AiEngineLog
import com.antgskds.calendarassistant.aiengine.AiEngineStatus
import com.antgskds.calendarassistant.core.ai.AnalysisFailure
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.LocalRecognitionPromptBuilder
import com.antgskds.calendarassistant.core.ai.RecognitionJsonParser
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.core.localmodel.LocalModelInfo
import com.antgskds.calendarassistant.core.localmodel.LocalModelRuntime
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileOutputStream

object LocalSemanticProvider : SemanticProvider {
    private const val TAG = "LocalSemanticProvider"
    private val localJson = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<RecognitionDraft> {
        val result = analyzeText(text, settings, context, source = LocalRecognitionPromptBuilder.Source.USER_TEXT)
        return when (result) {
            is AnalysisResult.Success -> result.data.firstOrNull()?.let { AnalysisResult.Success(it) } ?: AnalysisResult.Empty()
            is AnalysisResult.Empty -> result
            is AnalysisResult.Failure -> result
        }
    }

    override suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<RecognitionDraft>> {
        val app = context.applicationContext as? App
            ?: return AnalysisResult.Failure(AnalysisFailure("分析失败", "本地推理环境未就绪"))
        val model = app.localModelManager.getModel(settings.selectedLocalModelId)
            ?: return AnalysisResult.Failure(AnalysisFailure("分析失败", "请先选择本地模型"))
        AiEngineLog.setEnabled(context.applicationContext, true)

        if (settings.useMultimodalAi) {
            if (model.runtime == LocalModelRuntime.LITERT_LM && model.supportsMultimodal) {
                return analyzeImageWithLiteRt(bitmap, model, settings, context, app)
            }
            return AnalysisResult.Failure(AnalysisFailure("分析失败", "当前本地模型不支持图片识别，请关闭使用多模态AI或切换支持多模态的模型"))
        }

        val ocrText = RecognitionProcessor.recognizeOptimizedText(bitmap, context)
        if (ocrText.isBlank()) return AnalysisResult.Empty("OCR 结果为空")
        Log.d(TAG, "[本地推理] 优化 OCR 文本(${ocrText.length} chars): $ocrText")
        return analyzeText(ocrText, settings, context, source = LocalRecognitionPromptBuilder.Source.OCR_TEXT, preselectedModel = model, preselectedApp = app)
    }

    private suspend fun analyzeText(
        text: String,
        settings: MySettings,
        context: Context,
        source: LocalRecognitionPromptBuilder.Source,
        preselectedModel: LocalModelInfo? = null,
        preselectedApp: App? = null
    ): AnalysisResult<List<RecognitionDraft>> {
        val app = preselectedApp ?: context.applicationContext as? App
            ?: return AnalysisResult.Failure(AnalysisFailure("分析失败", "本地推理环境未就绪"))
        val model = preselectedModel ?: app.localModelManager.getModel(settings.selectedLocalModelId)
            ?: return AnalysisResult.Failure(AnalysisFailure("分析失败", "请先选择本地模型"))
        AiEngineLog.setEnabled(context.applicationContext, true)

        return try {
            val prompt = LocalRecognitionPromptBuilder.build(settings, source, text = text)
            AiEngineLog.write(context, "DEBUG", TAG, "LiteRT text prompt\n$prompt")
            val result = app.liteRtAiEngineClient.generate(
                model = model,
                prompt = prompt,
                enableVision = false,
                maxTokens = LITERT_MAX_TOKENS,
                onModelLoaded = { sendModelLoadedBroadcast(context) }
            )
            Log.d(TAG, "[LiteRT] 文本推理耗时 load=${result.loadElapsedMillis}ms inference=${result.inferenceElapsedMillis}ms total=${result.totalElapsedMillis}ms")
            val response = result.text
            Log.d(TAG, "[本地推理] 原始响应(${response.length} chars): $response")
            AiEngineLog.write(context, "DEBUG", TAG, "raw response (${response.length} chars)\n$response")
            val cleanJson = RecognitionJsonParser.cleanJsonString(response)
            Log.d(TAG, "[本地推理] 清洗 JSON(${cleanJson.length} chars): $cleanJson")
            AiEngineLog.write(context, "DEBUG", TAG, "clean json (${cleanJson.length} chars)\n$cleanJson")
            when (val parsed = parseLiteRtResponse(cleanJson)) {
                is LocalParseResult.Success -> {
                    Log.d(TAG, "[本地推理] 解析事件数=${parsed.events.size}, events=${parsed.events}")
                    AiEngineLog.write(context, "INFO", TAG, "parse status=SUCCESS events=${parsed.events.size}")
                    AnalysisResult.Success(parsed.events)
                }
                is LocalParseResult.Empty -> handleEmptyParse(parsed.status, context, logPrefix = "parse")
                is LocalParseResult.Failure -> handleParseFailure(parsed, context, logPrefix = "parse")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiEngineException) {
            Log.e(TAG, "本地推理失败 status=${e.status}", e)
            AiEngineLog.write(context, "ERROR", TAG, "engine status=${e.status} error=${e.message}")
            AnalysisResult.Failure(AnalysisFailure("分析失败", localStatusMessage(e.status)))
        } catch (e: Exception) {
            Log.e(TAG, "本地推理失败", e)
            AiEngineLog.write(context, "ERROR", TAG, "engine status=${AiEngineStatus.UNKNOWN_ERROR} error=${e.message}")
            AnalysisResult.Failure(AnalysisFailure("分析失败", "本地模型推理失败：${e.message ?: "未知错误"}"))
        }
    }

    private suspend fun analyzeImageWithLiteRt(
        bitmap: Bitmap,
        model: LocalModelInfo,
        settings: MySettings,
        context: Context,
        app: App
    ): AnalysisResult<List<RecognitionDraft>> {
        AiEngineLog.setEnabled(context.applicationContext, true)
        val prompt = LocalRecognitionPromptBuilder.build(settings, LocalRecognitionPromptBuilder.Source.IMAGE)
        return try {
            val imageFile = writeTempImage(context, bitmap)
            try {
                AiEngineLog.write(context, "DEBUG", TAG, "LiteRT vision prompt\n$prompt")
                val result = app.liteRtAiEngineClient.generate(
                    model = model,
                    prompt = prompt,
                    imagePath = imageFile.absolutePath,
                    enableVision = true,
                    maxTokens = LITERT_MAX_TOKENS,
                    onModelLoaded = { sendModelLoadedBroadcast(context) }
                )
                Log.d(TAG, "[LiteRT] 图片推理耗时 load=${result.loadElapsedMillis}ms inference=${result.inferenceElapsedMillis}ms total=${result.totalElapsedMillis}ms")
                Log.d(TAG, "[LiteRT] 原始响应(${result.text.length} chars): ${result.text}")
                AiEngineLog.write(context, "DEBUG", TAG, "vision raw response (${result.text.length} chars)\n${result.text}")
                val cleanJson = RecognitionJsonParser.cleanJsonString(result.text)
                Log.d(TAG, "[LiteRT] 清洗 JSON(${cleanJson.length} chars): $cleanJson")
                AiEngineLog.write(context, "DEBUG", TAG, "vision clean json (${cleanJson.length} chars)\n$cleanJson")
                when (val parsed = parseLiteRtResponse(cleanJson)) {
                    is LocalParseResult.Success -> {
                        Log.d(TAG, "[LiteRT] 解析事件数=${parsed.events.size}, events=${parsed.events}")
                        AiEngineLog.write(context, "INFO", TAG, "vision parse status=SUCCESS events=${parsed.events.size}")
                        AnalysisResult.Success(parsed.events)
                    }
                    is LocalParseResult.Empty -> handleEmptyParse(parsed.status, context, logPrefix = "vision parse")
                    is LocalParseResult.Failure -> handleParseFailure(parsed, context, logPrefix = "vision parse")
                }
            } finally {
                runCatching { imageFile.delete() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiEngineException) {
            Log.e(TAG, "LiteRT 图片推理失败 status=${e.status}", e)
            AiEngineLog.write(context, "ERROR", TAG, "vision engine status=${e.status} error=${e.message}")
            AnalysisResult.Failure(AnalysisFailure("分析失败", localStatusMessage(e.status)))
        } catch (e: Exception) {
            Log.e(TAG, "LiteRT 图片推理失败", e)
            AiEngineLog.write(context, "ERROR", TAG, "vision engine status=${AiEngineStatus.UNKNOWN_ERROR} error=${e.message}")
            AnalysisResult.Failure(AnalysisFailure("分析失败", "本地多模态推理失败：${e.message ?: "未知错误"}"))
        }
    }

    private fun handleEmptyParse(
        status: AiEngineStatus,
        context: Context,
        logPrefix: String
    ): AnalysisResult<List<RecognitionDraft>> {
        AiEngineLog.write(context, "INFO", TAG, "$logPrefix status=$status")
        return AnalysisResult.Empty(status.name)
    }

    private fun handleParseFailure(
        parsed: LocalParseResult.Failure,
        context: Context,
        logPrefix: String
    ): AnalysisResult<List<RecognitionDraft>> {
        AiEngineLog.write(context, "ERROR", TAG, "$logPrefix status=${parsed.status} detail=${parsed.detail}")
        return AnalysisResult.Failure(AnalysisFailure("分析失败", localStatusMessage(parsed.status)))
    }

    private fun parseLiteRtResponse(cleanJson: String): LocalParseResult {
        if (cleanJson.isBlank()) return LocalParseResult.Failure(AiEngineStatus.INVALID_JSON, "blank response")
        val element = runCatching { localJson.parseToJsonElement(cleanJson) }
            .getOrElse { return LocalParseResult.Failure(AiEngineStatus.INVALID_JSON, it.message.orEmpty()) }

        val explicitEventsCount = when (element) {
            is JsonObject -> (element["events"] as? JsonArray)?.size
            is JsonArray -> element.size
            else -> null
        }
        val events = RecognitionJsonParser.enforceRuleHeaders(
            RecognitionJsonParser.parseCalendarEvents(cleanJson)
        )
        val validEvents = events.filter { it.title.isNotBlank() && it.startTS > 0L && it.endTS > 0L }
        if (validEvents.isNotEmpty()) return LocalParseResult.Success(validEvents)
        if (explicitEventsCount == 0) return LocalParseResult.Empty(AiEngineStatus.EMPTY_EVENTS)
        return LocalParseResult.Failure(AiEngineStatus.INVALID_SCHEMA, "events=${events.size}, explicitEvents=$explicitEventsCount")
    }

    private fun localStatusMessage(status: AiEngineStatus): String {
        return when (status) {
            AiEngineStatus.EMPTY_EVENTS -> "EMPTY_EVENTS"
            AiEngineStatus.INVALID_JSON -> "INVALID_JSON"
            AiEngineStatus.INVALID_SCHEMA -> "INVALID_SCHEMA"
            AiEngineStatus.TIMEOUT_LOADING -> "TIMEOUT_LOADING"
            AiEngineStatus.TIMEOUT_GENERATING -> "TIMEOUT_GENERATING"
            AiEngineStatus.ENGINE_DISCONNECTED -> "ENGINE_DISCONNECTED"
            AiEngineStatus.ENGINE_KILLED_LOW_MEMORY -> "ENGINE_KILLED_LOW_MEMORY"
            AiEngineStatus.ENGINE_NATIVE_CRASH -> "ENGINE_NATIVE_CRASH"
            AiEngineStatus.ENGINE_JAVA_CRASH -> "ENGINE_JAVA_CRASH"
            AiEngineStatus.MODEL_FILE_MISSING -> "MODEL_FILE_MISSING"
            AiEngineStatus.MODEL_LOAD_FAILED -> "MODEL_LOAD_FAILED"
            AiEngineStatus.MODEL_UNSUPPORTED -> "MODEL_UNSUPPORTED"
            AiEngineStatus.USER_CANCELLED -> "USER_CANCELLED"
            AiEngineStatus.FOREGROUND_START_FAILED -> "FOREGROUND_START_FAILED"
            AiEngineStatus.SUCCESS -> "SUCCESS"
            AiEngineStatus.UNKNOWN_ERROR -> "UNKNOWN_ERROR"
        }
    }

    private suspend fun writeTempImage(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "ai_engine_images").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", dir)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        file
    }

    private fun sendModelLoadedBroadcast(context: Context) {
        val appContext = context.applicationContext
        appContext.sendBroadcast(Intent(ACTION_LOCAL_MODEL_READY).setPackage(appContext.packageName))
    }

    private sealed class LocalParseResult {
        data class Success(val events: List<RecognitionDraft>) : LocalParseResult()
        data class Empty(val status: AiEngineStatus) : LocalParseResult()
        data class Failure(val status: AiEngineStatus, val detail: String) : LocalParseResult()
    }

    private const val LITERT_MAX_TOKENS = 1024
    const val ACTION_LOCAL_MODEL_READY = "com.antgskds.calendarassistant.action.LOCAL_MODEL_READY"

}
