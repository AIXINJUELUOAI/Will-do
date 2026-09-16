package com.antgskds.calendarassistant.feature.accounting.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记账暂停期间仅保留数据库兼容结构，与记账测试版（数据库 16）的字段及索引一致。
 * 当前不提供 DAO、录入入口或同步业务；新安装只建空表，覆盖安装保留原表。
 */
@Entity(tableName = "accounting_entries", indices = [Index("occurredAt"), Index(value = ["dedupKey"], unique = true)])
data class AccountingEntry(
    @PrimaryKey val id: String,
    val amountMinor: Long,
    val direction: String,
    val currency: String,
    val merchant: String,
    val category: String,
    val note: String,
    val occurredAt: Long,
    val zoneId: String,
    val source: String,
    val channel: String,
    val transactionId: String,
    val status: String,
    val ruleId: String,
    val dedupKey: String?,
    val refundOf: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
