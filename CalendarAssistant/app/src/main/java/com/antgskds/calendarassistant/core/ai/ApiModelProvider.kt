package com.antgskds.calendarassistant.core.ai

import android.util.Log
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.ModelMessage
import com.antgskds.calendarassistant.data.model.ModelRequest // 假设在 data.model 中定义了
import com.antgskds.calendarassistant.data.model.ModelResponse // 假设在 data.model 中定义了
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject

// 假设的 ModelRequest/Response 定义，如果 data/model 下没有，请补充
// @Serializable data class ModelMessage(val role: String, val content: String)
// @Serializable data class ModelRequest(val model: String, val messages: List<ModelMessage>, val temperature: Double = 0.7)
// @Serializable data class ModelResponse(val choices: List<ModelChoice>)
// @Serializable data class ModelChoice(val message: ModelMessage)

object ApiModelProvider {

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
        modelName: String
    ): String {
        return try {
            if (baseUrl.isBlank() || apiKey.isBlank()) {
                Log.e("ApiModelProvider", "API URL or Key not configured")
                return "Error: 配置缺失"
            }

            Log.d("ApiModelProvider", "Requesting: $baseUrl (Model: $modelName)")

            // --- Gemini 原生支持分支 ---
            if (baseUrl.contains("googleapis") || baseUrl.contains("gemini")) {
                return generateGemini(client, baseUrl, apiKey, request)
            }

            // --- 标准 OpenAI 格式分支 ---
            val response = client.post {
                url(baseUrl)
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
                setBody(request.copy(model = modelName))
            }

            val rawBody = response.bodyAsText()
            Log.d("DEBUG_HTTP", "服务器原始响应: $rawBody")

            if (!response.status.isSuccess()) {
                Log.e("ApiModelProvider", "Request failed: ${response.status} - $rawBody")
                return "Error: HTTP ${response.status}"
            }

            val json = Json { ignoreUnknownKeys = true }
            val modelResponse = json.decodeFromString<ModelResponse>(rawBody)
            modelResponse.choices.firstOrNull()?.message?.content ?: "Error: Empty Content"

        } catch (e: Exception) {
            Log.e("ApiModelProvider", "Network/Parse error", e)
            "Error: ${e.javaClass.simpleName} - ${e.message}"
        }
    }

    private suspend fun generateGemini(client: HttpClient, baseUrl: String, apiKey: String, request: ModelRequest): String {
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

        if (!response.status.isSuccess()) {
            return "Error: Gemini HTTP ${response.status}"
        }

        return try {
            val root = JSONObject(rawBody)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    parts.getJSONObject(0).optString("text", "")
                } else {
                    "Error: Empty Parts"
                }
            } else {
                "Error: No Candidates"
            }
        } catch (e: Exception) {
            "Error: Parse Gemini Failed"
        }
    }
}