package com.antgskds.calendarassistant.feature.accounting.domain

/** 只用于合并 UI 刷新，不能替代账单入库去重。空字段表示未知，不能充当相同交易的证据。 */
data class PaymentPageIdentity(
    val ids: Map<String, String>, val nature: String?, val amount: String?,
    val time: String?, val merchant: String?,
) {
    fun conflicts(other: PaymentPageIdentity): Boolean {
        if (nature != null && other.nature != null && nature != other.nature) return true
        if (ids.any { (type, value) -> other.ids[type]?.let { it != value } == true }) return true
        if (ids.any { (type, value) -> other.ids[type] == value && type != "MERCHANT_ORDER" }) return false
        return listOf(amount to other.amount, time to other.time, merchant to other.merchant)
            .any { (a, b) -> a != null && b != null && a != b }
    }

    fun sameTransaction(other: PaymentPageIdentity): Boolean = !conflicts(other) &&
        nature != null && nature == other.nature &&
        ids.any { (type, value) -> other.ids[type] == value && (type != "MERCHANT_ORDER" || merchant != null && merchant == other.merchant) }

    fun enrich(other: PaymentPageIdentity) = PaymentPageIdentity(ids + other.ids,
        other.nature ?: nature, other.amount ?: amount, other.time ?: time, other.merchant ?: merchant)

    companion object {
        private val amountPattern = Regex("[+\\-−]?[¥￥]?\\s*[0-9]+\\.[0-9]{1,2}(?:\\s*元)?")
        private val currencyAmount = Regex("[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)|([0-9]+(?:\\.[0-9]{1,2})?)\\s*元")
        private val timePattern = Regex("(?:[0-9]{4}[年/\\-][0-9]{1,2}[月/\\-][0-9]{1,2}日?|今天|昨天)[\\sT]+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?")
        fun from(texts: List<String>): PaymentPageIdentity {
            val lines = texts.flatMap { it.split('\n') }.map(String::trim).filter(String::isNotEmpty)
            val text = lines.joinToString("\n")
            fun field(labels: List<String>): String? {
                for ((index, line) in lines.withIndex()) for (label in labels) {
                    if (!line.startsWith(label)) continue
                    val value = line.removePrefix(label).trim().trimStart(':', '：').trim()
                    return value.ifBlank { lines.getOrNull(index + 1).orEmpty() }.takeIf(String::isNotBlank)
                }
                return null
            }
            val ids = buildMap {
                for ((type, labels) in listOf("PAYMENT" to listOf("交易订单号", "交易单号", "转账单号"),
                        "REFUND" to listOf("退款单号"), "MERCHANT_ORDER" to listOf("商户订单号", "商户单号"))) {
                    field(labels)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }?.let { put(type, it) }
                }
            }
            val amountText = lines.firstOrNull { amountPattern.matches(it) }
                ?: currencyAmount.find(text)?.value
            val amount = amountText?.replace(Regex("[^0-9.]"), "")?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()
            val nature = when {
                lines.any { it == "退款成功" || it == "已退款" } || "退款单号" in text -> "REFUND"
                amountText?.startsWith("+") == true || lines.any { it in setOf("收入", "收款成功", "已收款", "收钱到账", "已收钱") } -> "INCOME"
                amountText?.let { it.startsWith("-") || it.startsWith("−") } == true ||
                    lines.any { it in setOf("支出", "支付成功", "付款成功", "转账成功") } -> "EXPENSE"
                else -> null
            }
            return PaymentPageIdentity(ids, nature, amount, timePattern.find(text)?.value,
                field(listOf("商户全称", "商家名称", "收款方", "付款方", "交易对方")))
        }
    }
}
