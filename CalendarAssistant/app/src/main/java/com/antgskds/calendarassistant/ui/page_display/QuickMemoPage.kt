package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoTodoState
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoTranscriptionStatus
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoType
import com.antgskds.calendarassistant.core.quickmemo.audio.AudioPlaybackState
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val quickMemoTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val quickMemoDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

@Composable
fun QuickMemoPage(
    viewModel: MainViewModel,
    searchQuery: String = "",
    extraBottomPadding: Dp = 0.dp,
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val bottomSafePadding = 112.dp + extraBottomPadding
    val filteredMemos = remember(quickMemos, searchQuery) {
        quickMemos.filter { memo ->
            searchQuery.isBlank() || memo.bodyText.contains(searchQuery, ignoreCase = true)
        }
    }
    val suggestionsByMemo = remember(suggestions) {
        suggestions
            .filter { it.status == QuickMemoSuggestionStatus.PENDING }
            .groupBy { it.quickMemoId }
    }

    if (filteredMemos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "还没有随口记" else "未找到相关随口记",
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = if (searchQuery.isBlank()) {
                        "从悬浮窗快速记下一句话，之后可以继续整理。"
                    } else {
                        "换个关键词试试，搜索会匹配随口记正文。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 10.dp, bottom = bottomSafePadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filteredMemos, key = { it.id ?: it.hashCode().toLong() }) { memo ->
            QuickMemoCard(
                memo = memo,
                suggestions = memo.id?.let { suggestionsByMemo[it] }.orEmpty(),
                playbackState = playbackState,
                onSaveBody = { body -> memo.id?.let { viewModel.updateQuickMemoBody(it, body) } },
                onToggleTodo = { memo.id?.let { viewModel.toggleQuickMemoTodoCompletion(it) } },
                onToggleAudio = { path -> viewModel.toggleAudioPlayback(path) },
                onRetryTranscription = { memo.id?.let { viewModel.retryQuickMemoTranscription(it) } },
                onCreateSuggestion = { suggestion ->
                    suggestion.id?.let { id -> viewModel.createEventFromQuickMemoSuggestion(id) }
                },
                onLongPress = { onPendingDeleteChange(memo) },
                hapticEnabled = hapticEnabled,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickMemoCard(
    memo: QuickMemoEntity,
    suggestions: List<QuickMemoSuggestionEntity>,
    playbackState: AudioPlaybackState,
    onSaveBody: (String) -> Unit,
    onToggleTodo: () -> Unit,
    onToggleAudio: (String?) -> Unit,
    onRetryTranscription: () -> Unit,
    onCreateSuggestion: (QuickMemoSuggestionEntity) -> Unit,
    onLongPress: () -> Unit,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isEditing by remember(memo.id, memo.updatedAt) { mutableStateOf(false) }
    var isExpanded by remember(memo.id) { mutableStateOf(false) }
    var draftBody by remember(memo.id, memo.updatedAt) { mutableStateOf(memo.bodyText) }
    val isTodo = memo.todoState == QuickMemoTodoState.ACTIVE || memo.todoState == QuickMemoTodoState.COMPLETED
    val isCompleted = memo.todoState == QuickMemoTodoState.COMPLETED
    val isPlaying = memo.audioPath != null && playbackState.audioPath == memo.audioPath && playbackState.isPlaying
    val warmBg = Color(0xFFFFF8E1)
    val warmLine = Color(0xFFF2B705)
    val cardBg = if (isTodo) warmBg.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surface
    val bodyText = memo.bodyText.ifBlank { if (memo.type == QuickMemoType.VOICE) voiceFallbackText(memo) else "空白随口记" }
    val offsetX = remember(memo.id) { Animatable(0f) }
    val actionWidthPx = with(density) { 112.dp.toPx() }
    val longSwipePx = with(density) { 160.dp.toPx() }
    val swipeSpec = spring<Float>(dampingRatio = 0.82f, stiffness = 620f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isTodo && offsetX.value < -1f) {
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickMemoActionPill(
                        text = if (isCompleted) "撤回" else "完成",
                        filled = true,
                        alpha = ((-offsetX.value) / actionWidthPx).coerceIn(0f, 1f),
                        onClick = {
                            haptics.confirm()
                            onToggleTodo()
                            scope.launch { offsetX.animateTo(0f, swipeSpec) }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    QuickMemoActionPill(
                        text = "编辑",
                        alpha = ((-offsetX.value) / actionWidthPx).coerceIn(0f, 1f),
                        onClick = {
                            haptics.click()
                            isExpanded = true
                            isEditing = true
                            scope.launch { offsetX.animateTo(0f, swipeSpec) }
                        }
                    )
                }
            }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (isTodo && !isEditing) {
                        Modifier.pointerInput(memo.id, isCompleted) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        when {
                                            -offsetX.value >= longSwipePx -> {
                                                haptics.confirm()
                                                onToggleTodo()
                                                offsetX.animateTo(0f, tween(180))
                                            }
                                            -offsetX.value >= actionWidthPx * 0.35f -> offsetX.animateTo(-actionWidthPx, swipeSpec)
                                            else -> offsetX.animateTo(0f, swipeSpec)
                                        }
                                    }
                                },
                                onDragCancel = { scope.launch { offsetX.animateTo(0f, swipeSpec) } },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val next = (offsetX.value + dragAmount).coerceIn(-longSwipePx - 32f, 0f)
                                        offsetX.snapTo(next)
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
                .combinedClickable(
                    onClick = {
                        haptics.click()
                        if (offsetX.value < -1f) {
                            scope.launch { offsetX.animateTo(0f, swipeSpec) }
                        } else if (memo.type == QuickMemoType.VOICE) {
                            isExpanded = !isExpanded
                        } else if (!isEditing) {
                            isEditing = true
                        }
                    },
                    onLongClick = {
                        haptics.longPress()
                        onLongPress()
                    }
                ),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (isTodo) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(120.dp)
                            .background(warmLine)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (memo.type == QuickMemoType.VOICE) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "语音随口记",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = formatQuickMemoTime(memo.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isTodo) {
                                Text(
                                    text = if (isCompleted) "已完成" else "待办",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(warmLine.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8A6200)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isTodo) {
                                QuickMemoTodoMark(done = isCompleted, onClick = {
                                    haptics.confirm()
                                    onToggleTodo()
                                })
                            }
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (isEditing) {
                        QuickMemoBodyEditor(
                            value = draftBody,
                            onValueChange = { draftBody = it },
                            onCancel = {
                                haptics.click()
                                draftBody = memo.bodyText
                                isEditing = false
                            },
                            onSave = {
                                haptics.confirm()
                                onSaveBody(draftBody)
                                isEditing = false
                            }
                        )
                    } else if (memo.type == QuickMemoType.VOICE && !isExpanded) {
                        QuickMemoVoiceCollapsedRow(
                            isPlaying = isPlaying,
                            onWaveClick = {
                                haptics.click()
                                isExpanded = true
                            },
                            onPlayClick = {
                                haptics.click()
                                onToggleAudio(memo.audioPath)
                            }
                        )
                    } else {
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f) else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (memo.type == QuickMemoType.VOICE && isExpanded) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuickMemoVoicePlayInline(isPlaying = isPlaying) {
                                    haptics.click()
                                    onToggleAudio(memo.audioPath)
                                }
                                if (memo.transcriptionStatus == QuickMemoTranscriptionStatus.FAILED) {
                                    QuickMemoRetryButton {
                                        haptics.confirm()
                                        onRetryTranscription()
                                    }
                                }
                            }
                        }
                        if (suggestions.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestions.forEach { suggestion ->
                                    QuickMemoSuggestionRow(suggestion = suggestion) {
                                        haptics.confirm()
                                        onCreateSuggestion(suggestion)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun QuickMemoBodyEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (value.isBlank()) {
                        Text(
                            text = "编辑正文内容",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            QuickMemoIconButton(icon = Icons.Rounded.Close, contentDescription = "取消", onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            QuickMemoIconButton(icon = Icons.Rounded.Check, contentDescription = "保存", onClick = onSave, filled = true)
        }
    }
}

@Composable
private fun QuickMemoIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean = false
) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(999.dp),
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickMemoTodoMark(done: Boolean, onClick: () -> Unit) {
    val accent = if (done) Color.Gray else Color(0xFFF2B705)
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(if (done) accent.copy(alpha = 0.14f) else Color.Transparent, shape)
                .border(1.dp, accent.copy(alpha = if (done) 0.52f else 0.72f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Text("✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

@Composable
private fun QuickMemoActionPill(
    text: String,
    alpha: Float,
    onClick: () -> Unit,
    filled: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .alpha(alpha)
            .height(36.dp),
        shape = RoundedCornerShape(999.dp),
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickMemoVoiceCollapsedRow(
    isPlaying: Boolean,
    onWaveClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = onWaveClick,
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(13) { index ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((10 + (index % 5) * 3).dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.62f))
                    )
                }
            }
        }
        QuickMemoVoicePlayInline(isPlaying = isPlaying, compact = true, onClick = onPlayClick)
    }
}

@Composable
private fun QuickMemoVoicePlayInline(
    isPlaying: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = if (compact) Modifier.size(36.dp) else Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停语音" else "播放语音",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isPlaying) "暂停" else "播放语音",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QuickMemoRetryButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "重试转写",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickMemoSuggestionRow(
    suggestion: QuickMemoSuggestionEntity,
    onCreate: () -> Unit
) {
    val draft = remember(suggestion.candidateJson) { QuickMemoSuggestionCodec.decode(suggestion.candidateJson) }
    val title = draft?.title?.takeIf { it.isNotBlank() } ?: "未命名日程"
    val timeText = draft?.let { formatSuggestionTime(it.startTS, it.endTS) }.orEmpty()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (timeText.isNotBlank()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                onClick = onCreate,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "创建日程",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun voiceFallbackText(memo: QuickMemoEntity): String {
    return when (memo.transcriptionStatus) {
        QuickMemoTranscriptionStatus.PENDING,
        QuickMemoTranscriptionStatus.PROCESSING -> "转写中"
        QuickMemoTranscriptionStatus.FAILED -> "转写失败，可重试"
        else -> "仅音频"
    }
}

private fun formatSuggestionTime(startTs: Long, endTs: Long): String {
    val start = startTs.takeIf { it > 0L }?.let { formatEpochSeconds(it) }.orEmpty()
    val end = endTs.takeIf { it > 0L }?.let { formatEpochSeconds(it) }.orEmpty()
    return when {
        start.isNotBlank() && end.isNotBlank() -> "$start - $end"
        start.isNotBlank() -> start
        else -> ""
    }
}

private fun formatEpochSeconds(epochSeconds: Long): String {
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault())
        .format(quickMemoDateTimeFormatter)
}

private fun formatQuickMemoTime(timestamp: Long): String {
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    val today = LocalDateTime.now().toLocalDate()
    return if (time.toLocalDate() == today) {
        time.format(quickMemoTimeFormatter)
    } else {
        time.format(quickMemoDateTimeFormatter)
    }
}
