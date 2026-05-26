package com.antgskds.calendarassistant.core.ai

import com.antgskds.calendarassistant.core.rule.RecognitionRuleCatalog
import com.antgskds.calendarassistant.data.model.MySettings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LocalRecognitionPromptBuilder {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun build(
        settings: MySettings,
        source: Source,
        text: String? = null,
        now: LocalDateTime = LocalDateTime.now()
    ): String {
        val sourceBlock = when (source) {
            Source.IMAGE -> "请阅读随消息提供的图片。"
            Source.USER_TEXT,
            Source.OCR_TEXT -> "输入${source.label}：\n${compactInput(text.orEmpty(), maxChars = 2200)}"
        }

        return """
            你是 Will do 的本地日程识别模型。只输出一个 JSON 对象，不要 Markdown，不要解释。
            当前时间：${now.format(dateTimeFormatter)}，今天：${now.format(dateFormatter)}，${dayOfWeek(now)}。
            识别类型：${RecognitionRuleCatalog.localPromptRecognitionTypes()}。
            这些都算有效事件：${RecognitionRuleCatalog.localPromptEffectiveEvents()}。
            输出格式必须完全符合：{"events":[{"title":"","startTime":"yyyy-MM-dd HH:mm","endTime":"yyyy-MM-dd HH:mm","location":"","description":"","tag":"general"}]}
            description 规则：${RecognitionRuleCatalog.localPromptDescriptionRules()}。
            如果没有结束时间，endTime = startTime 加 ${settings.defaultEventDurationMinutes} 分钟。${RecognitionRuleCatalog.localPromptCurrentTimeFallbackRules()}没有明确时间时，startTime 用当前时间。
            ${RecognitionRuleCatalog.localPromptTaxiHint()}
            不要编造输入中不存在的信息。没有任何事件时输出 {"events":[]}。
            $sourceBlock
        """.trimIndent()
    }

    private fun compactInput(text: String, maxChars: Int): String {
        val compact = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return if (compact.length <= maxChars) compact else compact.take(maxChars)
    }

    private fun dayOfWeek(dateTime: LocalDateTime): String {
        return when (dateTime.dayOfWeek.value) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            else -> "星期日"
        }
    }

    enum class Source(val label: String) {
        USER_TEXT("用户文本"),
        OCR_TEXT("OCR文本"),
        IMAGE("图片")
    }
}
