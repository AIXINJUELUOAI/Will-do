package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField

enum class AccountingMessageKind { NOTIFICATION, SMS }

data class AccountingMessage(
    val kind: AccountingMessageKind,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val title: String = "",
)

/** sourcePattern 必须整串匹配包名/发件人；pattern 在标题换行正文中查找。 */
@Serializable
data class AccountingMessageRule(
    val id: String,
    val name: String,
    val kind: AccountingMessageKind = AccountingMessageKind.NOTIFICATION,
    val sourcePattern: String = "",
    val pattern: String = "",
    val direction: String = "EXPENSE",
    val channel: String = "",
    val category: String = "其他",
    val merchant: String = "",
    val refund: Boolean = false,
    val enabled: Boolean = true,
    val timeFormat: String = "yyyy-MM-dd HH:mm:ss",
)

object AccountingMessageRules {
    // 根据真实支付宝通知独立编写；金额组紧邻支出/退款语义，避免捕获营销券金额。
    fun defaults(): List<AccountingMessageRule> = listOf(
        AccountingMessageRule("alipay_expense", "支付宝支出通知", sourcePattern = "com\\.eg\\.android\\.AlipayGphone",
            pattern = "你有一笔(?<amount>[0-9]+(?:\\.[0-9]{1,2})?)元的支出", channel = "支付宝", merchant = "支付宝支出"),
        AccountingMessageRule("alipay_refund", "支付宝退款通知", sourcePattern = "com\\.eg\\.android\\.AlipayGphone",
            pattern = "你收到一笔(?<amount>[0-9]+(?:\\.[0-9]{1,2})?)元退款", direction = "INCOME",
            channel = "支付宝", category = "退款", merchant = "支付宝退款", refund = true),
    ) + listOf("EXPENSE" to "支出", "INCOME" to "收入").map { (direction, label) ->
        // 参考 AutoResource 建行储蓄卡公开样本的字段顺序，独立使用严格金额和命名分组实现。
        AccountingMessageRule("ccb_${direction.lowercase()}", "建设银行储蓄卡$label", kind = AccountingMessageKind.SMS,
            sourcePattern = "(?:\\+?86)?95533|建设银行|中国建设银行",
            pattern = "您尾号[0-9]{4}的储蓄卡(?<time>[0-9]{1,2}月[0-9]{1,2}日[0-9]{1,2}时[0-9]{1,2}分)(?<merchant>[^\\r\\n]*?)${label}人民币(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?)元[,，]活期余额[0-9,.]+元[。.]?[\\[【]建设银行[\\]】]",
            direction = direction, channel = "建设银行", merchant = "建设银行$label", timeFormat = "M月d日H时m分")
    }

    fun validate(rule: AccountingMessageRule): String? = runCatching {
        require(rule.name.isNotBlank()) { "规则名称不能为空" }
        require(rule.sourcePattern.isNotBlank() && rule.pattern.isNotBlank()) { "来源与正文正则不能为空" }
        require(rule.pattern.length <= ConfigCatalog.ACCOUNTING_MESSAGE_MAX_TEXT &&
            rule.sourcePattern.length <= ConfigCatalog.ACCOUNTING_MESSAGE_MAX_TEXT) { "正则过长" }
        Regex(rule.sourcePattern)
        Regex(rule.pattern)
        require("(?<amount>" in rule.pattern) { "正文正则需要 amount 命名分组" }
        require(rule.direction in setOf("EXPENSE", "INCOME", "TRANSFER")) { "收支方向无效" }
        require(!rule.refund || rule.direction == "INCOME") { "退款必须记为收入" }
        DateTimeFormatter.ofPattern(rule.timeFormat)
    }.exceptionOrNull()?.message

    fun parse(message: AccountingMessage, rules: List<AccountingMessageRule>): AccountingDraft? {
        val text = listOf(message.title, message.body).filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank() || text.length > ConfigCatalog.ACCOUNTING_MESSAGE_MAX_TEXT || message.sender.length > ConfigCatalog.ACCOUNTING_MESSAGE_MAX_TEXT || message.receivedAt <= 0) return null
        return rules.take(ConfigCatalog.ACCOUNTING_MESSAGE_MAX_RULES).firstNotNullOfOrNull { rule ->
            if (!rule.enabled || rule.kind != message.kind) return@firstNotNullOfOrNull null
            runCatching {
                if (validate(rule) != null || !Regex(rule.sourcePattern).matches(message.sender)) return@runCatching null
                val match = Regex(rule.pattern).find(text) ?: return@runCatching null
                fun group(name: String) = runCatching { match.groups[name]?.value.orEmpty().trim() }.getOrDefault("")
                val rawAmount = group("amount")
                if (!Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?").matches(rawAmount)) return@runCatching null
                val amount = rawAmount.replace(",", "").toBigDecimal()
                if (amount <= BigDecimal.ZERO) return@runCatching null
                amount.movePointRight(2).longValueExact()
                val zone = ZoneId.systemDefault()
                // 时间组存在但无效时整条跳过；没有时间才采用该消息的接收时间。
                val received = Instant.ofEpochMilli(message.receivedAt).atZone(zone).toLocalDateTime()
                val time = group("time").let {
                    if (it.isBlank()) received else {
                        val format = DateTimeFormatterBuilder().appendPattern(rule.timeFormat.replace("yyyy", "uuuu"))
                            .parseDefaulting(ChronoField.YEAR, received.year.toLong())
                            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                            .toFormatter().withResolverStyle(ResolverStyle.STRICT)
                        val parsed = LocalDateTime.parse(it, format)
                        // 银行短信常无年份；跨年延迟消息采用不晚于接收时间的最近一年。
                        if ('y' !in rule.timeFormat && 'u' !in rule.timeFormat && parsed > received) parsed.minusYears(1) else parsed
                    }
                }
                AccountingDraft(amount = amount.toPlainString(), currency = "CNY", direction = rule.direction,
                    merchant = group("merchant").ifBlank { rule.merchant.ifBlank { rule.name } },
                    category = rule.category, channel = rule.channel,
                    occurredAt = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), zoneId = zone.id,
                    transactionId = group("transactionId"),
                    transactionIdType = if (group("transactionId").isBlank()) "UNKNOWN" else "PAYMENT",
                    paymentStatus = if (rule.refund) "REFUNDED" else "COMPLETED")
            }.getOrNull()
        }
    }
}
