package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.serialization.json.*
import java.io.StringReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * 将参考 Hook 点产生的微信支付 XML、支付宝同步 JSON 转成账单候选。
 * 仅匹配明确的完成状态和带单位的金额，未知结构返回空，不猜金额单位、不调用 AI。
 */
object PaymentMessageParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val amountPattern = Regex("(?:支付金额|付款金额|实付金额|消费金额|收款金额|到账金额|交易金额)[：:\\s]*([^\\r\\n]+)")
    private val fullTime = Regex("[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}[ T][0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?")

    fun parse(sourcePackage: String, payload: String, receivedAt: Long): List<AccountingDraft> = runCatching {
        if (!AutomaticAccountingPolicy.supports(sourcePackage) || payload.length > ConfigCatalog.AUTO_ACCOUNTING_MAX_PAYLOAD) return emptyList()
        val root = json.parseToJsonElement(payload)
        if (sourcePackage == AutomaticAccountingPolicy.WECHAT) {
            val obj = root as? JsonObject ?: return emptyList()
            if (obj["kind"]?.jsonPrimitive?.contentOrNull != "wechat_payment") {
                return listOfNotNull(WechatPaymentParser.parse(obj, receivedAt))
            }
            val xml = obj["xml"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
            val createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull
                ?: Regex("<pub_time>\\s*(?:<!\\[CDATA\\[)?([0-9]+)(?:]]>)?\\s*</pub_time>").find(xml)
                    ?.groupValues?.get(1)?.let { epoch(JsonPrimitive(it)) } ?: return emptyList()
            if (!recent(createdAt, receivedAt)) return emptyList()
            listOfNotNull(parseText(xmlText(xml), sourcePackage, createdAt, receivedAt))
        } else {
            var remaining = ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES
            fun walk(element: JsonElement, inheritedTime: Long?, depth: Int): List<AccountingDraft> {
                if (--remaining < 0 || depth > ConfigCatalog.AUTO_ACCOUNTING_MESSAGE_MAX_DEPTH) return emptyList()
                return when (element) {
                    is JsonArray -> element.flatMap { walk(it, inheritedTime, depth + 1) }
                    is JsonPrimitive -> {
                        val text = element.contentOrNull.orEmpty().trim()
                        if (text.startsWith("{") || text.startsWith("[")) {
                            runCatching { walk(json.parseToJsonElement(text), inheritedTime, depth + 1) }.getOrDefault(emptyList())
                        } else emptyList()
                    }
                    is JsonObject -> {
                        val timestamp = listOf("gmtCreate", "createTime", "msgTime", "gmt_create", "timestamp")
                            .firstNotNullOfOrNull { epoch(element[it]) } ?: inheritedTime
                        if ("money" in element && "unit" in element && "status" in element) {
                            return listOfNotNull(alipayTrade(element, timestamp, receivedAt))
                        }
                        val children = element.values.flatMap { walk(it, timestamp, depth + 1) }
                        if (children.isNotEmpty()) children else {
                            // 只合并当前对象的文本，不将多个嵌套交易拼成一条。
                            val text = element.entries.mapNotNull { (key, value) ->
                                val primitive = value as? JsonPrimitive ?: return@mapNotNull null
                                val content = primitive.contentOrNull.orEmpty()
                                if (content.trimStart().startsWith("{") || content.trimStart().startsWith("[")) null
                                else if (key in setOf("支付金额", "付款金额", "收款金额", "到账金额", "交易金额", "收款方", "付款方", "商户名称", "交易对方", "交易时间", "支付时间", "交易单号", "商户订单号")) "$key：$content"
                                else content
                            }.joinToString("\n")
                            listOfNotNull(parseText(text, sourcePackage, timestamp, receivedAt))
                        }
                    }
                }
            }
            walk(root, null, 0)
        }.distinctBy { listOf(it.amount, it.direction, it.merchant, it.occurredAt, it.transactionId) }
    }.getOrDefault(emptyList())

    private fun recent(time: Long, now: Long) = time in (now - ConfigCatalog.AUTO_ACCOUNTING_MAX_AGE_MS)..now

    /** 参考公开支付宝规则：money/unit/status + content 中的 title/content 字段对。 */
    private fun alipayTrade(obj: JsonObject, messageTime: Long?, receivedAt: Long): AccountingDraft? {
        fun value(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (value("unit") != "元" || value("failTip").isNotBlank()) return null
        val status = value("status")
        val refund = status in setOf("退款到账通知", "退款成功", "退款已到账")
        val income = refund || status in setOf("收到一笔转账", "二维码收款到账通知", "收款成功", "收款到账")
        val expense = status in setOf("付款成功", "支付成功", "自动扣款成功", "转账成功")
        if (!income && !expense) return null
        val lines = mutableListOf(if (refund) "退款到账通知" else if (income) "收款成功" else "付款成功",
            "${if (refund) "退款金额" else "交易金额"}：${value("money")}元")
        val pairs = (obj["content"] as? JsonArray).orEmpty()
        for (pair in pairs) {
            val field = pair as? JsonObject ?: continue
            val label = (field["title"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim().trimEnd('：', ':')
            val content = (field["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val key = when (label) { "交易对象", "对方账户", "户号", "申请人" -> if (income) "付款方" else "收款方"; else -> label }
            // 状态以结构化 status 为准，不把商品名、支付方式或备注当状态。
            if (key in setOf("付款方", "付款人", "转账方", "收款方", "收款人", "商户名称", "交易对方",
                    "到账时间", "交易时间", "付款时间", "支付时间", "退款时间")) lines += "$key：$content"
            if (refund && label == "退款去向") fullTime.find(content)?.value?.let { lines += "退款时间：$it" }
        }
        if (lines.none { fullTime.containsMatchIn(it) } && messageTime == null) {
            val date = value("date")
            if (date.isNotBlank()) lines += "交易时间：$date"
        }
        Regex("(?i)[?&]tradeNO=([a-z0-9_-]+)(?=&|#|$)").find(value("goto"))?.groupValues?.get(1)?.let {
            // 退款链接可能仍是原交易号，只有独立退款后缀可作本次退款身份。
            if (!refund || it.contains("_M")) lines += "交易单号：$it"
        }
        if (refund) lines += "交易对方：支付宝退款"
        return parseText(lines.joinToString("\n"), AutomaticAccountingPolicy.ALIPAY, messageTime, receivedAt)
    }

    internal fun epoch(element: JsonElement?): Long? {
        val value = (element as? JsonPrimitive)?.contentOrNull ?: return null
        val number = value.toLongOrNull()
        if (number != null && number > 0) return when (value.length) {
            10 -> number * 1000 // 协议秒时间戳；不将任意数字猜成时间。
            13 -> number
            else -> null
        }
        return parseDate(value)
    }

    private fun parseDate(value: String): Long? = runCatching {
        val cleaned = value.replace('/', '-').replace('T', ' ')
        val pattern = if (cleaned.count { it == ':' } == 1) "uuuu-M-d H:mm" else "uuuu-M-d H:mm:ss"
        LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern(pattern).withResolverStyle(java.time.format.ResolverStyle.STRICT))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    internal fun parseText(raw: String, pkg: String, messageTime: Long?, receivedAt: Long, historicalDetail: Boolean = false): AccountingDraft? {
        if (raw.isBlank() || raw.length > ConfigCatalog.AUTO_ACCOUNTING_MAX_TEXT) return null
        val text = raw.replace("\\n", "\n").replace("<br/>", "\n").replace("<br>", "\n")
        val state = text.lineSequence().filter { line ->
            (!line.contains('：') && !line.contains(':')) || listOf("交易状态", "当前状态", "支付状态").any { line.trimStart().startsWith(it) }
        }.joinToString("\n")
        val refund = listOf("退款到账通知", "退款已到账", "退款成功").any(state::contains)
        if (listOf("待收款", "待领取", "待付款", "等待付款", "支付失败", "付款失败", "交易关闭", "已撤销", "红包", "充值", "提现", "余额宝").any(state::contains)) return null
        if (state.contains("退款") && !refund) return null
        val income = refund || listOf("收款成功", "收钱到账", "收款到账", "转账到账").any(state::contains) || state.lineSequence().any { it.trim() == "已收款" }
        val expense = listOf("支付成功", "付款成功", "支付凭证", "付款凭证", "转账成功").any(state::contains)
        if (income == expense) return null // 状态不明或同时含收支，不能猜方向。
        val pattern = if (refund) Regex("退款金额[：:\\s]*([^\\r\\n]+)") else amountPattern
        val money = pattern.findAll(text).map { it.groupValues[1].trim() }.distinct().toList().singleOrNull() ?: return null
        if (money.none { it == '¥' || it == '￥' } && !money.contains("元") && !money.contains("CNY") && !money.contains("人民币")) return null
        val digits = money.replace(Regex("[¥￥\\s]|人民币|CNY|元"), "")
        if (!Regex("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?").matches(digits)) return null
        val amount = digits.replace(",", "")
        if (runCatching { AccountingEntryEditor.amountMinor(amount) }.isFailure) return null
        fun field(vararg labels: String): String = labels.firstNotNullOfOrNull { label ->
            Regex("(?:^|\\n)\\s*${Regex.escape(label)}[：:]\\s*([^\\r\\n]+)").find(text)?.groupValues?.get(1)?.trim()
        }.orEmpty()
        val merchant = if (income) field("付款方", "付款人", "转账方", "商户名称", "交易对方") else field("收款方", "收款人", "商户名称", "商家", "交易对方")
        val rawTime = if (refund) field("退款时间", "成功时间", "到账时间", "交易时间")
            else field("到账时间", "收款时间", "支付时间", "交易时间", "付款时间")
        val transactionTime = if (rawTime.isNotBlank()) fullTime.find(rawTime)?.value?.let(::parseDate) ?: return null else messageTime ?: return null
        if (transactionTime <= 0 || transactionTime > receivedAt || (!historicalDetail && !recent(transactionTime, receivedAt))) return null
        val paymentId = field("支付交易单号", "交易单号", "交易号", "交易订单号")
        val orderId = field("商户订单号", "商家订单号")
        val transaction = paymentId.ifBlank { orderId }
        val zone = ZoneId.systemDefault()
        return AccountingDraft(amount = amount, direction = if (income) "INCOME" else "EXPENSE", currency = "CNY",
            merchant = merchant, category = if (refund) "退款" else if (income) "收款" else "未分类", note = if (refund) "自动捕获到账退款，不冲销原支出" else "自动捕获支付信息",
            occurredAt = Instant.ofEpochMilli(transactionTime).atZone(zone).toLocalDateTime().toString(), zoneId = zone.id,
            channel = if (pkg == AutomaticAccountingPolicy.WECHAT) "微信支付" else "支付宝", transactionId = transaction,
            transactionIdType = when { paymentId.isNotBlank() -> "PAYMENT"; orderId.isNotBlank() -> "MERCHANT_ORDER"; else -> "UNKNOWN" },
            paymentStatus = if (refund) "REFUNDED" else "COMPLETED", createdAt = receivedAt)
    }

    private fun xmlText(xml: String): String {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true))
        val factory = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val appmsg = doc.getElementsByTagName("appmsg").item(0) as? org.w3c.dom.Element ?: return ""
        val texts = mutableListOf<String>()
        // 新版支付卡片以 key/word、value/word 保存字段，XML 顺序可能先 value 后 key。
        for (tag in listOf("topline", "line")) {
            val lines = appmsg.getElementsByTagName(tag)
            for (i in 0 until minOf(lines.length, ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES)) {
                val line = lines.item(i) as? org.w3c.dom.Element ?: continue
                fun word(field: String): String = (line.getElementsByTagName(field).item(0) as? org.w3c.dom.Element)
                    ?.getElementsByTagName("word")?.item(0)?.textContent?.trim().orEmpty()
                val key = word("key")
                val value = word("value")
                if (key.isNotBlank() && value.isNotBlank()) texts += "$key：$value"
            }
        }
        if (texts.isNotEmpty()) {
            val titles = appmsg.getElementsByTagName("title")
            for (i in 0 until minOf(titles.length, ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES)) titles.item(i)?.textContent?.let { texts += it }
            val refund = texts.any { it.contains("退款到账通知") }
            val idTag = if (refund) "refund_id" else "transaction_id"
            appmsg.getElementsByTagName(idTag).item(0)?.textContent?.trim()?.takeIf(String::isNotBlank)?.let { texts += "交易单号：$it" }
            appmsg.getElementsByTagName("display_name").item(0)?.textContent?.trim()?.takeIf(String::isNotBlank)?.let { texts += "交易对方：$it" }
            if (texts.any { it.contains("收款到账") } && texts.none { it.startsWith("付款方：") || it.startsWith("付款人：") || it.startsWith("交易对方：") }) texts += "交易对方：微信收款"
            appmsg.getElementsByTagName("pub_time").item(0)?.textContent?.let { epoch(JsonPrimitive(it)) }?.let {
                texts += "交易时间：${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()}"
            }
            return texts.joinToString("\n")
        }
        var remaining = ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES
        fun visit(node: org.w3c.dom.Node, depth: Int) {
            if (--remaining < 0 || depth > ConfigCatalog.AUTO_ACCOUNTING_MESSAGE_MAX_DEPTH) return
            if (node.nodeType == org.w3c.dom.Node.TEXT_NODE || node.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                node.nodeValue?.trim()?.takeIf(String::isNotBlank)?.let(texts::add)
            } else for (i in 0 until node.childNodes.length) visit(node.childNodes.item(i), depth + 1)
        }
        visit(appmsg, 0)
        return texts.joinToString("\n")
    }
}
