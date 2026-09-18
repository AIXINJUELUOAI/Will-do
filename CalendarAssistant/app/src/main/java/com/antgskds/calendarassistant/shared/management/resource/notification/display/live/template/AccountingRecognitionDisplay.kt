package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionResult
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleDisplayModel
import java.math.BigDecimal
import java.math.BigInteger

/** 仅统计实际成功入库的人民币记录；BigInteger 避免多笔大额累计溢出。 */
object AccountingRecognitionDisplay {
    fun create(result: AccountingRecognitionResult): CapsuleDisplayModel {
        fun sum(direction: String) = result.saved.filter { it.currency == "CNY" && it.direction == direction }
            .fold(BigInteger.ZERO) { total, entry -> total + BigInteger.valueOf(entry.amountMinor) }
        fun money(value: BigInteger) = "¥" + BigDecimal(value, 2).toPlainString()
        val expense = sum("EXPENSE")
        val income = sum("INCOME")
        val transfer = sum("TRANSFER")
        val total = money(expense + income)
        val short = when {
            result.saved.isNotEmpty() -> total
            result.suspectedDuplicates > 0 -> "疑似重复"
            result.duplicates > 0 -> "账单重复"
            else -> "账单待核对"
        }
        val title = if (result.saved.isNotEmpty()) "已记 ${result.saved.size} 笔" else short
        val lines = buildList {
            if (result.saved.isNotEmpty()) {
                add("总金额 $total")
                add("支出 ${money(expense)} · 收入 ${money(income)}")
                if (transfer > BigInteger.ZERO) add("不计收支 ${money(transfer)}")
            }
            if (result.duplicates > 0) add("重复 ${result.duplicates} 笔，暂不入库")
            if (result.suspectedDuplicates > 0) add("疑似重复 ${result.suspectedDuplicates} 笔，暂不入库")
            if (result.pending > 0) add("${result.pending} 笔待核对，暂不入库")
        }
        return CapsuleDisplayModel(shortText = short, primaryText = title,
            secondaryText = lines.firstOrNull(), tertiaryText = lines.getOrNull(1),
            expandedText = lines.joinToString("\n"), tapOpensAccounting = true)
    }
}
