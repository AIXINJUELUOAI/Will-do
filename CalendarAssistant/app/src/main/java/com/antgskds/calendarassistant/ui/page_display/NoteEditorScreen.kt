package com.antgskds.calendarassistant.ui.page_display

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.calendar.models.EventAttachment
import com.antgskds.calendarassistant.core.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.core.ai.recognitionConfigMissingMessage
import com.antgskds.calendarassistant.core.note.createNoteEvent
import com.antgskds.calendarassistant.core.note.extractNoteAttachmentIds
import com.antgskds.calendarassistant.core.note.noteMarkdown
import com.antgskds.calendarassistant.core.note.withNoteMarkdown
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.BlockNoteEditor
import com.antgskds.calendarassistant.ui.components.BlockNoteEditorController
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarExtraHeight
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.ui.components.NoteMarkdownTool
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    initialNote: Event?,
    editorSessionKey: Int = 0,
    currentEventsCount: Int,
    settings: MySettings,
    onDismiss: () -> Unit,
    onSave: (Event, List<String>) -> Unit,
    onAddAttachment: suspend (Long, Uri) -> EventAttachment,
    onAddPendingAttachment: suspend (String, Uri) -> EventAttachment,
    onDeletePendingAttachments: suspend (String) -> Unit,
    onLoadAttachments: suspend (Long) -> List<EventAttachment>,
    onLoadAttachmentsByIds: suspend (List<Long>) -> List<EventAttachment>,
    onOpenAttachment: suspend (EventAttachment) -> Boolean,
    onDelete: (Event) -> Unit,
    onShowMessage: (String, ToastType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val savedTitle = initialNote?.title?.takeUnless { it.isBlank() } ?: ""
    val savedMarkdown = initialNote?.noteMarkdown().orEmpty()
    val fallbackColor = MaterialTheme.colorScheme.primary
    val editorController = remember(editorSessionKey) { BlockNoteEditorController() }
    var titleText by rememberSaveable(editorSessionKey) { mutableStateOf(savedTitle) }
    var bodyText by rememberSaveable(editorSessionKey) { mutableStateOf(savedMarkdown) }
    var isAnalyzing by remember(editorSessionKey) { mutableStateOf(false) }
    var hasSavedOnExit by remember(editorSessionKey) { mutableStateOf(false) }
    var skipSaveOnExit by remember(editorSessionKey) { mutableStateOf(false) }
    var isMoreExpanded by remember(editorSessionKey) { mutableStateOf(false) }
    var pendingAttachmentKey by rememberSaveable(editorSessionKey) { mutableStateOf("") }
    var pendingAttachmentKeys by remember(editorSessionKey) { mutableStateOf(setOf<String>()) }
    val noteAttachments = remember(editorSessionKey) { mutableStateListOf<EventAttachment>() }
    val pendingAttachmentKeyPrefix = remember(editorSessionKey) {
        "note_pending_${System.currentTimeMillis()}_${editorSessionKey}"
    }

    fun ensurePendingAttachmentKey(): String {
        if (pendingAttachmentKey.isBlank()) {
            pendingAttachmentKey = "${pendingAttachmentKeyPrefix}_${pendingAttachmentKeys.size + 1}"
        }
        return pendingAttachmentKey
    }

    fun refreshAttachments(markdown: String = bodyText) {
        scope.launch {
            val eventId = initialNote?.id
            val loaded = if (eventId != null && eventId > 0L) {
                onLoadAttachments(eventId)
            } else {
                onLoadAttachmentsByIds(extractNoteAttachmentIds(markdown))
            }
            noteAttachments.clear()
            noteAttachments.addAll(loaded)
        }
    }

    LaunchedEffect(initialNote?.id, editorSessionKey) {
        refreshAttachments(savedMarkdown)
    }

    fun buildSavedNote(markdownOverride: String? = null): Event {
        val markdown = (markdownOverride ?: bodyText).trimEnd()
        val finalTitle = titleText.ifBlank { "无标题" }
        return if (initialNote == null) {
            val color = if (EventColors.isNotEmpty()) {
                EventColors[currentEventsCount % EventColors.size]
            } else {
                fallbackColor
            }
            createNoteEvent(title = finalTitle, markdown = markdown, color = color)
        } else {
            initialNote.withNoteMarkdown(title = finalTitle, markdown = markdown)
        }
    }

    fun exportMarkdown(uri: Uri) {
        val committedMarkdown = editorController.commit() ?: bodyText
        bodyText = committedMarkdown
        val exportText = buildString {
            val title = titleText.trim().ifBlank { "无标题" }
            append("# ").appendLine(title)
            if (committedMarkdown.isNotBlank()) {
                appendLine()
                append(committedMarkdown.trimEnd())
            }
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(exportText.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.onSuccess {
                withContext(Dispatchers.Main) { onShowMessage("已导出 Markdown", ToastType.SUCCESS) }
            }.onFailure {
                withContext(Dispatchers.Main) { onShowMessage("导出失败：${it.message ?: "未知错误"}", ToastType.ERROR) }
            }
        }
    }

    fun runAiAnalyze() {
        val committedMarkdown = editorController.commit() ?: bodyText
        bodyText = committedMarkdown
        if (!settings.isRecognitionConfigReady()) {
            haptics.error()
            onShowMessage(settings.recognitionConfigMissingMessage(), ToastType.ERROR)
            return
        }
        val markdown = committedMarkdown.trim()
        val text = buildString {
            if (titleText.isNotBlank()) appendLine(titleText.trim())
            if (markdown.isNotBlank()) append(markdown)
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

    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> uri?.let { exportMarkdown(it) } }

    fun insertAttachmentMarkdown(attachment: EventAttachment, forceImage: Boolean) {
        val id = attachment.id ?: return
        val label = attachment.displayName.ifBlank { "附件$id" }.replace("[", "(").replace("]", ")")
        val markdown = if (forceImage || attachment.mimeType.startsWith("image/", ignoreCase = true)) {
            "![$label](willdo-attachment://$id)"
        } else {
            "[$label](willdo-attachment://$id)"
        }
        val updated = editorController.insertMarkdownBlock(markdown) ?: bodyText
        bodyText = updated
        if (noteAttachments.none { it.id == attachment.id }) noteAttachments.add(attachment)
        refreshAttachments(updated)
    }

    fun addAttachmentFromUri(uri: Uri, forceImage: Boolean) {
        scope.launch {
            runCatching {
                val existingId = initialNote?.id
                if (existingId != null && existingId > 0L) {
                    onAddAttachment(existingId, uri)
                } else {
                    val key = ensurePendingAttachmentKey()
                    val attachment = onAddPendingAttachment(key, uri)
                    pendingAttachmentKeys = pendingAttachmentKeys + key
                    attachment
                }
            }.onSuccess { attachment ->
                insertAttachmentMarkdown(attachment, forceImage)
                onShowMessage("已插入附件", ToastType.SUCCESS)
            }.onFailure {
                haptics.error()
                onShowMessage("插入附件失败：${it.message ?: "未知错误"}", ToastType.ERROR)
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { addAttachmentFromUri(it, forceImage = true) } }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { addAttachmentFromUri(it, forceImage = false) } }

    fun saveIfNeeded() {
        if (hasSavedOnExit) return
        hasSavedOnExit = true
        if (skipSaveOnExit) return
        val committedMarkdown = editorController.commit() ?: bodyText
        bodyText = committedMarkdown
        val currentMarkdown = committedMarkdown.trimEnd()
        val hasPendingAttachments = pendingAttachmentKeys.isNotEmpty()
        val isDirty = titleText != savedTitle || currentMarkdown != savedMarkdown || hasPendingAttachments
        if (isDirty && !(initialNote == null && titleText.isBlank() && currentMarkdown.isBlank() && !hasPendingAttachments)) {
            onSave(buildSavedNote(committedMarkdown), pendingAttachmentKeys.toList())
        } else if (initialNote == null && hasPendingAttachments) {
            pendingAttachmentKeys.forEach { key -> scope.launch { onDeletePendingAttachments(key) } }
        }
    }

    fun saveAndDismiss() {
        saveIfNeeded()
        onDismiss()
    }

    DisposableEffect(editorSessionKey) {
        onDispose { saveIfNeeded() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = { Text(if (initialNote == null) "新建便签" else "编辑便签") },
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
                        if (initialNote != null) {
                            IconButton(onClick = {
                                haptics.warning()
                                skipSaveOnExit = true
                                hasSavedOnExit = true
                                onDelete(initialNote)
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
                    BlockNoteEditor(
                        title = titleText,
                        onTitleChange = { titleText = it },
                        markdown = bodyText,
                        onMarkdownChange = { bodyText = it },
                        controller = editorController,
                        modifier = Modifier.fillMaxSize(),
                        textColor = MaterialTheme.colorScheme.onSurface,
                        attachments = noteAttachments,
                        onOpenAttachment = { attachment ->
                            scope.launch {
                                if (!onOpenAttachment(attachment)) {
                                    onShowMessage("无法打开附件", ToastType.ERROR)
                                }
                            }
                        }
                    )
                }
            }
        }

        NoteEditorFloatingToolbar(
            visible = true,
            expanded = isMoreExpanded,
            isAnalyzing = isAnalyzing,
            onExpandedChange = { isMoreExpanded = it },
            onToolClick = { tool ->
                when (tool) {
                    NoteMarkdownTool.IMAGE -> imagePickerLauncher.launch(arrayOf("image/*"))
                    NoteMarkdownTool.FILE -> filePickerLauncher.launch(arrayOf("*/*"))
                    else -> {
                        val updated = editorController.applyMarkdownTool(tool) ?: bodyText
                        bodyText = updated
                    }
                }
            },
            onAnalyzeClick = {
                isMoreExpanded = false
                runAiAnalyze()
            },
            onExportClick = {
                isMoreExpanded = false
                val fileName = "${titleText.ifBlank { "note" }.sanitizeMarkdownFileName()}.md"
                exportMarkdownLauncher.launch(fileName)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + bottomInset)
                .imePadding()
        )
    }
}

private enum class NoteToolbarCategory(
    val label: String,
    val contentDescription: String
) {
    HEADING("H", "标题"),
    TEXT("T", "文本"),
    LIST("L", "列表"),
    INSERT("I", "插入")
}

private enum class NoteToolbarMode {
    CATEGORIES,
    CATEGORY_TOOLS,
    MORE_ACTIONS
}

private data class NoteToolbarContentState(
    val mode: NoteToolbarMode,
    val category: NoteToolbarCategory?
)

private data class NoteToolbarItem(
    val label: String,
    val tool: NoteMarkdownTool
)

private fun toolsForCategory(category: NoteToolbarCategory): List<NoteToolbarItem> = when (category) {
    NoteToolbarCategory.HEADING -> listOf(
        NoteToolbarItem("H1", NoteMarkdownTool.H1),
        NoteToolbarItem("H2", NoteMarkdownTool.H2)
    )
    NoteToolbarCategory.TEXT -> listOf(
        NoteToolbarItem("引用", NoteMarkdownTool.QUOTE),
        NoteToolbarItem("分割线", NoteMarkdownTool.DIVIDER),
        NoteToolbarItem("代码", NoteMarkdownTool.CODE)
    )
    NoteToolbarCategory.LIST -> listOf(
        NoteToolbarItem("待办", NoteMarkdownTool.TASK),
        NoteToolbarItem("无序", NoteMarkdownTool.BULLET),
        NoteToolbarItem("有序", NoteMarkdownTool.ORDERED)
    )
    NoteToolbarCategory.INSERT -> listOf(
        NoteToolbarItem("图片", NoteMarkdownTool.IMAGE),
        NoteToolbarItem("文件", NoteMarkdownTool.FILE)
    )
}

@Composable
private fun NoteEditorFloatingToolbar(
    visible: Boolean,
    expanded: Boolean,
    isAnalyzing: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToolClick: (NoteMarkdownTool) -> Unit,
    onAnalyzeClick: () -> Unit,
    onExportClick: () -> Unit,
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
                    onExportClick = {},
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
                                    onToolClick = { tool ->
                                        onToolClick(tool)
                                        activeCategory = null
                                        toolbarMode = NoteToolbarMode.CATEGORIES
                                    },
                                    onAnalyzeClick = {
                                        toolbarMode = NoteToolbarMode.CATEGORIES
                                        onExpandedChange(false)
                                        onAnalyzeClick()
                                    },
                                    onExportClick = {
                                        toolbarMode = NoteToolbarMode.CATEGORIES
                                        onExpandedChange(false)
                                        onExportClick()
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
    onToolClick: (NoteMarkdownTool) -> Unit,
    onAnalyzeClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (state.mode) {
            NoteToolbarMode.CATEGORIES -> {
                listOf(NoteToolbarCategory.TEXT, NoteToolbarCategory.LIST, NoteToolbarCategory.INSERT).forEach { category ->
                    NoteToolbarCategoryChip(category, accentColor, itemHeight) { onCategoryClick(category) }
                }
            }
            NoteToolbarMode.CATEGORY_TOOLS -> {
                toolsForCategory(state.category ?: NoteToolbarCategory.HEADING).forEach { item ->
                    NoteToolbarChip(item.label, accentColor, itemHeight) { onToolClick(item.tool) }
                }
            }
            NoteToolbarMode.MORE_ACTIONS -> {
                NoteToolbarAction("AI 分析", isAnalyzing, accentColor, itemHeight, onAnalyzeClick)
                NoteToolbarAction("导出", false, accentColor, itemHeight, onExportClick)
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
    onToolClick: (NoteMarkdownTool) -> Unit,
    onAnalyzeClick: () -> Unit,
    onExportClick: () -> Unit,
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
            onExportClick = onExportClick
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
private fun NoteToolbarChip(label: String, accentColor: Color, height: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.height(height)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 15.dp).height(height),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
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
                    imageVector = if (label.contains("导出")) Icons.Default.FileDownload else Icons.Default.AutoAwesome,
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

private fun String.sanitizeMarkdownFileName(): String {
    return trim()
        .ifBlank { "note" }
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
        .take(40)
        .ifBlank { "note" }
}
