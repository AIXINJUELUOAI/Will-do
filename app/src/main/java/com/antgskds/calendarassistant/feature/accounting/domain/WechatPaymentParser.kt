package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.serialization.json.*
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory

/** 参考 AutoAccounting 的支付协议，独立解析；不借用其他交易的金额缓存。 */
object WechatPaymentParser {
    fun parse(envelope: JsonObject, receivedAt: Long): AccountingDraft? = when (envelope.string("kind")) {
        "wechat_transfer" -> transfer(envelope, receivedAt)
        "wechat_detail" -> (envelope["data"] as? JsonObject)?.let { detail(it, receivedAt) }
        "wechat_red_packet" -> (envelope["data"] as? JsonObject)?.let { redPacket(it, receivedAt) }
        else -> null
    }

    private fun transfer(obj: JsonObject, now: Long): AccountingDraft? {
        val createdAt = PaymentMessageParser.epoch(obj["createdAt"]) ?: return null
        if (!recent(createdAt, now)) return null
        val xml = obj.string("xml")
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true))
        val doc = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
            .newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val appmsg = doc.getElementsByTagName("appmsg").item(0) as? Element ?: return null
        fun Element.text(tag: String) = getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
        if (appmsg.text("type") != "2000") return null
        val pay = appmsg.getElementsByTagName("wcpayinfo").item(0) as? Element ?: return null
        // isSend=1 的 subtype=3 是本人发送的收款确认，不是付款方又支出一次。
        if (obj["isSend"]?.jsonPrimitive?.intOrNull != 1) return null
        val income = when (pay.text("paysubtype")) { "1" -> false; "3" -> true; else -> return null }
        val id = pay.text("transferid").ifBlank { pay.text("paymsgid") }
        if (id.isBlank()) return null
        val fee = pay.text("feedesc")
        if (!Regex("[¥￥][0-9]+(?:\\.[0-9]{1,2})?").matches(fee)) return null
        // 收款确认可能晚于发起转账几个小时，不能把发起时间当成本人的到账时间。
        val time = if (income) PaymentMessageParser.epoch(JsonPrimitive(pay.text("receivetime"))) ?: createdAt
            else PaymentMessageParser.epoch(JsonPrimitive(pay.text("begintransfertime"))) ?: return null
        if (!recent(time, now)) return null
        return draft(fee.substring(1), income, "微信转账", time, id, now, "转账",
            "自动捕获${if (income) "已领取转账" else "已发出转账"}；消息未提供对方显示名称")
    }

    private fun detail(obj: JsonObject, now: Long): AccountingDraft? {
        if (obj["ret_code"]?.jsonPrimitive?.intOrNull != 0) return null
        val header = obj["header"] as? JsonObject ?: return null
        val fee = header.string("fee")
        if (!Regex("[+-][0-9]+(?:\\.[0-9]{1,2})?").matches(fee)) return null
        val fields = linkedMapOf<String, String>()
        for (row in (obj["preview"] as? JsonArray).orEmpty().take(ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES)) {
            val item = row as? JsonObject ?: continue
            val label = (item["label"] as? JsonObject)?.string("name").orEmpty()
            if (label.isBlank()) continue
            val values = (item["value"] as? JsonArray).orEmpty().mapNotNull { value ->
                val v = value as? JsonObject ?: return@mapNotNull null
                if (v["is_timestamp"]?.jsonPrimitive?.booleanOrNull == true) {
                    PaymentMessageParser.epoch(v["name"])?.let(::localTime)
                } else v.string("name")
            }.filter(String::isNotBlank)
            // 核心字段多值/重复冲突时不猜哪一个才是本次交易。
            if (values.size != 1) continue
            val previous = fields.put(label, values.single())
            if (previous != null && previous != values.single()) return null
        }
        val income = fee.startsWith('+')
        val status = fields["当前状态"] ?: fields["交易状态"] ?: return null
        val valid = if (income) status in setOf("已存入零钱", "已到账", "收款成功", "已收款", "交易成功")
            else status in setOf("支付成功", "付款成功", "转账成功", "交易成功", "对方已收款", "支付成功，对方已收款", "等待对方收款")
        // 退款详情含原付款额与退款额时不能仅按 header.fee 猜退款金额。
        if (!valid || fields.keys.any { it.contains("退款") }) return null
        val timeKeys = if (income) listOf("收款时间", "到账时间", "交易时间", "转账时间", "支付时间")
            else listOf("支付时间", "付款时间", "转账时间", "交易时间")
        val time = timeKeys.firstNotNullOfOrNull { fields[it] } ?: return null
        val id = listOf("转账单号", "交易单号", "交易订单号").firstNotNullOfOrNull { fields[it] } ?: return null
        val merchant = header.string("nickname").removePrefix("转账-来自").removePrefix("转账-给").ifBlank { "微信交易" }
        val text = "${if (income) "收款成功" else "付款成功"}\n交易金额：${fee.substring(1)}元\n交易对方：$merchant\n交易时间：$time\n交易单号：$id"
        // 主动打开详情可补记历史交易，保留真实时间；消息回放仍受十分钟时效约束。
        return PaymentMessageParser.parseText(text, AutomaticAccountingPolicy.WECHAT, null, now, historicalDetail = true)
    }

    private fun redPacket(obj: JsonObject, now: Long): AccountingDraft? {
        if (obj["retcode"]?.jsonPrimitive?.intOrNull != 0 || obj["isSender"]?.jsonPrimitive?.intOrNull != 0 ||
            obj["receiveStatus"]?.jsonPrimitive?.intOrNull != 2 || !obj.string("changeWording").contains("已存入零钱")) return null
        val receiveId = obj.string("receiveId").takeIf(String::isNotBlank) ?: return null
        // 群红包列表里有其他人的记录，必须与本人的 receiveId 相同。
        val record = (obj["record"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.string("receiveId") == receiveId }.singleOrNull() ?: return null
        if (record["state"]?.jsonPrimitive?.intOrNull != 1) return null
        val minor = record["receiveAmount"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
        if (obj["amount"]?.jsonPrimitive?.longOrNull != minor) return null
        val time = PaymentMessageParser.epoch(record["receiveTime"]) ?: return null
        if (!recent(time, now)) return null
        return draft(BigDecimal.valueOf(minor, 2).toPlainString(), true, "微信红包", time, receiveId, now,
            "红包", "自动捕获本人已领取红包；不含他人领取额")
    }

    private fun draft(amount: String, income: Boolean, merchant: String, time: Long, id: String, now: Long,
        category: String, note: String): AccountingDraft? {
        if (runCatching { AccountingEntryEditor.amountMinor(amount) }.isFailure) return null
        return AccountingDraft(amount = amount, direction = if (income) "INCOME" else "EXPENSE", currency = "CNY",
            merchant = merchant, category = category, note = note, occurredAt = localTime(time),
            zoneId = ZoneId.systemDefault().id, channel = "微信支付", transactionId = id,
            transactionIdType = "PAYMENT", paymentStatus = "COMPLETED", createdAt = now)
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun recent(time: Long, now: Long) = time in (now - ConfigCatalog.AUTO_ACCOUNTING_MAX_AGE_MS)..now
    private fun localTime(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
}
