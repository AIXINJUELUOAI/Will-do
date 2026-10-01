package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.*
import org.junit.Assert.*
import org.junit.Test

class PaymentPageIdentityTest {
    private val pkg = AutomaticAccountingPolicy.ALIPAY
    private fun fields(id: String? = null, amount: String = "-8.50") =
        listOf("账单详情", amount, "支付时间", "2026-09-30 12:30:00", "收款方", "测试商户") +
            if (id == null) emptyList() else listOf("交易单号", id)
    private fun policy() = PaymentDetailPolicy().apply { observeWindow(pkg, "com.alipay.DetailActivity", 1) }

    @Test fun distinctTransactionsInSameWindowHaveNoGlobalInterval() {
        val policy = policy()
        val first = requireNotNull(policy.claimTree(pkg, 1, fields("A"), false, 0))
        policy.captured(first)
        val second = requireNotNull(policy.claimTree(pkg, 1, fields("B"), false, 1))
        policy.captured(second)
        val third = requireNotNull(policy.claimTree(pkg, 1, fields("C"), false, 2))
        assertEquals(3, setOf(first.visit, second.visit, third.visit).size)
        assertFalse(policy.isValid(first, pkg, 1, fields("C"), false, 3))
        assertTrue(policy.isValid(third, pkg, 1, fields("C"), false, 3))
    }

    @Test fun enrichmentAndCompletedRefreshReuseTaskButDifferentNumberCreatesNewOne() {
        val policy = policy()
        val first = requireNotNull(policy.claimTree(pkg, 1, fields(), false, 0))
        assertNull(policy.claimTree(pkg, 1, fields("A"), false, 1))
        assertTrue(policy.isValid(first, pkg, 1, fields("A"), false, 2))
        policy.captured(first)
        policy.finished(first)
        assertNull(policy.claimTree(pkg, 1, fields("A") + "更多服务", false, 20_000))
        assertNotNull(policy.claimTree(pkg, 1, fields("B"), false, 20_001))
    }

    @Test fun failedCaptureReleasesOnlyItsOwnReservation() {
        val policy = policy()
        val first = requireNotNull(policy.claimTree(pkg, 1, fields("A"), false, 0))
        policy.release(first)
        val retry = requireNotNull(policy.claimTree(pkg, 1, fields("A"), false, 1))
        assertNotEquals(first.visit, retry.visit)
        policy.release(first)
        assertTrue(policy.isValid(retry, pkg, 1, emptyList(), false, 2))
        val second = requireNotNull(policy.claimTree(pkg, 1, fields("B"), false, 3))
        policy.release(retry)
        assertTrue(policy.isValid(second, pkg, 1, emptyList(), false, 4))
    }

    @Test fun capturedTaskSurvivesNavigationAndCaptureExpiryButRejectsDuplicateWhileActive() {
        val policy = policy()
        val first = requireNotNull(policy.claimTree(pkg, 1, fields("A"), false, 0))
        policy.captured(first)
        policy.observeForeground("other.app", 3)
        policy.observeWindow(pkg, "com.alipay.DetailActivity", 4)
        assertNull(policy.claimTree(pkg, 4, fields("A"), false, 20_000))
        policy.finished(first)
        // 等待中重新打开过同笔，完成后的本次访问刷新仍复用已完成任务。
        assertNull(policy.claimTree(pkg, 4, fields("A"), false, 20_001))
        policy.observeForeground("other.app", 5)
        policy.observeWindow(pkg, "com.alipay.DetailActivity", 6)
        assertNotNull(policy.claimTree(pkg, 6, fields("A"), false, 20_002))
    }

    @Test fun weakPaymentIdentityCannotMergeDifferentVisitsOrPaymentSessions() {
        val policy = policy()
        val success = listOf("支付成功", "¥8.50", "收款方", "商家")
        val first = requireNotNull(policy.claimPaymentResult(pkg, 1, success, false, 0))
        policy.captured(first)
        assertNull(policy.claimPaymentResult(pkg, 1, success, false, 1))
        assertNull(policy.claimPaymentResult(pkg, 1, listOf("确认付款", "¥8.50"), false, 2))
        val second = requireNotNull(policy.claimPaymentResult(pkg, 1, success, false, 3))
        assertNotEquals(first.visit, second.visit)
        policy.observeWindow(pkg, "com.alipay.DetailActivity", 2)
        assertNotNull(policy.claimPaymentResult(pkg, 2, success, false, 4))
    }

    @Test fun captureRejectsContradictingTransactionAndRefundIsIndependent() {
        val policy = policy()
        val first = requireNotNull(policy.claimTree(pkg, 1, fields("A"), false, 0))
        assertFalse(policy.isValid(first, pkg, 1, fields("B"), false, 1))
        assertFalse(policy.isValid(first, pkg, 1, listOf("加载中"), false, 1))
        assertTrue(policy.isValid(first, pkg, 1, emptyList(), false, 1))
        val expense = PaymentPageIdentity.from(fields("A"))
        val refund = PaymentPageIdentity.from(fields("A", "+8.50") + "退款成功")
        assertTrue(expense.conflicts(refund))
        assertFalse(expense.sameTransaction(refund))
    }
}
