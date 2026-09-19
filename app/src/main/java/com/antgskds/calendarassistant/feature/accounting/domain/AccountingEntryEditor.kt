package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class AccountingEntryInput(
    val id: String? = null,
    val amount: String,
    val direction: String,
    val merchant: String,
    val category: String,
    val note: String,
    val date: LocalDate,
    val time: LocalTime,
    val currency: String = "CNY",
    val channel: String = "",
    val transactionId: String = "",
    val paymentConfirmed: Boolean = false,
    val allowPossibleDuplicate: Boolean = false,
    val transactionIdType: String = "PAYMENT",
)

/** 只转换用户可编辑字段，导入身份和核对状态由原记录保留。 */
object AccountingEntryEditor {
    fun amountMinor(text: String): Long {
        val value = text.trim()
        require(value.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?"))) { "请输入有效金额，最多两位小数" }
        val amount = try { BigDecimal(value).movePointRight(2).longValueExact() }
            catch (_: ArithmeticException) { throw IllegalArgumentException("金额超出可保存范围") }
        require(amount > 0) { "金额必须大于零" }
        return amount
    }

    fun build(input: AccountingEntryInput, existing: AccountingEntry?, now: Long): AccountingEntry {
        require(input.id == existing?.id) { "账单不存在或已删除，请重新打开" }
        require(existing?.deletedAt == null) { "账单已删除，无法保存" }
        require(input.direction in setOf("EXPENSE", "INCOME", "TRANSFER")) { "请选择收支类型" }
        require(input.merchant.isNotBlank()) { "请输入账单名称或交易对方" }
        val zone = existing?.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val occurredAt = input.date.atTime(input.time).atZone(zone).toInstant().toEpochMilli()
        val base = existing ?: AccountingEntry(
            id = UUID.randomUUID().toString(), amountMinor = 0L, direction = input.direction, currency = "CNY",
            merchant = "", category = "", note = "", occurredAt = occurredAt, zoneId = zone.id,
            source = "MANUAL", channel = "手动记账", transactionId = "", status = "CONFIRMED", ruleId = "manual",
            dedupKey = null, refundOf = null, createdAt = now, updatedAt = now, deletedAt = null,
        )
        return base.copy(amountMinor = amountMinor(input.amount), direction = input.direction,
            merchant = input.merchant.trim(), category = input.category.trim().ifBlank { "未分类" },
            note = input.note.trim(), occurredAt = occurredAt, zoneId = zone.id, updatedAt = now,
            ruleId = if (existing == null) "manual" else "manual-edit")
    }
}
