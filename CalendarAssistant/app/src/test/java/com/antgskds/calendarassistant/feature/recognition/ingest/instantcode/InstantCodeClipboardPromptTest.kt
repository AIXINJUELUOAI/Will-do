package com.antgskds.calendarassistant.feature.recognition.ingest.instantcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstantCodeClipboardPromptTest {
    @Test
    fun `prompt parser matches an explicitly labelled pickup code`() {
        val candidate = InstantCodeParser.parseClipboard(
            text = "您的快递已到，取件码：W8511，请及时领取。",
        )

        assertEquals(InstantCodeType.PICKUP, candidate?.type)
        assertEquals("W8511", candidate?.code)
    }

    @Test
    fun `prompt parser ignores unrelated clipboard text`() {
        val candidate = InstantCodeParser.parseClipboard(
            text = "明天下午三点参加会议",
        )

        assertNull(candidate)
    }

    @Test
    fun `prompt parser prefers pickup credential over waybill suffix`() {
        val samples = mapOf(
            "【圆通快递】请凭1-1-1346到汇凯青年城妈妈驿站取运单尾号8478包裹" to "1-1-1346",
            "【圆通快递】请凭1-1-8196到汇凯青年城妈妈驿站取运单尾号8478包裹" to "1-1-8196",
        )

        samples.forEach { (text, expectedCode) ->
            val candidate = InstantCodeParser.parseClipboard(text)

            assertEquals(InstantCodeType.PICKUP, candidate?.type)
            assertEquals(expectedCode, candidate?.code)
        }
    }

    @Test
    fun `prompt parser does not treat waybill suffix as pickup code`() {
        val candidate = InstantCodeParser.parseClipboard(
            text = "【圆通快递】包裹已到，运单尾号8478",
        )

        assertNull(candidate)
    }
}
