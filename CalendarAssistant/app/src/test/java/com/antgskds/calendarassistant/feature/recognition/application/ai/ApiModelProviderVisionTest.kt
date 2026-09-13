package com.antgskds.calendarassistant.feature.recognition.application.ai

import com.antgskds.calendarassistant.feature.recognition.application.ai.model.ModelMessage
import com.antgskds.calendarassistant.feature.recognition.application.ai.model.ModelRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiModelProviderVisionTest {
    @Test
    fun deepSeekImageUsesUserContentBlocksAndNativeThinkingSwitch() {
        val dataUrl = "data:image/jpeg;base64,dGVzdA=="
        val body = ApiModelProvider.buildVisionRequestBody(
            modelName = "deepseek-v4-flash-vision-exp",
            prompt = "输出日程 JSON",
            dataUrl = dataUrl,
            reasoningEffort = null,
            disableDeepSeekThinking = true
        )
        assertEquals("deepseek-v4-flash-vision-exp", body["model"]?.jsonPrimitive?.content)
        assertEquals("disabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertFalse(body.containsKey("reasoning_effort"))
        val messages = body.getValue("messages").jsonArray
        assertEquals("输出日程 JSON", messages[0].jsonObject["content"]?.jsonPrimitive?.content)
        val user = messages[1].jsonObject
        assertEquals("user", user["role"]?.jsonPrimitive?.content)
        val image = user.getValue("content").jsonArray[1].jsonObject
        assertEquals("image_url", image["type"]?.jsonPrimitive?.content)
        assertEquals(dataUrl, image["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content)
    }

    @Test
    fun otherProvidersKeepExistingVisionParameters() {
        val body = ApiModelProvider.buildVisionRequestBody("vision-model", "prompt", "data:image/png;base64,AA==", "low")
        assertFalse(body.containsKey("thinking"))
        assertEquals("low", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun deepSeekDetectionChecksHostInsteadOfUrlSubstring() {
        assertTrue(ApiModelProvider.isDeepSeekEndpoint("https://api.deepseek.com/v1/chat/completions"))
        assertTrue(ApiModelProvider.isDeepSeekEndpoint("https://API.DEEPSEEK.COM/chat/completions"))
        assertFalse(ApiModelProvider.isDeepSeekEndpoint("https://example.com/api.deepseek.com"))
        assertFalse(ApiModelProvider.isDeepSeekEndpoint("https://api.deepseek.com.example.com/v1"))
    }

    @Test
    fun textOnlyCallsToVisionModelSerializeThinkingAtTopLevel() {
        val request = ModelRequest(
            model = "deepseek-v4-flash-vision-exp",
            messages = listOf(ModelMessage("user", "明天上午九点开会")),
            thinking = mapOf("type" to "disabled")
        )
        val body = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject
        assertEquals("disabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertFalse(body.containsKey("extra_body"))
        assertFalse(body.containsKey("reasoning_effort"))
    }
}
