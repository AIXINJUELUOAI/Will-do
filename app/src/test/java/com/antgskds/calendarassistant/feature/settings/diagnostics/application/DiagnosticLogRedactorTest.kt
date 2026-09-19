package com.antgskds.calendarassistant.feature.settings.diagnostics.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogRedactorTest {

    @Test
    fun redactsHeadersJsonQueryParametersAndCaiyunPaths() {
        val source = """
            Authorization: Bearer header-secret
            {"apiKey":"json-secret","password":"password-secret"}
            https://example.com/v1/models?api_key=query-secret&mode=list
            https://api.caiyunapp.com/v2.7/path-token/116.4,39.9/weather
        """.trimIndent()

        val redacted = DiagnosticLogRedactor.redact(source)

        listOf("header-secret", "json-secret", "password-secret", "query-secret", "path-token").forEach {
            assertFalse(redacted.contains(it))
        }
        assertTrue(redacted.contains("<redacted>"))
        assertTrue(redacted.contains("mode=list"))
    }
}
