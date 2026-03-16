package com.antgskds.calendarassistant.core.ai

import android.util.Base64
import android.util.Log
import com.antgskds.calendarassistant.data.model.ModelRequest // 假设在 data.model 中定义了
import com.antgskds.calendarassistant.data.model.ModelResponse // 假设在 data.model 中定义了
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// 假设的 ModelRequest/Response 定义，如果 data/model 下没有，请补充
// @Serializable data class ModelMessage(val role: String, val content: String)
// @Serializable data class ModelRequest(val model: String, val messages: List<ModelMessage>, val temperature: Double = 0.7)
// @Serializable data class ModelResponse(val choices: List<ModelChoice>)
// @Serializable data class ModelChoice(val message: ModelMessage)

enum class ApiErrorKind {
    HTTP,
    NETWORK,
    PARSE,
    CONFIG,
    UNKNOWN
}

sealed class ApiCallResult {
    data class Success(val content: String) : ApiCallResult()
    data class Failure(
        val kind: ApiErrorKind,
        val statusCode: Int? = null,
        val message: String = "",
        val rawBody: String? = null
    ) : ApiCallResult()
}

object ApiModelProvider {

    private val reasoningSupportCache = ConcurrentHashMap<String, Boolean>()

    // 建议在 App.kt 中初始化一个全局 Client 传进来，或者在这里 lazy
    private val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    suspend fun generate(
        request: ModelRequest,
        apiKey: String,
        baseUrl: String,
        modelName: String,
        disableThinking: Boolean = false
    ): ApiCallResult {
        return try {
            if (baseUrl.isBlank() || apiKey.isBlank()) {
                Log.e("ApiModelProvider", "API URL or Key not configured")
                return ApiCallResult.Failure(ApiErrorKind.CONFIG, message = "配置缺失")
            }

            Log.d("ApiModelProvider", "Requesting: $baseUrl (Model: $modelName)")

            // --- Gemini 原生支持分支 ---
            if (baseUrl.contains("googleapis") || baseUrl.contains("gemini")) {
                return generateGemini(client, baseUrl, apiKey, request)
            }

            // --- 标准 OpenAI 格式分支 ---
            val shouldAttemptReasoning = shouldAttemptReasoning(disableThinking, baseUrl, modelName)
            val requestBody = request.copy(
                model = modelName,
                reasoningEffort = if (shouldAttemptReasoning) "low" else null
            )

            val (statusCode, rawBody) = postJsonWithAuth(baseUrl, apiKey, requestBody)
            Log.d("DEBUG_HTTP", "服务器原始响应: $rawBody")

            if (statusCode in 200..299) {
                if (shouldAttemptReasoning) {
                    markReasoningSupport(baseUrl, modelName, true)
                }
                return parseModelResponse(rawBody)
            }

            if (shouldAttemptReasoning && isRetryableReasoningError(statusCode, rawBody)) {
                markReasoningSupport(baseUrl, modelName, false)
                val fallbackRequest = request.copy(model = modelName, reasoningEffort = null)
                val (fallbackStatus, fallbackBody) = postJsonWithAuth(baseUrl, apiKey, fallbackRequest)
                Log.d("DEBUG_HTTP", "服务器重试响应: $fallbackBody")
                if (fallbackStatus in 200..299) {
                    return parseModelResponse(fallbackBody)
                }
                Log.e("ApiModelProvider", "Retry failed: HTTP $fallbackStatus - $fallbackBody")
                return ApiCallResult.Failure(
                    ApiErrorKind.HTTP,
                    statusCode = fallbackStatus,
                    rawBody = fallbackBody
                )
            }

            Log.e("ApiModelProvider", "Request failed: HTTP $statusCode - $rawBody")
            return ApiCallResult.Failure(
                ApiErrorKind.HTTP,
                statusCode = statusCode,
                rawBody = rawBody
            )

        } catch (e: Exception) {
            Log.e("ApiModelProvider", "Network/Parse error", e)
            ApiCallResult.Failure(
                ApiErrorKind.NETWORK,
                message = "${e.javaClass.simpleName} - ${e.message}".trim()
            )
        }
    }

    suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        baseUrl: String,
        modelName: String,
        disableThinking: Boolean = false
    ): ApiCallResult {
        return try {
            if (baseUrl.isBlank() || apiKey.isBlank()) {
                Log.e("ApiModelProvider", "API URL or Key not configured")
                return ApiCallResult.Failure(ApiErrorKind.CONFIG, message = "配置缺失")
            }

            Log.d("ApiModelProvider", "Requesting (vision): $baseUrl (Model: $modelName)")

            if (baseUrl.contains("googleapis") || baseUrl.contains("gemini")) {
                return generateGeminiWithImage(client, baseUrl, apiKey, prompt, imageBytes, mimeType)
            }

            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val dataUrl = "data:$mimeType;base64,$base64"
            val shouldAttemptReasoning = shouldAttemptReasoning(disableThinking, baseUrl, modelName)
            val requestBody = buildVisionRequestBody(
                modelName = modelName,
                prompt = prompt,
                dataUrl = dataUrl,
                reasoningEffort = if (shouldAttemptReasoning) "low" else null
            )

            val (statusCode, rawBody) = postJsonWithAuth(baseUrl, apiKey, requestBody)
            Log.d("DEBUG_HTTP_VISION", "服务器原始响应: $rawBody")

            if (statusCode in 200..299) {
                if (shouldAttemptReasoning) {
                    markReasoningSupport(baseUrl, modelName, true)
                }
                return parseModelResponse(rawBody)
            }

            if (shouldAttemptReasoning && isRetryableReasoningError(statusCode, rawBody)) {
                markReasoningSupport(baseUrl, modelName, false)
                val fallbackBody = buildVisionRequestBody(
                    modelName = modelName,
                    prompt = prompt,
                    dataUrl = dataUrl,
                    reasoningEffort = null
                )
                val (fallbackStatus, fallbackRaw) = postJsonWithAuth(baseUrl, apiKey, fallbackBody)
                Log.d("DEBUG_HTTP_VISION", "服务器重试响应: $fallbackRaw")
                if (fallbackStatus in 200..299) {
                    return parseModelResponse(fallbackRaw)
                }
                Log.e("ApiModelProvider", "Vision retry failed: HTTP $fallbackStatus - $fallbackRaw")
                return ApiCallResult.Failure(
                    ApiErrorKind.HTTP,
                    statusCode = fallbackStatus,
                    rawBody = fallbackRaw
                )
            }

            Log.e("ApiModelProvider", "Request failed: HTTP $statusCode - $rawBody")
            return ApiCallResult.Failure(
                ApiErrorKind.HTTP,
                statusCode = statusCode,
                rawBody = rawBody
            )

        } catch (e: Exception) {
            Log.e("ApiModelProvider", "Vision network/parse error", e)
            ApiCallResult.Failure(
                ApiErrorKind.NETWORK,
                message = "${e.javaClass.simpleName} - ${e.message}".trim()
            )
        }
    }

    private suspend fun postJsonWithAuth(
        baseUrl: String,
        apiKey: String,
        body: Any
    ): Pair<Int, String> {
        val payload = if (body is JsonObject) body.toString() else body
        val response = client.post {
            url(baseUrl)
            contentType(ContentType.Application.Json)
            bearerAuth(apiKey)
            setBody(payload)
        }
        return response.status.value to response.bodyAsText()
    }

    private fun parseModelResponse(rawBody: String): ApiCallResult {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val modelResponse = json.decodeFromString<ModelResponse>(rawBody)
            val content = modelResponse.choices.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                ApiCallResult.Failure(ApiErrorKind.PARSE, message = "Empty Content", rawBody = rawBody)
            } else {
                ApiCallResult.Success(content)
            }
        } catch (e: Exception) {
            ApiCallResult.Failure(
                ApiErrorKind.PARSE,
                message = "${e.javaClass.simpleName} - ${e.message}".trim(),
                rawBody = rawBody
            )
        }
    }

    private fun isOpenAiEndpoint(baseUrl: String): Boolean {
        val lower = baseUrl.lowercase()
        return lower.contains("openai.com") || lower.contains("/v1/chat/completions")
    }

    private fun shouldAttemptReasoning(
        disableThinking: Boolean,
        baseUrl: String,
        modelName: String
    ): Boolean {
        if (!disableThinking) return false
        if (!isOpenAiEndpoint(baseUrl)) return false
        val cached = reasoningSupportCache[cacheKey(baseUrl, modelName)]
        return cached != false
    }

    private fun markReasoningSupport(baseUrl: String, modelName: String, supported: Boolean) {
        reasoningSupportCache[cacheKey(baseUrl, modelName)] = supported
    }

    private fun isRetryableReasoningError(statusCode: Int, rawBody: String): Boolean {
        if (statusCode != 400 && statusCode != 422) return false
        val lower = rawBody.lowercase()
        return lower.contains("unknown field") ||
            lower.contains("unrecognized") ||
            lower.contains("invalid request") ||
            lower.contains("invalid_request") ||
            lower.contains("unsupported")
    }

    private fun cacheKey(baseUrl: String, modelName: String): String {
        return "${baseUrl}|${modelName}"
    }

    private fun buildVisionRequestBody(
        modelName: String,
        prompt: String,
        dataUrl: String,
        reasoningEffort: String?
    ): JsonObject {
        return buildJsonObject {
            put("model", modelName)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", prompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "请解析图片并输出JSON")
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", dataUrl)
                            }
                        })
                    }
                })
            }
            put("temperature", 0.1)
            if (!reasoningEffort.isNullOrBlank()) {
                put("reasoning_effort", reasoningEffort)
            }
        }
    }

    private suspend fun generateGemini(
        client: HttpClient,
        baseUrl: String,
        apiKey: String,
        request: ModelRequest
    ): ApiCallResult {
        val finalUrl = if (baseUrl.contains("?")) "$baseUrl&key=$apiKey" else "$baseUrl?key=$apiKey"

        val fullPrompt = request.messages.joinToString("\n\n") { msg ->
            "【${msg.role}】: ${msg.content}"
        }

        val geminiJson = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", fullPrompt)
                        })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", request.temperature)
            }
        }

        val response = client.post {
            url(finalUrl)
            contentType(ContentType.Application.Json)
            setBody(geminiJson)
        }

        val rawBody = response.bodyAsText()
        Log.d("DEBUG_HTTP_GEMINI", "Gemini 响应: $rawBody")

        if (response.status.value !in 200..299) {
            return ApiCallResult.Failure(
                ApiErrorKind.HTTP,
                statusCode = response.status.value,
                rawBody = rawBody
            )
        }

        return try {
            val root = JSONObject(rawBody)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    if (text.isBlank()) {
                        ApiCallResult.Failure(ApiErrorKind.PARSE, message = "Empty Parts", rawBody = rawBody)
                    } else {
                        ApiCallResult.Success(text)
                    }
                } else {
                    ApiCallResult.Failure(ApiErrorKind.PARSE, message = "Empty Parts", rawBody = rawBody)
                }
            } else {
                ApiCallResult.Failure(ApiErrorKind.PARSE, message = "No Candidates", rawBody = rawBody)
            }
        } catch (e: Exception) {
            ApiCallResult.Failure(
                ApiErrorKind.PARSE,
                message = "${e.javaClass.simpleName} - ${e.message}".trim(),
                rawBody = rawBody
            )
        }
    }

    private suspend fun generateGeminiWithImage(
        client: HttpClient,
        baseUrl: String,
        apiKey: String,
        prompt: String,
        imageBytes: ByteArray,
        mimeType: String
    ): ApiCallResult {
        val finalUrl = if (baseUrl.contains("?")) "$baseUrl&key=$apiKey" else "$baseUrl?key=$apiKey"
        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val geminiJson = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", mimeType)
                                put("data", base64)
                            }
                        })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.1)
            }
        }

        val response = client.post {
            url(finalUrl)
            contentType(ContentType.Application.Json)
            setBody(geminiJson)
        }

        val rawBody = response.bodyAsText()
        Log.d("DEBUG_HTTP_GEMINI", "Gemini 视觉响应: $rawBody")

        if (response.status.value !in 200..299) {
            return ApiCallResult.Failure(
                ApiErrorKind.HTTP,
                statusCode = response.status.value,
                rawBody = rawBody
            )
        }

        return try {
            val root = JSONObject(rawBody)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    if (text.isBlank()) {
                        ApiCallResult.Failure(ApiErrorKind.PARSE, message = "Empty Parts", rawBody = rawBody)
                    } else {
                        ApiCallResult.Success(text)
                    }
                } else {
                    ApiCallResult.Failure(ApiErrorKind.PARSE, message = "Empty Parts", rawBody = rawBody)
                }
            } else {
                ApiCallResult.Failure(ApiErrorKind.PARSE, message = "No Candidates", rawBody = rawBody)
            }
        } catch (e: Exception) {
            ApiCallResult.Failure(
                ApiErrorKind.PARSE,
                message = "${e.javaClass.simpleName} - ${e.message}".trim(),
                rawBody = rawBody
            )
        }
    }
}
