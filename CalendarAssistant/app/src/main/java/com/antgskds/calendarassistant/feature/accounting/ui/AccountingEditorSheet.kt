package com.antgskds.calendarassistant.feature.accounting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryInput
import com.antgskds.calendarassistant.shared.ui.material.component.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingEditorSheet(
    state: AccountingEditorState,
    onSave: (AccountingEntryInput) -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = state.entry
    val draft = state.draft
    if (entry == null && draft == null) {
        AccountingCreateSheet(state, onSave, onDismiss)
        return
    }
    val localTime = remember(entry) { entry?.let {
        Instant.ofEpochMilli(it.occurredAt).atZone(runCatching { ZoneId.of(it.zoneId) }.getOrDefault(ZoneId.systemDefault()))
    } }
    var amount by rememberSaveable { mutableStateOf(draft?.amount ?: entry?.let { BigDecimal.valueOf(it.amountMinor, 2).toPlainString() }.orEmpty()) }
    var direction by rememberSaveable { mutableStateOf(draft?.direction ?: entry?.direction ?: "EXPENSE") }
    var merchant by rememberSaveable { mutableStateOf(draft?.merchant ?: entry?.merchant.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(draft?.category ?: entry?.category.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(draft?.note ?: entry?.note.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(if (draft != null) draft.occurredAt.substringBefore('T').substringBefore(' ') else (localTime?.toLocalDate() ?: state.initialDate).toString()) }
    var time by rememberSaveable { mutableStateOf(if (draft != null) draft.occurredAt.replace('T', ' ').substringAfter(' ', "") else (localTime?.toLocalTime() ?: LocalTime.now().withSecond(0).withNano(0)).toString()) }
    var currency by rememberSaveable { mutableStateOf(draft?.currency ?: entry?.currency ?: "CNY") }
    var channel by rememberSaveable { mutableStateOf(draft?.channel.orEmpty()) }
    var transactionId by rememberSaveable { mutableStateOf(draft?.transactionId.orEmpty()) }
    var paymentConfirmed by rememberSaveable { mutableStateOf(draft?.paymentStatus in setOf("COMPLETED", "REFUNDED")) }
    var allowDuplicate by rememberSaveable { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var chooseDate by rememberSaveable { mutableStateOf(false) }
    var chooseTime by rememberSaveable { mutableStateOf(false) }
    val saving by rememberUpdatedState(state.saving)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving })
    AppModalBottomSheet(title = if (draft != null) "确认识别账单" else if (entry == null) "新建账单" else "编辑账单", sheetState = sheet,
        onDismissRequest = onDismiss, actions = listOf(
            AppSheetAction("取消", onDismiss, AppSheetActionRole.Secondary, enabled = !state.saving),
            AppSheetAction(if (state.saving) "保存中…" else "保存", {
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                val parsedTime = runCatching { LocalTime.parse(time) }.getOrNull()
                if (parsedDate == null || parsedTime == null) validationError = "请补全有效的交易日期和时间"
                else {
                    validationError = null
                    onSave(AccountingEntryInput(entry?.id, amount, direction, merchant, category, note,
                        parsedDate, parsedTime, currency, channel, transactionId, paymentConfirmed, allowDuplicate,
                        transactionIdType = draft?.transactionIdType ?: "UNKNOWN"))
                }
            }, enabled = !state.saving),
        )) {
        AppSegmentedControl(listOf("EXPENSE", "INCOME", "TRANSFER"), direction, { direction = it },
            { when (it) { "EXPENSE" -> "支出"; "INCOME" -> "入账"; else -> "不计收支" } }, enabled = !state.saving)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(amount, { amount = it }, label = { Text("金额（${currency.ifBlank { "币种待确认" }}）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            enabled = !state.saving, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(merchant, { merchant = it }, label = { Text("名称 / 交易对方") }, singleLine = true,
            enabled = !state.saving, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(category, { category = it }, label = { Text("分类") }, singleLine = true,
            enabled = !state.saving, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { chooseDate = true }, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text(date.ifBlank { "选择交易日期" }) }
            OutlinedButton(onClick = { chooseTime = true }, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text(time.take(5).ifBlank { "选择交易时间" }) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(note, { note = it }, label = { Text("备注") }, enabled = !state.saving,
            minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
        if (draft != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(currency, { currency = it.uppercase() }, label = { Text("币种") }, singleLine = true,
                enabled = !state.saving, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(channel, { channel = it }, label = { Text("支付渠道") }, singleLine = true,
                enabled = !state.saving, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(transactionId, { transactionId = it }, label = { Text(when (draft.transactionIdType) {
                "PAYMENT" -> "支付交易单号（选填）"
                "MERCHANT_ORDER" -> "商户订单号（选填）"
                else -> "单号（类型未确定，选填）"
            }) }, singleLine = true,
                enabled = !state.saving, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(paymentConfirmed, { paymentConfirmed = it }, enabled = !state.saving)
                Text(if (draft.paymentStatus == "REFUNDED") "已核对退款到账及退款金额" else "已核对交易完成及收支方向")
            }
            if (currency.isNotBlank() && currency != "CNY") Text("外币账单保存为待核对，不计入人民币统计。")
            if (state.error?.contains("仍然保存") == true) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(allowDuplicate, { allowDuplicate = it }, enabled = !state.saving)
                Text("已核对为另一笔交易，仍然保存")
            }
        }
        if (entry?.status == "PENDING") Text("此账单仍为待核对，修改内容不会自动将其纳入统计。", Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
        validationError?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
        AccountingSourceImage(draft?.sourceImagePath ?: entry?.sourceImagePath)
    }
    if (chooseDate) WheelDatePickerDialog(runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()), onDismiss = { chooseDate = false }) {
        date = it.toString(); chooseDate = false
    }
    if (chooseTime) WheelTimePickerDialog(runCatching { LocalTime.parse(time).toString() }.getOrDefault("12:00"), onDismiss = { chooseTime = false }) {
        time = it; chooseTime = false
    }
}
