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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.QuickMemoFolderPicker
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoDraftPolicy
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.QuickMemoSelectionToolbar
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.QuickMemoDeleteConfirmationSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.QuickMemoFolderMenu
import com.antgskds.calendarassistant.shared.ui.material.component.AppMenuItem
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
    val draft by viewModel.quickMemoDraft.collectAsState()
    val folders by viewModel.quickMemoFolders.collectAsState()
    val browser by viewModel.quickMemoBrowser.collectAsState()
    var showMove by remember { mutableStateOf(false) }
    var deleteIds by remember { mutableStateOf<Set<Long>?>(null) }
    var organizing by remember { mutableStateOf(false) }
    val suggestions by viewModel.quickMemoSuggestions.collectAsState()
    val playbackState by viewModel.audioPlaybackState.collectAsState()
    val capsuleUiState by viewModel.capsuleUiState.collectAsState()
    val mainUiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val demoModeEnabled = mainUiState.settings.developerOptionsEnabled &&
        mainUiState.settings.developerDemoModeEnabled
    val reminders by viewModel.quickMemoReminders.collectAsState()
    val displayedMemos = remember(quickMemos, reminders, draft?.storedId, demoModeEnabled, mainUiState.today) {
        if (demoModeEnabled) DemoModeDataFactory.quickMemos(mainUiState.today) else quickMemos.filter { memo ->
            memo.id != draft?.storedId && QuickMemoDraftPolicy.hasContent(memo, reminders.any { it.quickMemoId == memo.id })
        }
    }
    val visibleMemos = remember(displayedMemos, browser.folderId, searchQuery) {
        displayedMemos.filter { memo ->
            (browser.folderId == null || if (browser.folderId == "") memo.folderId == null else memo.folderId == browser.folderId) &&
                (searchQuery.isBlank() || memo.bodyText.contains(searchQuery, true) || memo.title.contains(searchQuery, true) ||
                    memo.sourceUrl?.contains(searchQuery, true) == true)
        }
    }
    val visibleIds = visibleMemos.mapNotNull { it.id }.toSet()
    LaunchedEffect(visibleIds, folders) {
        val current = viewModel.quickMemoBrowser.value
        // Startup folder flow can be empty before the first Room emission; only remove missing selections here.
        viewModel.updateQuickMemoBrowser(current.copy(selectedIds = current.selectedIds.intersect(visibleIds)))
    }
    BackHandler(enabled = browser.selectionMode) {
        viewModel.updateQuickMemoBrowser(browser.cancelSelection())
    }
    var demoVoicePlaying by remember(demoModeEnabled) { mutableStateOf(true) }
    val displayedPlayback = if (demoModeEnabled) AudioPlaybackState(DemoModeDataFactory.QUICK_MEMO_AUDIO_PATH, demoVoicePlaying) else playbackState
    val state = remember(visibleMemos, suggestions, displayedPlayback, capsuleUiState, demoModeEnabled) {
        QuickMemoListUiState(
            memos = visibleMemos,
            suggestions = if (demoModeEnabled) emptyList() else suggestions,
            playbackState = displayedPlayback,
            pinnedMemoId = activeTextQuickMemoId(capsuleUiState)
        )
    }

    var selectedMemoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val memoIds = remember(displayedMemos) { displayedMemos.mapNotNull { it.id } }

    LaunchedEffect(twoPane, openMemoId, memoIds, draft != null) {
        if (twoPane && openMemoId != null && (openMemoId in memoIds || QuickMemoDraftPolicy.isDraft(openMemoId) && draft?.memo?.id == openMemoId)) {
            selectedMemoId = openMemoId
            viewModel.updateQuickMemoBrowser(browser.filter(null))
            onMemoOpened()
        }
    }

    LaunchedEffect(twoPane, memoIds, draft != null) {
        if (!twoPane) {
            selectedMemoId = null
        } else if (selectedMemoId !in memoIds && !(selectedMemoId != null && draft?.memo?.id == selectedMemoId)) {
            selectedMemoId = memoIds.firstOrNull()
        }
    }

    val listContent: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                QuickMemoScreen(
                    state = state,
                    searchQuery = searchQuery,
                    uiSize = uiSize,
                    extraBottomPadding = extraBottomPadding,
                    selectedMemoId = selectedMemoId.takeIf { twoPane },
                    selectedMemoIds = browser.selectedIds,
                    selectionMode = browser.selectionMode,
                    reserveFloatingBarSpace = !twoPane || browser.selectionMode,
                    hapticEnabled = hapticEnabled,
                    onAction = { action ->
                        if (demoModeEnabled && action is QuickMemoUiAction.ToggleAudio) {
                            demoVoicePlaying = !demoVoicePlaying
                            return@QuickMemoScreen
                        }
                        if (demoModeEnabled && action !is QuickMemoUiAction.OpenDetail) return@QuickMemoScreen
                        if (action is QuickMemoUiAction.RequestDelete) {
                            action.memo.id?.let { viewModel.updateQuickMemoBrowser(browser.select(it)) }
                            return@QuickMemoScreen
                        }
                        if (browser.selectionMode) {
                            if (action is QuickMemoUiAction.OpenDetail) viewModel.updateQuickMemoBrowser(browser.toggle(action.memoId))
                            return@QuickMemoScreen
                        }
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
            if (browser.selectionMode) QuickMemoSelectionToolbar(
                allSelected = visibleIds.isNotEmpty() && browser.selectedIds.containsAll(visibleIds),
                onSelectAll = {
                    viewModel.updateQuickMemoBrowser(browser.copy(selectedIds =
                        if (browser.selectedIds.containsAll(visibleIds)) emptySet() else visibleIds))
                },
                enabled = browser.selectedIds.isNotEmpty() && !organizing,
                onMove = { showMove = true },
                onDelete = { deleteIds = browser.selectedIds.toSet() },
                backgroundMode = mainUiState.settings.appBackgroundImagePath.isNotBlank(),
                miuiBlurEnabled = mainUiState.settings.appBackgroundMiuiBlurTestEnabled,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = extraBottomPadding),
            )
        }
    }

    QuickMemoFolderPicker(
        visible = showMove, folders = folders, isLoading = organizing,
        onDismiss = { showMove = false },
        onSelect = { folderId ->
            if (!organizing) {
                val ids = browser.selectedIds.toList()
                organizing = true
                viewModel.moveQuickMemos(ids, folderId) { result ->
                    organizing = false
                    result.onSuccess { showMove = false; viewModel.updateQuickMemoBrowser(viewModel.quickMemoBrowser.value.cancelSelection()) }
                        .onFailure { Toast.makeText(context, it.message ?: "移动失败", Toast.LENGTH_SHORT).show() }
                }
            }
        },
        onCreate = { name, callback ->
            if (demoModeEnabled) callback(Result.failure(IllegalStateException("演示模式不会保存修改")))
            else viewModel.createQuickMemoFolder(name, callback)
        },
        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
    )
    QuickMemoDeleteConfirmationSheet(
        visible = deleteIds != null, title = "删除随口记",
        message = "确认删除 ${deleteIds?.size ?: 0} 条随口记？删除后无法恢复。",
        confirmText = "删除", isLoading = organizing,
        confirmEnabled = !deleteIds.isNullOrEmpty(),
        onConfirm = {
            deleteIds?.takeIf { !organizing }?.let { ids ->
                organizing = true
                viewModel.deleteQuickMemos(ids) { result ->
                    organizing = false
                    result.onSuccess { deleteIds = null; viewModel.updateQuickMemoBrowser(viewModel.quickMemoBrowser.value.cancelSelection()) }
                        .onFailure { Toast.makeText(context, it.message ?: "删除失败，请核对剩余记录", Toast.LENGTH_SHORT).show() }
                }
            }
        },
        onDismiss = { deleteIds = null },
    )

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

/** Menu content is hosted beside the top-bar button, not in the list viewport. */
@Composable
fun QuickMemoFolderMenuRoute(
    viewModel: MainViewModel,
    containerColor: Color,
    selectionColor: Color,
    contentColor: Color,
    additionalItems: List<AppMenuItem> = emptyList(),
) {
    val folders by viewModel.quickMemoFolders.collectAsState()
    val browser by viewModel.quickMemoBrowser.collectAsState()
    val mainState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val demoModeEnabled = mainState.settings.developerOptionsEnabled && mainState.settings.developerDemoModeEnabled
    QuickMemoFolderMenu(
        expanded = browser.foldersVisible, folders = folders, selectedFolderId = browser.folderId,
        onDismiss = { viewModel.updateQuickMemoBrowser(viewModel.quickMemoBrowser.value.copy(foldersVisible = false)) },
        onSelect = { viewModel.updateQuickMemoBrowser(browser.filter(it)) },
        onCreate = { name, callback ->
            if (demoModeEnabled) callback(Result.failure(IllegalStateException("演示模式不会保存修改")))
            else viewModel.createQuickMemoFolder(name, callback)
        },
        onDelete = { id, callback ->
            if (demoModeEnabled) callback(Result.failure(IllegalStateException("演示模式不会保存修改")))
            else viewModel.deleteQuickMemoFolder(id, callback)
        },
        onRename = { id, name, callback ->
            if (demoModeEnabled) callback(Result.failure(IllegalStateException("演示模式不会保存修改")))
            else viewModel.renameQuickMemoFolder(id, name, callback)
        },
        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        containerColor = containerColor, selectionColor = selectionColor, contentColor = contentColor,
        additionalItems = additionalItems,
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
    val linkAnalyses by viewModel.quickMemoLinkAnalyses.collectAsState()
    val draft by viewModel.quickMemoDraft.collectAsState()
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
    val storedId = if (QuickMemoDraftPolicy.isDraft(memoId)) draft?.takeIf { it.memo.id == memoId }?.storedId else memoId
    val state = remember(memoId, storedId, draft, displayedMemos, reminders, suggestions, displayedPlayback, capsuleUiState, demoModeEnabled, linkAnalyses) {
        val memo = if (QuickMemoDraftPolicy.isDraft(memoId)) {
            displayedMemos.firstOrNull { it.id == storedId }?.copy(id = memoId) ?: draft?.memo?.takeIf { it.id == memoId }
        } else displayedMemos.firstOrNull { it.id == memoId }
        QuickMemoDetailUiState(
            memo = memo,
            reminders = if (demoModeEnabled) emptyList() else reminders.filter { it.quickMemoId == storedId },
            suggestions = if (demoModeEnabled) emptyList() else suggestions.filter {
                it.quickMemoId == storedId &&
                    (it.status == QuickMemoSuggestionStatus.PENDING ||
                        it.status == QuickMemoSuggestionStatus.CREATED)
            },
            playbackState = displayedPlayback,
            linkAnalysis = if(demoModeEnabled) null else linkAnalyses.firstOrNull { it.memoId==storedId },
            isPinned = storedId != null && storedId == activeTextQuickMemoId(capsuleUiState)
        )
    }

    DisposableEffect(memoId, demoModeEnabled) {
        onDispose {
            if (!demoModeEnabled && QuickMemoDraftPolicy.isDraft(memoId)) viewModel.finishQuickMemoDraft(memoId)
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
        is QuickMemoUiAction.UpdateTitle -> viewModel.updateQuickMemoTitle(action.memoId, action.title)
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
        is QuickMemoUiAction.AnalyzeLink -> viewModel.retryQuickMemoLinkAnalysis(action.memoId) { result ->
            result.onFailure { Toast.makeText(context,it.message ?: "无法开始摘要",Toast.LENGTH_LONG).show() }
        }
        is QuickMemoUiAction.CancelLinkAnalysis -> viewModel.cancelQuickMemoLinkAnalysis(action.memoId)
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
