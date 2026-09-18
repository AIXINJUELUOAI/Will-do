package com.antgskds.calendarassistant.feature.accounting.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.accounting.domain.BillFileSource
import com.antgskds.calendarassistant.shared.ui.material.component.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingImportSheet(
    state: AccountingImportState,
    onSource: (BillFileSource) -> Unit,
    onFile: (Uri, BillFileSource) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickerSource by rememberSaveable { mutableStateOf(state.source.name) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onFile(uri, BillFileSource.valueOf(pickerSource))
    }
    val saving by rememberUpdatedState(state.saving)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving })
    val chooseFile = {
        pickerSource = state.source.name
        picker.launch(arrayOf("*/*"))
    }
    AppModalBottomSheet(title = "导入账单", subtitle = "文件只在本机解析，确认后保存",
        sheetState = sheetState, onDismissRequest = onDismiss,
        actions = if (state.result != null) listOf(AppSheetAction("完成", onDismiss)) else listOf(
            AppSheetAction(if (state.preview == null) "选择文件" else "重新选择", chooseFile,
                AppSheetActionRole.Secondary, enabled = !state.busy),
            AppSheetAction(if (state.saving) "正在导入…" else "确认导入", onConfirm,
                enabled = !state.busy && state.preview?.entries?.isNotEmpty() == true),
        )) {
        AppSegmentedControl(BillFileSource.entries, state.source, onSource, { it.label }, enabled = !state.busy && state.result == null)
        Spacer(Modifier.height(16.dp))
        Text(if (state.source == BillFileSource.WILLDO) "选择本应用导出的账单 JSON，包含已保存账单及截图。追加恢复，已有记录不会覆盖；待确认识别草稿不包含在备份中。"
            else "微信支持 CSV、XLSX；支付宝支持 CSV。若下载的是 ZIP，请先解压，再选择其中的账单文件。",
            style = MaterialTheme.typography.bodyMedium)
        if (state.busy) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (state.saving) "正在保存账单，请稍候" else "正在读取账单…", Modifier.padding(top = 8.dp))
        }
        state.error?.let {
            Text(it, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
        }
        state.preview?.let { preview ->
            Spacer(Modifier.height(16.dp))
            val result = state.result
            Text(if (result == null) "识别到 ${preview.entries.size} 条账单" else
                "新增 ${result.inserted} 条 · 重复 ${result.duplicates} 条 · 更新 ${result.updated} 条",
                style = MaterialTheme.typography.titleMedium)
            Text("跳过未完成交易 ${preview.ignored} 条 · 格式错误 ${preview.issues.size} 条",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            if (preview.entries.isEmpty()) Text("没有可导入的账单，请检查来源和错误提示后重新选择文件。", Modifier.padding(top = 8.dp))
            if (preview.pending > 0) Text("${preview.pending} 条需核对：含退款状态的支出或外币账单会保留原记录，暂不计入统计。本版暂不支持手动核对和退款抵扣。", Modifier.padding(top = 8.dp))
            if (preview.neutral > 0) Text("${preview.neutral} 条不计收支（如转入余额、提现），保留记录但不计入支出或入账。", Modifier.padding(top = 8.dp))
            if (result == null) Text(if (preview.source == BillFileSource.WILLDO)
                "确认后恢复账单与 ${preview.images.size} 张截图；已有或已删除的同一记录会跳过。"
                else "确认后导入格式有效的账单；同平台、同交易号的重复记录会自动跳过。", Modifier.padding(top = 8.dp))
            preview.entries.minOfOrNull { it.occurredAt }?.let { start ->
                val zone = java.time.ZoneId.of("Asia/Shanghai")
                fun day(time: Long) = java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                Text("账单日期：${day(start)} — ${day(preview.entries.maxOf { it.occurredAt })}", Modifier.padding(top = 8.dp))
            }
            if (result == null && preview.entries.isNotEmpty()) {
                Text("记录预览（前 5 条）", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                preview.entries.take(5).forEach { entry ->
                    val direction = when (entry.direction) { "EXPENSE" -> "支出"; "INCOME" -> "入账"; else -> "不计收支" }
                    Text("${entry.merchant} · $direction ${entry.currency} ${java.math.BigDecimal.valueOf(entry.amountMinor, 2).toPlainString()}",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            preview.issues.take(10).forEach { issue ->
                Text("${issue.sheet} 第 ${issue.row} 行：${issue.reason}", Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (preview.issues.size > 10) Text("另有 ${preview.issues.size - 10} 行格式错误，本次不会导入。", Modifier.padding(top = 8.dp))
        }
    }
}
