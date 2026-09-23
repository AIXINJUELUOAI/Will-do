package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import com.antgskds.calendarassistant.feature.accounting.domain.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import org.junit.Assert.*
import org.junit.Test

class AccountingNotificationPriorityTest {
    private val policy = AccountingNotificationPriorityPolicy()
    private val alipay = AutomaticAccountingPolicy.ALIPAY
    private val wechat = AutomaticAccountingPolicy.WECHAT
    private val at = 1_800_000_000_000L
    private val expense = AccountingNotificationPriorityPolicy.Hint(835L, "EXPENSE")
    private fun message(source: String = alipay, time: Long = at, kind: AccountingMessageKind = AccountingMessageKind.NOTIFICATION) =
        AccountingMessage(kind, source, "", time)
    private fun bill(id: String = "first", amount: Long = 835L, direction: String = "EXPENSE", time: Long = at) =
        AccountingEntry(id, amount, direction, "CNY", "商户", "其他", "", time, "Asia/Shanghai", "RECOGNITION", "支付宝",
            "", "CONFIRMED", "recognition-confirmed", null, null, time, time, null)
    private fun record(entry: AccountingEntry = bill(), source: String = alipay, time: Long = at) =
        policy.record(message(source, time), AccountingRecognitionResult(saved = listOf(entry)), time + 100)

    @Test fun notificationSavedBeforeOrDuringImageWaitSkipsOneAiRequest() {
        record()
        assertTrue(policy.claim(alipay, expense, at + 300, at + 500))
        assertFalse(policy.claim(alipay, expense, at + 300, at + 600))
        assertFalse(policy.claim(alipay, expense, at + 1_000, at + 1_000))
        record(bill("second", time = at + 1_200), time = at + 1_200)
        assertTrue(policy.claim(alipay, expense, at + 1_000, at + 2_500))
    }

    @Test fun receivedButUnparsedFailedPendingOrDuplicateDoesNotSuppress() {
        for (result in listOf(null, AccountingRecognitionResult(), AccountingRecognitionResult(pending = 1),
            AccountingRecognitionResult(suspectedDuplicates = 1), AccountingRecognitionResult(duplicates = 1))) {
            policy.record(message(), result, at + 100)
            assertFalse(policy.claim(alipay, expense, at, at + 500))
        }
    }

    @Test fun differentSourceAmountOrDirectionDoesNotConsumeReceipt() {
        record()
        assertFalse(policy.claim(wechat, expense, at, at + 100))
        assertFalse(policy.claim(alipay, expense.copy(amountMinor = 500L), at, at + 100))
        assertFalse(policy.claim(alipay, expense.copy(direction = "INCOME"), at, at + 100))
        assertTrue(policy.claim(alipay, expense, at, at + 100))
    }

    @Test fun lateOrHistoricalNotificationsSmsAndUnknownAppsNeverSuppress() {
        val delay = ConfigCatalog.AUTO_ACCOUNTING_NOTIFICATION_MATCH_MS.toLong() + 1
        policy.record(message(), AccountingRecognitionResult(saved = listOf(bill())), at + delay)
        policy.record(message(kind = AccountingMessageKind.SMS), AccountingRecognitionResult(saved = listOf(bill())), at + 100)
        record(source = "other.app")
        record(bill(time = at - 86_400_000))
        record(bill().copy(status = "PENDING"))
        record(bill().copy(deletedAt = at))
        assertFalse(policy.claim(alipay, expense, at, at + 500))
        record()
        assertFalse(policy.claim(alipay, expense, at, at + delay + 100))
    }

    @Test fun ambiguousEmptyTreeFallsBackToImageAndKnownAmountCanDisambiguate() {
        val unknown = expense.copy(amountMinor = null)
        record()
        record(bill("second", amount = 999L))
        assertFalse(policy.claim(alipay, unknown, at, at + 500))
        assertTrue(policy.claim(alipay, expense, at, at + 500))
        assertTrue(policy.claim(alipay, unknown, at, at + 500))
        record(bill("third"))
        policy.reset()
        assertFalse(policy.claim(alipay, unknown, at, at + 500))
    }

    @Test fun snapshotAmountMustBeUnambiguousAndUsesMinorUnits() {
        assertEquals(expense, AccountingNotificationPriorityPolicy.hint(listOf("付款成功", "¥8.35", "8.35元")))
        assertEquals(123456L, AccountingNotificationPriorityPolicy.hint(listOf("支付成功", "￥1,234.56"))?.amountMinor)
        assertEquals("INCOME", AccountingNotificationPriorityPolicy.hint(listOf("收款成功", "9.99元"))?.direction)
        assertNull(AccountingNotificationPriorityPolicy.hint(listOf("支付成功")))
        assertNull(AccountingNotificationPriorityPolicy.hint(listOf("支付成功", "¥8.35", "余额 ¥900.00")))
    }

    @Test fun paymentResultRetainsHintButHistoricalDetailsNeverGetOne() {
        val detail = PaymentDetailPolicy()
        detail.observeWindow(alipay, "com.alipay.mobile.bill.detail.BillDetailActivity", 1)
        val historical = requireNotNull(detail.claimTree(alipay, 1,
            listOf("账单详情", "交易成功", "￥8.35", "支付时间", "2026-09-20 10:20:30"), false, 0))
        assertFalse(historical.isPaymentResult)
        assertNull(historical.notificationHint)
        detail.reset()
        detail.observeWindow(alipay, "com.alipay.mobile.pay.SuccessActivity", 2)
        val payment = requireNotNull(detail.claimPaymentResult(alipay, 2, listOf("付款成功", "￥8.35"), false, 0))
        assertTrue(payment.isPaymentResult)
        assertEquals(expense, payment.notificationHint)
    }
}
