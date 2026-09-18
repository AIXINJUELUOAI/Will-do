package com.antgskds.calendarassistant.feature.accounting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionMapper
import com.antgskds.calendarassistant.shared.ui.material.component.*

/** 与记账编辑表单共用确认动作，列表中的记录尚未入账。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingRecognitionSheet(state: AccountingRecognitionState, onEdit: (AccountingDraft) -> Unit,
    onDiscard: (String) -> Unit, onDismiss: () -> Unit, onCountAnyway: (AccountingDraft) -> Unit) {
    AppModalBottomSheet(title = "待确认账单（${state.drafts.size}）", onDismissRequest = onDismiss, scrollState = null,
        actions = listOf(AppSheetAction("稍后处理", onDismiss, enabled = !state.busy))) {
        LazyColumn(Modifier.fillMaxWidth()) {
        state.message?.let { message -> item(key = "message") { Text(message, Modifier.padding(bottom = 12.dp)) } }
        if (state.drafts.isEmpty()) item { Text("没有待确认账单") }
        items(state.drafts, key = { it.id }) { draft ->
            OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(draft.merchant.ifBlank { "交易对方待补充" }, style = MaterialTheme.typography.titleMedium)
                    Text("${draft.currency.ifBlank { "币种待确认" }} ${draft.amount.ifBlank { "金额待补充" }} · " + when (draft.direction) {
                        "EXPENSE" -> "支出"; "INCOME" -> "入账"; "TRANSFER" -> "不计收支"; else -> "方向待确认"
                    })
                    Text(draft.occurredAt.ifBlank { "交易时间待补充" })
                    if (draft.note.isNotBlank()) Text(draft.note)
                    if (draft.sourceType == "quick_memo") Text("来自随口记")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = { onDiscard(draft.id) }, enabled = !state.busy) { Text("忽略") }
                        TextButton(onClick = { onEdit(draft) }, enabled = !state.busy) { Text("核对并入账") }
                    }
                    if (AccountingRecognitionMapper.isPossibleDuplicate(draft)) {
                        Button(onClick = { onCountAnyway(draft) }, enabled = !state.busy,
                            modifier = Modifier.align(Alignment.End)) { Text("仍然计入") }
                    }
                    AccountingSourceImage(draft.sourceImagePath)
                }
            }
        }
        }
    }
}
