package com.antgskds.calendarassistant.ui.page_display

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.core.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.core.note.NoteDocument
import com.antgskds.calendarassistant.core.note.NoteAttachmentStore
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.note.NoteParagraph
import com.antgskds.calendarassistant.core.note.NoteParagraphStyle
import com.antgskds.calendarassistant.core.note.NoteParagraphType
import com.antgskds.calendarassistant.core.note.NoteTextStyle
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarExtraHeight
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.ui.components.PlainNoteEditor
import com.antgskds.calendarassistant.ui.components.PlainNoteEditorController
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    initialNote: NoteEntity?,
    editorSessionKey: Int = 0,
    settings: MySettings,
    onDismiss: () -> Unit,
    onSave: (Long?, String, NoteDocument, Long?, (Long) -> Unit) -> Unit,
    onDelete: (Long, () -> Unit) -> Unit,
    onSetPinned: (Long, Boolean) -> Unit,
    onExportNote: (Long, android.net.Uri, (Result<Unit>) -> Unit) -> Unit,
    onExportMarkdownNote: (Long, android.net.Uri, (Result<Unit>) -> Unit) -> Unit,
    onImportNote: (android.net.Uri, (Result<Long>) -> Unit) -> Unit,
    onOpenImportedNote: (Long) -> Unit,
    onShowMessage: (String, ToastType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val savedTitle = initialNote?.title.orEmpty()
    val savedDocument = initialNote?.document() ?: NoteDocument()
    val editorController = remember(editorSessionKey) { PlainNoteEditorController() }
    var noteId by rememberSaveable(editorSessionKey) { mutableStateOf(initialNote?.id) }
    var titleText by rememberSaveable(editorSessionKey) { mutableStateOf(savedTitle) }
    var document by remember(editorSessionKey) { mutableStateOf(savedDocument) }
    var createdAtMillis by rememberSaveable(editorSessionKey) { mutableStateOf(initialNote?.createdAt) }
    var isAnalyzing by remember(editorSessionKey) { mutableStateOf(false) }
    var isMoreExpanded by remember(editorSessionKey) { mutableStateOf(false) }
    var hasDeleted by remember(editorSessionKey) { mutableStateOf(false) }
    var pendingDelete by remember(editorSessionKey) { mutableStateOf(false) }
    var pendingExportChoice by remember(editorSessionKey) { mutableStateOf(false) }
    var isPinned by rememberSaveable(editorSessionKey) { mutableStateOf(initialNote?.pinnedAt != null) }
    var previewImage by remember(editorSessionKey) { mutableStateOf<NoteParagraph?>(null) }
    var saveJob by remember(editorSessionKey) { mutableStateOf<Job?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        editorController.insertAttachment(uri)?.let { document = it }
    }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        editorController.insertAttachment(uri)?.let { document = it }
    }
    val noteExportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = noteId
        if (uri != null && id != null) {
            onExportNote(id, uri) { result ->
                onShowMessage(if (result.isSuccess) "便签导出成功" else "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}", if (result.isSuccess) ToastType.SUCCESS else ToastType.ERROR)
            }
        }
    }
    val noteExportZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = noteId
        if (uri != null && id != null) {
            onExportNote(id, uri) { result ->
                onShowMessage(if (result.isSuccess) "便签导出成功" else "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}", if (result.isSuccess) ToastType.SUCCESS else ToastType.ERROR)
            }
        }
    }
    val noteExportMarkdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val id = noteId
        if (uri != null && id != null) {
            onExportMarkdownNote(id, uri) { result ->
                onShowMessage(if (result.isSuccess) "Markdown 导出成功" else "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}", if (result.isSuccess) ToastType.SUCCESS else ToastType.ERROR)
            }
        }
    }
    val noteExportMarkdownZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = noteId
        if (uri != null && id != null) {
            onExportMarkdownNote(id, uri) { result ->
                onShowMessage(if (result.isSuccess) "Markdown 导出成功" else "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}", if (result.isSuccess) ToastType.SUCCESS else ToastType.ERROR)
            }
        }
    }
    val noteImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        onImportNote(uri) { result ->
            result.onSuccess { importedId ->
                onShowMessage("便签导入成功", ToastType.SUCCESS)
                onOpenImportedNote(importedId)
            }.onFailure { error ->
                onShowMessage("导入失败：${error.message ?: "未知错误"}", ToastType.ERROR)
            }
        }
    }

    fun isBlankNewNote(): Boolean {
        return noteId == null && titleText.isBlank() && document.plainText().isBlank() && document.todoCount() == 0
    }

    fun saveNow(force: Boolean = false) {
        if (hasDeleted) return
        if (isBlankNewNote()) return
        val idSnapshot = noteId
        val titleSnapshot = titleText
        val documentSnapshot = document
        val createdAtSnapshot = createdAtMillis ?: System.currentTimeMillis().also { createdAtMillis = it }
        onSave(idSnapshot, titleSnapshot, documentSnapshot, createdAtSnapshot) { savedId ->
            noteId = savedId
        }
    }

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(700L)
            saveNow()
        }
    }

    LaunchedEffect(titleText, document) {
        scheduleSave()
    }

    BackHandler(enabled = pendingExportChoice) {
        pendingExportChoice = false
    }

    BackHandler(enabled = previewImage != null) {
        previewImage = null
    }

    DisposableEffect(editorSessionKey) {
        onDispose {
            saveJob?.cancel()
            saveNow(force = true)
        }
    }

    fun saveAndDismiss() {
        saveJob?.cancel()
        saveNow(force = true)
        onDismiss()
    }

    fun runAiAnalyze() {
        if (!settings.isRecognitionConfigReady()) {
            haptics.error()
            onShowMessage(settings.recognitionConfigMissingMessage(), ToastType.ERROR)
            return
        }
        val text = buildString {
            if (titleText.isNotBlank()) appendLine(titleText.trim())
            val body = document.aiPlainText().trim()
            if (body.isNotBlank()) append(body)
        }.trim()
        if (text.isBlank()) {
            haptics.error()
            onShowMessage("便签内容为空", ToastType.INFO)
            return
        }
        scope.launch {
            haptics.confirm()
            isAnalyzing = true
            try {
                when (withContext(Dispatchers.IO) {
                    (context.applicationContext as App)
                        .recognitionCenter
                        .parseUserText(
                            text = text,
                            settings = settings,
                            context = context.applicationContext,
                            sourceType = RecognitionFeedbackSource.NOTE_SOURCE_TYPE,
                            sourceId = RecognitionFeedbackSource.NOTE_SOURCE_ID,
                            ingestRequested = true
                        )
                }) {
                    is AnalysisResult.Success -> onShowMessage("识别完成，正在保存...", ToastType.INFO)
                    is AnalysisResult.Empty -> Unit
                    is AnalysisResult.Failure -> Unit
                }
            } finally {
                isAnalyzing = false
            }
        }
    }

    fun openAttachment(paragraph: NoteParagraph) {
        if (paragraph.type == NoteParagraphType.IMAGE) {
            previewImage = paragraph
            return
        }
        val intent = NoteAttachmentStore.openAttachmentIntent(context, paragraph)
        if (intent == null) {
            onShowMessage("附件不存在", ToastType.ERROR)
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { onShowMessage("无法打开该附件", ToastType.ERROR) }
    }

    fun exportDefaultNote() {
        saveNow(force = true)
        val timestamp = System.currentTimeMillis()
        if (document.hasTransferAttachments()) {
            noteExportZipLauncher.launch("willdo_note_$timestamp.zip")
        } else {
            noteExportJsonLauncher.launch("willdo_note_$timestamp.json")
        }
    }

    fun exportMarkdownNote() {
        saveNow(force = true)
        val timestamp = System.currentTimeMillis()
        if (document.hasTransferAttachments()) {
            noteExportMarkdownZipLauncher.launch("willdo_note_$timestamp.zip")
        } else {
            noteExportMarkdownLauncher.launch("willdo_note_$timestamp.md")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.background)
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = { Text(if (noteId == null) "新建便签" else "编辑便签") },
                    navigationIcon = {
                        IconButton(onClick = ::saveAndDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    actions = {
                        if (noteId != null) {
                            IconButton(onClick = {
                                val id = noteId ?: return@IconButton
                                haptics.click()
                                isPinned = !isPinned
                                onSetPinned(id, isPinned)
                                onShowMessage(if (isPinned) "已置顶" else "已取消置顶", ToastType.INFO)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = if (isPinned) "取消置顶" else "置顶",
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(onClick = {
                                haptics.warning()
                                pendingDelete = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else {
                            Box(modifier = Modifier.width(48.dp))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                key(editorSessionKey) {
                    PlainNoteEditor(
                        title = titleText,
                        onTitleChange = { titleText = it },
                        document = document,
                        onDocumentChange = { document = it },
                        controller = editorController,
                        onOpenAttachment = ::openAttachment,
                        onImageShortcut = { imagePicker.launch("image/*") },
                        onFileShortcut = { attachmentPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxSize(),
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (pendingExportChoice) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { pendingExportChoice = false }
            )
        }

        NoteEditorFloatingToolbar(
            visible = true,
            expanded = isMoreExpanded,
            isAnalyzing = isAnalyzing,
            onExpandedChange = { isMoreExpanded = it },
            onTodoClick = {
                editorController.toggleCurrentTodo()?.let { document = it }
            },
            onImageClick = {
                imagePicker.launch("image/*")
            },
            onParagraphStyleClick = { style ->
                editorController.setParagraphStyle(style)?.let { document = it }
            },
            onTextStyleClick = { style ->
                editorController.applyTextStyle(style)?.let { document = it }
            },
            onDividerClick = {
                editorController.insertDivider()?.let { document = it }
            },
            onAnalyzeClick = {
                isMoreExpanded = false
                runAiAnalyze()
            },
            onAttachmentClick = {
                attachmentPicker.launch(arrayOf("*/*"))
            },
            onExportClick = {
                pendingExportChoice = true
            },
            onImportClick = {
                pendingExportChoice = false
                noteImportLauncher.launch(arrayOf("application/json", "application/zip", "text/markdown", "text/x-markdown", "text/plain", "*/*"))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + bottomInset)
                .imePadding()
        )

        NoteExportChoiceCard(
            visible = pendingExportChoice,
            onDefaultClick = {
                pendingExportChoice = false
                exportDefaultNote()
            },
            onMarkdownClick = {
                pendingExportChoice = false
                exportMarkdownNote()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp + bottomInset + IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight + 10.dp
                )
                .imePadding()
        )

        NoteImagePreviewOverlay(
            paragraph = previewImage,
            onDismiss = { previewImage = null },
            modifier = Modifier.align(Alignment.Center)
        )
    }

    com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard(
        visible = pendingDelete,
        title = "删除便签",
        content = "删除后无法恢复，确认删除这条便签吗？",
        confirmText = "删除",
        dismissText = "取消",
        isDestructive = true,
        isLoading = false,
        predictiveBackEnabled = settings.predictiveBackEnabled,
        onConfirm = {
            val id = noteId ?: return@PredictiveFloatingActionCard
            hasDeleted = true
            saveJob?.cancel()
            onDelete(id) { onDismiss() }
        },
        onDismiss = { pendingDelete = false },
        modifier = Modifier.padding(bottom = 24.dp + bottomInset)
    )

}

private enum class NoteToolbarMode {
    CATEGORIES,
    CATEGORY_TOOLS,
    MORE_ACTIONS
}

@Composable
private fun NoteExportChoiceCard(
    visible: Boolean,
    onDefaultClick: () -> Unit,
    onMarkdownClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(140)) + slideInVertically(animationSpec = tween(180)) { it / 4 },
        exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(animationSpec = tween(160)) { it / 4 },
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "导出便签",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "选择导出的文件格式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onMarkdownClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Markdown")
                }
                Button(onClick = onDefaultClick) {
                    Text("默认文件")
                }
            }
        }
    }
}

private enum class NoteToolbarCategory(
    val label: String,
    val contentDescription: String
) {
    HEADING("H", "标题"),
    TEXT("T", "文本"),
    INSERT("I", "插入")
}

private data class NoteToolbarContentState(
    val mode: NoteToolbarMode,
    val category: NoteToolbarCategory?
)

private data class NoteToolbarItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val textStyle: NoteTextStyle? = null,
    val paragraphStyle: NoteParagraphStyle? = null,
    val isTodo: Boolean = false,
    val isImage: Boolean = false,
    val isAttachment: Boolean = false,
    val isDivider: Boolean = false
)

private fun NoteDocument.aiPlainText(): String {
    return paragraphs
        .filterNot { it.type == NoteParagraphType.IMAGE || it.type == NoteParagraphType.FILE || it.type == NoteParagraphType.DIVIDER }
        .joinToString("\n") { it.text }
}

private fun NoteDocument.hasTransferAttachments(): Boolean {
    return paragraphs.any { it.attachmentPath.isNotBlank() }
}

private fun toolsForCategory(category: NoteToolbarCategory): List<NoteToolbarItem> = when (category) {
    NoteToolbarCategory.HEADING -> listOf(
        NoteToolbarItem(label = "正文", paragraphStyle = NoteParagraphStyle.BODY),
        NoteToolbarItem(label = "H1", paragraphStyle = NoteParagraphStyle.H1),
        NoteToolbarItem(label = "H2", paragraphStyle = NoteParagraphStyle.H2),
        NoteToolbarItem(label = "H3", paragraphStyle = NoteParagraphStyle.H3),
        NoteToolbarItem(label = "H4", paragraphStyle = NoteParagraphStyle.H4),
        NoteToolbarItem(label = "H5", paragraphStyle = NoteParagraphStyle.H5)
    )
    NoteToolbarCategory.TEXT -> listOf(
        NoteToolbarItem(label = "B", icon = Icons.Default.FormatBold, textStyle = NoteTextStyle.BOLD),
        NoteToolbarItem(label = "I", icon = Icons.Default.FormatItalic, textStyle = NoteTextStyle.ITALIC),
        NoteToolbarItem(label = "U", icon = Icons.Default.FormatUnderlined, textStyle = NoteTextStyle.UNDERLINE),
        NoteToolbarItem(label = "S", icon = Icons.Default.FormatStrikethrough, textStyle = NoteTextStyle.STRIKE),
        NoteToolbarItem(label = "引用", icon = Icons.Default.FormatQuote, paragraphStyle = NoteParagraphStyle.QUOTE),
        NoteToolbarItem(label = "代码", icon = Icons.Default.Code, paragraphStyle = NoteParagraphStyle.CODE),
        NoteToolbarItem(label = "无序", icon = Icons.Default.FormatListBulleted, paragraphStyle = NoteParagraphStyle.BULLET),
        NoteToolbarItem(label = "有序", icon = Icons.Default.FormatListNumbered, paragraphStyle = NoteParagraphStyle.ORDERED)
    )
    NoteToolbarCategory.INSERT -> listOf(
        NoteToolbarItem(label = "待办", icon = Icons.Default.CheckBox, isTodo = true),
        NoteToolbarItem(label = "图片", icon = Icons.Default.Image, isImage = true),
        NoteToolbarItem(label = "附件", icon = Icons.Default.Description, isAttachment = true),
        NoteToolbarItem(label = "分割线", icon = Icons.Default.HorizontalRule, isDivider = true)
    )
}

@Composable
private fun NoteEditorFloatingToolbar(
    visible: Boolean,
    expanded: Boolean,
    isAnalyzing: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTodoClick: () -> Unit,
    onImageClick: () -> Unit,
    onParagraphStyleClick: (NoteParagraphStyle) -> Unit,
    onTextStyleClick: (NoteTextStyle) -> Unit,
    onDividerClick: () -> Unit,
    onAnalyzeClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeCategory by remember { mutableStateOf<NoteToolbarCategory?>(null) }
    var toolbarMode by remember { mutableStateOf(NoteToolbarMode.CATEGORIES) }
    var measuredContentWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val baseToolbarHeight = IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight
    val toolbarHeight = baseToolbarHeight.coerceIn(60.dp, 72.dp)
    val iconSize = (toolbarHeight - 20.dp).coerceIn(40.dp, 52.dp)
    val outerHorizontalPadding = 10.dp
    val itemSpacing = 8.dp
    val leadingWidth = if (toolbarMode == NoteToolbarMode.MORE_ACTIONS) 0.dp else iconSize + itemSpacing
    val trailingWidth = iconSize

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val accentColor = MaterialTheme.colorScheme.primary
        val barColor = MaterialTheme.colorScheme.surface
        BoxWithConstraints {
            val maxToolbarWidth = maxWidth
            val fixedWidth = outerHorizontalPadding * 2 + leadingWidth + itemSpacing + trailingWidth
            val targetToolbarWidth = (fixedWidth + measuredContentWidth).coerceAtMost(maxToolbarWidth)
            val toolbarWidth by animateDpAsState(
                targetValue = targetToolbarWidth,
                animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f),
                label = "noteToolbarWidth"
            )
            val contentViewportWidth = (toolbarWidth - fixedWidth).coerceAtLeast(0.dp)

            Box(
                modifier = Modifier
                    .height(0.dp)
                    .alpha(0f)
                    .clipToBounds()
            ) {
                ToolbarContentRow(
                    state = NoteToolbarContentState(toolbarMode, activeCategory),
                    accentColor = accentColor,
                    itemHeight = iconSize,
                    isAnalyzing = isAnalyzing,
                    onCategoryClick = {},
                    onToolClick = {},
                    onAnalyzeClick = {},
                    onAttachmentClick = {},
                    onExportClick = {},
                    onImportClick = {},
                    modifier = Modifier.onSizeChanged { size ->
                        val width = with(density) { size.width.toDp() }
                        if (width != measuredContentWidth) measuredContentWidth = width
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = barColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .height(toolbarHeight)
                            .width(toolbarWidth)
                    ) {
                        Row(
                            modifier = Modifier
                                .height(toolbarHeight)
                                .width(toolbarWidth)
                                .padding(horizontal = outerHorizontalPadding, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (toolbarMode != NoteToolbarMode.MORE_ACTIONS) {
                                NoteToolbarLeadingButton(
                                    mode = toolbarMode,
                                    accentColor = accentColor,
                                    size = iconSize,
                                    onClick = {
                                        when (toolbarMode) {
                                            NoteToolbarMode.CATEGORIES -> {
                                                activeCategory = NoteToolbarCategory.HEADING
                                                toolbarMode = NoteToolbarMode.CATEGORY_TOOLS
                                            }
                                            NoteToolbarMode.CATEGORY_TOOLS -> {
                                                activeCategory = null
                                                toolbarMode = NoteToolbarMode.CATEGORIES
                                            }
                                            NoteToolbarMode.MORE_ACTIONS -> Unit
                                        }
                                    }
                                )
                            }

                            AnimatedToolbarContent(
                                state = NoteToolbarContentState(toolbarMode, activeCategory),
                                accentColor = accentColor,
                                itemHeight = iconSize,
                                modifier = Modifier.width(contentViewportWidth),
                                isAnalyzing = isAnalyzing,
                                onCategoryClick = { category ->
                                    activeCategory = category
                                    toolbarMode = NoteToolbarMode.CATEGORY_TOOLS
                                },
                                onToolClick = { item ->
                                    when {
                                        item.isTodo -> onTodoClick()
                                        item.isImage -> onImageClick()
                                        item.isAttachment -> onAttachmentClick()
                                        item.isDivider -> onDividerClick()
                                        item.paragraphStyle != null -> onParagraphStyleClick(item.paragraphStyle)
                                        item.textStyle != null -> onTextStyleClick(item.textStyle)
                                    }
                                    activeCategory = null
                                    toolbarMode = NoteToolbarMode.CATEGORIES
                                },
                                onAnalyzeClick = {
                                    activeCategory = null
                                    toolbarMode = NoteToolbarMode.CATEGORIES
                                    onExpandedChange(false)
                                    onAnalyzeClick()
                                },
                                onAttachmentClick = {
                                    activeCategory = null
                                    toolbarMode = NoteToolbarMode.CATEGORIES
                                    onExpandedChange(false)
                                    onAttachmentClick()
                                },
                                onExportClick = {
                                    activeCategory = null
                                    toolbarMode = NoteToolbarMode.CATEGORIES
                                    onExpandedChange(false)
                                    onExportClick()
                                },
                                onImportClick = {
                                    activeCategory = null
                                    toolbarMode = NoteToolbarMode.CATEGORIES
                                    onExpandedChange(false)
                                    onImportClick()
                                }
                            )

                            if (toolbarMode == NoteToolbarMode.MORE_ACTIONS) {
                                NoteToolbarTrailingBackButton(
                                    accentColor = accentColor,
                                    size = iconSize,
                                    onClick = {
                                        activeCategory = null
                                        toolbarMode = NoteToolbarMode.CATEGORIES
                                        onExpandedChange(false)
                                    }
                                )
                            } else {
                                NoteToolbarPlusButton(
                                    accentColor = accentColor,
                                    size = iconSize,
                                    onClick = {
                                        activeCategory = null
                                        toolbarMode = NoteToolbarMode.MORE_ACTIONS
                                        onExpandedChange(true)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarContentRow(
    state: NoteToolbarContentState,
    accentColor: Color,
    itemHeight: Dp,
    isAnalyzing: Boolean,
    onCategoryClick: (NoteToolbarCategory) -> Unit,
    onToolClick: (NoteToolbarItem) -> Unit,
    onAnalyzeClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (state.mode) {
            NoteToolbarMode.CATEGORIES -> {
                listOf(NoteToolbarCategory.TEXT, NoteToolbarCategory.INSERT).forEach { category ->
                    NoteToolbarCategoryChip(category, accentColor, itemHeight) { onCategoryClick(category) }
                }
            }
            NoteToolbarMode.CATEGORY_TOOLS -> {
                toolsForCategory(state.category ?: NoteToolbarCategory.HEADING).forEach { item ->
                    NoteToolbarChip(item, accentColor, itemHeight) { onToolClick(item) }
                }
            }
            NoteToolbarMode.MORE_ACTIONS -> {
                NoteToolbarAction("AI 分析", Icons.Default.AutoAwesome, isAnalyzing, accentColor, itemHeight, onAnalyzeClick)
                NoteToolbarAction("导出", Icons.Default.Download, false, accentColor, itemHeight, onExportClick)
                NoteToolbarAction("导入", Icons.Default.Upload, false, accentColor, itemHeight, onImportClick)
            }
        }
    }
}

@Composable
private fun AnimatedToolbarContent(
    state: NoteToolbarContentState,
    accentColor: Color,
    itemHeight: Dp,
    isAnalyzing: Boolean,
    onCategoryClick: (NoteToolbarCategory) -> Unit,
    onToolClick: (NoteToolbarItem) -> Unit,
    onAnalyzeClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (slideInHorizontally(animationSpec = tween(220)) { fullWidth -> fullWidth / 3 } + fadeIn(animationSpec = tween(160))) togetherWith
                (slideOutHorizontally(animationSpec = tween(220)) { fullWidth -> -fullWidth / 3 } + fadeOut(animationSpec = tween(140))) using
                SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(220) })
        },
        label = "noteToolbarContent",
        modifier = modifier
    ) { target ->
        ToolbarContentRow(
            state = target,
            accentColor = accentColor,
            itemHeight = itemHeight,
            isAnalyzing = isAnalyzing,
            onCategoryClick = onCategoryClick,
            onToolClick = onToolClick,
            onAnalyzeClick = onAnalyzeClick,
            onAttachmentClick = onAttachmentClick,
            onExportClick = onExportClick,
            onImportClick = onImportClick
        )
    }
}

@Composable
private fun NoteToolbarLeadingButton(
    mode: NoteToolbarMode,
    accentColor: Color,
    size: Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.15f),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (mode == NoteToolbarMode.CATEGORIES) {
                Text(
                    text = NoteToolbarCategory.HEADING.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回工具分类",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun NoteToolbarPlusButton(
    accentColor: Color,
    size: Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = accentColor,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "更多操作",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun NoteToolbarTrailingBackButton(
    accentColor: Color,
    size: Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.15f),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "返回工具分类",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun NoteToolbarCategoryChip(
    category: NoteToolbarCategory,
    accentColor: Color,
    height: Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.12f),
        modifier = Modifier.height(height)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = category.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = accentColor
            )
            Text(
                text = category.contentDescription,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun NoteToolbarChip(item: NoteToolbarItem, accentColor: Color, height: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.height(height)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item.icon?.let { icon ->
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteToolbarAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    loading: Boolean,
    accentColor: Color,
    height: Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.12f),
        modifier = Modifier.height(height)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = accentColor)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentColor
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun NoteImagePreviewOverlay(
    paragraph: NoteParagraph?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = paragraph != null,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = modifier
    ) {
        if (paragraph != null) {
            NoteImagePreviewContent(paragraph = paragraph, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun NoteImagePreviewContent(paragraph: NoteParagraph, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(paragraph.attachmentPath) {
        BitmapFactory.decodeFile(NoteAttachmentStore.fileForRelativePath(context, paragraph.attachmentPath).absolutePath)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            ComposeImage(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = paragraph.attachmentName.ifBlank { "图片预览" },
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(20.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "图片不存在",
                color = Color.White,
                modifier = Modifier.systemBarsPadding()
            )
        }
    }
}
