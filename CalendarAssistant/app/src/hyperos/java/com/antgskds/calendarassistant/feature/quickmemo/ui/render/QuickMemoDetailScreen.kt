package com.antgskds.calendarassistant.feature.quickmemo.ui.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoDetailUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoUiAction
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.QuickMemoDetailContent
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun QuickMemoDetailScreen(
    state: QuickMemoDetailUiState,
    onBack: () -> Unit,
    uiSize: Int,
    hapticEnabled: Boolean,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    cardAlphaPercent: Int,
    autoStopDurationMs: Long?,
    onAction: (QuickMemoUiAction) -> Unit,
) {
    val memo = state.memo
    AppBackgroundStyleTheme(
        enabled = backgroundMode,
        miuiBlurEnabled = false,
        cardAlphaPercent = 100,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                SmallTopAppBar(
                    title = "随口记详情",
                    color = MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Normal.Back, "返回")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (memo == null) {
                    Text(
                        text = "随口记不存在或已删除",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    return@Box
                }

                QuickMemoDetailContent(
                    memo = memo,
                    suggestions = state.suggestions,
                    playbackState = state.playbackState,
                    onSaveBody = { body ->
                        memo.id?.let { onAction(QuickMemoUiAction.UpdateBody(it, body)) }
                    },
                    onAttachImage = { path, onResult ->
                        memo.id?.let {
                            onAction(QuickMemoUiAction.AttachImage(it, path, onResult))
                        } ?: onResult(Result.failure(IllegalStateException("随口记不存在")))
                    },
                    onRemoveImage = { onResult ->
                        memo.id?.let {
                            onAction(QuickMemoUiAction.RemoveImage(it, onResult))
                        } ?: onResult(Result.failure(IllegalStateException("随口记不存在")))
                    },
                    onAttachVoice = { path, duration, onResult ->
                        memo.id?.let {
                            onAction(QuickMemoUiAction.AttachVoice(it, path, duration, onResult))
                        } ?: onResult(Result.failure(IllegalStateException("随口记不存在")))
                    },
                    onToggleTodo = {
                        memo.id?.let { onAction(QuickMemoUiAction.ToggleTodoCompletion(it)) }
                    },
                    onMarkTodo = {
                        memo.id?.let { onAction(QuickMemoUiAction.MarkTodo(it)) }
                    },
                    onToggleAudio = { onAction(QuickMemoUiAction.ToggleAudio(it)) },
                    isPinned = state.isPinned,
                    onTogglePinned = {
                        memo.id?.let { onAction(QuickMemoUiAction.TogglePinned(it, state.isPinned)) }
                    },
                    onRetryTranscription = {
                        memo.id?.let { onAction(QuickMemoUiAction.RetryTranscription(it)) }
                    },
                    onCreateSuggestion = { suggestion ->
                        suggestion.id?.let { onAction(QuickMemoUiAction.CreateSuggestionEvent(it)) }
                    },
                    uiSize = uiSize,
                    hapticEnabled = hapticEnabled,
                    backgroundMode = backgroundMode,
                    miuiBlurEnabled = false,
                    autoStopDurationMs = autoStopDurationMs,
                )
            }
        }
    }
}
