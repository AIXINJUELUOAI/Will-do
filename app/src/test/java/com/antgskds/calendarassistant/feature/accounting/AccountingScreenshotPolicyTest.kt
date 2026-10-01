package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.AccountingScreenshotPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.WechatPaymentSessionPolicy
import org.junit.Assert.*
import org.junit.Test

class AccountingScreenshotPolicyTest {
    @Test fun rejectsEmptySolidBlackWhiteAndLowContrastButAllowsVisibleContent() {
        assertFalse(AccountingScreenshotPolicy.hasContent(intArrayOf()))
        assertFalse(AccountingScreenshotPolicy.hasContent(intArrayOf(0xff000000.toInt(), 0xff000000.toInt())))
        assertFalse(AccountingScreenshotPolicy.hasContent(intArrayOf(0xffffffff.toInt(), 0xffffffff.toInt())))
        assertFalse(AccountingScreenshotPolicy.hasContent(intArrayOf(0xff000000.toInt(), 0xff050505.toInt())))
        assertTrue(AccountingScreenshotPolicy.hasContent(intArrayOf(0xffffffff.toInt(), 0xff808080.toInt())))
    }

    @Test fun sameWechatWindowCanStartAnotherPaymentAfterNewCheckoutEvidence() {
        val policy = WechatPaymentSessionPolicy()
        val pkg = AutomaticAccountingPolicy.WECHAT
        policy.observeWindow(pkg, "com.tencent.mm.framework.app.UIPageFragmentActivity", 1, 0)
        val first = requireNotNull(policy.claimSuccess(pkg, 1, listOf("支付成功"), 1))
        policy.observePaymentContent(pkg, 1, listOf("支付成功", "付款方式"), false, 2)
        assertTrue(policy.hasClaimedSuccess())
        assertFalse(policy.hasClaimedSuccess(AutomaticAccountingPolicy.ALIPAY))
        policy.observePaymentContent(pkg, 1, listOf("确认付款", "￥8.50"), false, 3)
        val second = requireNotNull(policy.claimSuccess(pkg, 1, listOf("支付成功"), 4))
        assertNotEquals(first.sessionId, second.sessionId)
        policy.release(first)
        assertTrue(policy.isValid(second, pkg, 1, 5))
    }
}
