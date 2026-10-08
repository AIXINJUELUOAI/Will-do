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
        NotificationKind.entries.filter { it != NotificationKind.CLIPBOARD_CODE_PROMPT }.forEach {
            assertNull(Policy.route(it, true))
            assertNull(Policy.route(it, false))
        }
        assertTrue(Policy.owns(Policy.key(10)))
        assertNotEquals(Policy.key(10), Policy.key(11))
        assertFalse(Policy.owns(NotificationKey("recognition:accounting:10")))
        assertFalse(Policy.owns(NotificationKey("quick-memo:reminder:10")))
    }

    @Test fun normalAndLiveTemplatesRetainOnlyTypeAndConfirmationHint() {
        val snapshot = Display.snapshot("取件码")
        val live = Display.notification(snapshot)
        assertEquals("识别到取件码", snapshot.primaryText)
        assertEquals(snapshot.shortText, live.shortText)
        assertEquals(snapshot.primaryText, live.primaryText)
        assertEquals(snapshot.secondaryText, live.secondaryText)
        assertEquals(snapshot.expandedText, live.expandedText)
        assertTrue(live.effectiveActions.isEmpty())
        assertFalse(live.tapOpensPickupList)
        assertFalse(live.tapOpensAccounting)
        assertNull(live.tapEventId)
        assertNull(live.tapQuickMemoId)
        val island = XiaomiLiveNotificationTemplate.create(live, true, false, true, null, 10, Long.MAX_VALUE)
        assertEquals("识别到取件码", island.title)
        assertTrue(island.content.contains("确认是否创建日程"))
        assertEquals(XiaomiLiveTemplateKind.TEXT_ICON, island.templateKind)
        assertNull(island.hintTitle)
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
