package com.antgskds.calendarassistant.feature.quickmemo.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.MaterialQuickMemoDetailBottomBar

@Composable
fun QuickMemoDetailActionBar(
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
    modifier: Modifier = Modifier,
) = MaterialQuickMemoDetailBottomBar(
    isRecordingVoice = isRecordingVoice,
    isSavingVoice = isSavingVoice,
    hasVoice = hasVoice,
    isPinned = isPinned,
    isTodo = isTodo,
    isCompleted = isCompleted,
    hasImage = hasImage,
    isAttachingImage = isAttachingImage,
    onVoiceClick = onVoiceClick,
    onPinClick = onPinClick,
    onTodoClick = onTodoClick,
    onImageClick = onImageClick,
    backgroundMode = backgroundMode,
    miuiBlurEnabled = miuiBlurEnabled,
    modifier = modifier,
)
