package com.antgskds.calendarassistant.feature.accounting.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 两个入口使用同一个 ViewModel 导出快照；取消文件选择不会写入或提示成功。 */
@Composable
fun rememberAccountingExportAction(viewModel: AccountingViewModel): () -> Unit {
    val context = LocalContext.current
    val exporting by viewModel.exporting.collectAsState()
    val message by viewModel.exportMessage.collectAsState()
    var choosing by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        choosing = false
        if (uri != null) viewModel.exportFile(uri)
    }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeExportMessage()
        }
    }
    return {
        if (!exporting && !choosing) {
            choosing = true
            launcher.launch("WillDo-账单-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json")
        }
    }
}
