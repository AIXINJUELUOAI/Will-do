package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.WechatRedPacketSessionPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.WechatRedPacketSessionPolicy.EventKind.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WechatRedPacketSessionTest {
    private val policy = WechatRedPacketSessionPolicy()
    private val wechat = AutomaticAccountingPolicy.WECHAT
    private val prepare = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewPrepareUI"
    private val payment = "com.tencent.mm.framework.app.UIPageFragmentActivity"
    private fun event(kind: WechatRedPacketSessionPolicy.EventKind, clazz: String = "", window: Int = 3,
        text: String = "", now: Long = 20_000, source: String = wechat, foreground: String? = wechat) =
        policy.observe(source, clazz, window, listOf(text), kind, foreground, 3, now, 1_800_000_000_000L + now)
    private fun open(): WechatRedPacketSessionPolicy.Action.Capture {
        event(WINDOW, prepare, now = 2_986)
        event(CLICK, text = "塞钱进红包", now = 11_363)
        event(WINDOW, "com.tencent.mm.ui.widget.dialog.obfuscated", text = "微信支付", now = 11_396)
        return event(WINDOW, payment, now = 17_026) as WechatRedPacketSessionPolicy.Action.Capture
    }
    private fun cache(capture: WechatRedPacketSessionPolicy.Action.Capture = open()) {
        assertTrue(policy.markCaptured(capture, wechat, 3, 17_576))
    }

    @Test fun recordedSequenceUsesCachedImageAndConsumesToastOnce() {
        cache()
        val sent = event(TOAST, "android.widget.Toast", window = -1, text = "已发送", now = 21_782)
            as WechatRedPacketSessionPolicy.Action.Sent
        assertEquals(1_800_000_021_782L, sent.evidence.sentAtMillis)
        assertNull(event(TOAST, text = "已发送", now = 21_800))
        assertNull(policy.sessionId)
    }

    @Test fun recordedClickArrivesAfterForegroundAlreadyChangedToLoadingDialog() {
        fun observed(kind: WechatRedPacketSessionPolicy.EventKind, clazz: String, window: Int, foreground: Int, text: String, at: Long) =
            policy.observe(wechat, clazz, window, listOf(text), kind, wechat, foreground, at, 1_800_000_000_000L + at)
        observed(WINDOW, prepare, 1, 1, "发红包", 2_986)
        // 诊断 e99 的 source 仍为准备页，activeRoot 已是加载弹窗；这是实际事件/窗口不同步。
        observed(CLICK, "android.widget.Button", 1, 2, "塞钱进红包", 11_363)
        observed(WINDOW, "com.tencent.mm.ui.widget.dialog.obfuscated", 2, 2, "微信支付", 11_396)
        val capture = observed(WINDOW, payment, 3, 3, "微信", 17_026) as WechatRedPacketSessionPolicy.Action.Capture
        assertTrue(policy.markCaptured(capture, wechat, 3, 17_576))
        // Toast 到达时可能已回聊天窗口，成功会话仍以尚未消费的发送上下文校验。
        assertNotNull(observed(TOAST, "android.widget.Toast", -1, 4, "已发送", 21_782))
        assertNull(observed(TOAST, "android.widget.Toast", -1, 4, "已发送", 21_800))
    }

    @Test fun paymentContainerAndChatSentWithoutSequenceNeverTrigger() {
        assertNull(event(WINDOW, payment))
        assertNull(event(TOAST, text = "已发送"))
        event(WINDOW, prepare)
        assertNull(event(WINDOW, payment)) // 缺少用户提交。
        assertNull(event(TOAST, text = "已发送"))
    }

    @Test fun editingAmountAndLoadingDialogDoNotAuthorizeCapture() {
        event(WINDOW, prepare, now = 0)
        for (amount in listOf("1", "10", "20", "77.35")) assertNull(event(OTHER, text = amount, now = 100))
        event(CLICK, text = "塞钱进红包", now = 200)
        assertNull(event(WINDOW, "com.tencent.mm.ui.widget.dialog.random", text = "微信支付", now = 300))
        assertNull(event(TOAST, text = "已发送", now = 400))
    }

    @Test fun successWithoutCompletedCaptureCannotAcceptLateChatScreenshot() {
        val capture = open()
        assertNull(event(TOAST, text = "已发送"))
        assertFalse(policy.markCaptured(capture, wechat, 3, 21_000))
    }

    @Test fun cancellationFailureLeavingAndResetInvalidateOldCapture() {
        for (cancel in listOf("取消", "关闭", "返回")) {
            val capture = open(); cache(capture)
            event(CLICK, text = cancel)
            assertNull(event(TOAST, text = "已发送"))
            assertFalse(policy.captureValid(capture, wechat, 3, 21_000))
        }
        cache(); event(TOAST, text = "支付失败"); assertNull(event(TOAST, text = "已发送"))
        cache(); event(WINDOW, "com.tencent.mm.ui.chatting.variants.ChattingMainUI")
        assertNull(event(TOAST, text = "已发送"))
        cache(); event(WINDOW, foreground = "com.android.launcher")
        assertNull(event(TOAST, text = "已发送"))
        cache(); policy.reset(); assertNull(event(TOAST, text = "已发送"))
    }

    @Test fun unrelatedOrNonToastEventsCannotAuthorizeSend() {
        cache()
        assertNull(event(TOAST, source = "other.app", text = "已发送"))
        assertNull(event(OTHER, text = "已发送"))
        assertNull(event(TOAST, text = "消息已发送"))
        assertNotNull(event(TOAST, text = "已发送"))
    }

    @Test fun expiryNewSessionAndWindowMismatchRejectStaleCapture() {
        val first = open()
        assertFalse(policy.markCaptured(first, wechat, 9, 18_000))
        assertFalse(policy.markCaptured(first, "other.app", 3, 18_000))
        cache(first)
        assertNull(event(TOAST, text = "已发送", now = ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 3_000L))
        val second = open()
        assertNotEquals(first.sessionId, second.sessionId)
        assertFalse(policy.markCaptured(first, wechat, 3, 18_000))
        event(WINDOW, prepare, now = 18_000) // 返回改金额，新提交不得沿用上一张图。
        assertFalse(policy.markCaptured(second, wechat, 3, 19_000))
        assertNull(event(TOAST, text = "已发送"))
    }

    @Test fun successfulEvidenceSuppliesNameAndActualSendTimeWithoutInventingAmount() {
        val sentAt = 1_800_000_021_782L
        val evidence = WechatRedPacketSessionPolicy.SentEvidence(sentAt)
        val draft = AccountingDraft(amount = "77.35", direction = "EXPENSE", currency = "CNY", paymentStatus = "COMPLETED")
        val bill = requireNotNull(evidence.complete(listOf(draft)))
        assertEquals("77.35", bill.amount)
        assertEquals("微信红包", bill.merchant)
        assertEquals(Instant.ofEpochMilli(sentAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(), bill.occurredAt)
        assertEquals("接收人", evidence.complete(listOf(draft.copy(merchant = "接收人")))?.merchant)
        for (invalid in listOf(draft.copy(amount = ""), draft.copy(amount = "0"), draft.copy(amount = "-1"),
            draft.copy(paymentStatus = "REVIEW"), draft.copy(paymentStatus = "UNPAID"), draft.copy(direction = "INCOME"), draft.copy(currency = "USD")))
            assertNull(evidence.complete(listOf(invalid)))
        assertNull(evidence.complete(emptyList()))
        assertNull(evidence.complete(listOf(draft, draft)))
    }
}
