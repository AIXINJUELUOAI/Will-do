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
        kind: Kind = Kind.SCHEDULE,
        now: LocalDateTime = LocalDateTime.now()
    ): String {
        val sourceBlock = buildSourceBlock(source, text)
        return when (kind) {
            Kind.INSTANT_CODE -> buildInstantCodePrompt(settings, sourceBlock, now)
            Kind.SCHEDULE -> buildSchedulePrompt(settings, sourceBlock, now)
            Kind.UNIFIED_MULTIMODAL -> buildUnifiedPrompt(settings, sourceBlock, now)
        }
    }

    private fun buildSchedulePrompt(
        settings: MySettings,
        sourceBlock: String,
        now: LocalDateTime
    ): String {
        return """
            你是 Will do 的本地日程识别模型。只输出一个 JSON 对象，不要 Markdown，不要解释。
            当前时间：${now.format(dateTimeFormatter)}，今天：${now.format(dateFormatter)}，${dayOfWeek(now)}。
            任务：只提取普通日程、交通、打车等非码类事件。纯取件码、取餐码、取票码、寄件码请忽略。
            输出格式必须完全符合：{"events":[{"title":"","startTime":"yyyy-MM-dd HH:mm","endTime":"yyyy-MM-dd HH:mm","location":"","description":"","tag":"general"}]}
            title 规则：
            ${RecognitionRuleCatalog.localPromptScheduleTitleRules()}
            description 规则：${RecognitionRuleCatalog.localPromptScheduleDescriptionRules()}。
            所有识别出的事件都必须按默认持续时间计算 endTime，不要自行猜测其他时长；endTime = ${formatDuration(settings.defaultEventDurationMinutes)}。
            ${RecognitionRuleCatalog.localPromptTaxiHint()}
            不要编造输入中不存在的信息。没有任何事件时输出 {"events":[]}。
            $sourceBlock
        """.trimIndent()
    }

    private fun buildInstantCodePrompt(
        settings: MySettings,
        sourceBlock: String,
        now: LocalDateTime
    ): String {
        return """
            你是 Will do 的本地码类识别模型。只输出一个 JSON 对象，不要 Markdown，不要解释。
            当前时间：${now.format(dateTimeFormatter)}，今天：${now.format(dateFormatter)}，${dayOfWeek(now)}。
            任务：只提取取件码、取餐码、取票码、寄件码。普通日程、聊天内容、订单号、运单号、验证码请忽略。
            输出格式必须完全符合：{"events":[{"title":"","startTime":"yyyy-MM-dd HH:mm","endTime":"yyyy-MM-dd HH:mm","location":"","description":"","tag":"pickup"}]}
            可用 tag：pickup、food、ticket、sender。
            title 规则：
            ${RecognitionRuleCatalog.localPromptInstantCodeTitleRules()}
            description 规则：${RecognitionRuleCatalog.localPromptInstantCodeDescriptionRules()}。
            所有码类事件 startTime 必须使用当前时间 ${now.format(dateTimeFormatter)}；endTime = ${formatDuration(settings.defaultEventDurationMinutes)}。
            不要编造输入中不存在的信息。没有任何码类事件时输出 {"events":[]}。
            $sourceBlock
        """.trimIndent()
    }

    private fun buildUnifiedPrompt(
        settings: MySettings,
        sourceBlock: String,
        now: LocalDateTime
    ): String {
        return """
            你是 Will do 的本地截图识别模型。只输出一个 JSON 对象，不要 Markdown，不要解释。
            当前时间：${now.format(dateTimeFormatter)}，今天：${now.format(dateFormatter)}，${dayOfWeek(now)}。
            识别类型：${RecognitionRuleCatalog.localPromptRecognitionTypes()}。
            这些都算有效事件：${RecognitionRuleCatalog.localPromptEffectiveEvents()}。
            输出格式必须完全符合：{"events":[{"title":"","startTime":"yyyy-MM-dd HH:mm","endTime":"yyyy-MM-dd HH:mm","location":"","description":"","tag":"general"}]}
            title 规则：
            ${RecognitionRuleCatalog.localPromptTitleRules()}
            description 规则：${RecognitionRuleCatalog.localPromptDescriptionRules()}。
            所有识别出的事件都必须按默认持续时间计算 endTime；endTime = ${formatDuration(settings.defaultEventDurationMinutes)}。
            ${RecognitionRuleCatalog.localPromptCurrentTimeFallbackRules()}没有明确时间时，startTime 用当前时间。
            ${RecognitionRuleCatalog.localPromptTaxiHint()}
            不要编造输入中不存在的信息。没有任何事件时输出 {"events":[]}。
            $sourceBlock
        """.trimIndent()
    }

    private fun buildSourceBlock(source: Source, text: String?): String {
        return when (source) {
            Source.IMAGE -> "请阅读随消息提供的图片。"
            Source.USER_TEXT,
            Source.OCR_TEXT -> "输入${source.label}：\n${compactInput(text.orEmpty(), maxChars = 2200)}"
        }
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

    private fun formatDuration(minutes: Int): String {
        return when (minutes) {
            END_OF_DAY_DURATION -> "当天 23:59"
            60 -> "startTime + 1 小时"
            120 -> "startTime + 2 小时"
            180 -> "startTime + 3 小时"
            360 -> "startTime + 6 小时"
            1440 -> "startTime + 24 小时"
            else -> "startTime + ${minutes.coerceAtLeast(1)} 分钟"
        }
    }

    enum class Source(val label: String) {
        USER_TEXT("用户文本"),
        OCR_TEXT("OCR文本"),
        IMAGE("图片")
    }

    enum class Kind {
        SCHEDULE,
        INSTANT_CODE,
        UNIFIED_MULTIMODAL
    }

    private const val END_OF_DAY_DURATION = -1
}
