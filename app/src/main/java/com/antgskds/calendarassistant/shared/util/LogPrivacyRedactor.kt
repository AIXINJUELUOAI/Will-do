package com.antgskds.calendarassistant.shared.util

import java.net.URI
import java.util.Collections
import java.util.IdentityHashMap

/** Routine logs retain diagnostics, never request/response bodies or credentials. */
object LogPrivacyRedactor {
    private val urls = Regex("""(?i)https?://[^\s<>"'\\]+""")
    private val secrets = Regex("""(?im)(?<![\w])(["']?(?:authorization|proxy-authorization|cookie|set-cookie|[\w-]*api[_-]?key|(?:mm)?modelKey|[\w-]*(?:password|passphrase|token))["']?\s*[:=]\s*)[^\r\n]*""")
    private val contentFields = Regex("""(?im)(?<![\w])(["']?(?:title|text|body|rawBody|content|prompt|transcript|remark|merchant|contact|pickupCode|amount)["']?\s*[:=]\s*)[^\r\n]*""")
    private val exceptionMessages = Regex("""(?m)((?:[\w$]+\.)*[\w$]*(?:Exception|Error|Throwable)):[^\r\n]*""")
    private val legacyContent = Regex("""用户输入\s*:|服务器(?:原始|重试)响应\s*:|Gemini\s*(?:视觉\s*)?响应\s*:|\[(?:AI文本输入|多模态识别)\].*(?:原始响应|清洗后\s*JSON|清洗后内容)""")
    private val recordStart = Regex("""^\s*(?:\[(?:APP|LOGCAT)\]\s*)?\[?(?:\d{4}-)?\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}""")

    fun redact(raw: String): String {
        // Old multiline AI records are removed as a whole, including continuation lines.
        var skipping = false
        val withoutContent = raw.lineSequence().mapNotNull { line ->
            if (recordStart.containsMatchIn(line)) skipping = false
            val marker = legacyContent.find(line)
            when {
                marker != null -> {
                    skipping = true
                    line.substring(0, marker.range.first) + "<redacted content>"
                }
                skipping -> null
                else -> line
            }
        }.joinToString("\n")
        val withoutUrls = urls.replace(withoutContent) { match ->
            runCatching {
                val uri = URI(match.value)
                val host = uri.host ?: return@runCatching "<redacted url>"
                "${uri.scheme}://$host"
            }.getOrDefault("<redacted url>")
        }
        val withoutSecrets = secrets.replace(withoutUrls) { "${it.groupValues[1]}<redacted>" }
        val withoutFields = contentFields.replace(withoutSecrets) { "${it.groupValues[1]}<redacted>" }
        return exceptionMessages.replace(withoutFields) { "${it.groupValues[1]}: <redacted>" }
    }

    /** Exception messages may embed response bodies, URLs and user input. Keep types/frames only. */
    fun stackTrace(error: Throwable?): String {
        if (error == null) return ""
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        return buildString {
            fun appendError(item: Throwable, prefix: String) {
                if (!seen.add(item)) {
                    appendLine("$prefix<cycle>")
                    return
                }
                appendLine(prefix + item.javaClass.name)
                item.stackTrace.forEach { appendLine("\tat $it") }
                item.suppressed.forEach { appendError(it, "Suppressed: ") }
                item.cause?.let { appendError(it, "Caused by: ") }
            }
            appendError(error, "")
        }.trimEnd()
    }
}
