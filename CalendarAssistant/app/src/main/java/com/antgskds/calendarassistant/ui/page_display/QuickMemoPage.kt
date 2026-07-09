package com.antgskds.calendarassistant.ui.page_display

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoTodoState
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoTranscriptionStatus
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoType
import com.antgskds.calendarassistant.core.quickmemo.audio.AudioPlaybackState
import com.antgskds.calendarassistant.core.quickmemo.audio.QuickMemoAudioRecorder
import com.antgskds.calendarassistant.core.util.ImageImportUtils
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.state.CapsuleType
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarExtraHeight
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarShadowPadding
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.page_display.settings.AppBackgroundStyleTheme
import com.antgskds.calendarassistant.ui.page_display.settings.LocalAppBackgroundStyleEnabled
import com.antgskds.calendarassistant.ui.page_display.settings.rememberAppBackgroundStylePalette
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val quickMemoTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val quickMemoDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val quickMemoDateGroupFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)
private const val TEXT_QUICK_MEMO_ID_PREFIX = "TEXT_QUICK_MEMO_"

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
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val pinnedQuickMemoId = remember(capsuleUiState) { activeTextQuickMemoId(capsuleUiState) }
    val context = LocalContext.current
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
            .filter { it.status == QuickMemoSuggestionStatus.PENDING || it.status == QuickMemoSuggestionStatus.CREATED }
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
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (searchQuery.isBlank()) {
                        "从悬浮窗快速记下一句话，之后可以继续整理。"
                    } else {
                        "换个关键词试试，搜索会匹配随口记正文。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
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
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(memos, key = { it.id ?: it.hashCode().toLong() }) { memo ->
                QuickMemoListItem(
                    memo = memo,
                    suggestions = memo.id?.let { suggestionsByMemo[it] }.orEmpty(),
                    playbackState = playbackState,
                    isPinned = memo.id == pinnedQuickMemoId,
                    onToggleTodo = { memo.id?.let { viewModel.toggleQuickMemoTodoCompletion(it) } },
                    onToggleTodoMode = {
                        memo.id?.let { id ->
                            if (memo.isTodo) viewModel.removeQuickMemoTodo(id) else viewModel.markQuickMemoTodo(id)
                        }
                    },
                    onTogglePinned = {
                        memo.id?.let { id ->
                            toggleQuickMemoPinned(viewModel, id, memo.id == pinnedQuickMemoId, context)
                        }
                    },
                    onDelete = { onPendingDeleteChange(memo) },
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
    hapticEnabled: Boolean = true,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val pinnedQuickMemoId = remember(capsuleUiState) { activeTextQuickMemoId(capsuleUiState) }
    val context = LocalContext.current
    val memo = remember(quickMemos, memoId) { quickMemos.firstOrNull { it.id == memoId } }
    val pendingSuggestions = remember(suggestions, memoId) {
        suggestions.filter {
            it.quickMemoId == memoId &&
                (it.status == QuickMemoSuggestionStatus.PENDING || it.status == QuickMemoSuggestionStatus.CREATED)
        }
    }
    val haptics = rememberAppHaptics(hapticEnabled)

    AppBackgroundStyleTheme(
        enabled = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        cardAlphaPercent = cardAlphaPercent
    ) {
        val pageContainerColor = if (backgroundMode) Color.Transparent else MaterialTheme.colorScheme.background
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = pageContainerColor,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = pageContainerColor,
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
                color = pageContainerColor
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
                    onAttachImage = { imagePath, onResult ->
                        val id = memo.id
                        if (id == null) {
                            onResult(Result.failure(IllegalStateException("随口记不存在")))
                        } else {
                            viewModel.attachImageToQuickMemo(id, imagePath, onResult)
                        }
                    },
                    onRemoveImage = { onResult ->
                        val id = memo.id
                        if (id == null) {
                            onResult(Result.failure(IllegalStateException("随口记不存在")))
                        } else {
                            viewModel.removeImageFromQuickMemo(id, onResult)
                        }
                    },
                    onAttachVoice = { audioPath, durationMs, onResult ->
                        val id = memo.id
                        if (id == null) {
                            onResult(Result.failure(IllegalStateException("随口记不存在")))
                        } else {
                            viewModel.attachVoiceToQuickMemo(id, audioPath, durationMs, onResult)
                        }
                    },
                    onToggleTodo = { memo.id?.let { viewModel.toggleQuickMemoTodoCompletion(it) } },
                    onMarkTodo = { memo.id?.let { viewModel.markQuickMemoTodo(it) } },
                    onToggleAudio = { path -> viewModel.toggleAudioPlayback(path) },
                    isPinned = memo.id == pinnedQuickMemoId,
                    onTogglePinned = {
                        memo.id?.let { id ->
                            toggleQuickMemoPinned(viewModel, id, memo.id == pinnedQuickMemoId, context)
                        }
                    },
                    onRetryTranscription = { memo.id?.let { viewModel.retryQuickMemoTranscription(it) } },
                    onCreateSuggestion = { suggestion ->
                        suggestion.id?.let { id ->
                            haptics.confirm()
                            viewModel.createEventFromQuickMemoSuggestion(id)
                        }
                    },
                    uiSize = uiSize,
                    hapticEnabled = hapticEnabled,
                    backgroundMode = backgroundMode,
                    miuiBlurEnabled = miuiBlurEnabled
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickMemoListItem(
    memo: QuickMemoEntity,
    suggestions: List<QuickMemoSuggestionEntity>,
    playbackState: AudioPlaybackState,
    isPinned: Boolean,
    onToggleTodo: () -> Unit,
    onToggleTodoMode: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
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
    val isImage = memo.type == QuickMemoType.IMAGE
    val isPlaying = memo.audioPath != null && playbackState.audioPath == memo.audioPath && playbackState.isPlaying
    val usesWallpaperText = LocalAppBackgroundStyleEnabled.current
    val primaryTextColor = if (usesWallpaperText) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (usesWallpaperText) {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val warmLine = Color(0xFFF2B705)
    val accentColor = when {
        isCompleted -> MaterialTheme.colorScheme.outlineVariant
        isTodo -> warmLine
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isCompleted) secondaryTextColor else primaryTextColor
    val bodyText = memo.bodyText.ifBlank {
        when {
            isVoice -> voiceFallbackText(memo)
            isImage -> "图片随口记"
            else -> "空白随口记"
        }
    }
    val offsetX = remember(memo.id) { Animatable(0f) }
    val actionButtonSize = when (uiSize) {
        1 -> 48.dp
        2 -> 52.dp
        else -> 56.dp
    }
    val actionMenuWidth = when (uiSize) {
        1 -> 170.dp
        2 -> 185.dp
        else -> 200.dp
    }
    val actionWidthPx = with(density) { actionMenuWidth.toPx() }
    val revealedActionWidth = with(density) { (-offsetX.value).coerceIn(0f, actionWidthPx).toDp() }
    val revealProgress = (-offsetX.value / actionWidthPx).coerceIn(0f, 1f)
    val voicePlayButtonAlpha = (1f - revealProgress * 1.35f).coerceIn(0f, 1f)
    val swipeSpec = spring<Float>(dampingRatio = 0.82f, stiffness = 620f)
    var thresholdHapticPlayed by remember(memo.id) { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (offsetX.value < -1f) {
            Box(
                modifier = Modifier
                    .width(revealedActionWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .width(actionMenuWidth)
                        .fillMaxHeight()
                        .padding(end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickMemoSwipeActionIcon(
                        icon = Icons.Rounded.NotificationsActive,
                        contentDescription = if (isPinned) "取消实况挂起" else "挂到实况",
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        size = actionButtonSize,
                        onClick = {
                            haptics.confirm()
                            onTogglePinned()
                            scope.launch { offsetX.animateTo(0f, swipeSpec) }
                        }
                    )
                    QuickMemoSwipeActionIcon(
                        icon = if (isTodo) Icons.Rounded.Close else Icons.Rounded.CheckCircle,
                        contentDescription = if (isTodo) "转普通" else "转待办",
                        backgroundColor = warmLine,
                        size = actionButtonSize,
                        onClick = {
                            haptics.confirm()
                            onToggleTodoMode()
                            scope.launch { offsetX.animateTo(0f, swipeSpec) }
                        }
                    )
                    QuickMemoSwipeActionIcon(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "删除",
                        backgroundColor = MaterialTheme.colorScheme.error,
                        size = actionButtonSize,
                        onClick = {
                            haptics.longPress()
                            onDelete()
                            scope.launch { offsetX.animateTo(0f, swipeSpec) }
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(memo.id, isCompleted, isPinned, isTodo) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (-offsetX.value >= actionWidthPx * 0.28f) {
                                    if (!thresholdHapticPlayed) haptics.threshold()
                                    thresholdHapticPlayed = true
                                    offsetX.animateTo(-actionWidthPx, swipeSpec)
                                } else {
                                    thresholdHapticPlayed = false
                                    offsetX.animateTo(0f, swipeSpec)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { thresholdHapticPlayed = false; offsetX.animateTo(0f, swipeSpec) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val next = (offsetX.value + dragAmount).coerceIn(-actionWidthPx - 32f, 0f)
                                if (!thresholdHapticPlayed && -next >= actionWidthPx * 0.5f) {
                                    haptics.threshold()
                                    thresholdHapticPlayed = true
                                } else if (-next < actionWidthPx * 0.28f) {
                                    thresholdHapticPlayed = false
                                }
                                offsetX.snapTo(next)
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = {
                        haptics.click()
                        if (offsetX.value < -1f) {
                            scope.launch { offsetX.animateTo(0f, swipeSpec) }
                            thresholdHapticPlayed = false
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
                            enabled = voicePlayButtonAlpha > 0.18f,
                            onClick = {
                                haptics.click()
                                onToggleAudio(memo.audioPath)
                            },
                            modifier = Modifier.alpha(voicePlayButtonAlpha),
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
                            color = secondaryTextColor
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatQuickMemoTime(memo.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryTextColor
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
    onAttachImage: (String, (Result<Unit>) -> Unit) -> Unit,
    onRemoveImage: ((Result<Unit>) -> Unit) -> Unit,
    onAttachVoice: (String, Long, (Result<Unit>) -> Unit) -> Unit,
    onToggleTodo: () -> Unit,
    onMarkTodo: () -> Unit,
    onToggleAudio: (String?) -> Unit,
    isPinned: Boolean,
    onTogglePinned: () -> Unit,
    onRetryTranscription: () -> Unit,
    onCreateSuggestion: (QuickMemoSuggestionEntity) -> Unit,
    uiSize: Int = 2,
    hapticEnabled: Boolean,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val metrics = quickMemoUiMetrics(uiSize)
    var draftBody by remember(memo.id, memo.updatedAt) { mutableStateOf(memo.bodyText) }
    var bodyEditorBounds by remember { mutableStateOf<Rect?>(null) }
    var isAttachingImage by remember { mutableStateOf(false) }
    var isImageSelected by remember(memo.id, memo.imagePath) { mutableStateOf(false) }
    val isTodo = memo.todoState == QuickMemoTodoState.ACTIVE || memo.todoState == QuickMemoTodoState.COMPLETED
    val isCompleted = memo.todoState == QuickMemoTodoState.COMPLETED
    val isVoice = memo.type == QuickMemoType.VOICE
    val isImage = memo.type == QuickMemoType.IMAGE
    val hasImage = !memo.imagePath.isNullOrBlank()
    val isPlaying = memo.audioPath != null && playbackState.audioPath == memo.audioPath && playbackState.isPlaying
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || isAttachingImage) return@rememberLauncherForActivityResult
        scope.launch {
            isAttachingImage = true
            try {
                val imageFile = withContext(Dispatchers.IO) {
                    ImageImportUtils.createQuickMemoImageFile(context).also { file ->
                        check(ImageImportUtils.copyUriToFile(context, uri, file)) { "图片读取失败" }
                    }
                }
                onAttachImage(imageFile.absolutePath) { result ->
                    isAttachingImage = false
                    result
                        .onSuccess { Toast.makeText(context, "已插入图片", Toast.LENGTH_SHORT).show() }
                        .onFailure { error ->
                            runCatching { imageFile.delete() }
                            Toast.makeText(context, error.message ?: "插入图片失败", Toast.LENGTH_SHORT).show()
                        }
                }
            } catch (e: Exception) {
                isAttachingImage = false
                Toast.makeText(context, e.message ?: "插入图片失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val audioRecorder = remember(context) { QuickMemoAudioRecorder(context.applicationContext) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var isSavingVoice by remember { mutableStateOf(false) }
    var recordAudioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    fun startVoiceRecording() {
        if (isRecordingVoice || isSavingVoice) return
        scope.launch {
            isRecordingVoice = true
            try {
                withContext(Dispatchers.IO) { audioRecorder.start() }
                haptics.confirm()
                Toast.makeText(context, "正在录音，再点一次保存", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                isRecordingVoice = false
                withContext(Dispatchers.IO) { audioRecorder.stopAndDiscard() }
                Toast.makeText(context, e.message ?: "录音失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopVoiceRecording() {
        if (!isRecordingVoice || isSavingVoice) return
        scope.launch {
            isSavingVoice = true
            try {
                val result = withContext(Dispatchers.IO) { audioRecorder.stop() }
                isRecordingVoice = false
                if (result == null || result.durationMs < QuickMemoAudioRecorder.MIN_RECORDING_MS) {
                    result?.path?.let { path -> withContext(Dispatchers.IO) { runCatching { java.io.File(path).delete() } } }
                    isSavingVoice = false
                    Toast.makeText(context, "录音太短", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                onAttachVoice(result.path, result.durationMs) { attachResult ->
                    isSavingVoice = false
                    attachResult
                        .onSuccess { Toast.makeText(context, "已保存语音，正在转写", Toast.LENGTH_SHORT).show() }
                        .onFailure { error ->
                            runCatching { java.io.File(result.path).delete() }
                            Toast.makeText(context, error.message ?: "保存语音失败", Toast.LENGTH_SHORT).show()
                        }
                }
            } catch (e: Exception) {
                isRecordingVoice = false
                isSavingVoice = false
                withContext(Dispatchers.IO) { audioRecorder.stopAndDiscard() }
                Toast.makeText(context, e.message ?: "录音失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        recordAudioGranted = granted
        if (granted) {
            startVoiceRecording()
        } else {
            Toast.makeText(context, "需要麦克风权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleVoiceAction() {
        if (isRecordingVoice) {
            stopVoiceRecording()
            return
        }
        recordAudioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (recordAudioGranted) {
            startVoiceRecording()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(audioRecorder) {
        onDispose {
            audioRecorder.stopAndDiscard()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

        if (hasImage) {
            QuickMemoImagePreview(
                imagePath = memo.imagePath,
                selected = isImageSelected,
                onLongPress = {
                    haptics.longPress()
                    isImageSelected = true
                },
                onDismissSelection = { isImageSelected = false },
                onDelete = {
                    haptics.warning()
                    onRemoveImage { result ->
                        result
                            .onSuccess {
                                isImageSelected = false
                                Toast.makeText(context, "已删除图片", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { error ->
                                Toast.makeText(context, error.message ?: "删除图片失败", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(),
                cornerRadius = 24.dp,
                preserveAspectRatio = true,
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(24.dp))
        }

        if (!recordAudioGranted) {
            QuickMemoRecordPermissionCard(
                onGrantClick = { recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
            Spacer(Modifier.height(20.dp))
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatQuickMemoTime(memo.createdAt)} 创建",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
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

        Spacer(Modifier.height(112.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

        QuickMemoDetailBottomBar(
            isRecordingVoice = isRecordingVoice,
            isSavingVoice = isSavingVoice,
            hasVoice = memo.audioPath?.isNotBlank() == true,
            isPinned = isPinned,
            isTodo = isTodo,
            isCompleted = isCompleted,
            hasImage = hasImage,
            isAttachingImage = isAttachingImage,
            onVoiceClick = {
                haptics.click()
                handleVoiceAction()
            },
            onPinClick = {
                haptics.confirm()
                onTogglePinned()
            },
            onTodoClick = {
                haptics.confirm()
                if (isTodo) onToggleTodo() else onMarkTodo()
            },
            onImageClick = {
                haptics.confirm()
                imagePickerLauncher.launch("image/*")
            },
            backgroundMode = backgroundMode,
            miuiBlurEnabled = miuiBlurEnabled,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun QuickMemoRecordPermissionCard(onGrantClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "需要录音权限",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "开启后才能在随口记里录音和转写。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
            Button(onClick = onGrantClick) {
                Text("授权")
            }
        }
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
private fun QuickMemoDetailBottomBar(
    isRecordingVoice: Boolean,
    isSavingVoice: Boolean,
    hasVoice: Boolean,
    isPinned: Boolean,
    isTodo: Boolean,
    isCompleted: Boolean,
    hasImage: Boolean,
    isAttachingImage: Boolean,
    onVoiceClick: () -> Unit,
    onPinClick: () -> Unit,
    onTodoClick: () -> Unit,
    onImageClick: () -> Unit,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundPalette = rememberAppBackgroundStylePalette(
        enabled = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled
    )
    val barHeight = IntegratedFloatingBarHeight + IntegratedFloatingBarExtraHeight
    val itemWidth = 72.dp
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (backgroundMode) {
        backgroundPalette.surface
    } else if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val indicatorColor = if (backgroundMode) {
        backgroundPalette.accent
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (backgroundMode) {
        backgroundPalette.content
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val disabledColor = if (backgroundMode) {
        backgroundPalette.secondaryContent.copy(alpha = 0.46f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    val todoIndicatorColor = when {
        isCompleted -> MaterialTheme.colorScheme.surfaceVariant
        isTodo -> Color(0xFFF2B705).copy(alpha = 0.24f)
        else -> indicatorColor
    }
    val todoContentColor = when {
        isCompleted -> MaterialTheme.colorScheme.outline
        isTodo -> Color(0xFF8A6500)
        else -> contentColor
    }

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = IntegratedFloatingBarShadowPadding)
            .height(barHeight),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = if (backgroundMode) 0.dp else 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (isRecordingVoice) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(itemWidth * 3f)
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(indicatorColor),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingSiriWaveform(
                        isPlaying = true,
                        modifier = Modifier
                            .width(128.dp)
                            .height(24.dp),
                        color = contentColor
                    )
                }
                QuickMemoActionButton(
                    icon = Icons.Rounded.Check,
                    contentDescription = if (isSavingVoice) "保存中" else "保存",
                    isActive = true,
                    enabled = !isSavingVoice,
                    width = itemWidth,
                    indicatorColor = indicatorColor,
                    contentColor = contentColor,
                    disabledColor = disabledColor,
                    onClick = onVoiceClick
                )
            } else {
                QuickMemoActionButton(
                    icon = Icons.Rounded.NotificationsActive,
                    contentDescription = if (isPinned) "取消实况挂起" else "挂到实况",
                    isActive = isPinned,
                    width = itemWidth,
                    indicatorColor = indicatorColor,
                    contentColor = contentColor,
                    disabledColor = disabledColor,
                    onClick = onPinClick,
                )
                QuickMemoActionButton(
                    icon = Icons.Rounded.CheckCircle,
                    contentDescription = when {
                        isCompleted -> "撤回"
                        isTodo -> "完成"
                        else -> "设为待办"
                    },
                    isActive = isTodo,
                    width = itemWidth,
                    indicatorColor = todoIndicatorColor,
                    contentColor = todoContentColor,
                    disabledColor = disabledColor,
                    onClick = onTodoClick,
                )
                QuickMemoActionButton(
                    icon = Icons.Rounded.Image,
                    contentDescription = if (hasImage) "换图" else "插入图片",
                    isActive = hasImage,
                    enabled = !isAttachingImage,
                    width = itemWidth,
                    indicatorColor = indicatorColor,
                    contentColor = contentColor,
                    disabledColor = disabledColor,
                    onClick = onImageClick,
                )
                QuickMemoActionButton(
                    icon = Icons.Rounded.Mic,
                    contentDescription = if (hasVoice) "重录" else "语音",
                    isActive = hasVoice,
                    enabled = !isSavingVoice,
                    width = itemWidth,
                    indicatorColor = indicatorColor,
                    contentColor = contentColor,
                    disabledColor = disabledColor,
                    onClick = onVoiceClick,
                )
            }
        }
    }
}

@Composable
private fun QuickMemoActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    enabled: Boolean = true,
    width: Dp,
    indicatorColor: Color,
    contentColor: Color,
    disabledColor: Color,
    onClick: () -> Unit
) {
    val tintColor = if (enabled) contentColor else disabledColor

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (isActive) indicatorColor else Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(28.dp),
                    tint = tintColor
                )
            }
        }
    }
}

@Composable
private fun QuickMemoPinButton(
    isPinned: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (isPinned) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        modifier = Modifier.height(if (compact) 26.dp else 34.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPinned) "已挂起" else "挂起",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
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
private fun QuickMemoSwipeActionIcon(
    icon: ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    size: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = backgroundColor,
            modifier = Modifier.size(22.dp)
        )
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
    val created = suggestion.status == QuickMemoSuggestionStatus.CREATED
    val accentColor = if (created) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary

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
                .background(accentColor)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (created) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (timeText.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            onClick = { if (!created) onCreate() },
            shape = RoundedCornerShape(999.dp),
            color = if (created) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                text = if (created) "已创建" else "添加",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (created) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
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
    val created = suggestion.status == QuickMemoSuggestionStatus.CREATED
    val containerColor = if (created) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    }
    Surface(
        onClick = { if (!created) onCreate() },
        shape = RoundedCornerShape(16.dp),
        color = containerColor
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
                    .background(if (created) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (created) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (timeText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (created) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (created) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = if (created) "已创建" else "添加",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (created) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
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
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
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
    if (memo.type == QuickMemoType.IMAGE) return if (memo.isTodo) "图片待办" else "图片随口记"
    if (memo.type != QuickMemoType.VOICE) return if (memo.isTodo) "文字待办" else "文字随口记"
    return when (memo.transcriptionStatus) {
        QuickMemoTranscriptionStatus.PENDING -> "等待转写"
        QuickMemoTranscriptionStatus.PROCESSING -> "正在转写"
        QuickMemoTranscriptionStatus.SUCCESS -> "转写完成"
        QuickMemoTranscriptionStatus.FAILED -> "转写失败，可重试"
        else -> "仅音频"
    }
}

@Composable
private fun QuickMemoImagePreview(
    imagePath: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    preserveAspectRatio: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onDismissSelection: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val bitmap = remember(imagePath) { decodeQuickMemoPreviewBitmap(imagePath) }
    val shape = RoundedCornerShape(cornerRadius)
    if (bitmap != null) {
        if (preserveAspectRatio) {
            BoxWithConstraints(modifier = modifier) {
                val ratio = (bitmap.width.toFloat() / bitmap.height.toFloat()).takeIf { it.isFinite() && it > 0f } ?: 1f
                val targetHeight = (maxWidth / ratio).coerceIn(120.dp, 520.dp)
                QuickMemoImageBox(
                    bitmap = bitmap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(targetHeight),
                    shape = shape,
                    contentScale = contentScale,
                    selected = selected,
                    onLongPress = onLongPress,
                    onDismissSelection = onDismissSelection,
                    onDelete = onDelete
                )
            }
        } else {
            QuickMemoImageBox(
                bitmap = bitmap,
                modifier = modifier,
                shape = shape,
                contentScale = contentScale,
                selected = selected,
                onLongPress = onLongPress,
                onDismissSelection = onDismissSelection,
                onDelete = onDelete
            )
        }
    } else {
        Box(
            modifier = (if (preserveAspectRatio) modifier.height(180.dp) else modifier)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "图片不可用",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun QuickMemoImageBox(
    bitmap: Bitmap,
    modifier: Modifier,
    shape: RoundedCornerShape,
    contentScale: ContentScale,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onDismissSelection: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .combinedClickable(
                onClick = { if (selected) onDismissSelection() },
                onLongClick = onLongPress
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "随口记图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.36f))
            )
            Surface(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(34.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.error,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "删除图片",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun activeTextQuickMemoId(state: CapsuleUiState): Long? {
    val active = state as? CapsuleUiState.Active ?: return null
    return active.capsules.firstOrNull { it.type == CapsuleType.TEXT_QUICK_MEMO }
        ?.id
        ?.removePrefix(TEXT_QUICK_MEMO_ID_PREFIX)
        ?.toLongOrNull()
}

private fun toggleQuickMemoPinned(
    viewModel: MainViewModel,
    memoId: Long,
    isPinned: Boolean,
    context: Context
) {
    if (isPinned) {
        viewModel.clearPinnedQuickMemo(memoId) { result ->
            result
                .onSuccess { Toast.makeText(context, "已移除挂起", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "移除挂起失败", Toast.LENGTH_SHORT).show() }
        }
    } else {
        viewModel.pinQuickMemo(memoId) { result ->
            result
                .onSuccess { Toast.makeText(context, "已挂起到胶囊", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "挂起失败", Toast.LENGTH_SHORT).show() }
        }
    }
}

private fun decodeQuickMemoPreviewBitmap(imagePath: String?, maxSide: Int = 900): Bitmap? {
    val path = imagePath?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sample = 1
        while (width / sample > maxSide || height / sample > maxSide) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }.getOrNull()
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
