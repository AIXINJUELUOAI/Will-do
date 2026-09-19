package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.WechatPaymentSessionPolicy
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import org.junit.Assert.*
import org.junit.Test

class WechatPaymentSessionTest {
    private val wechat = AutomaticAccountingPolicy.WECHAT
    private val entry = "com.tencent.mm.plugin.remittance.ui.RemittanceUI"
    private val payment = "com.tencent.mm.framework.app.UIPageFragmentActivity"
    private val chat = "com.tencent.mm.ui.chatting.ChattingUI"

    /** 两份实测包的脱敏事件顺序；金额、联系人和完整 UI 树无需进入测试。 */
    @Test fun bothRecordedTransfersAcceptEventTextWithoutAnyTreeText() {
        for ((entryAt, paymentAt, successAt) in listOf(
            Triple(26_568L, 31_848L, 33_827L), Triple(8_255L, 34_099L, 36_383L))) {
            val policy = WechatPaymentSessionPolicy()
            policy.observeWindow(wechat, entry, 1, entryAt)
            policy.observeWindow(wechat, "com.tencent.mm.ui.widget.dialog.obfuscated", 2, entryAt + 1_000)
            policy.observeWindow(wechat, payment, 3, paymentAt)
            policy.observeWindow(wechat, payment, 3, paymentAt + 121) // 第二轮的重复窗口事件。
            policy.observeWindow("com.android.systemui", "android.widget.FrameLayout", 4, successAt - 1)
            val candidate = requireNotNull(policy.claimSuccess(wechat, 3, listOf("支付成功"), successAt))
            assertTrue(policy.isValid(candidate, wechat, 3, successAt + ConfigCatalog.AUTO_ACCOUNTING_DEBOUNCE_MS))
            assertFalse(AutomaticAccountingPolicy.matchesScreen(wechat, emptyList(), false))
            assertNull(policy.claimSuccess(wechat, 3, listOf("支付成功"), successAt + 100))
            assertTrue(policy.hasClaimedSuccess())
        }
    }

    @Test fun paymentContainerWithoutSuccessOrChatTextNeverTriggers() {
        val policy = WechatPaymentSessionPolicy()
        policy.observeWindow(wechat, payment, 3, 0)
        assertNull(policy.claimSuccess(wechat, 3, emptyList(), 100))
        assertNull(policy.claimSuccess(wechat, 3, listOf("确认付款", "支付处理中"), 100))
        policy.observeWindow(wechat, entry, 1, 200)
        assertNull(policy.claimSuccess(wechat, 1, listOf("支付成功"), 300))
        policy.observeWindow(wechat, payment, 3, 400)
        assertNull(policy.claimSuccess(wechat, 3, listOf("确认付款", "支付处理中"), 500))
        assertNull(policy.claimSuccess(wechat, 3, listOf("聊天引用：支付成功"), 500))
        assertNull(policy.claimSuccess("com.android.systemui", 3, listOf("支付成功"), 500))
        assertNull(policy.claimSuccess(wechat, 4, listOf("支付成功"), 500))
        assertNull(policy.claimSuccess(wechat, -1, listOf("支付成功"), 500))
        policy.observeWindow(wechat, chat, 4, 600)
        assertNull(policy.claimSuccess(wechat, 4, listOf("支付成功"), 700))
    }

    /** 扫码等入口的合成回归：没有 RemittanceUI 前置事件也能复用成功事件路径。 */
    @Test fun directPaymentContainerCanTriggerOnceWithoutFriendTransferEntry() {
        val policy = WechatPaymentSessionPolicy()
        policy.observeWindow(wechat, chat, 1, 0)
        assertNull(policy.claimSuccess(wechat, 1, listOf("支付成功"), 100))
        policy.observeWindow(wechat, payment, 3, 200)
        val candidate = requireNotNull(policy.claimSuccess(wechat, 3, listOf("支付成功"), 300))
        assertTrue(policy.isValid(candidate, wechat, 3, 300L + ConfigCatalog.AUTO_ACCOUNTING_DEBOUNCE_MS))
        policy.observeWindow(wechat, payment, 3, 400)
        assertNull(policy.claimSuccess(wechat, 3, listOf("支付成功"), 500))
        // 停留成功页超过上下文有效期，不应因相同窗口的刷新重新调用模型。
        policy.observeWindow(wechat, payment, 3, ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 1_000L)
        assertNull(policy.claimSuccess(wechat, 3, listOf("支付成功"), ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 1_100L))
        policy.observeWindow(wechat, chat, 1, ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 1_200L)
        policy.observeWindow(wechat, payment, 4, ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 1_300L)
        val next = requireNotNull(policy.claimSuccess(wechat, 4, listOf("付款成功"), ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 1_400L))
        assertNotEquals(candidate.sessionId, next.sessionId)
    }

    @Test fun leavingPaymentWindowInvalidatesPendingScreenshotAndOldCandidate() {
        val policy = WechatPaymentSessionPolicy()
        policy.observeWindow(wechat, entry, 1, 0)
        policy.observeWindow(wechat, payment, 3, 100)
        val candidate = requireNotNull(policy.claimSuccess(wechat, 3, listOf("", "支付成功"), 200)) // desc 也可承载文字。
        assertFalse(policy.isValid(candidate, "com.android.launcher", 8, 300))
        assertFalse(policy.isValid(candidate, wechat, 4, 300))
        policy.observeWindow(wechat, "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI", 4, 300)
        assertFalse(policy.isValid(candidate, wechat, 3, 400))
        policy.observeWindow(wechat, entry, 1, 500)
        policy.observeWindow(wechat, payment, 3, 600)
        val next = requireNotNull(policy.claimSuccess(wechat, 3, listOf("转账成功"), 700))
        assertNotEquals(candidate.sessionId, next.sessionId)
        assertFalse(policy.isValid(candidate, wechat, 3, 800))
        assertTrue(policy.isValid(next, wechat, 3, 800))
        policy.reset() // 关闭开关、开始诊断或服务中断。
        assertFalse(policy.isValid(next, wechat, 3, 900))
    }

    @Test fun expiredSignalsDoNotScreenshotOrRearmThroughDuplicateWindowEvents() {
        val policy = WechatPaymentSessionPolicy()
        policy.observeWindow(wechat, entry, 1, 0)
        policy.observeWindow(wechat, payment, 3, 100)
        val candidate = requireNotNull(policy.claimSuccess(wechat, 3, listOf("支付成功"), 200))
        assertFalse(policy.isValid(candidate, wechat, 3, 201L + ConfigCatalog.AUTO_ACCOUNTING_SUCCESS_EVENT_MS))
        policy.observeWindow(wechat, payment, 3, 20_000)
        assertNull(policy.claimSuccess(wechat, 3, listOf("支付成功"), 20_100))
        policy.reset()
        policy.observeWindow(wechat, payment, 3, 0)
        policy.observeWindow(wechat, payment, 3, ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 1L)
        assertNull(policy.claimSuccess(wechat, 3, listOf("支付成功"), ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 2L))
    }

    @Test fun readableAlipayScanTransferKeepsExistingEligibility() {
        assertTrue(AutomaticAccountingPolicy.matchesScreen(AutomaticAccountingPolicy.ALIPAY,
            listOf("转账成功", "￥8.50"), false))
        assertFalse(AutomaticAccountingPolicy.matchesScreen(AutomaticAccountingPolicy.ALIPAY,
            listOf("交易详情", "转账成功", "￥8.50"), false))
    }
}
