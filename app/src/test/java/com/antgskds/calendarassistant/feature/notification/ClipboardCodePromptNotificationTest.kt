package com.antgskds.calendarassistant.feature.notification

import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoLinkParser
import com.antgskds.calendarassistant.feature.notification.policy.ClipboardCodePromptDeliveryPolicy as Policy
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.ClipboardCodePromptDisplay as Display
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveNotificationTemplate
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveTemplateKind
import org.junit.Assert.*
import org.junit.Test

class ClipboardCodePromptNotificationTest {
    @Test fun independentPromptsChooseRealPublisherRoutesWithoutOwningScheduleOrMemoNotifications() {
        assertEquals(NotificationRoute.LIVE, Policy.route(NotificationKind.CLIPBOARD_CODE_PROMPT, true))
        assertEquals(NotificationRoute.NORMAL, Policy.route(NotificationKind.CLIPBOARD_CODE_PROMPT, false))
        assertEquals(NotificationRoute.LIVE, Policy.route(NotificationKind.CLIPBOARD_LINK_PROMPT, true))
        assertEquals(NotificationRoute.NORMAL, Policy.route(NotificationKind.CLIPBOARD_LINK_PROMPT, false))
        NotificationKind.entries.filter { it !in setOf(NotificationKind.CLIPBOARD_CODE_PROMPT, NotificationKind.CLIPBOARD_LINK_PROMPT) }.forEach {
            assertNull(Policy.route(it, true))
            assertNull(Policy.route(it, false))
        }
        assertTrue(Policy.owns(Policy.key(10)))
        assertNotEquals(Policy.key(10), Policy.key(11))
        assertFalse(Policy.owns(NotificationKey("recognition:accounting:10")))
        assertFalse(Policy.owns(NotificationKey("quick-memo:reminder:10")))
    }

    @Test fun linkPromptExpiresInOneMinuteAndCodePromptKeepsItsExistingLifetime() {
        assertEquals(60_000L, Policy.timeout(NotificationKind.CLIPBOARD_LINK_PROMPT))
        assertNull(Policy.timeout(NotificationKind.CLIPBOARD_CODE_PROMPT))
        assertNull(Policy.timeout(NotificationKind.SCHEDULE_REMINDER))
    }

    @Test fun normalAndLiveTemplatesShareCapturedCodeAndDirectAction() {
        val key = Policy.key(10, "session")
        val action = com.antgskds.calendarassistant.feature.recognition.ingest.clipboard.ClipboardPromptAction.create(key, "添加取件")
        val snapshot = Display.snapshot("取件码", "1234-56")
        val live = Display.notification(snapshot, listOf(action))
        assertEquals("识别到取件码|1234-56", snapshot.primaryText)
        assertEquals(snapshot.shortText, live.shortText)
        assertEquals(snapshot.primaryText, live.primaryText)
        assertEquals(snapshot.secondaryText, live.secondaryText)
        assertEquals(snapshot.expandedText, live.expandedText)
        assertEquals("添加取件", live.effectiveActions.single().label)
        assertEquals(action.payload, live.effectiveActions.single().stringExtras)
        val island = XiaomiLiveNotificationTemplate.create(live, true, true, false, null, 10, Long.MAX_VALUE)
        assertEquals(snapshot.primaryText, island.title)
        assertEquals(XiaomiLiveTemplateKind.TEXT_ICON_ACTION, island.templateKind)
    }

    @Test fun linkPromptHasSameCollectActionAndGenericTitle() {
        val action = com.antgskds.calendarassistant.feature.recognition.ingest.clipboard.ClipboardPromptAction.create(Policy.key(12), "收藏")
        val live = Display.notification(Display.linkSnapshot("小红书"), listOf(action))
        assertEquals("小红书链接", live.shortText)
        assertEquals("识别到小红书链接", live.primaryText)
        assertEquals("收藏", live.effectiveActions.single().label)
        assertEquals("发现链接", Display.linkSnapshot("").shortText)
        assertEquals("抖音链接", Display.linkSnapshot("抖音").shortText)
        assertEquals("识别到链接", Display.linkSnapshot("").primaryText)
        listOf(
            "贴吧" to "https://tieba.baidu.com/p/123?share=synthetic",
            "知乎" to "https://www.zhihu.com/question/123/answer/456?share_code=synthetic",
            "微博" to "https://weibo.com/123/456",
        ).forEach { (source, url) ->
            val link = requireNotNull(QuickMemoLinkParser.parse(url))
            val snapshot = Display.linkSnapshot(link.source)
            val platformLive = Display.notification(snapshot, listOf(action))
            assertEquals("${source}链接", snapshot.shortText)
            assertEquals("识别到${source}链接", snapshot.primaryText)
            assertEquals(snapshot.shortText, platformLive.shortText)
            assertEquals(snapshot.primaryText, platformLive.primaryText)
            assertEquals("收藏", platformLive.effectiveActions.single().label)
            val platformCollapsed = XiaomiLiveNotificationTemplate.create(platformLive, true, true, false, null, 10, 60_010)
            val platformExpanded = XiaomiLiveNotificationTemplate.create(platformLive, false, true, false, null, 10, 60_010)
            assertEquals(snapshot.shortText, platformCollapsed.title)
            assertEquals(snapshot.primaryText, platformExpanded.title)
        }
        val collapsed = XiaomiLiveNotificationTemplate.create(live, true, true, false, null, 10, 60_010)
        val expanded = XiaomiLiveNotificationTemplate.create(live, false, true, false, null, 10, 60_010)
        assertEquals(live.shortText, collapsed.title)
        assertEquals(live.primaryText, expanded.title)
        assertEquals("收藏到随口记，稍后继续查看", live.expandedText)
    }

    @Test fun savedLinkUsesDirectDetailTargetAndReplacesCollectWithView() {
        val memoId = 42L
        val action = NotificationAction.viewQuickMemo(memoId)
        val tap = NotificationTapTarget(NotificationTapTargetType.QUICK_MEMO_DETAIL, mapOf("quickMemoId" to memoId.toString()))
        val snapshot = Display.savedLinkSnapshot("小红书收藏")
        val live = Display.notification(snapshot, listOf(action), tap)
        assertEquals("已收藏", snapshot.shortText)
        assertEquals(snapshot.shortText, live.shortText)
        assertEquals("已收藏到随口记", snapshot.primaryText)
        assertEquals(snapshot.primaryText, live.primaryText)
        assertEquals("小红书收藏", live.secondaryText)
        assertEquals("42", live.tapQuickMemoId)
        assertTrue(live.tapOpensQuickMemoDetail)
        assertEquals("查看", live.effectiveActions.single().label)
        assertEquals(memoId, live.effectiveActions.single().openQuickMemoId)
        assertEquals(memoId, action.openQuickMemoId)
        assertNotEquals(com.antgskds.calendarassistant.feature.recognition.ingest.clipboard.ClipboardPromptAction.RECEIVER_ACTION, action.key)
        assertNull(NotificationAction(NotificationAction.OPEN_QUICK_MEMO, "查看", mapOf("quickMemoId" to "-1")).openQuickMemoId)
        assertNull(NotificationAction("other_action", "查看", mapOf("quickMemoId" to "42")).openQuickMemoId)
    }

    @Test fun readyOrFailureMustNotBeLoggedAsPosted() {
        val key = Policy.key(10)
        NotificationState.entries.forEach {
            assertEquals(it == NotificationState.POSTED, Policy.isDelivered(NotificationResult.Success(key, it)))
        }
        assertFalse(Policy.isDelivered(NotificationResult.Success(key)))
        assertFalse(Policy.isDelivered(NotificationResult.Failure(key, NotificationFailureReason.PERMISSION_DENIED)))
        assertFalse(Policy.isDelivered(NotificationResult.Failure(key, NotificationFailureReason.PUBLISH_FAILED)))
    }
}
