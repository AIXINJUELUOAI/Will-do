package com.antgskds.calendarassistant.data.model

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
    @SerialName("schedule_prompt")
    val schedulePrompt: String = "",
    @SerialName("pickup_prompt")
    val pickupPrompt: String = ""
) {
    fun isValid(): Boolean {
        return version > 0 &&
            schedulePrompt.isNotBlank() &&
            pickupPrompt.isNotBlank()
    }
}
