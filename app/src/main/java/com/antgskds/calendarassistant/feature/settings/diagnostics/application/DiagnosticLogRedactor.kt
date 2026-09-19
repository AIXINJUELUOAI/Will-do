package com.antgskds.calendarassistant.feature.settings.diagnostics.application

internal object DiagnosticLogRedactor {
    private val secretPatterns = listOf(
        Regex("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)[^\\s,;]+"),
        Regex("(?i)((?:api[_-]?key|token|password|sync[_-]?passphrase)\\s*[:=]\\s*)[^&\\s,;\\\"}]+"),
        Regex("(?i)(\\\"(?:api[_-]?key|token|password|syncPassphrase)\\\"\\s*:\\s*\\\")[^\\\"]*"),
        Regex("(?i)([?&](?:key|token|api[_-]?key|apikey)=)[^&\\s]+"),
        Regex("(?i)(api\\.caiyunapp\\.com/v2\\.7/)[^/\\s]+"),
    )

    fun redact(raw: String): String {
        var redacted = raw
        secretPatterns.forEach { pattern ->
            redacted = pattern.replace(redacted) { match ->
                val prefix = match.groups[1]?.value.orEmpty()
                "$prefix<redacted>"
            }
        }
        return redacted
    }
}
