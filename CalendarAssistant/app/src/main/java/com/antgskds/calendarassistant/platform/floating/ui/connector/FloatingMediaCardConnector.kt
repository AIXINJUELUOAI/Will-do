package com.antgskds.calendarassistant.platform.floating.ui.connector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingMediaCardUiAction
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingMediaCardUiState
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingMediaPage
import com.antgskds.calendarassistant.platform.floating.ui.render.FloatingMediaCardContent

@Composable
fun EventMediaFloatingCardRoute(
    event: Event,
    imagePaths: List<String>,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    onMediaUnavailable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember(event, imagePaths) { buildEventMediaState(event, imagePaths) }
    FloatingMediaCardContent(
        state = state,
        onAction = { action ->
            when (action) {
                FloatingMediaCardUiAction.Dismiss -> onClose()
                FloatingMediaCardUiAction.Complete -> onComplete()
                FloatingMediaCardUiAction.MediaUnavailable -> onMediaUnavailable()
            }
        },
        modifier = modifier
    )
}

@Composable
fun QuickMemoMediaFloatingCardRoute(
    memoId: Long,
    bodyText: String,
    imagePath: String,
    onClose: () -> Unit,
    onMediaUnavailable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember(memoId, bodyText, imagePath) {
        FloatingMediaCardUiState(
            typeLabel = "随口记图片",
            title = bodyText.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "图片随口记",
            pages = listOf(FloatingMediaPage.ImageFile(imagePath, "随口记图片"))
        )
    }
    FloatingMediaCardContent(
        state = state,
        onAction = { action ->
            when (action) {
                FloatingMediaCardUiAction.Dismiss -> onClose()
                FloatingMediaCardUiAction.MediaUnavailable -> onMediaUnavailable()
                FloatingMediaCardUiAction.Complete -> Unit
            }
        },
        modifier = modifier
    )
}

internal fun buildEventMediaState(event: Event, imagePaths: List<String>): FloatingMediaCardUiState {
    val qrState = event.codeQrPayload.takeIf { it.isNotBlank() }?.let { buildPickupQrFloatingCardUiState(event) }
    val pages = buildList {
        if (qrState != null) {
            add(FloatingMediaPage.QrCode(qrState.qrPayload, qrState.typeLabel))
        }
        imagePaths.forEachIndexed { index, path ->
            add(FloatingMediaPage.ImageFile(path, "日程图片 ${index + 1}"))
        }
    }
    return FloatingMediaCardUiState(
        typeLabel = qrState?.typeLabel ?: "日程图片",
        title = event.title.ifBlank { qrState?.typeLabel ?: "日程图片" },
        detailText = qrState?.let { listOf(it.code, it.location).filter { value -> value.isNotBlank() }.joinToString(" · ") }.orEmpty(),
        pages = pages,
        completeLabel = qrState?.completeLabel
    )
}
