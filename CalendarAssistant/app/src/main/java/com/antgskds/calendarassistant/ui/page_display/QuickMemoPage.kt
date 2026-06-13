package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private val quickMemoTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val quickMemoDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val quickMemoDateGroupFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)

@Composable
fun QuickMemoPage(
    viewModel: MainViewModel,
    searchQuery: String = "",
    uiSize: Int = 2,
    extraBottomPadding: Dp = 0.dp,
    onOpenDetail: (Long) -> Unit = {},
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val metrics = quickMemoUiMetrics(uiSize)
    val bottomSafePadding = 112.dp + extraBottomPadding
    val filteredMemos = remember(quickMemos, searchQuery) {
        quickMemos.filter { memo ->
            searchQuery.isBlank() || memo.bodyText.contains(searchQuery, ignoreCase = true)
        }
    }
    val groupedMemos = remember(filteredMemos) {
        filteredMemos
            .groupBy { memo ->
                LocalDateTime.ofInstant(Instant.ofEpochMilli(memo.createdAt), ZoneId.systemDefault()).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
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
        contentPadding = PaddingValues(top = 10.dp, bottom = bottomSafePadding + 24.dp)
    ) {
        groupedMemos.forEach { (date, memos) ->
            item(key = "header_$date") {
                Text(
                    text = "—— ${formatDateGroup(date)}",
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            items(memos, key = { it.id ?: it.hashCode().toLong() }) { memo ->
                QuickMemoListItem(
                    memo = memo,
                    suggestions = memo.id?.let { suggestionsByMemo[it] }.orEmpty(),
                    playbackState = playbackState,
                    onToggleTodo = { memo.id?.let { viewModel.toggleQuickMemoTodoCompletion(it) } },
                    onToggleAudio = { path -> viewModel.toggleAudioPlayback(path) },
                    onOpenDetail = { memo.id?.let(onOpenDetail) },
                    onLongPress = { onPendingDeleteChange(memo) },
                    hapticEnabled = hapticEnabled,
                    uiSize = uiSize,
                    modifier = Modifier.padding(
                        horizontal = metrics.listHorizontalPadding,
                        vertical = metrics.listVerticalPadding
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoDetailPage(
    memoId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    uiSize: Int = 2,
    hapticEnabled: Boolean = true
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val memo = remember(quickMemos, memoId) { quickMemos.firstOrNull { it.id == memoId } }
    val pendingSuggestions = remember(suggestions, memoId) {
        suggestions.filter { it.quickMemoId == memoId && it.status == QuickMemoSuggestionStatus.PENDING }
    }
    val haptics = rememberAppHaptics(hapticEnabled)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = { Text("随口记详情") },
                navigationIcon = {
                    IconButton(onClick = { haptics.click(); onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (memo == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("随口记不存在或已删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Surface
            }

            QuickMemoDetailContent(
                memo = memo,
                suggestions = pendingSuggestions,
                playbackState = playbackState,
                onSaveBody = { body -> memo.id?.let { viewModel.updateQuickMemoBody(it, body) } },
                onToggleTodo = { memo.id?.let { viewModel.toggleQuickMemoTodoCompletion(it) } },
                onToggleAudio = { path -> viewModel.toggleAudioPlayback(path) },
                onRetryTranscription = { memo.id?.let { viewModel.retryQuickMemoTranscription(it) } },
                onCreateSuggestion = { suggestion ->
                    suggestion.id?.let { id ->
                        haptics.confirm()
                        viewModel.createEventFromQuickMemoSuggestion(id)
                    }
                },
                uiSize = uiSize,
                hapticEnabled = hapticEnabled
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickMemoListItem(
    memo: QuickMemoEntity,
    suggestions: List<QuickMemoSuggestionEntity>,
    playbackState: AudioPlaybackState,
    onToggleTodo: () -> Unit,
    onToggleAudio: (String?) -> Unit,
    onOpenDetail: () -> Unit,
    onLongPress: () -> Unit,
    hapticEnabled: Boolean,
    uiSize: Int = 2,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val metrics = quickMemoUiMetrics(uiSize)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isTodo = memo.todoState == QuickMemoTodoState.ACTIVE || memo.todoState == QuickMemoTodoState.COMPLETED
    val isCompleted = memo.todoState == QuickMemoTodoState.COMPLETED
    val isVoice = memo.type == QuickMemoType.VOICE
    val isPlaying = memo.audioPath != null && playbackState.audioPath == memo.audioPath && playbackState.isPlaying
    val warmLine = Color(0xFFF2B705)
    val accentColor = when {
        isCompleted -> MaterialTheme.colorScheme.outlineVariant
        isTodo -> warmLine
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val bodyText = memo.bodyText.ifBlank { if (isVoice) voiceFallbackText(memo) else "空白随口记" }
    val offsetX = remember(memo.id) { Animatable(0f) }
    val actionWidthPx = with(density) { 80.dp.toPx() }
    val swipeSpec = spring<Float>(dampingRatio = 0.82f, stiffness = 620f)

    Box(modifier = modifier.fillMaxWidth()) {
        if (isTodo && offsetX.value < -1f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickMemoActionPill(
                    text = if (isCompleted) "撤回" else "完成待办",
                    filled = true,
                    alpha = ((-offsetX.value) / actionWidthPx).coerceIn(0f, 1f),
                    onClick = {
                        haptics.confirm()
                        onToggleTodo()
                        scope.launch { offsetX.animateTo(0f, swipeSpec) }
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (isTodo) {
                        Modifier.pointerInput(memo.id, isCompleted) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (-offsetX.value >= actionWidthPx * 0.4f) {
                                            offsetX.animateTo(-actionWidthPx, swipeSpec)
                                        } else {
                                            offsetX.animateTo(0f, swipeSpec)
                                        }
                                    }
                                },
                                onDragCancel = { scope.launch { offsetX.animateTo(0f, swipeSpec) } },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val next = (offsetX.value + dragAmount).coerceIn(-actionWidthPx - 32f, 0f)
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
                        } else {
                            onOpenDetail()
                        }
                    },
                    onLongClick = {
                        haptics.longPress()
                        onLongPress()
                    }
                )
        ) {
            if (isVoice) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(metrics.listIndicatorWidth)
                                .height(metrics.listIndicatorHeight)
                                .clip(RoundedCornerShape(metrics.listIndicatorRadius))
                                .background(accentColor)
                        )
                        Spacer(Modifier.width(12.dp))
                        FloatingSiriWaveform(
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .width(quickMemoWaveformWidth(memo.audioDurationMs, metrics.listWaveMinWidth, metrics.listWaveMaxWidth))
                                .height(metrics.listWaveHeight),
                            color = if (isCompleted) MaterialTheme.colorScheme.outline else accentColor
                        )
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.width(14.dp))
                        FloatingVoicePlayButton(
                            isPlaying = isPlaying,
                            onClick = {
                                haptics.click()
                                onToggleAudio(memo.audioPath)
                            },
                            buttonSize = metrics.listPlayButtonSize,
                            iconSize = metrics.listPlayIconSize,
                            containerColor = if (isCompleted) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            },
                            iconColor = if (isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = bodyText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = metrics.listBodyStartPadding,
                                end = metrics.listPlayButtonSize + 34.dp
                            ),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = metrics.listBodyFontSize,
                            lineHeight = metrics.listBodyLineHeight
                        ),
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = metrics.listBodyStartPadding,
                                end = metrics.listPlayButtonSize + 34.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatQuickMemoTime(memo.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (suggestions.isNotEmpty()) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "有日程推荐",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(metrics.listIndicatorWidth)
                            .height(metrics.listIndicatorHeight)
                            .clip(RoundedCornerShape(metrics.listIndicatorRadius))
                            .background(accentColor)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = metrics.listBodyFontSize,
                                lineHeight = metrics.listBodyLineHeight
                            ),
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatQuickMemoTime(memo.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            if (suggestions.isNotEmpty()) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "有日程推荐",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    if (isTodo) {
                        Spacer(Modifier.width(12.dp))
                        QuickMemoTodoMark(done = isCompleted) {
                            haptics.confirm()
                            onToggleTodo()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMemoDetailContent(
    memo: QuickMemoEntity,
    suggestions: List<QuickMemoSuggestionEntity>,
    playbackState: AudioPlaybackState,
    onSaveBody: (String) -> Unit,
    onToggleTodo: () -> Unit,
    onToggleAudio: (String?) -> Unit,
    onRetryTranscription: () -> Unit,
    onCreateSuggestion: (QuickMemoSuggestionEntity) -> Unit,
    uiSize: Int = 2,
    hapticEnabled: Boolean
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val focusManager = LocalFocusManager.current
    val metrics = quickMemoUiMetrics(uiSize)
    var draftBody by remember(memo.id, memo.updatedAt) { mutableStateOf(memo.bodyText) }
    var bodyEditorBounds by remember { mutableStateOf<Rect?>(null) }
    val isTodo = memo.todoState == QuickMemoTodoState.ACTIVE || memo.todoState == QuickMemoTodoState.COMPLETED
    val isCompleted = memo.todoState == QuickMemoTodoState.COMPLETED
    val isVoice = memo.type == QuickMemoType.VOICE
    val isPlaying = memo.audioPath != null && playbackState.audioPath == memo.audioPath && playbackState.isPlaying

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(bodyEditorBounds) {
                detectTapGestures { offset ->
                    if (bodyEditorBounds?.contains(offset) != true) {
                        focusManager.clearFocus(force = true)
                    }
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (isVoice) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp, bottom = metrics.detailAudioBottomSpacing)
                        .widthIn(max = metrics.detailAudioMaxRowWidth),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    FloatingSiriWaveform(
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .width(quickMemoWaveformWidth(memo.audioDurationMs, metrics.detailWaveMinWidth, metrics.detailWaveMaxWidth))
                            .height(metrics.detailWaveHeight)
                    )
                    Spacer(Modifier.width(metrics.detailAudioButtonSpacing))
                    FloatingVoicePlayButton(
                        isPlaying = isPlaying,
                        onClick = {
                            haptics.click()
                            onToggleAudio(memo.audioPath)
                        },
                        buttonSize = metrics.detailPlayButtonSize,
                        iconSize = metrics.detailPlayIconSize
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "正文",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (isVoice) {
                if (memo.transcriptionStatus == QuickMemoTranscriptionStatus.FAILED) {
                    QuickMemoTextButton(
                        text = "重试转写",
                        onClick = {
                            haptics.confirm()
                            onRetryTranscription()
                        }
                    )
                } else {
                    Text(
                        text = quickMemoStatusText(memo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        BasicTextField(
            value = draftBody,
            onValueChange = {
                draftBody = it
                onSaveBody(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    bodyEditorBounds = coordinates.boundsInParent()
                }
                .defaultMinSize(minHeight = 100.dp),
            textStyle = TextStyle(
                color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f) else MaterialTheme.colorScheme.onSurface,
                fontSize = metrics.detailBodyFontSize,
                lineHeight = metrics.detailBodyLineHeight,
                fontWeight = FontWeight.Medium,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else null
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (draftBody.isBlank()) {
                        Text(
                            text = "点击输入正文...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = metrics.detailBodyFontSize
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "${formatQuickMemoTime(memo.createdAt)} 创建",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        if (isTodo) {
            Spacer(Modifier.height(32.dp))
            Surface(
                onClick = {
                    haptics.confirm()
                    onToggleTodo()
                },
                modifier = Modifier
                    .height(metrics.completeButtonHeight),
                shape = RoundedCornerShape(999.dp),
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = metrics.completeButtonHorizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isCompleted) "撤销完成状态" else "标记为已完成",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = "日程待办",
                modifier = Modifier.padding(bottom = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            suggestions.forEach { suggestion ->
                QuickMemoSuggestionItem(suggestion = suggestion, uiSize = uiSize) {
                    haptics.confirm()
                    onCreateSuggestion(suggestion)
                }
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
@Composable
private fun QuickMemoTextButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
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
private fun QuickMemoSuggestionItem(
    suggestion: QuickMemoSuggestionEntity,
    uiSize: Int = 2,
    onCreate: () -> Unit
) {
    val metrics = quickMemoUiMetrics(uiSize)
    val draft = remember(suggestion.candidateJson) { QuickMemoSuggestionCodec.decode(suggestion.candidateJson) }
    val title = draft?.title?.takeIf { it.isNotBlank() } ?: "未命名日程"
    val timeText = draft?.let { formatSuggestionTime(it.startTS, it.endTS) }.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = metrics.suggestionVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(metrics.suggestionIndicatorHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (timeText.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            onClick = onCreate,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                text = "添加",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
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
        onClick = onCreate,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (timeText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "添加",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FloatingSiriWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "quick_memo_siri_waveform")
    val timePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "quick_memo_siri_wave_phase"
    )
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.15f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "quick_memo_siri_wave_amplitude"
    )
    val paths = remember { List(3) { Path() } }

    Canvas(modifier = modifier) {
        val width = size.width
        val centerY = size.height / 2f
        val maxAmplitude = size.height / 2f * 0.85f
        val step = (width / 120f).coerceAtLeast(1f)
        val waveParams = listOf(
            FloatingWaveParam(1.5f, 1.0f, 1.0f, 0.88f, 2.4.dp.toPx()),
            FloatingWaveParam(2.2f, -1.2f, 0.7f, 0.46f, 1.9.dp.toPx()),
            FloatingWaveParam(3.0f, 0.8f, 0.4f, 0.28f, 1.4.dp.toPx())
        )

        waveParams.forEachIndexed { index, param ->
            val path = paths[index]
            path.reset()
            path.moveTo(0f, centerY)

            var x = 0f
            while (x <= width) {
                val progress = x / width
                val envelope = sin(progress * PI).pow(2).toFloat()
                val waveX = progress * PI.toFloat() * 2f * param.frequency
                val currentPhase = timePhase * param.speedMultiplier
                val sineValue = sin(waveX + currentPhase)
                val y = centerY + sineValue * maxAmplitude * param.amplitudeWeight * amplitudeFactor * envelope

                path.lineTo(x, y)
                x += step
            }

            drawPath(
                path = path,
                color = color.copy(alpha = param.alpha),
                style = Stroke(
                    width = param.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private data class FloatingWaveParam(
    val frequency: Float,
    val speedMultiplier: Float,
    val amplitudeWeight: Float,
    val alpha: Float,
    val strokeWidth: Float
)

private data class QuickMemoUiMetrics(
    val listHorizontalPadding: Dp,
    val listVerticalPadding: Dp,
    val listItemMaxWidth: Dp,
    val listIndicatorWidth: Dp,
    val listIndicatorHeight: Dp,
    val listIndicatorRadius: Dp,
    val listBodyStartPadding: Dp,
    val listBodyFontSize: androidx.compose.ui.unit.TextUnit,
    val listBodyLineHeight: androidx.compose.ui.unit.TextUnit,
    val listWaveMinWidth: Dp,
    val listWaveMaxWidth: Dp,
    val listWaveHeight: Dp,
    val listPlayButtonSize: Dp,
    val listPlayIconSize: Dp,
    val detailAudioMaxRowWidth: Dp,
    val detailAudioBottomSpacing: Dp,
    val detailAudioButtonSpacing: Dp,
    val detailWaveMinWidth: Dp,
    val detailWaveMaxWidth: Dp,
    val detailWaveHeight: Dp,
    val detailPlayButtonSize: Dp,
    val detailPlayIconSize: Dp,
    val detailBodyFontSize: androidx.compose.ui.unit.TextUnit,
    val detailBodyLineHeight: androidx.compose.ui.unit.TextUnit,
    val completeButtonHeight: Dp,
    val completeButtonHorizontalPadding: Dp,
    val suggestionVerticalPadding: Dp,
    val suggestionIndicatorHeight: Dp
)

private fun quickMemoUiMetrics(uiSize: Int): QuickMemoUiMetrics = when (uiSize) {
    1 -> QuickMemoUiMetrics(
        listHorizontalPadding = 22.dp,
        listVerticalPadding = 16.dp,
        listItemMaxWidth = 300.dp,
        listIndicatorWidth = 4.dp,
        listIndicatorHeight = 28.dp,
        listIndicatorRadius = 2.dp,
        listBodyStartPadding = 16.dp,
        listBodyFontSize = 15.sp,
        listBodyLineHeight = 22.sp,
        listWaveMinWidth = 86.dp,
        listWaveMaxWidth = 180.dp,
        listWaveHeight = 20.dp,
        listPlayButtonSize = 38.dp,
        listPlayIconSize = 18.dp,
        detailAudioMaxRowWidth = 300.dp,
        detailAudioBottomSpacing = 42.dp,
        detailAudioButtonSpacing = 18.dp,
        detailWaveMinWidth = 140.dp,
        detailWaveMaxWidth = 220.dp,
        detailWaveHeight = 26.dp,
        detailPlayButtonSize = 42.dp,
        detailPlayIconSize = 20.dp,
        detailBodyFontSize = 18.sp,
        detailBodyLineHeight = 29.sp,
        completeButtonHeight = 40.dp,
        completeButtonHorizontalPadding = 18.dp,
        suggestionVerticalPadding = 10.dp,
        suggestionIndicatorHeight = 34.dp
    )
    3 -> QuickMemoUiMetrics(
        listHorizontalPadding = 26.dp,
        listVerticalPadding = 20.dp,
        listItemMaxWidth = 360.dp,
        listIndicatorWidth = 6.dp,
        listIndicatorHeight = 38.dp,
        listIndicatorRadius = 3.dp,
        listBodyStartPadding = 20.dp,
        listBodyFontSize = 18.sp,
        listBodyLineHeight = 27.sp,
        listWaveMinWidth = 108.dp,
        listWaveMaxWidth = 236.dp,
        listWaveHeight = 24.dp,
        listPlayButtonSize = 46.dp,
        listPlayIconSize = 22.dp,
        detailAudioMaxRowWidth = 380.dp,
        detailAudioBottomSpacing = 56.dp,
        detailAudioButtonSpacing = 24.dp,
        detailWaveMinWidth = 176.dp,
        detailWaveMaxWidth = 300.dp,
        detailWaveHeight = 32.dp,
        detailPlayButtonSize = 50.dp,
        detailPlayIconSize = 24.dp,
        detailBodyFontSize = 22.sp,
        detailBodyLineHeight = 35.sp,
        completeButtonHeight = 46.dp,
        completeButtonHorizontalPadding = 24.dp,
        suggestionVerticalPadding = 14.dp,
        suggestionIndicatorHeight = 44.dp
    )
    else -> QuickMemoUiMetrics(
        listHorizontalPadding = 24.dp,
        listVerticalPadding = 18.dp,
        listItemMaxWidth = 330.dp,
        listIndicatorWidth = 5.dp,
        listIndicatorHeight = 32.dp,
        listIndicatorRadius = 2.5.dp,
        listBodyStartPadding = 17.dp,
        listBodyFontSize = 16.sp,
        listBodyLineHeight = 24.sp,
        listWaveMinWidth = 96.dp,
        listWaveMaxWidth = 220.dp,
        listWaveHeight = 22.dp,
        listPlayButtonSize = 42.dp,
        listPlayIconSize = 20.dp,
        detailAudioMaxRowWidth = 340.dp,
        detailAudioBottomSpacing = 50.dp,
        detailAudioButtonSpacing = 22.dp,
        detailWaveMinWidth = 160.dp,
        detailWaveMaxWidth = 270.dp,
        detailWaveHeight = 30.dp,
        detailPlayButtonSize = 46.dp,
        detailPlayIconSize = 22.dp,
        detailBodyFontSize = 20.sp,
        detailBodyLineHeight = 32.sp,
        completeButtonHeight = 44.dp,
        completeButtonHorizontalPadding = 22.dp,
        suggestionVerticalPadding = 12.dp,
        suggestionIndicatorHeight = 40.dp
    )
}

private fun quickMemoWaveformWidth(durationMs: Long, minWidth: Dp, maxWidth: Dp): Dp {
    val seconds = (durationMs.coerceAtLeast(0L) / 1000.0).coerceAtMost(60.0)
    val progress = (ln(1.0 + seconds / 4.0) / ln(1.0 + 60.0 / 4.0)).coerceIn(0.0, 1.0).toFloat()
    return minWidth + (maxWidth - minWidth) * progress
}

@Composable
private fun FloatingVoicePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        modifier = modifier.size(buttonSize)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停语音" else "播放语音",
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
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

private fun quickMemoStatusText(memo: QuickMemoEntity): String {
    if (memo.type != QuickMemoType.VOICE) return if (memo.isTodo) "文字待办" else "文字随口记"
    return when (memo.transcriptionStatus) {
        QuickMemoTranscriptionStatus.PENDING -> "等待转写"
        QuickMemoTranscriptionStatus.PROCESSING -> "正在转写"
        QuickMemoTranscriptionStatus.SUCCESS -> "转写完成"
        QuickMemoTranscriptionStatus.FAILED -> "转写失败，可重试"
        else -> "仅音频"
    }
}

private fun formatDateGroup(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(quickMemoDateGroupFormatter)
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
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        .format(quickMemoTimeFormatter)
}
