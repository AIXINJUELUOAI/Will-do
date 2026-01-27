package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 发送给 AI 的请求载荷
 */
@Serializable
data class ModelRequest(
    val model: String,
    val messages: List<ModelMessage>,
    val temperature: Double = 0.5,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null
) {
    @Serializable
    data class ResponseFormat(val type: String)
}

/**
 * 消息体 (纯文本模式)
 */
@Serializable
data class ModelMessage(
    val role: String,
    val content: String
)

/**
 * AI 返回的响应结构
 */
@Serializable
data class ModelResponse(
    val choices: List<Choice>
) {
    @Serializable
    data class Choice(
        val message: ModelMessage
    )
}