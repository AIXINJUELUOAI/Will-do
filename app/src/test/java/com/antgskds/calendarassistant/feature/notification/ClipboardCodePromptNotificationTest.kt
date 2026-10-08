package com.antgskds.calendarassistant.feature.notification

import com.antgskds.calendarassistant.feature.notification.model.*
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
        assertEquals("识别到小红书链接", live.primaryText)
        assertEquals("收藏", live.effectiveActions.single().label)
        assertEquals("识别到链接", Display.linkSnapshot("").primaryText)
    }

    @Test fun savedLinkUsesDirectDetailTargetAndReplacesCollectWithView() {
        val memoId = 42L
        val action = NotificationAction.viewQuickMemo(memoId)
        val tap = NotificationTapTarget(NotificationTapTargetType.QUICK_MEMO_DETAIL, mapOf("quickMemoId" to memoId.toString()))
        val snapshot = Display.savedLinkSnapshot("小红书收藏")
        val live = Display.notification(snapshot, listOf(action), tap)
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
