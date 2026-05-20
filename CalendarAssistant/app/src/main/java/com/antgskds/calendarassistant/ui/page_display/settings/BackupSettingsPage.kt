package com.antgskds.calendarassistant.ui.page_display.settings

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.ai.AiPrompts
import com.antgskds.calendarassistant.core.center.ImportMode
import com.antgskds.calendarassistant.core.center.ParsedCourseImport
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.ui.components.FloatingActionCard
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsPage(viewModel: SettingsViewModel, mainViewModel: MainViewModel, uiSize: Int = 2) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentSettings by viewModel.settings.collectAsState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    var showImportMethodDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingParsedImport by remember { mutableStateOf<ParsedCourseImport?>(null) }
    var importMode by remember { mutableStateOf(ImportMode.APPEND) }
    var importSettings by remember { mutableStateOf(true) }
    var shareImportLoading by remember { mutableStateOf(false) }
    var importMethodError by remember { mutableStateOf<String?>(null) }
    val promptLocalVersion by mainViewModel.promptLocalVersion.collectAsState()
    val promptSource by mainViewModel.promptSource.collectAsState()
    val promptCheckInProgress by mainViewModel.promptCheckInProgress.collectAsState()

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

    // 监听 Prompt 检查反馈
    LaunchedEffect(mainViewModel) {
        mainViewModel.promptCheckFeedback.collect { feedback ->
            showToast(feedback.message, feedback.type)
        }
    }
    fun prepareExternalImport(parsed: ParsedCourseImport) {
        pendingParsedImport = parsed
        importMode = ImportMode.APPEND
        importSettings = parsed.canImportSettings && currentSettings.semesterStartDate.isBlank()
        showImportConfirmDialog = true
    }

    fun readClipboardText(): String {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip ?: return ""
        if (clip.itemCount <= 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
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
                        val externalResult = viewModel.parseExternalCourseImport(content)
                        if (externalResult.isSuccess) {
                            withContext(Dispatchers.Main) {
                                prepareExternalImport(externalResult.getOrThrow())
                            }
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
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(jsonData.toByteArray())
                    } ?: throw IOException("无法打开导出文件")
                    val totalCount = viewModel.getTotalEventsCount()
                    withContext(Dispatchers.Main) { showToast("日程数据导出成功，共 $totalCount 条日程", ToastType.SUCCESS) }
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
                            if (result.isSuccess) {
                                val importResult = result.getOrNull()
                                val successCount = importResult?.successCount ?: 0
                                val skippedCount = importResult?.skippedCount ?: 0
                                val archiveStatusUpdateCount = importResult?.archiveStatusUpdateCount ?: 0
                                val message = buildString {
                                    append("日程数据导入成功：新增 $successCount 条")
                                    if (skippedCount > 0) {
                                        append("，跳过 $skippedCount 条（重复）")
                                    }
                                    if (archiveStatusUpdateCount > 0) {
                                        append("，归档状态更新 $archiveStatusUpdateCount 条")
                                    }
                                }
                                showToast(message, ToastType.SUCCESS)
                            } else {
                                showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                            }
                        }
                    }
                } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("导入失败: ${e.message}", ToastType.ERROR) } }
            }
        }
    }

    // 提示词导出
    val exportPromptsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val jsonData = AiPrompts.exportToJson(context)
                    if (jsonData.isNotBlank()) {
                        context.contentResolver.openOutputStream(uri)?.use { output -> output.write(jsonData.toByteArray()) }
                        withContext(Dispatchers.Main) { showToast("提示词导出成功", ToastType.SUCCESS) }
                    } else {
                        withContext(Dispatchers.Main) { showToast("导出失败：无法获取提示词", ToastType.ERROR) }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("导出失败: ${e.message}", ToastType.ERROR) }
                }
            }
        }
    }

    // 提示词导入
    val importPromptsLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val success = AiPrompts.importFromJson(context, content)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                showToast("提示词导入成功", ToastType.SUCCESS)
                                mainViewModel.refreshPromptInfo()
                            } else {
                                showToast("导入失败：格式无效", ToastType.ERROR)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast("导入失败: ${e.message}", ToastType.ERROR) }
                }
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
                onImport = {
                    importMethodError = null
                    showImportMethodDialog = true
                },
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

            Text("提示词管理", style = sectionTitleStyle)

            val promptSourceText = if (promptSource == AiPrompts.PromptSource.CLOUD) {
                "云端 v$promptLocalVersion"
            } else {
                "本地"
            }
            BackupCard(
                title = "提示词来源：$promptSourceText",
                desc = "导入/导出提示词，或检查云端更新",
                onExport = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    exportPromptsLauncher.launch("ai_prompts_$timestamp.json")
                },
                onImport = { importPromptsLauncher.launch(arrayOf("application/json")) },
                swapButtons = true, // 导出在左，导入在右
                extraButton = {
                    OutlinedButton(
                        onClick = { mainViewModel.checkPromptUpdatesManually() },
                        enabled = !promptCheckInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (promptCheckInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("检查中...")
                        } else {
                            Text("检查更新")
                        }
                    }
                },
                extraButtonText = "检查更新",
                extraButtonOnTop = false,
                cardTitleStyle = cardTitleStyle,
                cardSubtitleStyle = cardSubtitleStyle
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )

        AnimatedVisibility(
            visible = showImportMethodDialog,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (showImportMethodDialog && !shareImportLoading) {
                                showImportMethodDialog = false
                                importMethodError = null
                            }
                        }
                    )
            )
        }

        FloatingActionCard(
            visible = showImportMethodDialog,
            title = "选择导入方式",
            content = importMethodError?.let { "从口令导入失败：$it" }
                ?: "文件导入支持本应用备份、WakeUp 文件和 ICS；口令导入会读取剪贴板中的 WakeUp 分享文本。",
            confirmText = "从口令",
            dismissText = "从文件",
            isLoading = shareImportLoading,
            onConfirm = {
                importMethodError = null
                shareImportLoading = true
                val clipboardText = readClipboardText()
                scope.launch {
                    val result = viewModel.fetchWakeUpShareImport(clipboardText)
                    shareImportLoading = false
                    if (result.isSuccess) {
                        showImportMethodDialog = false
                        prepareExternalImport(result.getOrThrow())
                    } else {
                        importMethodError = result.exceptionOrNull()?.message ?: "WakeUp 口令导入失败"
                    }
                }
            },
            onDismiss = {
                importMethodError = null
                showImportMethodDialog = false
                importCoursesLauncher.launch(arrayOf("*/*"))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset)
        )

        val parsedImport = pendingParsedImport
        if (showImportConfirmDialog && parsedImport != null) {
            CourseImportConfirmSheet(
                parsed = parsedImport,
                currentSemesterStartDate = currentSettings.semesterStartDate,
                importMode = importMode,
                importSettings = importSettings,
                cardValueStyle = cardValueStyle,
                cardSubtitleStyle = cardSubtitleStyle,
                onModeChange = { importMode = it },
                onImportSettingsChange = { importSettings = it },
                onDismiss = {
                    showImportConfirmDialog = false
                    pendingParsedImport = null
                },
                onConfirm = {
                    viewModel.importParsedCourseImport(parsedImport, importMode, importSettings && parsedImport.canImportSettings) { result ->
                        if (result.isSuccess) showToast("成功导入 ${result.getOrNull()} 门课程", ToastType.SUCCESS)
                        else showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                    }
                    showImportConfirmDialog = false
                    pendingParsedImport = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseImportConfirmSheet(
    parsed: ParsedCourseImport,
    currentSemesterStartDate: String,
    importMode: ImportMode,
    importSettings: Boolean,
    cardValueStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    onModeChange: (ImportMode) -> Unit,
    onImportSettingsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text("导入外部课表", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "确认导入内容，并选择是否同步课表设置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ImportSummaryCard(parsed, cardValueStyle)

                if (currentSemesterStartDate.isNotBlank() && parsed.semesterStartDate != null &&
                    currentSemesterStartDate != parsed.semesterStartDate
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("当前 App 开学日期：$currentSemesterStartDate", style = cardValueStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("导入来源开学日期：${parsed.semesterStartDate}", style = cardValueStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                Text(
                    text = "同步设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = parsed.canImportSettings) { onImportSettingsChange(!importSettings) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = importSettings && parsed.canImportSettings,
                        enabled = parsed.canImportSettings,
                        onCheckedChange = onImportSettingsChange
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("同步课表设置", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (parsed.canImportSettings) "同步已检测到的开学日期、总周数和每节课时间" else "未检测到可同步的课表设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "导入方式",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                ImportOptionRadio(importMode, MaterialTheme.typography.bodyLarge, onModeChange)

                if (importMode == ImportMode.OVERWRITE) {
                    Text(
                        "覆盖模式会清空当前所有课程，仅保留本次导入内容。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()
                CoursePreviewSection(parsed.courses, cardValueStyle, cardSubtitleStyle)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onConfirm,
                enabled = parsed.courses.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导入")
            }
        }
    }
}

@Composable
private fun ImportSummaryCard(parsed: ParsedCourseImport, cardValueStyle: TextStyle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("导入摘要", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SummaryRow("来源", parsed.sourceName, cardValueStyle)
            SummaryRow("课程", "${parsed.courses.size} 门", cardValueStyle)
            SummaryRow("开学日期", parsed.semesterStartDate ?: "未检测到", cardValueStyle)
            SummaryRow("总周数", parsed.totalWeeks?.let { "$it 周" } ?: "未检测到", cardValueStyle)
            SummaryRow("作息时间", if (parsed.hasTimeTable) "可同步 ${parsed.timeNodeCount} 节" else "未检测到", cardValueStyle)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, style: TextStyle) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = style, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CoursePreviewSection(courses: List<Course>, cardValueStyle: TextStyle, cardSubtitleStyle: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("课程预览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        courses.take(5).forEach { course ->
            Column {
                Text(course.name, style = cardValueStyle, fontWeight = FontWeight.Medium)
                Text(course.previewText(), style = cardSubtitleStyle)
            }
        }
        if (courses.size > 5) {
            Text("还有 ${courses.size - 5} 门课程将在导入时一并处理", style = cardSubtitleStyle)
        }
    }
}

private fun Course.previewText(): String {
    return buildString {
        append(weekdayText(dayOfWeek))
        append(" 第")
        append(startNode)
        append('-')
        append(endNode)
        append("节")
        append(" · 第")
        append(startWeek)
        append('-')
        append(endWeek)
        append("周")
        weekTypeText(weekType).takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        if (location.isNotBlank()) append(" · ").append(location)
        if (teacher.isNotBlank()) append(" · ").append(teacher)
    }
}

private fun weekdayText(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "周$dayOfWeek"
    }
}

private fun weekTypeText(weekType: Int): String {
    return when (weekType) {
        1 -> "单周"
        2 -> "双周"
        else -> ""
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
            Text("追加 (保留现有课程，追加新课)", modifier = Modifier.clickable { onModeChange(ImportMode.APPEND) }, style = contentBodyStyle)
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
    extraButton: @Composable (() -> Unit)? = null,
    extraButtonText: String = "检查更新",
    extraButtonOnTop: Boolean = false,
    swapButtons: Boolean = false, // 是否交换导出/导入按钮顺序（导出在左，导入在右）
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
            if (extraButton != null && extraButtonOnTop) {
                extraButton()
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(importLabel)
                    }
                    if (showExport) {
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出")
                        }
                    }
                }
            } else if (extraButton != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (swapButtons) {
                        // 导出在左，导入在右
                        if (showExport) {
                            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("导出")
                            }
                        }
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(importLabel)
                        }
                    } else {
                        // 导入在左，导出在右
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(importLabel)
                        }
                        if (showExport) {
                            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("导出")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                extraButton()
            } else {
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
}
