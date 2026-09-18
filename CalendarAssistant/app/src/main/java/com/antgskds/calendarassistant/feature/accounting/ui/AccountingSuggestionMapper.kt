package com.antgskds.calendarassistant.feature.accounting.ui

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class AccountingSuggestion(val id: String, val text: String)

/** 只描述已有记录，不把无记录等同于零消费；所有金额汇总避免 Long 溢出。 */
internal object AccountingSuggestionMapper {
    fun suggestions(bills: List<PreviewBill>, anchor: LocalDate, period: PreviewPeriod, today: LocalDate): List<AccountingSuggestion> {
        val range = AccountingPreviewData.range(anchor, period)
        val current = bills.filter { it.date in range && !it.date.isAfter(today) }
        if (current.isEmpty()) return listOf(AccountingSuggestion("empty", "当前暂无账单，记录后可查看支出情况。"))
        val confirmed = current.filter(::counted)
        val expenses = confirmed.filter { it.direction == "EXPENSE" }
        val income = confirmed.filter { it.direction == "INCOME" }
        val total = sum(expenses)
        val minRecords = ConfigCatalog.ACCOUNTING_SUGGESTION_MIN_RECORDS
        val label = when (period) {
            PreviewPeriod.DAY -> if (anchor == today) "今天" else "这一天"
            PreviewPeriod.WEEK -> if (today in range) "本周" else "这一周"
            PreviewPeriod.MONTH -> if (today in range) "本月" else "这个月"
        }
        val previousRange = AccountingPreviewData.range(AccountingPreviewData.shift(anchor, period, -1), period)
        // 月长不同时只比较双方都有的日期；本期未结束时不拿上期整月来比较。
        val elapsed = ChronoUnit.DAYS.between(range.start, minOf(range.end, today)) + 1
        val comparisonDays = minOf(elapsed, ChronoUnit.DAYS.between(previousRange.start, previousRange.end) + 1)
        val currentCompareEnd = range.start.plusDays(comparisonDays - 1)
        val previousCompareEnd = previousRange.start.plusDays(comparisonDays - 1)
        val currentCompare = expenses.filter { !it.date.isAfter(currentCompareEnd) }
        val previousCompare = bills.filter { counted(it) && it.direction == "EXPENSE" &&
            it.date >= previousRange.start && it.date <= previousCompareEnd }
        val priorTotal = sum(previousCompare)
        val compareTotal = sum(currentCompare)
        val shopping = expenses.filter(::shopping)
        val dining = expenses.filter { it.category.contains("餐饮") || it.category.contains("外卖") }
        val delivery = expenses.filter { it.category.contains("外卖") || it.title.contains("外卖") ||
            it.title.contains("饿了么") || it.note.contains("外卖") }
        val small = expenses.filter { it.cents <= ConfigCatalog.ACCOUNTING_SUGGESTION_SMALL_MINOR }
        return buildList {
            // 大额与明确到账信息优先；顺序固定，数据更新不自动轮播。
            expenses.maxByOrNull { it.cents }?.takeIf { it.cents >= ConfigCatalog.ACCOUNTING_SUGGESTION_LARGE_MINOR }?.let {
                add(AccountingSuggestion("large", "${label}有一笔 ${money(it.cents.toBigInteger())} 的较大支出，可以留意本月累计消费。"))
            }
            val reimbursements = income.filter { it.category.contains("报销") || it.note.contains("报销") || it.title.contains("报销") }
            if (reimbursements.isNotEmpty()) add(AccountingSuggestion("reimbursement", "${label}有 ${money(sum(reimbursements))} 报销到账。"))
            listOf(false, true).forEach { incoming ->
                val transfers = (if (incoming) income else expenses).filter(::transfer)
                    .filter { it.title.isNotBlank() && it.title !in setOf("转账", "红包", "收款", "未分类", "未知", "未提供交易对方") }
                    .groupBy { it.title.trim() }.toSortedMap()
                transfers.filterValues { it.size >= minRecords }.maxByOrNull { sum(it.value) }?.let { (name, rows) ->
                    add(AccountingSuggestion(if (incoming) "transfer_in" else "transfer_out",
                        if (incoming) "${label}收到「$name」${rows.size} 笔转账，共 ${money(sum(rows))}。"
                        else "${label}给「$name」转账较多，共 ${rows.size} 笔、${money(sum(rows))}。"))
                }
            }
            if (delivery.size >= minRecords) add(AccountingSuggestion("delivery", "${label}外卖消费较频繁，共 ${delivery.size} 笔，可以关注一下累计支出。"))
            if (small.size >= ConfigCatalog.ACCOUNTING_SUGGESTION_SMALL_COUNT) add(AccountingSuggestion("small",
                "${label}有 ${small.size} 笔小额消费，累计 ${money(sum(small))}，可以留意一下。"))
            if (shopping.size >= minRecords && highShare(sum(shopping), total)) add(AccountingSuggestion("shopping_share",
                "${label}购物支出较多，可以留意一下接下来的消费。"))
            if (expenses.size >= minRecords && highShare(sum(dining), total)) add(AccountingSuggestion("dining_share",
                "${label}餐饮支出占比较高，可以看看主要花在了哪里。"))
            val priorShopping = previousCompare.filter(::shopping)
            val currentShopping = currentCompare.filter(::shopping)
            if (priorShopping.size >= minRecords && currentShopping.size >= minRecords &&
                sum(currentShopping) * 100.toBigInteger() >= sum(priorShopping) * (100 + ConfigCatalog.ACCOUNTING_SUGGESTION_CHANGE_PERCENT).toBigInteger()) {
                add(AccountingSuggestion("shopping_increase", "${label}已记录的购物支出比上一周期同期有所增加。"))
            }
            if (currentCompare.size >= minRecords && previousCompare.size >= minRecords && priorTotal.signum() > 0) {
                if (compareTotal * 100.toBigInteger() <= priorTotal * (100 - ConfigCatalog.ACCOUNTING_SUGGESTION_CHANGE_PERCENT).toBigInteger())
                    add(AccountingSuggestion("decrease", "${label}已记录的支出比上一周期同期有所减少。"))
                if ((compareTotal - priorTotal).abs() * 100.toBigInteger() <= priorTotal * ConfigCatalog.ACCOUNTING_SUGGESTION_STABLE_PERCENT.toBigInteger())
                    add(AccountingSuggestion("stable", "${label}已记录的支出较为平稳，与上一周期同期基本持平。"))
            }
            if (isEmpty()) add(AccountingSuggestion("summary", if (confirmed.isEmpty())
                "当前记录暂未计入收支，确认后的人民币收入和支出会用于生成建议。"
                else "${label}已记录 ${confirmed.size} 笔收支，支出 ${money(total)}，收入 ${money(sum(income))}。"))
        }
    }

    private fun counted(bill: PreviewBill) = bill.counted && bill.status == "CONFIRMED" && bill.currency == "CNY" &&
        bill.cents > 0 && bill.direction in setOf("EXPENSE", "INCOME")
    private fun shopping(bill: PreviewBill) = listOf("购物", "服饰", "数码", "日用", "生活用品").any { it in bill.category }
    private fun transfer(bill: PreviewBill) = bill.category.contains("转账") || bill.note.contains("转账")
    private fun sum(bills: List<PreviewBill>) = bills.fold(BigInteger.ZERO) { total, bill -> total + bill.cents.toBigInteger() }
    private fun money(value: BigInteger) = "¥${BigDecimal(value, 2).toPlainString()}"
    private fun highShare(part: BigInteger, total: BigInteger) = total.signum() > 0 &&
        part * 100.toBigInteger() >= total * ConfigCatalog.ACCOUNTING_SUGGESTION_SHARE_PERCENT.toBigInteger()
}
