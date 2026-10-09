package com.antgskds.calendarassistant.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogPrivacyRedactorTest {
    @Test fun removesCredentialsFromHeadersSettingsAndCookies() {
        val raw = """
            Authorization: Bearer header-marker
            Cookie: sid=cookie-marker; uid=user-marker
            Set-Cookie: auth=session-marker; Path=/
            {"modelKey":"model-marker","mmModelKey":"vision-marker","syncPassphrase":"passphrase-marker"}
            password=phrase-marker with spaces
            token=token-marker
            stage=HTTP_DONE status=200 bytes=32 elapsed_ms=9
        """.trimIndent()
        val output = LogPrivacyRedactor.redact(raw)
        listOf("header-marker", "cookie-marker", "user-marker", "session-marker", "model-marker",
            "vision-marker", "passphrase-marker", "phrase-marker", "with spaces", "token-marker").forEach {
            assertFalse(it, output.contains(it))
        }
        assertTrue(output.contains("stage=HTTP_DONE status=200 bytes=32 elapsed_ms=9"))
    }

    @Test fun eachCredentialFieldIsFilteredIndependently() {
        listOf("api_key", "modelKey", "mmModelKey", "weatherApiKey", "weatherQWeatherApiKey",
            "weatherCaiyunToken", "syncPassphrase", "access_token", "refresh_token", "xsec_token", "password").forEach { field ->
            val raw = "{\"$field\":\"credential-marker with spaces\"}\nstage=DONE"
            val output = LogPrivacyRedactor.redact(raw)
            assertFalse(field, output.contains("credential-marker"))
            assertFalse(field, output.contains("with spaces"))
            assertTrue(output.contains("stage=DONE"))
        }
    }

    @Test fun urlsRetainHostButDiscardUserInfoPathQueryAndFragment() {
        val output = LogPrivacyRedactor.redact(
            "source=https://account-marker:password-marker@example.com/path-marker?xsec_token=token-marker#fragment-marker " +
                "media=https://media.example.com/audio-marker.mp3?sign=signature-marker"
        )
        listOf("account-marker", "password-marker", "path-marker", "token-marker", "fragment-marker",
            "audio-marker", "signature-marker").forEach { assertFalse(it, output.contains(it)) }
        assertTrue(output.contains("https://example.com"))
        assertTrue(output.contains("https://media.example.com"))
    }

    @Test fun removesLegacyMultilineContentAndPreservesNextRecords() {
        val raw = """
            [APP] 2026-10-09 10:00:00.000 DEBUG/Recognition: 用户输入: input-marker
            input-continuation-marker
            [APP] 2026-10-09 10:00:01.000 DEBUG/HTTP: 服务器原始响应: {
              "message": "response-marker"
            }
            [LOGCAT] 2026-10-09 10:00:02.000 123 123 I LinkAnalysis: stage=EXTRACTED text_chars=12 media_count=1
            [APP] 2026-10-09 10:00:03.000 DEBUG/Recognition: [多模态识别] 清洗后内容(20 chars): {
              "events": [{"description": "description-marker"}]
            }
            [APP] 2026-10-09 10:00:04.000 INFO/Task: stage=DONE elapsed_ms=20
        """.trimIndent()
        val output = LogPrivacyRedactor.redact(raw)
        listOf("input-marker", "input-continuation-marker", "response-marker", "description-marker").forEach {
            assertFalse(it, output.contains(it))
        }
        assertTrue(output.contains("stage=EXTRACTED text_chars=12 media_count=1"))
        assertTrue(output.contains("stage=DONE elapsed_ms=20"))
    }

    @Test fun filtersContentFieldsAndLegacyExceptionMessages() {
        val raw = "title=title-marker\nbody=body-marker\namount=amount-marker\n" +
            "java.lang.IllegalArgumentException: exception-marker\n\tat Diag.operation(Diag.kt:42)\n" +
            "error_type=IllegalArgumentException stage=FAILED"
        val output = LogPrivacyRedactor.redact(raw)
        listOf("title-marker", "body-marker", "amount-marker", "exception-marker").forEach {
            assertFalse(it, output.contains(it))
        }
        assertTrue(output.contains("Diag.operation(Diag.kt:42)"))
        assertTrue(output.contains("error_type=IllegalArgumentException stage=FAILED"))
    }

    @Test fun throwableCausesSuppressedExceptionsAndCyclesRetainFramesWithoutMessages() {
        val error = IllegalStateException("outer-marker")
        val cause = IllegalArgumentException("cause-marker")
        error.initCause(cause)
        cause.initCause(error)
        error.addSuppressed(RuntimeException("suppressed-marker"))
        error.stackTrace = arrayOf(StackTraceElement("Diag", "operation", "Diag.kt", 42))
        val output = LogPrivacyRedactor.stackTrace(error)
        listOf("outer-marker", "cause-marker", "suppressed-marker").forEach {
            assertFalse(it, output.contains(it))
        }
        assertTrue(output.contains("java.lang.IllegalStateException"))
        assertTrue(output.contains("Caused by: java.lang.IllegalArgumentException"))
        assertTrue(output.contains("Suppressed: java.lang.RuntimeException"))
        assertTrue(output.contains("Diag.operation(Diag.kt:42)"))
        assertTrue(output.contains("<cycle>"))
    }

    @Test fun repeatedFilteringPreservesSafeMetadata() {
        val raw = "stage=HTTP_DONE status=200 bytes=64 url=https://example.com/path?token=secret\napiKey=secret"
        val once = LogPrivacyRedactor.redact(raw)
        assertEquals(once, LogPrivacyRedactor.redact(once))
        assertEquals("stage=HTTP_DONE status=200 bytes=64", LogPrivacyRedactor.redact("stage=HTTP_DONE status=200 bytes=64"))
    }
}
