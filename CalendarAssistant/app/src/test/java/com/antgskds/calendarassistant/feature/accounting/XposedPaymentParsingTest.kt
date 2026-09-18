package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionMapper
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.PaymentMessageParser
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** 按上游公开字段构造脱敏协议样例；不是实机兼容性测试。 */
class XposedPaymentParsingTest {
    private val now = Instant.parse("2026-09-18T06:40:00Z").toEpochMilli()
    private val wx = AutomaticAccountingPolicy.WECHAT
    private val ali = AutomaticAccountingPolicy.ALIPAY
    private fun local(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
    private fun envelope(kind: String, data: JsonObject) = buildJsonObject { put("kind", kind); put("data", data) }.toString()
    private fun xmlEnvelope(xml: String, kind: String = "wechat_payment", send: Int = 0, time: Long = now) = buildJsonObject {
        put("kind", kind); put("xml", xml); put("isSend", send); put("createdAt", time)
    }.toString()
    private fun line(key: String, value: String) = "<line><value><word>$value</word></value><key><word>$key</word></key></line>"
    private fun card(title: String, fields: String, display: String = "", id: String = "") = """<msg><appmsg><title>卡片消息</title>
        <mmreader><template_detail><lines>$fields</lines></template_detail><template_header><title>$title</title>
        <display_name>$display</display_name><transaction_id>$id</transaction_id><pub_time>${now / 1000}</pub_time>
        </template_header></mmreader></appmsg></msg>"""

    @Test fun paymentCodeGetsMerchantFromHeaderAndDoesNotRejectPaymentMethod() {
        val xml = card("微信支付凭证", line("付款金额", "￥23.47") + line("付款方式", "余额宝"), "测试商店", "wx-pay-a")
        val bill = PaymentMessageParser.parse(wx, xmlEnvelope(xml), now).single()
        assertEquals("23.47", bill.amount); assertEquals("测试商店", bill.merchant)
        assertEquals("EXPENSE", bill.direction); assertEquals("wx-pay-a", bill.transactionId)
    }

    @Test fun receiptCardUsesThisPaymentAndNotDailyTotal() {
        val xml = card("收款到账通知", line("收款金额", "￥7.63") + line("汇总", "今日第8笔，共计￥987.65"))
        val bill = PaymentMessageParser.parse(wx, xmlEnvelope(xml), now).single()
        assertEquals("7.63", bill.amount); assertEquals("INCOME", bill.direction); assertEquals("微信收款", bill.merchant)
    }

    @Test fun formattedAmountsAreNeverTruncatedToAnotherAmount() {
        val formatted = card("微信支付凭证", line("付款金额", "￥1,234.56"), "测试商店")
        assertEquals("1234.56", PaymentMessageParser.parse(wx, xmlEnvelope(formatted), now).single().amount)
        for (amount in listOf("￥12.345", "￥1,23.45", "￥8.20USD", "￥8.20/9.20")) {
            val xml = card("微信支付凭证", line("付款金额", amount), "测试商店")
            assertTrue(amount, PaymentMessageParser.parse(wx, xmlEnvelope(xml), now).isEmpty())
        }
    }

    @Test fun appMessageFallbackRequiresRecentOriginalPublicationTime() {
        val xml = card("微信支付凭证", line("付款金额", "￥21.64"), "测试商户")
        fun payload(value: String) = buildJsonObject { put("kind", "wechat_payment"); put("xml", value) }.toString()
        assertEquals("21.64", PaymentMessageParser.parse(wx, payload(xml), now).single().amount)
        assertTrue(PaymentMessageParser.parse(wx, payload(xml.replace((now / 1000).toString(), ((now - 86_400_000) / 1000).toString())), now).isEmpty())
        assertTrue(PaymentMessageParser.parse(wx, payload(xml.replace(Regex("<pub_time>.*?</pub_time>"), "")), now).isEmpty())
    }

    private fun transfer(subtype: Int, send: Int = 1, start: Long = now, created: Long = now, fee: String = "￥18.36") = xmlEnvelope(
        """<msg><appmsg><type>2000</type><title>微信转账</title><wcpayinfo>
        <feedesc>$fee</feedesc><paysubtype>$subtype</paysubtype><transferid>transfer-one</transferid>
        <transcationid>different-internal-id</transcationid><begintransfertime>${start / 1000}</begintransfertime>
        </wcpayinfo></appmsg></msg>""", "wechat_transfer", send, created)

    @Test fun transferDirectionUsesMessageSubtypeRatherThanIsSendAlone() {
        val sent = PaymentMessageParser.parse(wx, transfer(1), now).single()
        val received = PaymentMessageParser.parse(wx, transfer(3), now).single()
        assertEquals("EXPENSE", sent.direction); assertEquals("INCOME", received.direction)
        assertEquals("transfer-one", sent.transactionId); assertEquals("18.36", sent.amount)
        assertTrue(PaymentMessageParser.parse(wx, transfer(1, send = 0), now).isEmpty())
        assertTrue(PaymentMessageParser.parse(wx, transfer(3, send = 0), now).isEmpty())
        assertTrue(PaymentMessageParser.parse(wx, transfer(4), now).isEmpty())
    }

    @Test fun acceptingOldTransferUsesConfirmationTimeButReplayedMessagesAreRejected() {
        val old = now - 3_600_000
        assertEquals(local(now), PaymentMessageParser.parse(wx, transfer(3, start = old), now).single().occurredAt)
        assertTrue(PaymentMessageParser.parse(wx, transfer(1, start = old), now).isEmpty())
        assertTrue(PaymentMessageParser.parse(wx, transfer(3, start = old, created = old), now).isEmpty())
        for (fee in listOf("18.36", "$18.36", "￥0.00", "￥-1.00", "￥18.369")) {
            assertTrue(fee, PaymentMessageParser.parse(wx, transfer(1, fee = fee), now).isEmpty())
        }
    }

    private fun detail(fee: String = "+18.36", status: String = "已存入零钱", time: Long = now) = buildJsonObject {
        put("ret_code", 0)
        put("header", buildJsonObject { put("fee", fee); put("nickname", "转账-来自测试用户") })
        put("preview", buildJsonArray {
            for ((label, value) in listOf("当前状态" to status, "收款时间" to (time / 1000).toString(),
                    "转账时间" to (time / 1000).toString(), "转账单号" to "transfer-one")) {
                add(buildJsonObject {
                    put("label", buildJsonObject { put("name", label) })
                    put("value", buildJsonArray { add(buildJsonObject {
                        put("name", value); put("is_timestamp", label.endsWith("时间"))
                    }) })
                })
            }
        })
    }

    @Test fun explicitlyOpenedHistoricalDetailKeepsOriginalTimeAndSameTransferIdentity() {
        val old = now - 86_400_000
        val detail = PaymentMessageParser.parse(wx, envelope("wechat_detail", detail(time = old)), now).single()
        assertEquals(local(old), detail.occurredAt); assertEquals("测试用户", detail.merchant)
        val message = PaymentMessageParser.parse(wx, transfer(3), now).single()
        assertEquals(AccountingRecognitionMapper.transactionKey(message.channel, message.transactionId, message.direction),
            AccountingRecognitionMapper.transactionKey(detail.channel, detail.transactionId, detail.direction))
    }

    @Test fun detailRequiresSignedAmountCompletedStateAndRealTime() {
        for (data in listOf(detail(fee = "18.36"), detail(status = "待收款"), detail(status = "退款成功"),
            detail(time = now + 1000), detail(fee = "-18.36", status = "已存入零钱"))) {
            assertTrue(PaymentMessageParser.parse(wx, envelope("wechat_detail", data), now).isEmpty())
        }
        assertEquals("EXPENSE", PaymentMessageParser.parse(wx,
            envelope("wechat_detail", detail(fee = "-18.36", status = "等待对方收款")), now).single().direction)
    }

    private fun redPacket(sender: Int = 0, status: Int = 2, amount: Long = 263, recordId: String = "receive-a", time: Long = now) = buildJsonObject {
        put("retcode", 0); put("isSender", sender); put("receiveStatus", status); put("amount", amount)
        put("receiveId", "receive-a"); put("changeWording", "已存入零钱，可直接消费")
        put("totalAmount", 98000); put("recAmount", 80000)
        put("record", buildJsonArray {
            add(buildJsonObject { put("receiveId", "someone-else"); put("receiveAmount", 77737); put("state", 1); put("receiveTime", time / 1000) })
            add(buildJsonObject { put("receiveId", recordId); put("receiveAmount", 263); put("state", 1); put("receiveTime", time / 1000) })
        })
    }

    @Test fun redPacketUsesOnlyOwnConfirmedRecordAndConvertsFenExactly() {
        val bill = PaymentMessageParser.parse(wx, envelope("wechat_red_packet", redPacket()), now).single()
        assertEquals("2.63", bill.amount); assertEquals("receive-a", bill.transactionId); assertEquals("INCOME", bill.direction)
        for (data in listOf(redPacket(sender = 1), redPacket(status = 0), redPacket(amount = 98000),
                redPacket(recordId = "another-person"), redPacket(time = now - 86_400_000))) {
            assertTrue(PaymentMessageParser.parse(wx, envelope("wechat_red_packet", data), now).isEmpty())
        }
    }

    @Test fun wechatRefundUsesRefundAmountAndDoesNotReuseOriginalPaymentId() {
        val xml = card("退款到账通知", line("退款金额", "￥6.28") + line("付款金额", "￥45.00") +
            line("商品详情", "交通卡充值") + line("商户名称", "测试商户") + line("成功时间", local(now)), id = "original-payment")
        val bill = PaymentMessageParser.parse(wx, xmlEnvelope(xml), now).single()
        assertEquals("6.28", bill.amount); assertEquals("REFUNDED", bill.paymentStatus)
        assertEquals("INCOME", bill.direction); assertEquals("", bill.transactionId)
    }

    private fun aliTrade(status: String, extra: Map<String, String> = emptyMap(), id: String = "ali-tx") = buildJsonObject {
        put("gmtCreate", now); put("money", "16.82"); put("unit", "元"); put("status", status); put("failTip", "")
        put("date", "09月18日"); put("goto", "alipays://trade?tradeNO=$id&bizType=TRADE")
        put("content", buildJsonArray {
            for ((title, content) in extra) add(buildJsonObject { put("title", "$title："); put("content", content) })
        })
    }

    @Test fun alipayCodeCollectionIgnoresDailySummaryAndUsesPayer() {
        val payload = aliTrade("二维码收款到账通知", mapOf("今日汇总" to "累计收款金额999.99元", "付款人" to "顾客甲"))
        val bill = PaymentMessageParser.parse(ali, payload.toString(), now).single()
        assertEquals("16.82", bill.amount); assertEquals("顾客甲", bill.merchant); assertEquals(local(now), bill.occurredAt)
    }

    @Test fun alipayPaymentSupportsBalanceFundAndUtilityTopupDescription() {
        for (status in listOf("付款成功", "自动扣款成功", "转账成功")) {
            val payload = aliTrade(status, mapOf("付款方式" to "余额宝", "交易对象" to "测试运营商", "商品说明" to "话费充值"))
            assertEquals("EXPENSE", PaymentMessageParser.parse(ali, payload.toString(), now).single().direction)
        }
    }

    @Test fun alipayRefundRequiresArrivalAndUsesIndependentRefundNumber() {
        val payload = aliTrade("退款到账通知", mapOf("退款去向" to "银行卡 入账时间${local(now)}", "退款说明" to "退款-车票"), "ali-tx_Mrefund1")
        val bill = PaymentMessageParser.parse(ali, payload.toString(), now).single()
        assertEquals("REFUNDED", bill.paymentStatus); assertEquals("ali-tx_Mrefund1", bill.transactionId)
        assertEquals("16.82", bill.amount)
        assertTrue(PaymentMessageParser.parse(ali, aliTrade("退款处理中").toString(), now).isEmpty())
        assertTrue(PaymentMessageParser.parse(ali, aliTrade("待付款").toString(), now).isEmpty())
        val originalId = PaymentMessageParser.parse(ali, aliTrade("退款到账通知").toString(), now).single()
        assertEquals("", originalId.transactionId)
    }
}
