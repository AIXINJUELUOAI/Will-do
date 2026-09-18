package com.antgskds.calendarassistant.feature.recognition.application.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemotePrompts(
    val version: Int = 0,
    @SerialName("prompt_header")
    val promptHeader: String = "",
    @SerialName("user_text_prompt")
    val userTextPrompt: String = "",
    @SerialName("mm_unified_prompt")
    val mmUnifiedPrompt: String = "",
    // 仅兼容旧提示词文件；运行时使用统一图文提示词。
    @SerialName("schedule_prompt")
    val schedulePrompt: String = "",
    @SerialName("pickup_prompt")
    val pickupPrompt: String = ""
) {
    fun isValid(): Boolean {
        return version > 0 && (
            mmUnifiedPrompt.isNotBlank() || userTextPrompt.isNotBlank() ||
                (schedulePrompt.isNotBlank() && pickupPrompt.isNotBlank())
            )
    }
}
