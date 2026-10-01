package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.notification.policy.AccountingRecognitionNotificationPolicy as Policy
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.AccountingRecognitionDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.*
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import org.junit.Assert.*
import org.junit.Test

class AccountingRecognitionNotificationTest {
    private fun key(id: String) = NotificationKey.recognition("accounting:$id")
    private val progress = NotificationDisplaySnapshot("正在识别", "正在识别", "已排队", expandedText = "等待前面的账单")
    private fun cancel(id: String) = NotificationAction(EventActionReceiver.ACTION_CANCEL_RECOGNITION, "取消",
        mapOf(EventActionReceiver.EXTRA_ACCOUNTING_TASK_ID to id))

    @Test fun automaticTasksChooseActualPublisherForEitherSettingAndLeaveOtherNotificationsAlone() {
        for (id in listOf("first", "second", "third")) {
            assertEquals(NotificationRoute.LIVE, Policy.route(NotificationKind.RECOGNITION_STATUS, key(id), true))
            assertEquals(NotificationRoute.NORMAL, Policy.route(NotificationKind.RECOGNITION_STATUS, key(id), false))
        }
        assertNull(Policy.route(NotificationKind.SCHEDULE_REMINDER, key("first"), true))
        assertNull(Policy.route(NotificationKind.RECOGNITION_STATUS, NotificationKey("accounting:recognition"), true))
        assertNull(Policy.route(NotificationKind.QUICK_MEMO_REMINDER, NotificationKey("quick-memo:reminder:1"), true))
    }

    @Test fun liveProgressKeepsIndependentCancelTargetsAndAccountingTap() {
        val displays = listOf("first", "second", "third").map {
            AccountingRecognitionDisplay.notification(progress, listOf(cancel(it)))
        }
        assertEquals(listOf("first", "second", "third"), displays.map {
            it.effectiveActions.single().stringExtras[EventActionReceiver.EXTRA_ACCOUNTING_TASK_ID]
        })
        displays.forEach {
            assertTrue(it.tapOpensAccounting)
            assertEquals("正在识别", it.shortText)
            assertEquals("等待前面的账单", it.expandedText)
            assertEquals(EventActionReceiver.ACTION_CANCEL_RECOGNITION, it.effectiveActions.single().receiverAction)
            assertNull(it.effectiveActions.single().extraLongKey)
        }
    }

    @Test fun resultRetainsAmountAndClearsActionsInExistingXiaomiTemplate() {
        val result = AccountingRecognitionDisplay.notification(
            NotificationDisplaySnapshot("¥8.50", "已记 1 笔", "支出 ¥8.50", expandedText = "支出 ¥8.50 · 收入 ¥0.00"), emptyList())
        assertTrue(result.effectiveActions.isEmpty())
        assertTrue(result.tapOpensAccounting)
        val island = XiaomiLiveNotificationTemplate.create(result, true, false, true, null, 10, 100)
        assertEquals("¥8.50", island.summaryTitle)
        assertEquals("已记 1 笔", island.title)
        assertEquals("支出 ¥8.50 · 收入 ¥0.00", island.content)
        assertEquals(XiaomiLiveTemplateKind.TEXT_ICON, island.templateKind)
    }

    @Test fun progressUsesActionTemplateAndFailureOrCancellationRetainsIndependentResult() {
        val display = AccountingRecognitionDisplay.notification(progress, listOf(cancel("second")))
        val island = XiaomiLiveNotificationTemplate.create(display, true, true, false, null, 10, Long.MAX_VALUE)
        assertEquals(XiaomiLiveTemplateKind.TEXT_ICON_ACTION, island.templateKind)
        for (title in listOf("自动记账失败", "识别已取消")) {
            val result = AccountingRecognitionDisplay.notification(NotificationDisplaySnapshot(title, title), emptyList())
            assertEquals(title, result.shortText)
            assertTrue(result.effectiveActions.isEmpty())
        }
    }

    @Test fun waitingHasNoExpiryAndResultsUseConfiguredDuration() {
        val settings = MySettings(resultNotificationTimeoutMs = 15_000)
        assertNull(Policy.timeout(true, settings))
        assertEquals(15_000L, Policy.timeout(false, settings))
        assertTrue(requireNotNull(Policy.timeout(false, settings.copy(resultNotificationTimeoutMs = -1))) > 0)
    }
}
