package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/** 内存中只保存短时入库回执，不保存通知正文；IO 入库与主线程截图通过同步方法交接。 */
class AccountingNotificationPriorityPolicy {
    data class Hint(val amountMinor: Long?, val direction: String)
    private data class Receipt(val id: String, val source: String, val receivedAt: Long,
        val savedAt: Long, val amountMinor: Long, val direction: String)
    private val receipts = ArrayDeque<Receipt>()

    @Synchronized
    fun record(message: AccountingMessage, result: AccountingRecognitionResult?, now: Long) {
        prune(now)
        if (message.kind != AccountingMessageKind.NOTIFICATION || !AutomaticAccountingPolicy.supports(message.sender) ||
            now - message.receivedAt !in 0..ConfigCatalog.AUTO_ACCOUNTING_NOTIFICATION_MATCH_MS.toLong()) return
        // 失败、疑似重复、待核对和纯重复回调都没有实际 saved，不得拿它们吞掉另一笔现场交易。
        for (bill in result?.saved.orEmpty()) {
            if (bill.status != "CONFIRMED" || bill.currency != "CNY" || bill.amountMinor <= 0 ||
                bill.direction !in setOf("EXPENSE", "INCOME") || bill.deletedAt != null ||
                kotlin.math.abs(bill.occurredAt - message.receivedAt) > ConfigCatalog.AUTO_ACCOUNTING_NOTIFICATION_MATCH_MS) continue
            if (receipts.any { it.id == bill.id }) continue
            while (receipts.size >= ConfigCatalog.AUTO_ACCOUNTING_QUEUE_SIZE) receipts.removeFirst()
            receipts.addLast(Receipt(bill.id, message.sender, message.receivedAt, now, bill.amountMinor, bill.direction))
        }
    }

    /** 每个成功入库回执只消费一次；金额不可读时要求时间窗内只有一笔同向回执。 */
    @Synchronized
    fun claim(source: String, hint: Hint, detectedAt: Long, now: Long): Boolean {
        prune(now)
        val matching = receipts.filter { it.source == source && it.direction == hint.direction &&
            kotlin.math.abs(it.receivedAt - detectedAt) <= ConfigCatalog.AUTO_ACCOUNTING_NOTIFICATION_MATCH_MS &&
            (hint.amountMinor == null || hint.amountMinor == it.amountMinor) }
        val receipt = matching.singleOrNull() ?: return false
        receipts.remove(receipt)
        return true
    }

    @Synchronized fun reset() { receipts.clear() }

    private fun prune(now: Long) {
        receipts.removeAll { now - it.savedAt !in 0..ConfigCatalog.AUTO_ACCOUNTING_NOTIFICATION_MATCH_MS.toLong() }
    }

    companion object {
        // 只读明确带币种/元的金额；多个金额无法确定哪一个是实付时不抑制识图。
        private val amountPattern = Regex("(?:[¥￥]\\s*([0-9,]+(?:\\.[0-9]{1,2})?)|([0-9,]+(?:\\.[0-9]{1,2})?)\\s*元)")
        fun hint(texts: List<String>): Hint? {
            val amounts = amountPattern.findAll(texts.joinToString("\n")).mapNotNull { match ->
                runCatching { (match.groupValues[1].ifBlank { match.groupValues[2] }).replace(",", "")
                    .toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()?.takeIf { it > 0 }
            }.toSet()
            val amount = amounts.singleOrNull() ?: return null
            val income = texts.any { it.trim() in setOf("收款成功", "已收款", "已收钱", "收钱到账") }
            return Hint(amount, if (income) "INCOME" else "EXPENSE")
        }
    }
}
