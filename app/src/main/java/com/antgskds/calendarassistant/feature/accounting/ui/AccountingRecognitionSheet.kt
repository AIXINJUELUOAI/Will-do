package com.antgskds.calendarassistant.feature.accounting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.shared.ui.material.component.*

/** 底部操作只作用于选中的草稿；收起保留待处理，快捷放行疑似重复仅在通知中提供。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingRecognitionSheet(state: AccountingRecognitionState, onEdit: (AccountingDraft) -> Unit,
    onDiscard: (String) -> Unit, onDismiss: () -> Unit) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val current = state.drafts.firstOrNull { it.id == selectedId } ?: state.drafts.firstOrNull()
    AppModalBottomSheet(title = "待确认账单（${state.drafts.size}）", onDismissRequest = onDismiss,
        subtitle = if (state.drafts.size > 1) "点击选择要处理的账单" else null, scrollState = null,
        actions = if (current == null) emptyList() else listOf(
            AppSheetAction("忽略", { onDiscard(current.id) }, AppSheetActionRole.Secondary, !state.busy),
            AppSheetAction("核对入库", { onEdit(current) }, enabled = !state.busy),
        )) {
        LazyColumn(Modifier.fillMaxWidth()) {
            state.message?.let { message -> item(key = "message") { Text(message, Modifier.padding(bottom = 12.dp)) } }
            if (state.drafts.isEmpty()) item { Text("没有待确认账单") }
            items(state.drafts, key = { it.id }) { draft ->
                val isSelected = draft.id == current?.id
                OutlinedCard(onClick = { selectedId = draft.id }, enabled = !state.busy,
                    colors = CardDefaults.outlinedCardColors(containerColor = if (isSelected)
                        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).semantics { selected = isSelected }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(draft.merchant.ifBlank { "交易对方待补充" }, style = MaterialTheme.typography.titleMedium)
                        Text("${draft.currency.ifBlank { "币种待确认" }} ${draft.amount.ifBlank { "金额待补充" }} · " + when (draft.direction) {
                            "EXPENSE" -> "支出"; "INCOME" -> "入账"; "TRANSFER" -> "不计收支"; else -> "方向待确认"
                        })
                        Text(draft.occurredAt.ifBlank { "交易时间待补充" })
                        if (draft.note.isNotBlank()) Text(draft.note)
                        if (draft.sourceType == "quick_memo") Text("来自随口记")
                        AccountingSourceImage(draft.sourceImagePath)
                    }
                }
            }
        }
    }
}
