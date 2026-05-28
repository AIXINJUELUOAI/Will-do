/*
 * 短信码类解析兼容入口。
 *
 * 核心规则已统一迁移到 core.instantcode，方便短信、剪贴板等入口共用维护。
 */

package com.antgskds.calendarassistant.core.sms

import com.antgskds.calendarassistant.core.instantcode.InstantCodeParser
import com.antgskds.calendarassistant.core.model.RecognitionDraft

object SmsAnalysis {
    fun parse(
        sender: String,
        body: String,
        ignoreKeywords: Set<String> = emptySet()
    ): RecognitionDraft? {
        return InstantCodeParser.parseSms(sender, body, ignoreKeywords)
    }
}
