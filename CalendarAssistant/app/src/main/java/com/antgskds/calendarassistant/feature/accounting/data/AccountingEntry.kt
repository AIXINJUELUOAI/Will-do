package com.antgskds.calendarassistant.feature.accounting.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 沿用原记账字段及索引；数据库 19 增加交易号类型，20 增加关联截图路径。
 */
@Entity(tableName = "accounting_entries", indices = [Index("occurredAt"), Index(value = ["dedupKey"], unique = true)])
@kotlinx.serialization.Serializable
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
    @ColumnInfo(defaultValue = "'UNKNOWN'") val transactionIdType: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "NULL") val sourceImagePath: String? = null,
)
