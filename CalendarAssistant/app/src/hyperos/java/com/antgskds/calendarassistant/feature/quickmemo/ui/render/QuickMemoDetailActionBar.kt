package com.antgskds.calendarassistant.feature.quickmemo.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Unpin
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HyperMemoAction(
                selected = isPinned,
                enabled = !isRecordingVoice,
                onClick = onPinClick,
            ) {
                Icon(
                    imageVector = if (isPinned) MiuixIcons.Normal.Unpin else MiuixIcons.Normal.Pin,
                    contentDescription = if (isPinned) "取消实况挂起" else "挂到实况",
                )
            }
            HyperMemoAction(
                selected = isTodo || isCompleted,
                enabled = !isRecordingVoice,
                onClick = onTodoClick,
            ) {
                Icon(MiuixIcons.Normal.SelectAll, if (isCompleted) "撤回待办" else "设为待办")
            }
            HyperMemoAction(
                selected = isRecordingVoice || hasVoice,
                enabled = !isSavingVoice,
                onClick = onVoiceClick,
            ) {
                Icon(MiuixIcons.Normal.Mic, if (isRecordingVoice) "结束录音" else "录音")
            }
            HyperMemoAction(
                selected = hasImage,
                enabled = !isAttachingImage && !isRecordingVoice,
                onClick = onImageClick,
            ) {
                Icon(MiuixIcons.Normal.Image, if (hasImage) "更换图片" else "插入图片")
            }
        }
    }
}

@Composable
private fun HyperMemoAction(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        backgroundColor = if (selected) {
            MiuixTheme.colorScheme.primaryVariant
        } else {
            MiuixTheme.colorScheme.surfaceContainer
        },
        content = content,
    )
}
