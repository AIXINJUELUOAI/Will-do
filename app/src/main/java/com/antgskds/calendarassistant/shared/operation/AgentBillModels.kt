package com.antgskds.calendarassistant.shared.operation

import kotlinx.serialization.Serializable

/** 账单时间统一为 Unix 毫秒；金额输入为十进制元字符串，输出为整数分。 */
@Serializable
data class AgentBillDraft(
    val amount: String,
    val direction: String,
    val currency: String = "CNY",
    val category: String = "未分类",
    val merchant: String = "",
    val note: String = "",
    val occurredAt: Long? = null,
    val zoneId: String? = null,
    val channel: String = "",
    val transactionId: String = "",
    val transactionIdType: String = "UNKNOWN",
)

@Serializable
data class AgentBillPatch(
    val amount: String? = null,
    val direction: String? = null,
    val category: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val occurredAt: Long? = null,
)

@Serializable
data class AgentBillFilter(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val direction: String? = null,
    val category: String? = null,
    val currency: String? = null,
    val text: String? = null,
)

@Serializable
data class AgentBillQuery(
    val filter: AgentBillFilter = AgentBillFilter(),
    val offset: Int = 0,
    val limit: Int = WillDoAgentContract.MAX_QUERY_LIMIT,
    val includePending: Boolean = false,
)

@Serializable
data class AgentBill(
    val id: String,
    val amountMinor: Long,
    val direction: String,
    val currency: String,
    val merchant: String,
    val category: String,
    val note: String,
    val occurredAt: Long,
    val zoneId: String,
    val channel: String,
    val transactionId: String,
    val transactionIdType: String,
    val status: String,
    val source: String,
    val hasAttachment: Boolean,
)

@Serializable
data class AgentBillPage(
    val bills: List<AgentBill>,
    val total: Int,
    val nextOffset: Int?,
)

/** DUPLICATE 没有新记录；SUSPECTED_DUPLICATE/PENDING 的 draftId 可在应用待确认页核对。 */
@Serializable
data class AgentBillCreation(
    val status: String,
    val bill: AgentBill? = null,
    val draftId: String? = null,
)

@Serializable
data class AgentBillSummary(
    val currency: String,
    val count: Int,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val transferMinor: Long,
    val balanceMinor: Long,
)
