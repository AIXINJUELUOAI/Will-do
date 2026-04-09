package com.antgskds.calendarassistant.ui.page_display

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.core.ai.activeAiConfig
import com.antgskds.calendarassistant.core.ai.convertAiEventToMyEvent
import com.antgskds.calendarassistant.core.ai.isConfigured
import com.antgskds.calendarassistant.core.ai.missingConfigMessage
import com.antgskds.calendarassistant.core.note.createNoteEvent
import com.antgskds.calendarassistant.core.note.noteMarkdown
import com.antgskds.calendarassistant.core.note.withNoteMarkdown
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.BlockNoteEditor
import com.antgskds.calendarassistant.ui.components.BlockNoteEditorController
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    initialNote: MyEvent?,
    currentEventsCount: Int,
    settings: MySettings,
    onDismiss: () -> Unit,
    onSave: (MyEvent) -> Unit,
    onDelete: (MyEvent) -> Unit,
    onAnalyzeResult: (MyEvent) -> Unit,
    onShowMessage: (String, ToastType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedTitle = initialNote?.title?.takeUnless { it.isBlank() } ?: ""
    val savedMarkdown = initialNote?.noteMarkdown().orEmpty()
    val fallbackColor = MaterialTheme.colorScheme.primary
    val editorController = remember { BlockNoteEditorController() }
    var titleText by remember(initialNote?.id) { mutableStateOf(savedTitle) }
    var bodyText by remember(initialNote?.id) { mutableStateOf(savedMarkdown) }
    var isAnalyzing by remember { mutableStateOf(false) }

    fun buildSavedNote(markdownOverride: String? = null): MyEvent {
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

    fun saveAndDismiss() {
        val committedMarkdown = editorController.commit() ?: bodyText
        bodyText = committedMarkdown
        val currentMarkdown = committedMarkdown.trimEnd()
        val isDirty = titleText != savedTitle || currentMarkdown != savedMarkdown
        if (isDirty && !(initialNote == null && titleText.isBlank() && currentMarkdown.isBlank())) {
            onSave(buildSavedNote(committedMarkdown))
        }
        onDismiss()
    }

    BackHandler(onBack = ::saveAndDismiss)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        topBar = {
            CenterAlignedTopAppBar(
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
                        IconButton(onClick = { onDelete(initialNote) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "删除",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.width(48.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val config = settings.activeAiConfig()
                    if (!config.isConfigured()) {
                        onShowMessage(config.missingConfigMessage(), ToastType.ERROR)
                        return@FloatingActionButton
                    }
                    val markdown = bodyText.trim()
                    val text = buildString {
                        if (titleText.isNotBlank()) appendLine(titleText.trim())
                        if (markdown.isNotBlank()) append(markdown)
                    }.trim()
                    if (text.isBlank()) {
                        onShowMessage("便签内容为空", ToastType.INFO)
                        return@FloatingActionButton
                    }
                    scope.launch {
                        isAnalyzing = true
                        try {
                            when (val result = withContext(Dispatchers.IO) {
                                RecognitionProcessor.parseUserText(text, settings, context.applicationContext)
                            }) {
                                is AnalysisResult.Success -> {
                                    onAnalyzeResult(
                                        convertAiEventToMyEvent(
                                            eventData = result.data,
                                            currentEventsCount = currentEventsCount,
                                            sourceImagePath = null
                                        )
                                    )
                                }

                                is AnalysisResult.Empty -> onShowMessage(result.message, ToastType.INFO)
                                is AnalysisResult.Failure -> onShowMessage(result.failure.fullMessage(), ToastType.ERROR)
                            }
                        } finally {
                            isAnalyzing = false
                        }
                    }
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 28.dp)
                    .size(60.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI 分析",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            BlockNoteEditor(
                title = titleText,
                onTitleChange = { titleText = it },
                markdown = bodyText,
                onMarkdownChange = { bodyText = it },
                controller = editorController,
                modifier = Modifier.fillMaxSize(),
                textColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
