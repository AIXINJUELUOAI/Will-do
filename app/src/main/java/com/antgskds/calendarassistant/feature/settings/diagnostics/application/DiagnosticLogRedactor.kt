package com.antgskds.calendarassistant.feature.settings.diagnostics.application

import com.antgskds.calendarassistant.shared.util.LogPrivacyRedactor

/** Ordinary and Agent exports use the same filter as runtime logging. */
internal object DiagnosticLogRedactor {
    fun redact(raw: String): String = LogPrivacyRedactor.redact(raw)
}
