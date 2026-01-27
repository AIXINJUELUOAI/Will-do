package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsPage(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }

    fun showToast(message: String, type: ToastType) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // 课程数据导出
    val exportCoursesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = viewModel.exportCoursesData()
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(jsonData.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        showToast("课程数据导出成功", ToastType.SUCCESS)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("导出失败: ${e.message}", ToastType.ERROR)
                    }
                }
            }
        }
    }

    // 课程数据导入
    val importCoursesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (jsonString != null) {
                        val result = viewModel.importCoursesData(jsonString)
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                showToast("课程数据导入成功，共 ${viewModel.getCoursesCount()} 门课程", ToastType.SUCCESS)
                            } else {
                                showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("导入失败: ${e.message}", ToastType.ERROR)
                    }
                }
            }
        }
    }

    // 日程数据导出
    val exportEventsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = viewModel.exportEventsData()
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(jsonData.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        showToast("日程数据导出成功，共 ${viewModel.getEventsCount()} 条日程", ToastType.SUCCESS)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("导出失败: ${e.message}", ToastType.ERROR)
                    }
                }
            }
        }
    }

    // 日程数据导入
    val importEventsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (jsonString != null) {
                        val result = viewModel.importEventsData(jsonString)
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                showToast("日程数据导入成功，共 ${viewModel.getEventsCount()} 条日程", ToastType.SUCCESS)
                            } else {
                                showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("导入失败: ${e.message}", ToastType.ERROR)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BackupCard(
                title = "课程数据",
                desc = "备份/恢复你的课程表和作息时间配置",
                onExport = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportCoursesLauncher.launch("calendar_courses_$timestamp.json")
                },
                onImport = { importCoursesLauncher.launch(arrayOf("application/json")) }
            )
            BackupCard(
                title = "日程数据",
                desc = "备份/恢复你的所有日程事件",
                onExport = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportEventsLauncher.launch("calendar_events_$timestamp.json")
                },
                onImport = { importEventsLauncher.launch(arrayOf("application/json")) }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            snackbar = { data ->
                UniversalToast(message = data.visuals.message, type = currentToastType)
            }
        )
    }
}

@Composable
fun BackupCard(title: String, desc: String, onExport: () -> Unit, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入")
                }
            }
        }
    }
}
