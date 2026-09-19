package com.antgskds.calendarassistant.feature.schedule.ui.connector

import com.antgskds.calendarassistant.shared.ui.material.component.AppFloatingActionButton
import com.antgskds.calendarassistant.shared.ui.material.component.AppFloatingActionButtonDefaults

import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.backup.courseimport.ImportMode
import com.antgskds.calendarassistant.feature.backup.courseimport.ParsedCourseImport
import com.antgskds.calendarassistant.feature.backup.ui.connector.CourseImportConfirmSheet
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.feature.schedule.domain.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.feature.schedule.domain.course.Course
import com.antgskds.calendarassistant.feature.schedule.ui.render.CourseEditDialog
import com.antgskds.calendarassistant.feature.schedule.ui.render.CourseManagerScreenContent
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.dialog.CourseItem
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.feature.schedule.ui.contract.CourseManagerUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.CourseManagerUiState
import com.antgskds.calendarassistant.shared.ui.material.component.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.shared.ui.material.component.ToastType
import com.antgskds.calendarassistant.shared.ui.material.component.UniversalToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CourseManagerScreen(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberAppHaptics(uiState.settings.hapticFeedbackEnabled)
    val courses = remember(uiState.rawEvents, uiState.settings) {
        CourseEventMapper.extractParentCourses(uiState.rawEvents, uiState.settings)
    }
    val maxNodes = remember(uiState.settings.timeTableJson) {
        TimeTableLayoutUtils.nodeCountFromJson(uiState.settings.timeTableJson)
    }
    val bottomInset = LocalAppPageBottomPadding.current

    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    var showImportMethodDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingParsedImport by remember { mutableStateOf<ParsedCourseImport?>(null) }
    var importMode by remember { mutableStateOf(ImportMode.APPEND) }
    var importSettings by remember { mutableStateOf(true) }
    var shareImportLoading by remember { mutableStateOf(false) }
    var importMethodError by remember { mutableStateOf<String?>(null) }

    fun showToast(message: String, type: ToastType) {
        when (type) {
            ToastType.ERROR -> haptics.error()
            ToastType.SUCCESS -> haptics.confirm()
            else -> Unit
        }
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun prepareExternalImport(parsed: ParsedCourseImport) {
        pendingParsedImport = parsed
        importMode = ImportMode.APPEND
        importSettings = parsed.canImportSettings && uiState.settings.semesterStartDate.isBlank()
        showImportConfirmDialog = true
    }

    fun readClipboardText(): String {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip ?: return ""
        if (clip.itemCount <= 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    }

    val importCoursesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    if (content.isBlank()) error("无法读取导入文件")

                    val externalResult = settingsViewModel.parseExternalCourseImport(content)
                    if (externalResult.isSuccess) {
                        withContext(Dispatchers.Main) {
                            prepareExternalImport(externalResult.getOrThrow())
                        }
                    } else {
                        val result = settingsViewModel.importCoursesData(content)
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                showToast("课程数据导入成功，共 ${settingsViewModel.getCoursesCount()} 门课程", ToastType.SUCCESS)
                            } else {
                                showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                            }
                        }
                    }
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("导入失败: ${error.message}", ToastType.ERROR)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CourseManagerScreenContent(
            state = CourseManagerUiState(
                courses = courses,
                maxNodes = maxNodes,
                timeTableJson = uiState.settings.timeTableJson,
                hapticEnabled = uiState.settings.hapticFeedbackEnabled,
                predictiveBackEnabled = uiState.settings.predictiveBackEnabled
            ),
            uiSize = uiSize,
            onAction = { action ->
                when (action) {
                    CourseManagerUiAction.ImportCourses -> {
                        importMethodError = null
                        showImportMethodDialog = true
                    }
                    is CourseManagerUiAction.AddCourse -> viewModel.addCourse(action.course)
                    is CourseManagerUiAction.UpdateCourse -> viewModel.updateCourse(action.course)
                    is CourseManagerUiAction.DeleteCourse -> viewModel.deleteCourse(action.course)
                }
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )

        PredictiveFloatingActionCard(
            visible = showImportMethodDialog,
            title = "选择导入方式",
            content = importMethodError?.let { "从口令导入失败：$it" }
                ?: "支持本应用备份、ICS、WakeUp 文件和分享口令",
            confirmText = "从口令",
            dismissText = "从文件",
            isLoading = shareImportLoading,
            allowDismissWhileLoading = false,
            predictiveBackEnabled = uiState.settings.predictiveBackEnabled,
            onConfirm = {
                haptics.confirm()
                importMethodError = null
                shareImportLoading = true
                val clipboardText = readClipboardText()
                scope.launch {
                    val result = settingsViewModel.fetchWakeUpShareImport(clipboardText)
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
                haptics.click()
                importMethodError = null
                showImportMethodDialog = false
                importCoursesLauncher.launch(arrayOf("*/*"))
            },
            onDismissRequest = {
                importMethodError = null
                showImportMethodDialog = false
            },
            modifier = Modifier.padding(bottom = bottomInset)
        )

        val parsedImport = pendingParsedImport
        if (showImportConfirmDialog && parsedImport != null) {
            CourseImportConfirmSheet(
                parsed = parsedImport,
                currentSemesterStartDate = uiState.settings.semesterStartDate,
                importMode = importMode,
                importSettings = importSettings,
                cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                onModeChange = { importMode = it },
                onImportSettingsChange = { importSettings = it },
                onDismiss = {
                    showImportConfirmDialog = false
                    pendingParsedImport = null
                },
                onConfirm = {
                    haptics.confirm()
                    settingsViewModel.importParsedCourseImport(
                        parsed = parsedImport,
                        mode = importMode,
                        importSettings = importSettings && parsedImport.canImportSettings
                    ) { result ->
                        if (result.isSuccess) {
                            showToast("成功导入 ${result.getOrNull()} 门课程", ToastType.SUCCESS)
                        } else {
                            showToast("导入失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                        }
                    }
                    showImportConfirmDialog = false
                    pendingParsedImport = null
                }
            )
        }
    }
}

@Composable
fun MaterialCourseManagerScreen(
    state: CourseManagerUiState,
    uiSize: Int = 2,
    onAction: (CourseManagerUiAction) -> Unit
) {
    val haptics = rememberAppHaptics(state.hapticEnabled)
    val courses = state.courses
    val maxNodes = state.maxNodes

    var showEditDialog by remember { mutableStateOf(false) }
    var courseToEdit by remember { mutableStateOf<Course?>(null) }
    val bottomInset = LocalAppPageBottomPadding.current

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 960.dp)
                .fillMaxSize(),
        ) {
            if (courses.isEmpty()) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Text("暂无课程，点击右下角添加", color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 144.dp + bottomInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(courses, key = { it.id }) { course ->
                        CourseItem(
                            course = course,
                            onDelete = { onAction(CourseManagerUiAction.DeleteCourse(course)) },
                            onClick = { courseToEdit = course; showEditDialog = true },
                            uiSize = uiSize
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp + bottomInset),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppFloatingActionButton(
                    onClick = { haptics.click(); onAction(CourseManagerUiAction.ImportCourses) },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "导入 WakeUp 课表",
                        modifier = Modifier.size(AppFloatingActionButtonDefaults.IconSize),
                    )
                }
                AppFloatingActionButton(
                    onClick = { haptics.click(); courseToEdit = null; showEditDialog = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加课程", modifier = Modifier.size(AppFloatingActionButtonDefaults.IconSize))
                }
            }
        }
    }

    if (showEditDialog) {
        CourseEditDialog(
            course = courseToEdit,
            maxNodes = maxNodes,
            timeTableJson = state.timeTableJson,
            hapticEnabled = state.hapticEnabled,
            predictiveBackEnabled = state.predictiveBackEnabled,
            onDismiss = { showEditDialog = false; courseToEdit = null },
            onConfirm = { course ->
                if (courseToEdit == null) {
                    onAction(CourseManagerUiAction.AddCourse(course))
                } else {
                    onAction(CourseManagerUiAction.UpdateCourse(course))
                }
                showEditDialog = false
                courseToEdit = null
            }
        )
    }
}
