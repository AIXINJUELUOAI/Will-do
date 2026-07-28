package com.antgskds.calendarassistant.feature.recognition.ingest.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsAnalysisTest {
    @Test
    fun `parses common pickup code formats`() {
        val messages = listOf(
            "您的快递已到菜鸟驿站，取件码：A1002，请及时领取",
            "您的包裹已到，取件码为 A1002",
            "快递已送达，请凭 A1002 取件"
        )

        messages.forEach { body ->
            val draft = requireNotNull(SmsAnalysis.parse("10086", body))
            val code = draft.description.removePrefix("【取件】").substringBefore('|')
            assertEquals("A1002", code)
        }
    }

    @Test
    fun `does not parse verification code as pickup code`() {
        assertNull(SmsAnalysis.parse("10086", "登录验证码：A1002，五分钟内有效"))
    }

    @Test
    fun `parses jd station pickup message`() {
        val body = "【京东快递服务站】您的快递:*03386已到湖北工业大学东苑食堂后京东服务站，" +
            "请用W8511到人工货架取包裹，咨询驿站工作人员"

        val draft = requireNotNull(SmsAnalysis.parse("京东快递", body))
        val code = draft.description.removePrefix("【取件】").substringBefore('|')
        assertEquals("W8511", code)
    }
}
