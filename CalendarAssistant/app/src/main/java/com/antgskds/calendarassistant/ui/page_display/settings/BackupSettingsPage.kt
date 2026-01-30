package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpSettingsDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsPage(viewModel: SettingsViewModel, uiSize: Int = 2) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentSettings by viewModel.settings.collectAsState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var detectedStartDate by remember { mutableStateOf<String?>(null) }
    var importMode by remember { mutableStateOf(ImportMode.APPEND) }
    var importSettings by remember { mutableStateOf(true) }

    fun showToast(message: String, type: ToastType) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // --- 字体样式优化 ---
    // 板块标题：Primary + ExtraBold
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    // 卡片标题：OnSurface + Medium
    val cardTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    // 说明文字：Grey + Transparent
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val contentBodyStyle = MaterialTheme.typography.bodyMedium
    val sectionHeaderStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    // ... (中间的逻辑代码保持不变：peekStartDate, Launchers 等) ...
    // 辅助：快速检测文件中的日期
    fun peekStartDate(jsonContent: String): String? {
        return try {
            jsonContent.lines().firstNotNullOfOrNull { line ->
                when {
                    line.trim().startsWith("{") && line.contains("\"startDate\"") -> {
                        val settings = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<WakeUpSettingsDTO>(line.trim())
                        settings.startDate
                    }
                    else -> null
                }
            }
        } catch (e: Exception) { null }
    }

    // 课程数据导出
    val exportCoursesLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = viewModel.exportCoursesData()
                    context.contentResolver.openOutputStream(uri)?.use { output -> output.write(jsonData.toByteArray()) }
                    withContext(Dispatchers.Main) { showToast("课程数据导出成功", ToastType.SUCCESS) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) }
                }
            }
        }
    }

    // 课程数据导入
    val importCoursesLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val isExternalFormat = content.contains("\"courseLen\"") || (content.contains("\"startNode\"") && content.contains("\"step\""))
                        if (isExternalFormat) {
                            pendingImportContent = content
                            detectedStartDate = peekStartDate(content)
                            importSettings = currentSettings.semesterStartDate.isBlank()
                            importMode = ImportMode.APPEND
                            showImportConfirmDialog = true
                        } else {
                            val result = viewModel.importCoursesData(content)
                            withContext(Dispatchers.Main) {
                                if (result.isSuccess) showToast("课程数据导入成功，共 ${viewModel.getCoursesCount()} 门课程", ToastType.SUCCESS)
                                else showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                            }
                        }
                    }
                } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("导入失败: ${e.message}", ToastType.ERROR) } }
            }
        }
    }

    // 日程数据导出
    val exportEventsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = viewModel.exportEventsData()
                    context.contentResolver.openOutputStream(uri)?.use { output -> output.write(jsonData.toByteArray()) }
                    withContext(Dispatchers.Main) { showToast("日程数据导出成功，共 ${viewModel.getEventsCount()} 条日程", ToastType.SUCCESS) }
                } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) } }
            }
        }
    }

    // 日程数据导入
    val importEventsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (jsonString != null) {
                        val result = viewModel.importEventsData(jsonString)
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) showToast("日程数据导入成功，共 ${viewModel.getEventsCount()} 条日程", ToastType.SUCCESS)
                            else showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                        }
                    }
                } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("导入失败: ${e.message}", ToastType.ERROR) } }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = 80.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("数据管理", style = sectionTitleStyle)

            BackupCard(
                title = "课程数据",
                desc = "备份/恢复课程表。支持本应用备份和外部课表导入",
                onExport = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportCoursesLauncher.launch("calendar_courses_$timestamp.json")
                },
                onImport = { importCoursesLauncher.launch(arrayOf("*/*")) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )
            BackupCard(
                title = "日程数据",
                desc = "备份/恢复你的所有日程事件",
                onExport = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportEventsLauncher.launch("calendar_events_$timestamp.json")
                },
                onImport = { importEventsLauncher.launch(arrayOf("application/json")) },
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )
    }

    // 确认导入弹窗
    if (showImportConfirmDialog && pendingImportContent != null) {
        Dialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportContent = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 670.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("导入外部课表", style = sectionHeaderStyle)
                    }

                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("检测到文件中的学期开始日期: ${detectedStartDate ?: "未知"}", style = contentBodyStyle)

                        if (currentSettings.semesterStartDate.isNotBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "当前 App 内的日期: ${currentSettings.semesterStartDate}",
                                            style = cardValueStyle,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "如需覆盖，请勾选下方选项",
                                            style = cardValueStyle,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Text("配置", style = sectionTitleStyle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = importSettings,
                                onCheckedChange = { importSettings = it }
                            )
                            Text("导入文件中的开学日期 (总周数)", modifier = Modifier.clickable { importSettings = !importSettings }, style = contentBodyStyle)
                        }

                        Text("课程", style = sectionTitleStyle)
                        ImportOptionRadio(importMode, contentBodyStyle) { importMode = it }

                        if (importMode == ImportMode.OVERWRITE) {
                            Text(
                                "⚠️ 警告：覆盖模式将删除当前所有课程数据！",
                                color = MaterialTheme.colorScheme.error,
                                style = cardValueStyle,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        HorizontalDivider()

                        Text(
                            "注：作息时间表始终使用 App 内的设置，不会从文件导入",
                            style = cardValueStyle
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showImportConfirmDialog = false; pendingImportContent = null }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val content = pendingImportContent
                            if (content != null) {
                                viewModel.importWakeUpFile(content, importMode, importSettings) { result ->
                                    if (result.isSuccess) showToast("成功导入 ${result.getOrNull()} 门课程", ToastType.SUCCESS)
                                    else showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                                }
                            }
                            showImportConfirmDialog = false
                            pendingImportContent = null
                        }) { Text("导入") }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportOptionRadio(currentMode: ImportMode, contentBodyStyle: TextStyle, onModeChange: (ImportMode) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = currentMode == ImportMode.APPEND,
                onClick = { onModeChange(ImportMode.APPEND) }
            )
            Text("加入 (保留现有课程，追加新课)", modifier = Modifier.clickable { onModeChange(ImportMode.APPEND) }, style = contentBodyStyle)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = currentMode == ImportMode.OVERWRITE,
                onClick = { onModeChange(ImportMode.OVERWRITE) }
            )
            Text("覆盖 (清空现有课程，仅保留新课)", modifier = Modifier.clickable { onModeChange(ImportMode.OVERWRITE) }, style = contentBodyStyle)
        }
    }
}

@Composable
fun BackupCard(
    title: String,
    desc: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    showExport: Boolean = true,
    importLabel: String = "导入",
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = cardTitleStyle)
            Text(desc, style = cardSubtitleStyle)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showExport) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导出")
                    }
                }
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(if (showExport) 1f else 1f)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(importLabel)
                }
            }
        }
    }
}