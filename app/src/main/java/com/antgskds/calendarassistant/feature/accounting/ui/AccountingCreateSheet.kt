package com.antgskds.calendarassistant.feature.accounting.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryEditor
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryInput
import com.antgskds.calendarassistant.shared.ui.material.component.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingCreateSheet(state: AccountingEditorState, onSave: (AccountingEntryInput) -> Unit, onDismiss: () -> Unit) {
    var direction by rememberSaveable { mutableStateOf("EXPENSE") }
    var category by rememberSaveable { mutableStateOf("未分类") }
    var amount by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var more by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(state.initialDate.toString()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.now().withSecond(0).withNano(0).toString()) }
    var chooseDate by rememberSaveable { mutableStateOf(false) }
    var chooseTime by rememberSaveable { mutableStateOf(false) }

    val validAmount = remember(amount) { runCatching { AccountingEntryEditor.amountMinor(amount) }.isSuccess }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val initialValues = remember { listOf(amount, direction, merchant, category, note, date, time) }
    val close = rememberWorkspaceCloseRequest(listOf(amount, direction, merchant, category, note, date, time) != initialValues, onDismiss)
    val saving by rememberUpdatedState(state.saving)

    val embedded = LocalDetailWorkspace.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving })

    val categories: List<Pair<String, ImageVector>> = remember(direction) {
        if (direction == "EXPENSE") listOf(
            "餐饮" to Icons.Default.Restaurant, "交通" to Icons.Default.DirectionsBus,
            "购物" to Icons.Default.ShoppingBag, "居家" to Icons.Default.Home,
            "娱乐" to Icons.Default.SportsEsports, "医疗" to Icons.Default.LocalHospital,
            "学习" to Icons.Default.School, "未分类" to Icons.Default.MoreHoriz,
        ) else listOf(
            "工资" to Icons.Default.Work, "奖金" to Icons.Default.Payments,
            "红包" to Icons.Default.CardGiftcard, "理财" to Icons.Default.TrendingUp,
            "退款" to Icons.Default.Undo, "兼职" to Icons.Default.Assignment,
            "其他" to Icons.Default.AccountBalanceWallet, "未分类" to Icons.Default.MoreHoriz,
        )
    }

    val save = {
        if (!state.saving && validAmount) onSave(AccountingEntryInput(
            amount = amount, direction = direction, merchant = merchant.trim().ifBlank { category },
            category = category, note = note, date = LocalDate.parse(date), time = LocalTime.parse(time),
        ))
    }

    AppEditorSheet(
        title = "新建账单",
        sheetState = sheet,
        onDismissRequest = close,
        closeEnabled = !state.saving,
        contentBottomPadding = 8.dp,
        actions = listOf(
            AppSheetAction(
                text = if (state.saving) "保存中…" else "保存",
                onClick = save,
                enabled = validAmount && !state.saving
            )
        ),
        footer = {
            // 移除了 Divider，解除捆绑感
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额（元）") },
                    leadingIcon = { Text("¥", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    enabled = !state.saving,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .focusRequester(focusRequester)
                )
                state.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp)
                    )
                }
                // 与操作区自带的 8.dp 顶部留白合并为 16.dp 组间距。
                Spacer(Modifier.height(8.dp))
            }

            LaunchedEffect(Unit) {
                if (!embedded) snapshotFlow { sheet.currentValue }.first { it == SheetValue.Expanded }
                withFrameNanos { }
                focusRequester.requestFocus()
                keyboard?.show()
            }
        },
    ) {
        AppSegmentedControl(
            options = listOf("EXPENSE", "INCOME"),
            selectedOption = direction,
            onSelected = { direction = it; category = "未分类" },
            label = { if (it == "EXPENSE") "支出" else "收入" },
            enabled = !state.saving
        )
        Spacer(Modifier.height(24.dp))

        categories.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, icon) ->
                    val chosen = category == label
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = { category = label },
                            enabled = !state.saving,
                            shape = RoundedCornerShape(16.dp),
                            color = if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                            contentColor = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            // 调整点 1：使用 0.85f 将正方形略微缩放，不再占满整个单元格的宽度
                            modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(8.dp))

        // 调整点 3：保持左对齐
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = !state.saving,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { more = !more }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (more) "收起" else "更多",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        if (more) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("名称") },
                singleLine = true,
                enabled = !state.saving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { chooseDate = true },
                    enabled = !state.saving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date)
                }
                OutlinedButton(
                    onClick = { chooseTime = true },
                    enabled = !state.saving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(time.take(5))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                enabled = !state.saving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        }
    }

    if (chooseDate) WheelDatePickerDialog(LocalDate.parse(date), onDismiss = { chooseDate = false }) {
        date = it.toString(); chooseDate = false
    }
    if (chooseTime) WheelTimePickerDialog(time, onDismiss = { chooseTime = false }) {
        time = it; chooseTime = false
    }
}
