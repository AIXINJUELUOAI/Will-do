package com.antgskds.calendarassistant.platform.clipboard

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ClipboardProcessProtocolTest {
    private fun payload(uid: Int = 2000, text: String = "测试\n取件码：W8511"): String =
        JSONObject().put("type", "clip").put("uid", uid).put("result", "text")
            .put("text", text).put("clipboard_time_ms", 123456L).toString()

    @Test fun shellAndRootResponsesPreserveUnicodeAndTimestamp() {
        val shell = ClipboardProcessProtocol.parse(payload())
        assertEquals("shell_process", shell.reader)
        assertEquals("测试\n取件码：W8511", shell.text)
        assertEquals(123456L, shell.timestamp)
        assertEquals("root_process", ClipboardProcessProtocol.parse(payload(0)).reader)
    }

    @Test fun emptyClipboardAndServiceErrorsDoNotProduceText() {
        val empty = ClipboardProcessProtocol.parse("""{"type":"clip","uid":2000,"result":"null_clip"}""")
        assertNull(empty.text)
        val error = ClipboardProcessProtocol.parse("""{"type":"error","uid":2000,"result":"error","error_type":"SecurityException"}""")
        assertEquals("SecurityException", error.errorType)
        assertNull(error.text)
    }

    @Test fun rejectsOrdinaryAppIdentityOrMalformedEvents() {
        assertThrows(IllegalArgumentException::class.java) { ClipboardProcessProtocol.parse(payload(10234)) }
        assertThrows(IllegalArgumentException::class.java) {
            ClipboardProcessProtocol.parse("""{"type":"ready","uid":2000,"result":"text","text":"stale"}""")
        }
        assertThrows(Exception::class.java) { ClipboardProcessProtocol.parse("not json") }
    }

    @Test fun rejectsOversizedTextInsteadOfMatchingTruncatedContents() {
        assertThrows(IllegalArgumentException::class.java) {
            ClipboardProcessProtocol.parse(payload(text = "x".repeat(ConfigCatalog.CLIPBOARD_PROCESS_MAX_TEXT_CHARS + 1)))
        }
    }
}
