package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.*
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.platform.receiver.AccountingMessageAccessPolicy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AccountingMessageRulesTest {
    private val now = LocalDateTime.of(2026, 11, 3, 22, 0)
    private val timestamp = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val defaults = AccountingMessageRules.defaults()
    private fun notice(body: String) = AccountingMessage(AccountingMessageKind.NOTIFICATION,
        "com.eg.android.AlipayGphone", body, timestamp, "交易提醒")

    @Test fun expenseDoesNotUseCouponAmountAndEntersExistingIngestMapper() {
        for (amount in listOf("10.73", "9.01", "67.89")) {
            val draft = AccountingMessageRules.parse(notice("你有一笔${amount}元的支出，点击领0.5元话费券。"), defaults)!!
            assertEquals(amount, draft.amount)
            assertEquals("EXPENSE", draft.direction)
            assertEquals(now, LocalDateTime.parse(draft.occurredAt))
            assertNotNull(AccountingRecognitionMapper.automaticInput(draft, false))
        }
    }

    @Test fun refundIsIncomeAndOnlyTransactionAmountIsCaptured() {
        val draft = AccountingMessageRules.parse(notice("你收到一笔9.01元退款，点此查看账单详情！"), defaults)!!
        assertEquals("9.01", draft.amount)
        assertEquals("INCOME", draft.direction)
        assertEquals("REFUNDED", draft.paymentStatus)
    }

    @Test fun unknownOrWrongSourceIsSilent() {
        val message = notice("你有一笔8.50元的支出，点击领1元话费券。")
        assertNull(AccountingMessageRules.parse(message.copy(sender = "com.example.chat"), defaults))
        assertNull(AccountingMessageRules.parse(message.copy(kind = AccountingMessageKind.SMS), defaults))
        for (body in listOf("本月累计支出100元", "支付失败", "领取50元红包", "你有一笔0元的支出", "你有一笔8.501元的支出")) {
            assertNull(body, AccountingMessageRules.parse(notice(body), defaults))
        }
    }

    @Test fun ccbExamplesReadTransactionInsteadOfBalanceAndCardNumber() {
        for ((direction, word) in listOf("EXPENSE" to "支出", "INCOME" to "收入")) {
            val message = AccountingMessage(AccountingMessageKind.SMS, "95533",
                "您尾号0956的储蓄卡11月3日21时54分银联${word}人民币1,400.00元,活期余额2348.62元。[建设银行]", timestamp)
            val draft = AccountingMessageRules.parse(message, defaults)!!
            assertEquals("1400.00", draft.amount)
            assertEquals(direction, draft.direction)
            assertEquals("建设银行", draft.channel)
            assertEquals(now.withHour(21).withMinute(54), LocalDateTime.parse(draft.occurredAt))
            assertNotNull(AccountingRecognitionMapper.automaticInput(draft, false))
            assertNull(AccountingMessageRules.parse(message.copy(sender = "13800138000"), defaults))
        }
    }

    @Test fun yearlessSmsUsesPreviousYearAtNewYearAndInvalidDateIsRejected() {
        val message = AccountingMessage(AccountingMessageKind.SMS, "95533",
            "您尾号1234的储蓄卡12月31日23时59分消费支出人民币18.99元,活期余额500.00元。[建设银行]",
            LocalDateTime.of(2027, 1, 1, 0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals("2026-12-31T23:59:00", AccountingMessageRules.parse(message, defaults)!!.occurredAt)
        assertNull(AccountingMessageRules.parse(message.copy(body = message.body.replace("12月31", "2月30")), defaults))
    }

    @Test fun userEditedRulesAndDisableAreUsedWithoutAiFallback() {
        val message = notice("收款成功：29.80元")
        assertNull(AccountingMessageRules.parse(message, defaults))
        val custom = defaults.first().copy(pattern = "收款成功：(?<amount>[0-9.]+)元", direction = "INCOME")
        assertEquals("29.80", AccountingMessageRules.parse(message, listOf(custom))!!.amount)
        assertNull(AccountingMessageRules.parse(message, listOf(custom.copy(enabled = false))))
        assertNull(AccountingMessageRules.parse(message, emptyList()))
        assertNotNull(AccountingMessageRules.validate(custom.copy(pattern = "(")))
        assertNotNull(AccountingMessageRules.validate(custom.copy(pattern = "金额([0-9]+)")))
    }

    @Test fun invalidCapturedTimeAndMoneyDoNotFallBackToNow() {
        val rule = defaults.first().copy(pattern = "金额(?<amount>[^;]+);时间(?<time>.+)")
        for (body in listOf("金额9.99;时间无效", "金额999999999999999999999;时间2026-11-03 12:00:00",
                "金额-9.99;时间2026-11-03 12:00:00")) {
            assertNull(AccountingMessageRules.parse(notice(body), listOf(rule)))
        }
    }

    @Test fun oldSettingsAreOffAndEveryGateIsRequired() {
        assertFalse(Json.decodeFromString<MySettings>("{}").accountingMessagesEnabled)
        val enabled = MySettings(automaticAccountingEnabled = true, accountingMessagesEnabled = true)
        assertTrue(Json.decodeFromString<MySettings>(Json.encodeToString(enabled)).accountingMessagesEnabled)
        for (a in listOf(false, true)) for (m in listOf(false, true))
            for (n in listOf(false, true)) for (s in listOf(false, true)) {
                assertEquals(a && m && n && s, AccountingMessageAccessPolicy.allows(a, m, n, s))
            }
    }

    @Test fun capturedIdentityAndRulePriorityArePreserved() {
        val rule = defaults.first().copy(pattern = "向(?<merchant>.+)支付(?<amount>[0-9.]+)元，交易号(?<transactionId>[0-9]+)")
        val message = notice("向食堂支付35.20元，交易号1234567890123")
        val draft = AccountingMessageRules.parse(message, listOf(rule, rule.copy(direction = "INCOME")))!!
        assertEquals("食堂", draft.merchant)
        assertEquals("1234567890123", draft.transactionId)
        assertEquals("PAYMENT", draft.transactionIdType)
        assertEquals("EXPENSE", draft.direction)
    }
}
