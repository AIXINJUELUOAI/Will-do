package com.antgskds.calendarassistant.platform.floating.ui.render.material

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeQrSupport
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingMediaCardUiAction
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingMediaCardUiState
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingMediaPage
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppOverlayCard
import com.antgskds.calendarassistant.shared.util.ImageImportUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface MediaBitmapState {
    data object Loading : MediaBitmapState
    data class Ready(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : MediaBitmapState
    data object Failed : MediaBitmapState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MaterialFloatingMediaCard(
    state: FloatingMediaCardUiState,
    onAction: (FloatingMediaCardUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    if (state.pages.isEmpty()) {
        LaunchedEffect(Unit) { onAction(FloatingMediaCardUiAction.MediaUnavailable) }
        return
    }
    val pagerState = rememberPagerState(pageCount = { state.pages.size })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    haptics.click()
                    onAction(FloatingMediaCardUiAction.Dismiss)
                }
            )
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AppOverlayCard(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .height(520.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                ),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 20.dp, end = 12.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOf(state.typeLabel, state.detailText)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.click()
                            onAction(FloatingMediaCardUiAction.Dismiss)
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) { pageIndex ->
                        when (val page = state.pages[pageIndex]) {
                            is FloatingMediaPage.QrCode -> QrMediaPage(page)
                            is FloatingMediaPage.ImageFile -> ImageMediaPage(
                                page = page,
                                onUnavailable = { onAction(FloatingMediaCardUiAction.MediaUnavailable) }
                            )
                        }
                    }

                    if (state.pages.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${state.pages.size}",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.86f),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                }

                state.completeLabel?.let { label ->
                    Button(
                        onClick = {
                            haptics.confirm()
                            onAction(FloatingMediaCardUiAction.Complete)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(label)
                    }
                } ?: Box(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun QrMediaPage(page: FloatingMediaPage.QrCode) {
    val qrImage = remember(page.payload) {
        InstantCodeQrSupport.createQrBitmap(page.payload)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (qrImage != null) {
                Image(
                    bitmap = qrImage,
                    contentDescription = page.contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    "二维码不可用",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImageMediaPage(
    page: FloatingMediaPage.ImageFile,
    onUnavailable: () -> Unit
) {
    val bitmapState by produceState<MediaBitmapState>(MediaBitmapState.Loading, page.path) {
        value = withContext(Dispatchers.IO) {
            val file = File(page.path)
            if (!file.isFile) {
                MediaBitmapState.Failed
            } else {
                ImageImportUtils.decodeSampledBitmapFromFile(file, maxSide = 1600)
                    ?.asImageBitmap()
                    ?.let { MediaBitmapState.Ready(it) }
                    ?: MediaBitmapState.Failed
            }
        }
    }
    LaunchedEffect(bitmapState) {
        if (bitmapState == MediaBitmapState.Failed) onUnavailable()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        when (val current = bitmapState) {
            MediaBitmapState.Loading -> CircularProgressIndicator()
            is MediaBitmapState.Ready -> {
                val imageAspectRatio = current.bitmap.width.toFloat() / current.bitmap.height.toFloat()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    item {
                        Image(
                            bitmap = current.bitmap,
                            contentDescription = page.contentDescription,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(imageAspectRatio),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
            MediaBitmapState.Failed -> Text(
                "图片无法打开",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
