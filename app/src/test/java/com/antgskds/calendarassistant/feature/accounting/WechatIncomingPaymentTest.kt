package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.WechatIncomingPaymentPolicy
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WechatIncomingPaymentTest {
    private val wechat = AutomaticAccountingPolicy.WECHAT
    private val transfer = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"
    private val receive = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewReceiveUI"
    private val detail = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewDetailUI"
    private val chat = "com.tencent.mm.ui.LauncherUI"
    private val loading = "com.tencent.mm.ui.widget.dialog.u3"
    private val success = listOf("￥6.30", "你已收款，资金已存入零钱")
    private val wall = 1_800_000_000_000L
    private fun event(policy: WechatIncomingPaymentPolicy, clazz: String = "", window: Int = 20,
        texts: List<String> = emptyList(), now: Long = 100, isWindow: Boolean = true, editable: Boolean = false,
        source: String = wechat, foreground: String? = wechat, foregroundWindow: Int? = window) =
        policy.observe(source, clazz, window, texts, editable, isWindow, foreground, foregroundWindow, now, wall + now)
    private fun transfer(policy: WechatIncomingPaymentPolicy): WechatIncomingPaymentPolicy.Candidate {
        event(policy, transfer)
        return requireNotNull(event(policy, texts = success, now = 200, isWindow = false))
    }
    private fun redPacket(policy: WechatIncomingPaymentPolicy): WechatIncomingPaymentPolicy.Candidate {
        event(policy, receive, window = 10, now = 100)
        event(policy, loading, window = 11, texts = listOf("正在加载"), now = 200)
        return requireNotNull(event(policy, detail, window = 12, now = 300))
    }

    @Test fun recordedReceiptWithEmptyTreeSurvivesLoadingDialogAndConsumesSuccessOnce() {
        val policy = WechatIncomingPaymentPolicy()
        event(policy, transfer, window = 1080, now = 3373)
        event(policy, loading, window = 1081, texts = listOf("正在加载"), now = 3978)
        event(policy, loading, window = 1082, texts = listOf("正在加载"), now = 4864)
        val candidate = requireNotNull(event(policy, window = 1080, texts = success, now = 5116, isWindow = false))
        assertEquals(WechatIncomingPaymentPolicy.Kind.TRANSFER, candidate.evidence.kind)
        assertTrue(policy.isValid(candidate, wechat, 1080, emptyList(), false, 5150))
        assertNull(event(policy, transfer, window = 1080, now = 5123))
        assertNull(event(policy, window = 1080, texts = success, now = 5200, isWindow = false))
        assertTrue(policy.hasClaimed(wechat, 1080))
    }

    @Test fun successBeforeActivityIsBoundOnlyToSameWindow() {
        val policy = WechatIncomingPaymentPolicy()
        assertNull(event(policy, texts = success, isWindow = false))
        val candidate = requireNotNull(event(policy, transfer, now = 150))
        assertTrue(policy.isValid(candidate, wechat, 20, emptyList(), false, 200))
        val other = WechatIncomingPaymentPolicy()
        event(other, texts = success, isWindow = false)
        assertNull(event(other, transfer, window = 21, now = 150))
    }

    @Test fun pendingSuccessExpiresWithoutRefreshingFromEmptyEvents() {
        val policy = WechatIncomingPaymentPolicy()
        event(policy, texts = success, now = 0, isWindow = false)
        assertNull(event(policy, transfer, now = ConfigCatalog.AUTO_ACCOUNTING_SUCCESS_EVENT_MS + 1L))
    }

    @Test fun chatQuoteEditableUnsupportedAndBackgroundEventsDoNotAuthorizeCapture() {
        for (clazz in listOf(chat, "com.tencent.mm.ui.chatting.ChattingUI")) {
            val policy = WechatIncomingPaymentPolicy()
            event(policy, clazz)
            assertNull(event(policy, texts = success, now = 200, isWindow = false))
            assertNull(event(policy, transfer, now = 300))
        }
        val editable = WechatIncomingPaymentPolicy()
        event(editable, transfer)
        assertNull(event(editable, texts = success, editable = true, isWindow = false))
        val other = WechatIncomingPaymentPolicy()
        assertNull(event(other, transfer, texts = success, source = "other.app", foreground = "other.app"))
        assertNull(event(other, transfer, texts = success, foregroundWindow = 21))
        assertNull(event(other, transfer, texts = success, foreground = null, foregroundWindow = null))
    }

    @Test fun amountOrPendingStateAloneCannotTriggerAndAmbiguousAmountsAreRejected() {
        val policy = WechatIncomingPaymentPolicy()
        event(policy, transfer)
        for (texts in listOf(listOf("￥6.30"), listOf("待你收款", "￥6.30"),
            listOf("你已收款，资金已存入零钱"), success + "余额 ￥88.00", listOf("对方已收款", "￥6.30")))
            assertNull(event(policy, texts = texts, isWindow = false))
    }

    @Test fun newAmountIsAnotherCandidateWhileSameAmountRefreshIsMerged() {
        val policy = WechatIncomingPaymentPolicy()
        val first = transfer(policy)
        assertNull(event(policy, texts = success, now = 250, isWindow = false))
        val second = requireNotNull(event(policy, texts = listOf("￥9.40", "你已收款，资金已存入零钱"), now = 300, isWindow = false))
        assertNotEquals(first.visit, second.visit)
        assertFalse(policy.isValid(first, wechat, 20, emptyList(), false, 350))
        assertTrue(policy.isValid(second, wechat, 20, emptyList(), false, 350))
    }

    @Test fun captureRejectsLoadingChangedAmountWrongWindowLeavingAndExpiry() {
        val policy = WechatIncomingPaymentPolicy()
        val candidate = transfer(policy)
        assertFalse(policy.isValid(candidate, wechat, 20, listOf("正在加载"), false, 250))
        assertFalse(policy.isValid(candidate, wechat, 20, listOf("￥9.40"), false, 250))
        assertFalse(policy.isValid(candidate, wechat, 20, listOf("待你收款"), false, 250))
        assertFalse(policy.isValid(candidate, wechat, 21, emptyList(), false, 250))
        assertFalse(policy.isValid(candidate, "other.app", 20, emptyList(), false, 250))
        assertFalse(policy.isValid(candidate, wechat, 20, emptyList(), true, 250))
        assertFalse(policy.isValid(candidate, wechat, 20, emptyList(), false, candidate.detectedAt + ConfigCatalog.AUTO_ACCOUNTING_SUCCESS_EVENT_MS + 1))
        event(policy, chat, now = 300)
        assertFalse(policy.isValid(candidate, wechat, 20, emptyList(), false, 350))
    }

    @Test fun rejectedCaptureCanRetryButOldReleaseCannotClearNewCandidate() {
        val policy = WechatIncomingPaymentPolicy()
        val first = transfer(policy)
        policy.release(first)
        val retry = requireNotNull(event(policy, transfer, now = 250))
        assertNotEquals(first.visit, retry.visit)
        policy.release(first)
        assertTrue(policy.isValid(retry, wechat, 20, emptyList(), false, 300))
        val next = requireNotNull(event(policy, texts = listOf("￥9.40", "你已收款，资金已存入零钱"), now = 350, isWindow = false))
        policy.release(first)
        assertTrue(policy.isValid(next, wechat, 20, emptyList(), false, 400))
    }

    @Test fun receivedRedPacketSequenceAuthorizesOneResultWithEmptyNodes() {
        val policy = WechatIncomingPaymentPolicy()
        val candidate = redPacket(policy)
        assertEquals(WechatIncomingPaymentPolicy.Kind.RED_PACKET, candidate.evidence.kind)
        assertEquals(wall + 300, candidate.evidence.receivedAtMillis)
        assertTrue(policy.isValid(candidate, wechat, 12, emptyList(), false, 350))
        assertNull(event(policy, detail, window = 12, now = 400))
        assertNull(event(policy, window = 12, now = 450, isWindow = false))
    }

    @Test fun receivedDetailClassHandoffInSameWindowKeepsConsumedCandidate() {
        val policy = WechatIncomingPaymentPolicy()
        val candidate = redPacket(policy)
        assertNull(event(policy, "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyBeforeDetailUI", window = 12, now = 350))
        assertTrue(policy.isValid(candidate, wechat, 12, emptyList(), false, 400))
        val old = WechatIncomingPaymentPolicy()
        assertNull(event(old, "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyBeforeDetailUI", now = 100))
    }

    @Test fun historicalRedPacketAndReceiveWithoutResultCannotTrigger() {
        val historical = WechatIncomingPaymentPolicy()
        assertNull(event(historical, detail))
        assertNull(event(historical, texts = listOf("￥6.30", "已存入零钱"), isWindow = false))
        val unopened = WechatIncomingPaymentPolicy()
        assertNull(event(unopened, receive, window = 10))
        assertNull(event(unopened, window = 10, texts = listOf("开"), isWindow = false))
        assertNull(event(unopened, detail, window = 10, now = 200))
    }

    @Test fun cancellationExpiredAndAlreadyClaimedRedPacketsCannotUseCurrentTime() {
        val cancelled = WechatIncomingPaymentPolicy()
        event(cancelled, receive, window = 10)
        event(cancelled, chat, window = 9, now = 200)
        assertNull(event(cancelled, detail, window = 12, now = 300))
        val expired = WechatIncomingPaymentPolicy()
        event(expired, receive, window = 10, now = 0)
        assertNull(event(expired, detail, window = 12, now = ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 1L))
        for (text in listOf("查看领取详情", "红包已领取", "红包已被领完", "红包已过期")) {
            val old = WechatIncomingPaymentPolicy()
            event(old, receive, window = 10, texts = listOf(text))
            assertNull(event(old, detail, window = 12, now = 200))
        }
    }

    @Test fun staleLauncherBehindResultCannotChangeItsPageIdentity() {
        val policy = WechatIncomingPaymentPolicy()
        val candidate = redPacket(policy)
        assertNull(event(policy, chat, window = 9, foregroundWindow = 12, now = 400))
        assertTrue(policy.isValid(candidate, wechat, 12, emptyList(), false, 450))
        policy.reset()
        assertFalse(policy.isValid(candidate, wechat, 12, emptyList(), false, 450))
    }

    private val income = AccountingDraft(amount = "6.30", direction = "INCOME", currency = "CNY", paymentStatus = "COMPLETED",
        occurredAt = "2026-10-09T14:20:00")

    @Test fun transferKeepsImageTimeAndRequiresMatchingConfirmedIncome() {
        val evidence = WechatIncomingPaymentPolicy.Evidence(WechatIncomingPaymentPolicy.Kind.TRANSFER, "6.3")
        val result = requireNotNull(evidence.complete(listOf(income), emptyList()))
        assertEquals(income.occurredAt, result.occurredAt)
        assertEquals("微信支付", result.channel)
        assertNull(evidence.complete(listOf(income.copy(occurredAt = "")), emptyList()))
        assertNull(evidence.complete(listOf(income.copy(amount = "9.40")), emptyList()))
        assertNull(evidence.complete(listOf(income), listOf("另有无法解析的交易")))
        assertNull(evidence.complete(emptyList(), emptyList()))
        assertNull(evidence.complete(listOf(income, income), emptyList()))
    }

    @Test fun receivedRedPacketUsesObservedClaimTimeAndPreservesExplicitImageTime() {
        val evidence = WechatIncomingPaymentPolicy.Evidence(WechatIncomingPaymentPolicy.Kind.RED_PACKET, receivedAtMillis = wall)
        val result = requireNotNull(evidence.complete(listOf(income.copy(occurredAt = "")), emptyList()))
        assertEquals(Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(), result.occurredAt)
        assertEquals(wall, result.createdAt)
        assertEquals("微信红包", result.merchant)
        assertEquals(income.occurredAt, evidence.complete(listOf(income), emptyList())?.occurredAt)
        assertNull(WechatIncomingPaymentPolicy.Evidence(WechatIncomingPaymentPolicy.Kind.RED_PACKET).complete(listOf(income), emptyList()))
    }

    @Test fun incompleteWrongDirectionAndInvalidAmountsNeverBecomeIncome() {
        val evidence = WechatIncomingPaymentPolicy.Evidence(WechatIncomingPaymentPolicy.Kind.RED_PACKET, receivedAtMillis = wall)
        for (bill in listOf(income.copy(direction = "EXPENSE"), income.copy(paymentStatus = "UNPAID"),
            income.copy(paymentStatus = "REVIEW"), income.copy(currency = "USD"), income.copy(amount = ""),
            income.copy(amount = "0"), income.copy(amount = "-1"), income.copy(amount = "6.301")))
            assertNull(evidence.complete(listOf(bill), emptyList()))
        assertNull(WechatIncomingPaymentPolicy.Evidence(WechatIncomingPaymentPolicy.Kind.TRANSFER, "invalid").complete(listOf(income), emptyList()))
    }
}
