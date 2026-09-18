package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.PaymentMessageParser
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AutomaticAccountingTest {
    private val now = Instant.parse("2026-09-17T11:32:00Z").toEpochMilli()
    private val localTime = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
    private val wechat = AutomaticAccountingPolicy.WECHAT
    private val alipay = AutomaticAccountingPolicy.ALIPAY
    private fun envelope(xml: String, time: Long = now) = buildJsonObject {
        put("kind", "wechat_payment"); put("createdAt", time); put("xml", xml)
    }.toString()
    private fun plainXml(amount: String = "￥35.20", status: String = "支付成功") =
        "<msg><appmsg><title>$status</title><des><![CDATA[付款金额：$amount\n收款方：餐厅\n交易单号：tx1]]></des></appmsg></msg>"

    @Test fun oldSettingsDefaultOffAndNewSwitchRoundTrips() {
        assertFalse(AutomaticAccountingPolicy.enabled(Json.decodeFromString<MySettings>("{}")))
        val enabled = MySettings(automaticAccountingEnabled = true)
        assertTrue(AutomaticAccountingPolicy.enabled(Json.decodeFromString<MySettings>(Json.encodeToString(enabled))))
    }

    @Test fun onlyCompletedPaymentWindowsTrigger() {
        val texts = listOf("支付成功", "￥35.20", "餐厅")
        assertTrue(AutomaticAccountingPolicy.matchesScreen(wechat, texts, false))
        assertTrue(AutomaticAccountingPolicy.matchesScreen(alipay, listOf("收款成功", "200.00元"), false))
        assertFalse(AutomaticAccountingPolicy.matchesScreen("com.example.chat", texts, false))
        assertFalse(AutomaticAccountingPolicy.matchesScreen(wechat, texts, true))
        for (extra in listOf("全部账单", "交易详情", "收支统计", "待付款", "输入密码")) {
            assertFalse(extra, AutomaticAccountingPolicy.matchesScreen(wechat, texts + extra, false))
        }
        assertFalse(AutomaticAccountingPolicy.matchesScreen(wechat, listOf("支付成功"), false))
        assertFalse(AutomaticAccountingPolicy.matchesScreen(wechat, listOf("确认付款", "35.20元"), false))
    }

    @Test fun debounceGateRetainsAmountAndLimitsRepeatedRequests() {
        val policy = AutomaticAccountingPolicy()
        assertTrue(policy.reserve(wechat, "支付成功35元", 0))
        assertFalse(policy.reserve(wechat, "支付成功36元", 1_000))
        assertFalse(policy.reserve(wechat, "支付成功35元", 20_000))
        assertTrue(policy.reserve(wechat, "支付成功36元", 20_000))
        assertTrue(policy.reserve(wechat, "支付成功35元", 300_000))
    }

    @Test fun screenDiagnosticsRetainAllBlockersWithoutChangingEligibility() {
        val check = AutomaticAccountingPolicy.inspectScreen(wechat, listOf("交易详情", "等待对方收款", "0.01元"), true)
        assertFalse(check.eligible)
        assertTrue(check.editable)
        assertEquals("交易详情", check.excludedMarker)
        assertFalse(check.success)
        assertTrue(check.amount)
        // 未暴露成功文字/金额的图片节点只能报告缺失，不能据此声称页面上没有交易。
        val imageOnly = AutomaticAccountingPolicy.inspectScreen(wechat, emptyList(), false)
        assertFalse(imageOnly.success)
        assertFalse(imageOnly.amount)
        assertFalse(imageOnly.eligible)
    }

    @Test fun reservationDiagnosticsDistinguishCooldownFromRepeatedPage() {
        val policy = AutomaticAccountingPolicy()
        assertEquals(AutomaticAccountingPolicy.Reservation.ACCEPTED, policy.reserveWithReason(wechat, "页面 A", 0))
        assertEquals(AutomaticAccountingPolicy.Reservation.MIN_INTERVAL, policy.reserveWithReason(wechat, "页面 B", 1_000))
        assertEquals(AutomaticAccountingPolicy.Reservation.REPEATED_PAGE, policy.reserveWithReason(wechat, "页面 A", 20_000))
        assertEquals(AutomaticAccountingPolicy.Reservation.ACCEPTED, policy.reserveWithReason(wechat, "页面 B", 20_000))
    }

    @Test fun paymentServiceMessageUsesExactAmountAndMessageTimestamp() {
        val bill = PaymentMessageParser.parse(wechat, envelope(plainXml()), now).single()
        assertEquals("35.20", bill.amount)
        assertEquals("餐厅", bill.merchant)
        assertEquals("EXPENSE", bill.direction)
        assertEquals("tx1", bill.transactionId)
        assertEquals("PAYMENT", bill.transactionIdType)
        assertEquals(localTime, bill.occurredAt)
    }

    @Test fun reversedWechatTemplateFieldsAndCounterpartyReceivedStatusAreParsed() {
        val xml = """<msg><appmsg><title>微信支付凭证</title><mmreader><template_detail><line_content>
            <topline><value><word>￥4.00</word></value><key><word>付款金额</word></key></topline>
            <lines><line><value><word>小唐</word></value><key><word>收款方</word></key></line>
            <line><value><word>支付成功，对方已收款</word></value><key><word>交易状态</word></key></line></lines>
            </line_content></template_detail><template_header><transaction_id>payment123</transaction_id>
            <pub_time>${now / 1000}</pub_time></template_header></mmreader></appmsg></msg>"""
        val bill = PaymentMessageParser.parse(wechat, envelope(xml), now).single()
        assertEquals("4.00", bill.amount); assertEquals("小唐", bill.merchant)
        assertEquals("EXPENSE", bill.direction); assertEquals("payment123", bill.transactionId)
    }

    @Test fun unsafeIncompleteAndReplayedMessagesNeverAutoEnterLedger() {
        for (payload in listOf(envelope(plainXml(status = "待付款")), envelope(plainXml(status = "退款成功")),
            envelope(plainXml(), now - 600_001), envelope(plainXml(amount = "35.20")),
            envelope(plainXml(amount = "USD35.20")), envelope(plainXml(amount = "￥0.00")),
            envelope("<!DOCTYPE msg [<!ENTITY data SYSTEM 'file:///test'>]><msg><appmsg>&data;</appmsg></msg>"), "not json")) {
            assertTrue(PaymentMessageParser.parse(wechat, payload, now).isEmpty())
        }
        assertTrue(PaymentMessageParser.parse("com.example", envelope(plainXml()), now).isEmpty())
    }

    private fun alipayMessage(amount: String, merchant: String) = buildJsonObject {
        put("gmtCreate", now)
        put("title", "支付成功")
        put("content", "付款金额：${amount}元\n收款方：$merchant\n支付时间：$localTime")
    }

    @Test fun nestedAlipayMessagesStaySeparateAndNoTimeDoesNotDefaultToNow() {
        val payload = buildJsonArray {
            add(buildJsonObject { put("pl", alipayMessage("8.80", "早餐店").toString()) })
            add(alipayMessage("19.90", "超市"))
        }.toString()
        val bills = PaymentMessageParser.parse(alipay, payload, now)
        assertEquals(listOf("8.80", "19.90"), bills.map { it.amount })
        assertEquals(listOf("早餐店", "超市"), bills.map { it.merchant })
        val noTime = """{"title":"支付成功","content":"付款金额：8.80元\n收款方：早餐店"}"""
        assertTrue(PaymentMessageParser.parse(alipay, noTime, now).isEmpty())
    }

    @Test fun alipayTradeTemplateCarriesDirectionTimeAndPlatformNumber() {
        val payload = buildJsonObject {
            put("money", "125.00"); put("unit", "元"); put("status", "收到一笔转账")
            put("goto", "alipays://trade?tradeNO=20260917001&bizType=D_TRANSFER")
            put("content", buildJsonArray {
                add(buildJsonObject { put("title", "付款方："); put("content", "小明") })
                add(buildJsonObject { put("title", "到账时间："); put("content", localTime) })
            })
        }
        val bill = PaymentMessageParser.parse(alipay, payload.toString(), now).single()
        assertEquals("125.00", bill.amount); assertEquals("INCOME", bill.direction)
        assertEquals("小明", bill.merchant); assertEquals("20260917001", bill.transactionId)
        assertTrue(PaymentMessageParser.parse(alipay, payload.toString().replace("收到一笔转账", "待收款"), now).isEmpty())
    }

    @Test fun conflictingAmountsAndAmbiguousDirectionsAreNotCombined() {
        val xml = plainXml().replace("交易单号：tx1", "交易金额：￥99.00")
        assertTrue(PaymentMessageParser.parse(wechat, envelope(xml), now).isEmpty())
        assertTrue(PaymentMessageParser.parse(wechat, envelope(plainXml(status = "收款成功 支付成功")), now).isEmpty())
    }
}
