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
