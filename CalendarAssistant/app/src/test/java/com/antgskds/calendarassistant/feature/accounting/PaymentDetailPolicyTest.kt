package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionMapper
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.PaymentDetailPolicy
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PaymentDetailPolicyTest {
    private val wechat = AutomaticAccountingPolicy.WECHAT
    private val alipay = AutomaticAccountingPolicy.ALIPAY
    private val web = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI"
    private val detail = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"
    private val completeFields = listOf("-8.50", "支付时间", "2026-09-17 12:30:00")
    private fun readyTexts(marker: String = "账单详情") = listOf(marker) + completeFields

    private val checkoutControls = listOf("关闭", "使用密码", "测试商家", "付款方式，已选择零钱，尾号:，点按两次进行修改")
    private val alipayCheckout = "com.alipay.android.msp.ui.views.MspContainerActivity"

    /** 拼多多微信：LauncherUI -> 独立收银台窗口 -> 指纹 -> 成功，后续根节点可一直为空。 */
    @Test fun shoppingWechatRequiresCheckoutThenSuccessInItsOwnWindow() {
        for (amount in listOf("7.45", "32.18")) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 10)
            policy.observeForeground(wechat, 11)
            assertNull(policy.claimTree(wechat, 11, checkoutControls, false, 100, "event_source"))
            assertNull(policy.claimPaymentResult(wechat, 11, checkoutControls, false, 100))
            assertNull(policy.claimPaymentResult(wechat, 11, listOf("请验证指纹"), false, 200))
            assertNull(policy.claimPaymentResult(wechat, 11, listOf("微信支付", "￥$amount"), false, 300))
            assertNull(policy.claimPaymentResult(wechat, 11, listOf("支付成功", "￥$amount"), false, 400))
            val success = listOf("支付成功", "测试商家", "￥$amount", "返回商家")
            assertNull(policy.claimTree(wechat, 11, success, false, 500, "event_source"))
            val candidate = requireNotNull(policy.claimPaymentResult(wechat, 11, success, false, 500))
            assertEquals("wechat_checkout_source", candidate.evidence)
            assertTrue(candidate.isPaymentResult)
            policy.observeWindow(wechat, "android.widget.FrameLayout", 11)
            assertNull(policy.claimTree(wechat, 11, emptyList(), false, 600))
            assertNull(policy.claimPaymentResult(wechat, 11, success, false, 600))
            assertTrue(policy.isValid(candidate, wechat, 11, emptyList(), false, 1_200))
            assertTrue(policy.isValid(candidate, wechat, 11, success, false, 1_300))
            assertFalse(policy.isValid(candidate, wechat, 11, success, true, 1_300))
            policy.observeForeground("com.xunmeng.pinduoduo", 12)
            assertFalse(policy.isValid(candidate, wechat, 11, emptyList(), false, 1_400))
        }
    }

    @Test fun shoppingWordsCannotAuthorizeChatMainWindowOrAnotherWindow() {
        val success = listOf("支付成功", "￥7.45", "返回商家")
        for (page in listOf("com.tencent.mm.ui.LauncherUI", "com.tencent.mm.ui.chatting.ChattingUI")) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(wechat, page, 10)
            assertNull(policy.claimPaymentResult(wechat, 10, checkoutControls, false, 0))
            assertNull(policy.claimPaymentResult(wechat, 10, success, false, 100))
            policy.observeForeground(wechat, 11)
            assertNull(policy.claimPaymentResult(wechat, 11, success, false, 200))
            assertNull(policy.claimPaymentResult(wechat, 11, checkoutControls + "按住说话", false, 300))
            assertNull(policy.claimPaymentResult(wechat, 11, checkoutControls, true, 400))
            assertNull(policy.claimPaymentResult(wechat, 11, success, false, 500))
            assertNull(policy.claimPaymentResult(wechat, 11, checkoutControls, false, 600))
            if (page.contains("chatting")) assertNull(policy.claimPaymentResult(wechat, 11, success, false, 700))
            policy.observeForeground(wechat, 12)
            assertNull(policy.claimPaymentResult(wechat, 12, success, false, 800))
        }
    }

    @Test fun shoppingWechatCheckoutExpiresAndDoesNotRefreshOnRepeatedControls() {
        val policy = PaymentDetailPolicy()
        policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 10)
        policy.observeForeground(wechat, 11)
        assertNull(policy.claimPaymentResult(wechat, 11, checkoutControls, false, 0))
        val expired = ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS + 1L
        assertNull(policy.claimPaymentResult(wechat, 11, checkoutControls, false, expired))
        assertNull(policy.claimPaymentResult(wechat, 11, listOf("支付成功", "￥7.45", "返回商家"), false, expired))
    }

    @Test fun shoppingWechatLoadingOrHistoricalSourceCancelsPendingScreenshot() {
        for (next in listOf(listOf("加载中"), readyTexts())) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 10)
            policy.observeForeground(wechat, 11)
            policy.claimPaymentResult(wechat, 11, checkoutControls, false, 0)
            val candidate = requireNotNull(policy.claimPaymentResult(wechat, 11,
                listOf("支付成功", "￥7.45", "返回商家"), false, 100))
            assertNull(policy.claimTree(wechat, 11, next, false, 200, "event_source"))
            assertNull(policy.claimPaymentResult(wechat, 11, next, false, 200))
            assertFalse(policy.isValid(candidate, wechat, 11, emptyList(), false, 800))
        }
    }

    /** 拼多多支付宝收银台付款前已带商户单号，且含“支付成功得绿色能量”营销文案。 */
    @Test fun alipayShoppingOrderNumberDoesNotLatchHistoricalDetailBeforeSuccess() {
        for (amount in listOf("6.15", "28.90")) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(alipay, alipayCheckout, 20)
            val unpaid = listOf("商户单号EXAMPLE", "¥", amount, "立即付款", "付款并开通", "使用密码",
                "付款,确认付款，测试商家，付款金额${amount}元人民币", "支付成功得绿色能量5g")
            assertNull(policy.claimTree(alipay, 20, unpaid, false, 100, "event_source"))
            assertNull(policy.claimEvent(alipay, 20, unpaid, 100))
            assertFalse(policy.hasDetailContext(alipay))
            assertNull(policy.claimPaymentResult(alipay, 20, unpaid, false, 100))
            val success = listOf("完成", "商户单号EXAMPLE", "支付成功￥$amount", "支付成功", "￥$amount", "交易方式", "余额宝")
            assertNull(policy.claimTree(alipay, 20, success, false, 200, "event_source"))
            val candidate = requireNotNull(policy.claimPaymentResult(alipay, 20, success, false, 200))
            assertTrue(candidate.isPaymentResult)
            assertFalse(policy.hasDetailContext(alipay))
            assertTrue(policy.isValid(candidate, alipay, 20, success, false, 900))
            assertTrue(policy.isValid(candidate, alipay, 20, emptyList(), false, 1_000))
            assertNull(policy.claimTree(alipay, 20, success, false, 1_100, "event_source"))
            assertNull(policy.claimPaymentResult(alipay, 20, success, false, 1_100))
            assertTrue(policy.isValid(candidate, alipay, 20, emptyList(), false, 1_200))
        }
    }

    @Test fun alipayCheckoutStillHonorsHistoricalDetailAndRefundEvidence() {
        for (fields in listOf(readyTexts("商户订单号"), readyTexts("账单详情"), readyTexts("退款记录"))) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(alipay, alipayCheckout, 20)
            val success = requireNotNull(policy.claimPaymentResult(alipay, 20, listOf("支付成功", "￥6.15"), false, 0))
            assertFalse(policy.isValid(success, alipay, 20, fields, false, 700))
            val detailCandidate = requireNotNull(policy.claimTree(alipay, 20, fields, false, 800, "event_source"))
            assertFalse(detailCandidate.isPaymentResult)
            assertFalse(policy.isValid(success, alipay, 20, emptyList(), false, 900))
        }
    }

    @Test fun wechatPaymentCodeUsesSourceAfterPageSwitchWithEmptyRootAndEventText() {
        // 诊断时序：付款码页 -> 支付中弹窗 -> Lite 成功页；文字仅在 source.desc 中。
        val policy = PaymentDetailPolicy()
        policy.observeWindow(wechat, "com.tencent.mm.plugin.offline.ui.WalletOfflineEntranceUI", 1)
        policy.observeForeground(wechat, 2)
        val texts = listOf("支付成功", "测试商店", "￥8.70", "完成")
        assertNull(policy.claimPaymentResult(wechat, 2, texts, false, 100))
        policy.observeWindow(wechat, "com.tencent.mm.plugin.lite.ui.WxaLiteAppLiteUI", 2)
        assertNull(policy.claimTree(wechat, 2, texts, false, 120, "event_source"))
        val candidate = requireNotNull(policy.claimPaymentResult(wechat, 2, texts, false, 120))
        assertTrue(candidate.isPaymentResult)
        assertEquals("payment_source", candidate.evidence)
        // 后续根节点只有一个空节点，不使已就绪候选失效，也不重启等待。
        assertNull(policy.claimTree(wechat, 2, emptyList(), false, 400, "event_source"))
        assertNull(policy.claimPaymentResult(wechat, 2, texts, false, 500))
        assertEquals(120L, candidate.detectedAt)
        assertTrue(policy.isValid(candidate, wechat, 2, emptyList(), false, 820))
        policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 3)
        assertFalse(policy.isValid(candidate, wechat, 3, emptyList(), false, 900))
    }

    @Test fun alipayPaymentSourceDoesNotRequireDetailTitleOrTransactionTime() {
        val policy = PaymentDetailPolicy()
        policy.observeWindow(alipay, "com.eg.android.AlipayGphone.AlipayLogin", 4)
        val texts = listOf("支付成功￥3.60", "支付成功", "￥3.60", "测试便利店", "交易方式", "余额宝(转出资金付款)")
        assertFalse(PaymentDetailPolicy.inspectReadiness(texts, false).ready)
        val candidate = requireNotNull(policy.claimPaymentResult(alipay, 4, texts, false, 200))
        // 根中仍可有原付款码页面，不能要求它重新提供成功金额。
        assertTrue(policy.isValid(candidate, alipay, 4, listOf("收付款", "向商家付款", "二维码"), false, 900))
        assertNull(policy.claimPaymentResult(alipay, 4, texts, false, 1000))
        assertFalse(policy.isValid(candidate, alipay, 4, emptyList(), false,
            201L + ConfigCatalog.AUTO_ACCOUNTING_DETAIL_SIGNAL_MS))
    }

    @Test fun paymentSourceRejectsChatUnknownIdentityWrongWindowAndIncompletePayment() {
        val texts = listOf("支付成功", "￥8.70")
        val policy = PaymentDetailPolicy()
        assertNull(policy.claimPaymentResult(wechat, 1, texts, false, 0))
        policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 1)
        assertNull(policy.claimPaymentResult(wechat, 1, texts, false, 0))
        policy.observeForeground(wechat, 2)
        assertNull(policy.claimPaymentResult(wechat, 2, texts, false, 0))
        policy.observeWindow(wechat, "com.tencent.mm.plugin.lite.ui.WxaLiteAppLiteUI", 2)
        assertNull(policy.claimPaymentResult(wechat, 1, texts, false, 0))
        assertNull(policy.claimPaymentResult(alipay, 2, texts, false, 0))
        assertNull(policy.claimPaymentResult(wechat, 2, texts, true, 0))
        for (invalid in listOf(listOf("付款码"), listOf("支付成功"), listOf("支付中", "￥8.70"),
            texts + "输入密码", texts + "账单详情", texts + "退款记录", texts + "加载中", texts + "按住说话")) {
            assertNull(invalid.toString(), policy.claimPaymentResult(wechat, 2, invalid, false, 0))
        }
    }

    @Test fun sourceCandidateCannotRefillCurrentTimeAfterSwitchingToHistoricalDetail() {
        val policy = PaymentDetailPolicy()
        policy.observeWindow(alipay, "com.eg.android.AlipayGphone.AlipayLogin", 1)
        val success = requireNotNull(policy.claimPaymentResult(alipay, 1, listOf("支付成功", "￥9.80"), false, 0))
        assertFalse(policy.isValid(success, alipay, 1, readyTexts(), false, 700))
        val detailCandidate = requireNotNull(policy.claimTree(alipay, 1, readyTexts(), false, 800, "event_source"))
        assertFalse(detailCandidate.isPaymentResult)
        assertFalse(policy.isValid(success, alipay, 1, emptyList(), false, 900))
        assertTrue(policy.isValid(detailCandidate, alipay, 1, emptyList(), false, 1500))
        policy.observeForeground("com.android.launcher", 2)
        assertFalse(policy.isValid(detailCandidate, alipay, 1, emptyList(), false, 1550))
    }

    @Test fun refundRecordsAndExpiredTransfersUseDetailRouteWithOriginalTime() {
        for (status in listOf("过期已退还", "已全额退款")) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(wechat, web, 1)
            val texts = listOf("退款记录", "已退款7.30元", "2026年9月18日 15:52:25", "当前状态", status, "-7.30")
            assertNull(policy.claimPaymentResult(wechat, 1, texts, false, 0))
            val candidate = requireNotNull(policy.claimTree(wechat, 1, texts, false, 100, "event_source"))
            assertFalse(candidate.isPaymentResult)
            assertTrue(policy.isValid(candidate, wechat, 1, emptyList(), false, 800))
        }
        assertFalse(PaymentDetailPolicy.inspectReadiness(listOf("退款记录", "已退款7.30元"), false).ready)
        assertTrue(PaymentDetailPolicy.inspectReadiness(
            listOf("退款到账通知", "退款金额", "￥7.30", "退款时间", "2026-09-18 15:52:25"), false).ready)
    }

    @Test fun confirmedRefundIsIncomeAndRequiresItsOwnArrivalTime() {
        val refund = AccountingDraft(amount = "7.30", direction = "INCOME", merchant = "测试退款", category = "退款",
            paymentStatus = "REFUNDED", occurredAt = "2026-09-18T15:52:25", currency = "CNY",
            transactionId = "", transactionIdType = "UNKNOWN")
        assertTrue(AutomaticAccountingPolicy.acceptsDetailResult(listOf(refund), emptyList()))
        assertFalse(AutomaticAccountingPolicy.acceptsDetailResult(listOf(refund.copy(occurredAt = "")), emptyList()))
        assertFalse(AutomaticAccountingPolicy.acceptsDetailResult(listOf(refund.copy(paymentStatus = "REFUND_PENDING")), emptyList()))
        val input = requireNotNull(AccountingRecognitionMapper.automaticInput(refund, false))
        val entry = AccountingRecognitionMapper.build(refund, input, 0)
        assertEquals("INCOME", entry.direction)
        assertEquals(730L, entry.amountMinor)
        assertEquals(java.time.LocalTime.of(15, 52, 25), input.time)
        assertNull(AccountingRecognitionMapper.transactionKey(refund.channel, refund.transactionId, refund.direction))
    }

    @Test fun wechatRefundOpenedFromBillListWaitsForRefundNumberAmountAndTime() {
        for (amount in listOf("+0.08", "+29.60")) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(wechat, web, 40)
            assertNull(policy.claimTree(wechat, 40, listOf("全部账单", "转账-退款,收入${amount}元"), false, 0))
            policy.observeForeground(wechat, 41)
            policy.observeWindow(wechat, web, 41)
            val fields = listOf("转账-退款", amount, "退款状态", "已退款", "退款时间", "2026年9月18日 10:15:20",
                "退款方式", "零钱", "退款单号", "refund-example", "原订单", "查看原订单")
            assertNull(policy.claimTree(wechat, 41, fields - amount, false, 100, "event_source"))
            assertNull(policy.claimTree(wechat, 41, fields - "2026年9月18日 10:15:20", false, 200, "event_source"))
            val candidate = requireNotNull(policy.claimTree(wechat, 41, fields, false, 300, "event_source"))
            assertFalse(candidate.isPaymentResult)
            assertTrue(policy.isValid(candidate, wechat, 41, emptyList(), false, 1_000))
            assertNull(policy.claimTree(wechat, 41, fields, false, 1_100, "event_source"))
        }
    }

    @Test fun independentAlipayRefundUsesCreationTimeAcrossMerchants() {
        for (amount in listOf("6.20", "18.35")) {
            val fields = listOf("账单详情", "全部账单", "${amount}元", "退款成功", "退款方式", "余额宝",
                "关联记录", "查看原账单", "创建时间", "2026-09-18 09:15:20")
            val policy = PaymentDetailPolicy()
            policy.observeWindow(alipay, "com.alipay.mobile.nebulax.xriver.activity.XRiverActivity", 50)
            val candidate = requireNotNull(policy.claimTree(alipay, 50, fields, false, 100, "event_source"))
            assertFalse(candidate.isPaymentResult)
            assertTrue(policy.isValid(candidate, alipay, 50, fields, false, 800))
            assertNull(policy.claimTree(alipay, 50, fields, false, 900, "event_source"))
            assertNull(policy.claimPaymentResult(alipay, 50, fields, false, 900))
            val bill = AccountingDraft(amount = amount, direction = "INCOME", currency = "CNY", category = "退款",
                paymentStatus = "REFUNDED", occurredAt = "2026-09-18 09:15:20", zoneId = "UTC")
            assertTrue(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill), emptyList()))
            val input = requireNotNull(AccountingRecognitionMapper.automaticInput(bill, false))
            assertEquals(LocalDate.of(2026, 9, 18), input.date)
            assertEquals(java.time.LocalTime.of(9, 15, 20), input.time)
        }
    }

    @Test fun creationTimeDoesNotAuthorizeOriginalPaymentPendingRefundOrIncompletePage() {
        val fields = listOf("账单详情", "6.20元", "退款成功", "退款方式", "余额宝", "创建时间", "2026-09-18 09:15:20")
        val invalid = listOf(fields - "账单详情", fields - "退款方式", fields - "6.20元",
            fields - "创建时间", fields - "2026-09-18 09:15:20", fields + "加载中", fields + "按住说话") +
            listOf("已全额退款", "退款处理中", "退款申请", "交易成功", "等待付款").map { (fields - "退款成功") + it }
        for (texts in invalid) assertFalse(texts.toString(), PaymentDetailPolicy.inspectReadiness(texts, false).ready)
        assertFalse(PaymentDetailPolicy.inspectReadiness(fields, true).ready)
        assertFalse(PaymentDetailPolicy.inspectReadiness(listOf("账单详情", "支出6.20元", "已全额退款",
            "付款方式", "余额宝", "创建时间", "2026-09-18 09:15:20"), false).ready)
    }

    @Test fun recognizedDetailMarkersRequireAmountAndTimeBeforeTriggering() {
        for (pkg in listOf(wechat, alipay)) for (marker in listOf("账单详情", "交易详情", "交易单号：123", "商户订单号")) {
            val policy = PaymentDetailPolicy()
            assertNull(policy.claimTree(pkg, 1, listOf(marker), false, 0))
            val candidate = requireNotNull(policy.claimTree(pkg, 1, readyTexts(marker), false, 100))
            assertTrue(policy.isValid(candidate, pkg, 1, listOf(marker), false, 700))
            assertNull(policy.claimTree(pkg, 1, listOf(marker, "加载完成"), false, 800))
        }
    }

    @Test fun emptyTreeCanUseEventTextButNotUnknownPageOrWrongWindow() {
        val policy = PaymentDetailPolicy()
        assertNull(policy.claimEvent(wechat, 1, listOf("账单详情"), 0))
        policy.observeWindow(wechat, web, 1)
        assertNull(policy.claimEvent(wechat, 2, listOf("账单详情"), 100))
        assertNull(policy.claimEvent("com.android.systemui", 1, listOf("账单详情"), 100))
        assertNull(policy.claimEvent(wechat, 1, listOf("交易单号"), 100))
        val candidate = requireNotNull(policy.claimEvent(wechat, 1, readyTexts("交易单号"), 200))
        assertEquals("event_text", candidate.evidence)
        assertTrue(policy.isValid(candidate, wechat, 1, emptyList(), false, 900))
        assertNull(policy.claimEvent(wechat, 1, listOf("交易详情"), 1_000))
    }

    @Test fun verifiedTransferDetailClassStillWaitsForAmountAndTime() {
        val policy = PaymentDetailPolicy()
        policy.observeWindow(wechat, "com.tencent.mm.framework.app.UIPageFragmentActivity", 1)
        assertNull(policy.claimEvent(wechat, 1, emptyList(), 0))
        policy.observeWindow(wechat, detail, 2)
        assertNull(policy.claimEvent(wechat, 2, emptyList(), 100))
        val candidate = requireNotNull(policy.claimEvent(wechat, 2, completeFields, 200))
        assertEquals("known_page", candidate.evidence)
        assertTrue(policy.isValid(candidate, wechat, 2, emptyList(), false, 800))
    }

    @Test fun chatClassesAndInputTreesCannotTriggerEvenWithDetailWords() {
        for (page in listOf("com.tencent.mm.ui.chatting.ChattingUI", "com.tencent.mm.ui.LauncherUI")) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(wechat, page, 1)
            policy.observeWindow(wechat, "android.widget.FrameLayout", 1)
            assertNull(policy.claimEvent(wechat, 1, listOf("账单详情", "交易单号"), 0))
            assertNull(policy.claimTree(wechat, 1, listOf("账单详情", "交易单号"), false, 100))
            policy.observeWindow(wechat, "android.widget.FrameLayout", 2)
            assertNull(policy.claimTree(wechat, 2, listOf("交易单号"), false, 200))
            assertTrue(policy.isBlocked(wechat, 2, listOf("交易单号"), false))
        }
        val policy = PaymentDetailPolicy()
        assertNull(policy.claimTree(alipay, 1, listOf("交易单号"), true, 0))
        assertNull(policy.claimTree(alipay, 1, listOf("账单详情", "按住说话"), false, 0))
    }

    @Test fun listsDoNotTriggerAndReturningToListRearmsSameWebView() {
        val policy = PaymentDetailPolicy()
        policy.observeWindow(wechat, web, 1)
        assertNull(policy.claimTree(wechat, 1, listOf("全部账单", "收支统计"), false, 0))
        val first = requireNotNull(policy.claimTree(wechat, 1, readyTexts(), false, 100))
        assertNull(policy.claimTree(wechat, 1, listOf("收支统计", "交易记录"), false, 200))
        assertFalse(policy.isValid(first, wechat, 1, listOf("账单详情"), false, 300))
        val second = requireNotNull(policy.claimTree(wechat, 1, readyTexts("交易详情"), false, 400))
        assertNotEquals(first.visit, second.visit)
    }

    /** 用户截图中的可见字段组合，未保留商家、金额或交易单号值。 */
    @Test fun detailNavigationButtonsDoNotVetoWechatOrAlipayScreens() {
        val samples = listOf(
            wechat to listOf("全部账单", "当前状态", "支付成功", "支付时间", "交易单号", "商户单号"),
            alipay to listOf("账单详情", "全部账单", "交易成功", "支付时间", "查看往来记录")
        )
        for ((pkg, labels) in samples) {
            val texts = labels + completeFields
            val policy = PaymentDetailPolicy()
            val candidate = requireNotNull(policy.claimTree(pkg, 1, texts, false, 0))
            assertFalse(policy.isBlocked(pkg, 1, texts, false))
            assertTrue(policy.isValid(candidate, pkg, 1, texts, false, 700))
            assertTrue(policy.isValid(candidate, pkg, 1, texts, false, 800)) // 截图后同样放行。
            assertNull(policy.claimTree(pkg, 1, texts, false, 900)) // 重复刷新不再次触发。
            assertFalse(policy.isValid(candidate, pkg, 1, texts, true, 1_000))
            assertFalse(policy.isValid(candidate, pkg, 1, texts + "按住说话", false, 1_000))
        }
    }

    @Test fun detailEventOverridesPreviousListStateInSameWebView() {
        val policy = PaymentDetailPolicy()
        policy.observeWindow(wechat, web, 1)
        assertNull(policy.claimTree(wechat, 1, listOf("全部账单"), false, 0))
        val texts = listOf("交易单号", "全部账单", "交易记录", "收支统计") + completeFields
        val candidate = requireNotNull(policy.claimEvent(wechat, 1, texts, 100))
        assertTrue(policy.isValid(candidate, wechat, 1, texts, false, 800))
        assertTrue(policy.isValid(candidate, wechat, 1, emptyList(), false, 800))
        assertFalse(policy.isValid(candidate, wechat, 1, listOf("全部账单"), false, 800))
        assertNull(policy.claimEvent(wechat, 1, texts, 900))
    }

    @Test fun foregroundChangeExpiryAndDisableInvalidateCandidate() {
        val policy = PaymentDetailPolicy()
        policy.observeWindow(wechat, detail, 1)
        val candidate = requireNotNull(policy.claimEvent(wechat, 1, completeFields, 0))
        assertFalse(policy.isValid(candidate, alipay, 1, emptyList(), false, 700))
        assertFalse(policy.isValid(candidate, wechat, 2, emptyList(), false, 700))
        assertFalse(policy.isValid(candidate, wechat, 1, listOf("收支统计"), false, 700))
        assertFalse(policy.isValid(candidate, wechat, 1, emptyList(), true, 700))
        assertFalse(policy.isValid(candidate, wechat, 1, emptyList(), false, ConfigCatalog.AUTO_ACCOUNTING_DETAIL_SIGNAL_MS + 1L))
        policy.observeWindow(wechat, detail, 1)
        assertNull(policy.claimEvent(wechat, 1, emptyList(), 10_000)) // 刷新不能重新消费。
        policy.reset()
        assertFalse(policy.isValid(candidate, wechat, 1, emptyList(), false, 700))
    }

    @Test fun historicalDetailRequiresExactlyOneCompletedBillAndRealTime() {
        val bill = AccountingDraft(amount = "19.90", direction = "EXPENSE", currency = "CNY", merchant = "午餐",
            paymentStatus = "COMPLETED", occurredAt = "2024-01-02 12:30:00", zoneId = "UTC")
        assertTrue(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill), emptyList()))
        assertEquals(LocalDate.of(2024, 1, 2), requireNotNull(AccountingRecognitionMapper.automaticInput(bill, false)).date)
        assertFalse(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill.copy(occurredAt = "")), emptyList()))
        assertFalse(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill.copy(occurredAt = "不清楚")), emptyList()))
        assertFalse(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill, bill.copy(id = "second")), emptyList()))
        assertFalse(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill), listOf("另一条账单解析失败")))
        for (status in listOf("UNPAID", "CANCELLED", "FAILED", "REVIEW", "REFUND_PENDING")) {
            assertFalse(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill.copy(paymentStatus = status)), emptyList()))
        }
        // 支付完成现场仍可按原有规则使用当前时间，详情规则不改变这条链路。
        assertNotNull(AccountingRecognitionMapper.automaticInput(bill.copy(occurredAt = ""), true))
    }

    @Test fun slowWebDetailWaitsForLoadedSourceInsteadOfClickOrLoadingTimer() {
        val policy = PaymentDetailPolicy()
        policy.observeForeground(wechat, 2120)
        policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 2120)
        assertNull(policy.claimEvent(wechat, 2120, listOf("账单详情", "¥", "0.99"), 3_675))
        policy.observeForeground(wechat, 2155)
        policy.observeWindow(wechat, web, 2155)
        assertNull(policy.claimTree(wechat, 2155, listOf("账单详情", "加载中"), false, 9_587, "event_source"))
        assertNull(policy.claimTree(wechat, 2155, emptyList(), false, 13_000)) // 前台根始终为空。
        val loaded = listOf("全部账单", "交易单号", "商户单号", "支付成功", "-0.99", "支付时间", "2026年9月17日 23:39:36")
        val candidate = requireNotNull(policy.claimTree(wechat, 2155, loaded, false, 13_273, "event_source"))
        assertEquals(13_273L, candidate.detectedAt) // 有效期从内容就绪起算，不是点击起算。
        assertEquals("event_source", candidate.evidence)
        assertTrue(policy.isValid(candidate, wechat, 2155, emptyList(), false, 13_973))
        // 后续 source 只指向导航按钮时，不得清掉已消费的候选或重新发起请求。
        assertNull(policy.claimTree(wechat, 2155, listOf("全部账单"), false, 13_300, "event_source"))
        assertNull(policy.claimTree(wechat, 2155, loaded, false, 13_486, "event_source"))
        assertTrue(policy.isValid(candidate, wechat, 2155, emptyList(), false, 13_973))
        assertFalse(policy.isValid(candidate, wechat, 2155, listOf("加载中"), false, 13_973))
    }

    @Test fun miniProgramDetailIgnoresBackgroundLauncherEventAndWaitsBeyondTitle() {
        val policy = PaymentDetailPolicy()
        policy.observeForeground(wechat, 2157)
        policy.observeWindow(wechat, "com.tencent.mm.plugin.appbrand.ui.AppBrandPluginUI", 2157)
        // 实测 22.629s 的 Launcher 事件属于后方窗口，实际前台仍是 2157。
        policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 2120, wechat, 2157)
        assertNull(policy.claimTree(wechat, 2157, listOf("交易详情"), false, 23_056, "event_source"))
        assertTrue(policy.hasDetailContext(wechat)) // 标题阶段已是详情，不得退回支付现场入口提前截图。
        val loaded = listOf("交易详情", "-4.00", "支付成功", "转账时间", "2026年09月17日 19:32:21")
        val candidate = requireNotNull(policy.claimTree(wechat, 2157, loaded, false, 23_535, "event_source"))
        assertTrue(policy.isValid(candidate, wechat, 2157, emptyList(), false, 24_235))
        policy.observeForeground(wechat, 2120) // 真正返回聊天时取消。
        policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 2120)
        assertFalse(policy.isValid(candidate, wechat, 2157, emptyList(), false, 24_300))
        assertFalse(policy.hasDetailContext(wechat))
        assertNull(policy.claimTree(wechat, 2120, loaded, false, 24_300, "event_source"))
    }

    @Test fun readinessRequiresDateValueAndAmountAndNoLoadingIndicator() {
        for (texts in listOf(
            listOf("交易详情", "-6.00", "支付时间"),
            listOf("交易详情", "支付时间", "2026-09-17 12:30:00", "123456789012345678"),
            listOf("交易详情", "-6.00", "2026-09-17 12:30:00"),
            readyTexts() + "加载中")) {
            assertFalse(PaymentDetailPolicy.inspectReadiness(texts, false).ready)
        }
        assertTrue(PaymentDetailPolicy.inspectReadiness(readyTexts(), false).ready)
        assertTrue(PaymentDetailPolicy.inspectReadiness(listOf("账单详情", "10.00元(11.62港币)", "支付时间", "2026-09-17 22:12:57"), false).ready)
        assertTrue(PaymentDetailPolicy.inspectReadiness(listOf("账单详情", "￥35", "交易时间", "昨天 12:30"), false).ready)
        assertFalse(PaymentDetailPolicy.inspectReadiness(readyTexts(), true).ready)
    }

    @Test fun sourceLoadingDuringDebounceBlocksScreenshotEvenWhenActiveRootIsEmpty() {
        val policy = PaymentDetailPolicy()
        val candidate = requireNotNull(policy.claimTree(wechat, 1, readyTexts(), false, 0, "event_source"))
        assertNull(policy.claimTree(wechat, 1, listOf("加载中"), false, 100, "event_source"))
        assertFalse(policy.isValid(candidate, wechat, 1, emptyList(), false, 700))
        assertNull(policy.claimTree(wechat, 1, readyTexts(), false, 800, "event_source"))
        assertTrue(policy.isValid(candidate, wechat, 1, emptyList(), false, 900))
    }

    /** 9 月 18 日收款诊断：加载后 source 提供转账单号、金额和时间，根节点仍无文字。 */
    @Test fun qrReceiptSourceTriggersWithoutBillTitleAndDoesNotDependOnSpecificAmount() {
        for (amount in listOf("+7.25", "+96.40", "+230.00")) {
            val policy = PaymentDetailPolicy()
            policy.observeForeground(wechat, 10)
            policy.observeWindow(wechat, web, 10)
            assertNull(policy.claimTree(wechat, 10, listOf("加载中"), false, 1_000, "event_source"))
            assertNull(policy.claimTree(wechat, 10, emptyList(), false, 2_000))
            val fields = listOf("当前状态", "已收钱", "收款方备注", "二维码收款", "收款时间",
                "2026年9月18日 10:20:30", "转账单号", "receipt-example", "账单服务", amount)
            val candidate = requireNotNull(policy.claimTree(wechat, 10, fields, false, 6_000, "event_source"))
            assertEquals("event_source", candidate.evidence)
            assertTrue(policy.hasDetailContext(wechat))
            assertTrue(policy.isValid(candidate, wechat, 10, emptyList(), false, 6_700))
            assertNull(policy.claimTree(wechat, 10, fields, false, 6_800, "event_source"))
            policy.observeForeground(wechat, 11)
            assertFalse(policy.isValid(candidate, wechat, 10, emptyList(), false, 6_900))
        }
    }

    @Test fun receiptListsAndIncompleteTransferDetailsDoNotTrigger() {
        val receipt = listOf("转账单号", "已收钱", "+7.25", "收款时间", "2026年9月18日 10:20:30")
        for (texts in listOf(
            listOf("全部账单", "收支统计", "二维码收款，收入7.25元"),
            receipt - "+7.25",
            receipt - "2026年9月18日 10:20:30",
            receipt + "加载中",
            receipt + "按住说话")) {
            assertFalse(texts.toString(), PaymentDetailPolicy.inspectReadiness(texts, false).ready)
        }
        assertFalse(PaymentDetailPolicy.inspectReadiness(receipt, true).ready)
    }

    /** 支付宝截图中的字段组合；金额和日期使用独立样例，不依赖用户的实际交易。 */
    @Test fun completedAlipayQrReceiptAcceptsCreationTimeInBillDetail() {
        for (amount in listOf("+6.80", "+87.30")) {
            val texts = listOf("账单详情", amount, "交易成功", "创建时间", "2026-08-12 09:15:20",
                "商品说明", "收钱码收款", "收入")
            val policy = PaymentDetailPolicy()
            val candidate = requireNotNull(policy.claimTree(alipay, 20, texts, false, 0))
            assertTrue(policy.isValid(candidate, alipay, 20, texts, false, 700))
            assertTrue(policy.hasDetailContext(alipay))
            assertNull(policy.claimTree(alipay, 20, texts, false, 800))
            assertFalse(AutomaticAccountingPolicy.matchesScreen(alipay, texts, false))
        }
    }

    @Test fun creationTimeDoesNotMakeUnpaidOrIncompleteReceiptsReady() {
        val texts = listOf("账单详情", "+6.80", "交易成功", "创建时间", "2026-08-12 09:15:20", "收钱码收款")
        for (incomplete in listOf(
            texts - "账单详情",
            texts - "收钱码收款",
            texts - "交易成功",
            (texts - "交易成功") + "等待付款",
            (texts - "交易成功") + "交易失败",
            texts - "+6.80",
            texts - "2026-08-12 09:15:20",
            texts + "加载中")) {
            assertFalse(incomplete.toString(), PaymentDetailPolicy.inspectReadiness(incomplete, false).ready)
        }
        assertFalse(PaymentDetailPolicy.inspectReadiness(texts, true).ready)
    }

    @Test fun alipayReceiptStatisticsCanTriggerAsOneBillWithoutTransactionTime() {
        val texts = listOf("经营分析", "日报", "2026年08月12日", "收款概览", "收钱码", "收款金额",
            "¥6.80", "退款0笔 共计¥0.00", "收款笔数", "1", "顾客人数", "1", "每笔均价", "¥6.80")
        assertTrue(PaymentDetailPolicy.inspectReadiness(texts, false).ready)
        val policy = PaymentDetailPolicy()
        val candidate = requireNotNull(policy.claimTree(alipay, 20, texts, false, 0))
        assertTrue(policy.isValid(candidate, alipay, 20, texts, false, 700))
        assertNull(policy.claimTree(alipay, 20, texts, false, 800))
        assertFalse(AutomaticAccountingPolicy.matchesScreen(alipay, texts, false))
    }

    @Test fun wechatMiniProgramReceiptSummaryUsesImmediateSourceWithoutRootText() {
        for (count in listOf(1, 3)) {
            val policy = PaymentDetailPolicy()
            policy.observeWindow(wechat, "com.tencent.mm.plugin.appbrand.ui.AppBrandUI00", 30)
            val texts = listOf("收款记录，今日收款${count}笔，共计86.50元元")
            val candidate = requireNotNull(policy.claimTree(wechat, 30, texts, false, 0, "event_source"))
            assertEquals("event_source", candidate.evidence)
            assertTrue(policy.hasDetailContext(wechat))
            assertTrue(policy.isValid(candidate, wechat, 30, emptyList(), false, 700))
            assertNull(policy.claimTree(wechat, 30, texts, false, 800, "event_source"))
            policy.observeForeground(wechat, 31)
            assertFalse(policy.isValid(candidate, wechat, 30, emptyList(), false, 900))
        }
    }

    @Test fun receiptSummaryStillRequiresContextAmountAndDateAndRejectsChatAndLoading() {
        val summary = listOf("收款概览", "收款金额", "¥86.50", "收款笔数", "3", "2026年08月12日")
        for (incomplete in listOf(summary - "收款概览", summary - "收款金额", summary - "收款笔数",
            summary - "¥86.50", summary - "2026年08月12日", summary + "加载中", summary + "按住说话")) {
            assertFalse(incomplete.toString(), PaymentDetailPolicy.inspectReadiness(incomplete, false).ready)
        }
        assertFalse(PaymentDetailPolicy.inspectReadiness(summary, true).ready)
        val policy = PaymentDetailPolicy()
        policy.observeWindow(wechat, "com.tencent.mm.ui.LauncherUI", 30)
        assertNull(policy.claimTree(wechat, 30, listOf("收款记录，今日收款3笔，共计86.50元元"), false, 0, "event_source"))
    }

    @Test fun receiptSummaryUsesExistingSingleBillValidationAndKeepsStatisticsDate() {
        val bill = AccountingDraft(amount = "86.50", direction = "INCOME", currency = "CNY", merchant = "支付宝收款汇总",
            note = "收款汇总，2026年08月12日，3笔", paymentStatus = "COMPLETED", occurredAt = "2026-08-12 00:00:00", zoneId = "UTC")
        assertTrue(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill), emptyList()))
        val input = requireNotNull(AccountingRecognitionMapper.automaticInput(bill, false))
        assertEquals(LocalDate.of(2026, 8, 12), input.date)
        assertEquals(java.time.LocalTime.MIDNIGHT, input.time)
        assertEquals("86.50", input.amount)
        assertFalse(AutomaticAccountingPolicy.acceptsDetailResult(listOf(bill, bill.copy(id = "average")), emptyList()))
        assertThrows(IllegalArgumentException::class.java) {
            AccountingRecognitionMapper.build(bill, input.copy(amount = "0.00"), 0)
        }
    }
}
