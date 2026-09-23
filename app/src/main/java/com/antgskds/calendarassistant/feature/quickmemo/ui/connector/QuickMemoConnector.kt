package com.antgskds.calendarassistant.feature.quickmemo.ui.connector

import android.content.Context
import android.widget.Toast
import com.antgskds.calendarassistant.feature.quickmemo.application.audio.AudioPlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleType
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoDetailUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoListUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoUiAction
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.QuickMemoDetailScreen
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.QuickMemoScreen
import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoAutoStopPolicy
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.shared.ui.adaptive.AdaptiveTwoPaneLayout
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.feature.settings.developer.application.DemoModeDataFactory

private const val TEXT_QUICK_MEMO_ID_PREFIX = "TEXT_QUICK_MEMO_"

@Composable
fun QuickMemoPage(
    viewModel: MainViewModel,
    searchQuery: String = "",
    uiSize: Int = 2,
    extraBottomPadding: Dp = 0.dp,
    twoPane: Boolean = false,
    openMemoId: Long? = null,
    onMemoOpened: () -> Unit = {},
    onOpenDetail: (Long) -> Unit = {},
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val mainUiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val demoModeEnabled = mainUiState.settings.developerOptionsEnabled &&
        mainUiState.settings.developerDemoModeEnabled
    val displayedMemos = remember(quickMemos, demoModeEnabled, mainUiState.today) {
        if (demoModeEnabled) DemoModeDataFactory.quickMemos(mainUiState.today) else quickMemos
    }
    var demoVoicePlaying by remember(demoModeEnabled) { mutableStateOf(true) }
    val displayedPlayback = if (demoModeEnabled) AudioPlaybackState(DemoModeDataFactory.QUICK_MEMO_AUDIO_PATH, demoVoicePlaying) else playbackState
    val state = remember(displayedMemos, suggestions, displayedPlayback, capsuleUiState, demoModeEnabled) {
        QuickMemoListUiState(
            memos = displayedMemos,
            suggestions = if (demoModeEnabled) emptyList() else suggestions,
            playbackState = displayedPlayback,
            pinnedMemoId = activeTextQuickMemoId(capsuleUiState)
        )
    }

    var selectedMemoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val memoIds = remember(displayedMemos) { displayedMemos.mapNotNull { it.id } }

    LaunchedEffect(twoPane, openMemoId, memoIds) {
        if (twoPane && openMemoId != null && openMemoId in memoIds) {
            selectedMemoId = openMemoId
            onMemoOpened()
        }
    }

    LaunchedEffect(twoPane, memoIds) {
        if (!twoPane) {
            selectedMemoId = null
        } else if (selectedMemoId !in memoIds) {
            selectedMemoId = memoIds.firstOrNull()
        }
    }

    val listContent: @Composable () -> Unit = {
        QuickMemoScreen(
            state = state,
            searchQuery = searchQuery,
            uiSize = uiSize,
            extraBottomPadding = extraBottomPadding,
            selectedMemoId = selectedMemoId.takeIf { twoPane },
            reserveFloatingBarSpace = !twoPane,
            hapticEnabled = hapticEnabled,
            onAction = { action ->
                if (demoModeEnabled && action is QuickMemoUiAction.ToggleAudio) {
                    demoVoicePlaying = !demoVoicePlaying
                    return@QuickMemoScreen
                }
                if (demoModeEnabled && action !is QuickMemoUiAction.OpenDetail) return@QuickMemoScreen
                handleQuickMemoAction(
                    action = action,
                    viewModel = viewModel,
                    context = context,
                    onOpenDetail = { memoId ->
                        if (twoPane) selectedMemoId = memoId else onOpenDetail(memoId)
                    },
                    onPendingDeleteChange = onPendingDeleteChange
                )
            }
        )
    }

    if (!twoPane) {
        listContent()
        return
    }

    AdaptiveTwoPaneLayout(
        primaryWidth = ConfigCatalog.ADAPTIVE_COMPACT_PANE_WIDTH_DP.dp,
        primary = listContent,
        secondary = {
            val memoId = selectedMemoId
            if (memoId == null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "选择一条随口记查看详情",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                QuickMemoDetailPage(
                    memoId = memoId,
                    viewModel = viewModel,
                    onBack = { selectedMemoId = null },
                    uiSize = uiSize,
                    hapticEnabled = hapticEnabled,
                    backgroundMode = mainUiState.settings.appBackgroundImagePath.isNotBlank(),
                    miuiBlurEnabled = mainUiState.settings.appBackgroundMiuiBlurTestEnabled,
                    cardAlphaPercent = mainUiState.settings.appBackgroundCardAlphaPercent,
                    embedded = true,
                )
            }
        },
    )
}

@Composable
fun QuickMemoDetailPage(
    memoId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    uiSize: Int = 2,
    hapticEnabled: Boolean = true,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
    embedded: Boolean = false,
) {
    val quickMemos by viewModel.quickMemos.collectAsState()
    val reminders by viewModel.quickMemoReminders.collectAsState()
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val mainUiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val demoModeEnabled = mainUiState.settings.developerOptionsEnabled &&
        mainUiState.settings.developerDemoModeEnabled
    val displayedMemos = remember(quickMemos, demoModeEnabled, mainUiState.today) {
        if (demoModeEnabled) DemoModeDataFactory.quickMemos(mainUiState.today) else quickMemos
    }
    var demoVoicePlaying by remember(demoModeEnabled) { mutableStateOf(true) }
    val displayedPlayback = if (demoModeEnabled) AudioPlaybackState(DemoModeDataFactory.QUICK_MEMO_AUDIO_PATH, demoVoicePlaying) else playbackState
    val state = remember(memoId, displayedMemos, reminders, suggestions, displayedPlayback, capsuleUiState, demoModeEnabled) {
        val memo = displayedMemos.firstOrNull { it.id == memoId }
        QuickMemoDetailUiState(
            memo = memo,
            reminders = if (demoModeEnabled) emptyList() else reminders.filter { it.quickMemoId == memoId },
            suggestions = if (demoModeEnabled) emptyList() else suggestions.filter {
                it.quickMemoId == memoId &&
                    (it.status == QuickMemoSuggestionStatus.PENDING ||
                        it.status == QuickMemoSuggestionStatus.CREATED)
            },
            playbackState = displayedPlayback,
            isPinned = memo?.id?.let { it == activeTextQuickMemoId(capsuleUiState) } == true
        )
    }

    DisposableEffect(memoId, demoModeEnabled) {
        onDispose {
            val latest = viewModel.quickMemos.value.firstOrNull { it.id == memoId }
            if (!demoModeEnabled && latest != null && isBlankTextQuickMemo(latest)) {
                viewModel.deleteQuickMemo(memoId)
            }
        }
    }

    QuickMemoDetailScreen(
        state = state,
        onBack = onBack,
        uiSize = uiSize,
        hapticEnabled = hapticEnabled,
        backgroundMode = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        cardAlphaPercent = cardAlphaPercent,
        embedded = embedded,
        autoStopDurationMs = QuickMemoAutoStopPolicy.durationMillis(mainUiState.settings),
        onAction = { action ->
            if (demoModeEnabled && action is QuickMemoUiAction.ToggleAudio) {
                demoVoicePlaying = !demoVoicePlaying
                return@QuickMemoDetailScreen
            }
            if (demoModeEnabled) {
                Toast.makeText(context, "演示模式不会保存修改，请关闭演示模式后操作真实记录", Toast.LENGTH_SHORT).show()
                return@QuickMemoDetailScreen
            }
            handleQuickMemoAction(
                action = action,
                viewModel = viewModel,
                context = context,
                onOpenDetail = {},
                onPendingDeleteChange = {}
            )
        }
    )
}

private fun handleQuickMemoAction(
    action: QuickMemoUiAction,
    viewModel: MainViewModel,
    context: Context,
    onOpenDetail: (Long) -> Unit,
    onPendingDeleteChange: (QuickMemoEntity?) -> Unit
) {
    when (action) {
        is QuickMemoUiAction.OpenDetail -> onOpenDetail(action.memoId)
        is QuickMemoUiAction.RequestDelete -> onPendingDeleteChange(action.memo)
        is QuickMemoUiAction.Delete -> viewModel.deleteQuickMemo(action.memoId)
        is QuickMemoUiAction.ToggleTodoCompletion -> viewModel.toggleQuickMemoTodoCompletion(action.memoId)
        is QuickMemoUiAction.MarkTodo -> viewModel.markQuickMemoTodo(action.memoId)
        is QuickMemoUiAction.RemoveTodo -> viewModel.removeQuickMemoTodo(action.memoId)
        is QuickMemoUiAction.ToggleAudio -> viewModel.toggleAudioPlayback(action.audioPath)
        is QuickMemoUiAction.UpdateBody -> viewModel.updateQuickMemoBody(action.memoId, action.body)
        is QuickMemoUiAction.SaveReminder ->
            viewModel.saveQuickMemoReminder(
                action.memoId,
                action.reminderId,
                action.reminderAt,
                action.reminderRRule
            ) { result ->
                result.onFailure { error ->
                    Toast.makeText(context, error.message ?: "提醒设置失败", Toast.LENGTH_SHORT).show()
                }
            }
        is QuickMemoUiAction.DeleteReminder ->
            viewModel.deleteQuickMemoReminder(action.reminderId) { result ->
                result.onFailure { error ->
                    Toast.makeText(context, error.message ?: "提醒删除失败", Toast.LENGTH_SHORT).show()
                }
            }
        is QuickMemoUiAction.AttachImage ->
            viewModel.attachImageToQuickMemo(action.memoId, action.imagePath, action.onResult)
        is QuickMemoUiAction.RemoveImage ->
            viewModel.removeImageFromQuickMemo(action.memoId, action.onResult)
        is QuickMemoUiAction.AttachVoice ->
            viewModel.attachVoiceToQuickMemo(
                action.memoId,
                action.audioPath,
                action.durationMs,
                action.onResult
            )
        is QuickMemoUiAction.RetryTranscription -> viewModel.retryQuickMemoTranscription(action.memoId)
        is QuickMemoUiAction.CreateSuggestionEvent ->
            viewModel.createEventFromQuickMemoSuggestion(action.suggestionId)
        is QuickMemoUiAction.TogglePinned -> {
            if (action.isPinned) {
                viewModel.clearPinnedQuickMemo(action.memoId) { result ->
                    result
                        .onSuccess { Toast.makeText(context, "已移除挂起", Toast.LENGTH_SHORT).show() }
                        .onFailure {
                            Toast.makeText(context, it.message ?: "移除挂起失败", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                viewModel.pinQuickMemo(action.memoId) { result ->
                    result
                        .onSuccess { Toast.makeText(context, "已挂起到胶囊", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "挂起失败", Toast.LENGTH_SHORT).show() }
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

private fun isBlankTextQuickMemo(memo: QuickMemoEntity): Boolean =
    memo.type == QuickMemoType.TEXT &&
        memo.bodyText.isBlank() &&
        memo.imagePath.isNullOrBlank() &&
        memo.audioPath.isNullOrBlank()
