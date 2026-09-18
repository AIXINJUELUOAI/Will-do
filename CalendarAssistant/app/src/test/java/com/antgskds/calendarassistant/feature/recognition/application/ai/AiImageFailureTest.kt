package com.antgskds.calendarassistant.feature.recognition.application.ai

import com.antgskds.calendarassistant.shared.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.RecognitionNormalDisplay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.*
import org.junit.Test

class AiImageFailureTest {
    private fun http(message: String, status: Int = 400): ApiCallResult.Failure =
        ApiCallResult.Failure(ApiErrorKind.HTTP, statusCode = status, rawBody = buildJsonObject {
            putJsonObject("error") { put("message", message) }
        }.toString())

    @Test
    fun explicitProviderCapabilityErrorsReachExistingFeedback() {
        listOf(
            "This model does not support image input.",
            "Invalid content type. image_url is only supported by certain models.",
            "Image inputs are not supported by this model.",
            "The model only supports text input.",
            "当前模型不支持多模态输入"
        ).forEach { message ->
            val result = AiFailureMapper.mapImage(http(message))
            assertEquals(message, "IMAGE_INPUT_UNSUPPORTED", result.errorCode)
            assertFalse(result.retryable)
            val display = RecognitionFailureMessageMapper.display(event(result))
            assertEquals("当前模型不支持图片输入", display.reason)
            assertEquals("请更换支持多模态的模型", display.suggestion)
            assertEquals("识别失败", result.title)
        }
    }

    @Test
    fun geminiAndSuccessfulHttpErrorEnvelopesAreHandled() {
        val raw = """{"error":{"code":400,"message":"Image input is not supported","status":"INVALID_ARGUMENT"}}"""
        val gemini = AiFailureMapper.mapImage(ApiCallResult.Failure(ApiErrorKind.HTTP, 400, rawBody = raw))
        assertEquals("IMAGE_INPUT_UNSUPPORTED", gemini.errorCode)
        val envelope = AiFailureMapper.mapImage(ApiCallResult.Failure(ApiErrorKind.UNKNOWN,
            message = "This model does not support images"))
        assertEquals("IMAGE_INPUT_UNSUPPORTED", envelope.errorCode)
    }

    @Test
    fun ordinaryImageAndParameterErrorsAreNotCapabilityErrors() {
        listOf(
            "Unsupported image format: WEBP",
            "This model does not support image format WEBP",
            "This model does not support images larger than 10MB",
            "This model does not support image generation",
            "Invalid image_url: failed to decode base64",
            "Unknown parameter: reasoning_effort",
            "The image exceeds the maximum resolution"
        ).forEach { message ->
            val mapped = AiFailureMapper.mapImage(http(message))
            assertNull(message, mapped.errorCode)
            assertTrue(mapped.detail.contains(message))
        }
    }

    @Test
    fun authenticationQuotaRateLimitAndTimeoutKeepTheirOwnReasons() {
        mapOf(401 to "密钥无效", 402 to "额度不足", 403 to "无权访问", 429 to "请求过多", 504 to "上游超时").forEach { (status, expected) ->
            val result = AiFailureMapper.mapImage(http("Request rejected", status))
            assertNull(result.errorCode)
            assertTrue(result.detail.startsWith(expected))
            assertTrue(RecognitionFailureMessageMapper.display(event(result)).reason.startsWith(expected))
        }
        val quota = AiFailureMapper.mapImage(http("You exceeded your current quota", 429))
        assertTrue(quota.detail.startsWith("额度不足(429)"))
        val timeout = AiFailureMapper.mapImage(ApiCallResult.Failure(ApiErrorKind.NETWORK, message = "SocketTimeoutException"))
        assertEquals("网络超时", timeout.detail)
        val unauthorized = AiFailureMapper.mapImage(http("This model does not support images", 401))
        assertNull(unauthorized.errorCode)
        assertTrue(unauthorized.detail.startsWith("密钥无效"))
    }

    @Test
    fun genericErrorRetainsMessageButDoesNotExposeRawGatewayPageOrRequest() {
        val plain = AiFailureMapper.mapImage(http("The requested model is temporarily unavailable", 503))
        assertTrue(RecognitionFailureMessageMapper.display(event(plain)).reason.contains("temporarily unavailable"))
        val gateway = AiFailureMapper.mapImage(ApiCallResult.Failure(ApiErrorKind.HTTP, 502,
            rawBody = "<html><body>gateway credentials and diagnostics</body></html>"))
        assertEquals("网关异常(502)", gateway.detail)
        val echoed = AiFailureMapper.mapImage(ApiCallResult.Failure(ApiErrorKind.HTTP, 400,
            rawBody = """{"error":{"message":"Invalid parameter"},"request":{"message":"model does not support images"}}"""))
        assertNull(echoed.errorCode)
        assertFalse(echoed.detail.contains("request"))
    }

    @Test
    fun unknownFailureDoesNotLoseUsefulMessageAndTextRequestIsUnchanged() {
        val failure = ApiCallResult.Failure(ApiErrorKind.UNKNOWN, message = "No route available for this deployment")
        assertEquals(failure.message, AiFailureMapper.mapImage(failure).detail)
        assertNull(AiFailureMapper.map(http("This model does not support images")).errorCode)
    }

    @Test
    fun expandedNotificationRetainsCompleteReason() {
        val reason = "参数错误(400)：" + "Provider diagnostic detail. ".repeat(5)
        val content = RecognitionNormalDisplay.failure(reason, "请检查配置")
        assertTrue(content.bigText.contains(reason))
    }

    private fun event(message: AiFailureMessage) = RecognitionFailedEvent(
        sourceType = "home", sourceId = "home_image_import",
        errorCode = message.errorCode ?: "ANALYSIS_FAILURE", retryable = message.retryable,
        message = "${message.title}：${message.detail}"
    )
}
