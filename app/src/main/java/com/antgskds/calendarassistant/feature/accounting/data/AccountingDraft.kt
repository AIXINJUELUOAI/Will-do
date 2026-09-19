package com.antgskds.calendarassistant.feature.accounting.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.util.UUID

/** 识别候选独立持久化，缺失字段保持为空；确认前不进入 accounting_entries 或收支统计。 */
@Entity(tableName = "accounting_drafts")
data class AccountingDraft(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: String = "",
    val direction: String = "",
    val currency: String = "",
    val merchant: String = "",
    val category: String = "",
    val note: String = "",
    val occurredAt: String = "",
    val zoneId: String = "",
    val channel: String = "",
    val transactionId: String = "",
    val paymentStatus: String = "REVIEW",
    val sourceType: String = "",
    val sourceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // PAYMENT / MERCHANT_ORDER / UNKNOWN；旧识别没有类型证据，不能假定为支付交易号。
    @ColumnInfo(defaultValue = "'UNKNOWN'") val transactionIdType: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "NULL") val sourceImagePath: String? = null,
)
