package com.antgskds.calendarassistant.feature.linkanalysis
import com.antgskds.calendarassistant.feature.recognition.application.ai.LinkMaterialRequestBuilder
import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.notification.policy.LinkSummaryNotificationPolicy
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LinkSummaryRequestTest {
    @Test fun audioIsInlineAudioAndImagesRetainOrder() {
        val body=LinkMaterialRequestBuilder.openAi("总结",listOf("image/jpeg" to "first","audio/wav" to "sound","image/webp" to "last"),"model")
        val content=body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("text",content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("data:image/jpeg;base64,first",content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertEquals("input_audio",content[2].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("wav",content[2].jsonObject["input_audio"]!!.jsonObject["format"]!!.jsonPrimitive.content)
        assertEquals("data:image/webp;base64,last",content[3].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }
    @Test fun completedNotificationRoutesAndTapTargetStayIndependentPerMemo() {
        assertEquals(NotificationRoute.NORMAL,LinkSummaryNotificationPolicy.route(NotificationKind.LINK_SUMMARY_READY,false))
        assertEquals(NotificationRoute.LIVE,LinkSummaryNotificationPolicy.route(NotificationKind.LINK_SUMMARY_READY,true))
        assertNull(LinkSummaryNotificationPolicy.route(NotificationKind.SCHEDULE_REMINDER,true))
        val one=LinkSummaryNotificationPolicy.request(1,"标题")
        val two=LinkSummaryNotificationPolicy.request(2,"标题")
        assertNotEquals(one.key,two.key)
        assertNotEquals(one.notificationId,two.notificationId)
        assertEquals("1",one.tapTarget!!.payload["quickMemoId"])
        assertEquals(1L,one.actions.single().openQuickMemoId)
        assertEquals("摘要完成", one.display.shortText)
        assertEquals("摘要已生成", one.display.primaryText)
        assertEquals("标题", one.display.expandedText)
        assertTrue(MySettings().linkAudioLocalTranscription)
        assertFalse(MySettings().linkAnalysisEnabled)
        assertEquals(60_000L, one.behavior.timeoutAfterMillis)
        assertEquals(60_000L, two.behavior.timeoutAfterMillis)
    }
}
