package com.antgskds.calendarassistant.feature.appearance.ui.connector

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppWallpaperImage
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundAverageLuminance
import com.antgskds.calendarassistant.app.ui.theme.material.background.calculateAppWallpaperLayout
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey
import com.antgskds.calendarassistant.feature.home.domain.sanitizeHomeStartPageKey
import com.antgskds.calendarassistant.feature.home.domain.visibleHomeBottomItems
import com.antgskds.calendarassistant.feature.home.ui.connector.HomePageRoute
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBar
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.adaptive.AdaptiveTwoPaneLayout
import com.antgskds.calendarassistant.shared.ui.adaptive.AdaptiveLayoutInfo
import com.antgskds.calendarassistant.shared.ui.adaptive.LocalAdaptiveLayoutInfo
import com.antgskds.calendarassistant.shared.ui.material.component.AppGlassSettings
import com.antgskds.calendarassistant.shared.ui.material.component.AppGlassSettingsProvider
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

internal data class WallpaperEditorSource(
    val bitmap: Bitmap,
    val uri: Uri,
    val averageLuminance: Float
)

internal data class WallpaperEditorResult(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val visibleLuminance: Float
)

@Composable
internal fun WallpaperEditorDialog(
    source: WallpaperEditorSource,
    settings: MySettings,
    mainViewModel: MainViewModel,
    saving: Boolean,
    onDismiss: () -> Unit,
    onApply: (WallpaperEditorResult) -> Unit
) {
    val imageBitmap = remember(source.bitmap) { source.bitmap.asImageBitmap() }
    var imageScale by remember(source) {
        mutableStateOf(MySettings.APP_BACKGROUND_IMAGE_SCALE_DEFAULT)
    }
    var imageOffsetX by remember(source) {
        mutableStateOf(0f)
    }
    var imageOffsetY by remember(source) {
        mutableStateOf(0f)
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val previewPages = remember(settings.homeBottomItems, settings.voiceInputEnabled) {
        visibleHomeBottomItems(
            raw = settings.homeBottomItems,
            quickMemoEnabled = settings.voiceInputEnabled
        )
    }
    var previewPageKey by remember(source) {
        mutableStateOf(sanitizeHomeStartPageKey(settings.homeStartPageKey, previewPages))
    }
    val wallpaperBackdrop = rememberLayerBackdrop()
    val wallpaperBlurRadius = with(LocalDensity.current) { 28.dp.toPx() }
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    val adaptiveLayoutInfo = LocalAdaptiveLayoutInfo.current

    LaunchedEffect(previewPages, previewPageKey) {
        if (previewPageKey !in previewPages) {
            previewPageKey = previewPages.firstOrNull() ?: HomeEntryKey.TODAY
        }
    }

    DisposableEffect(source.bitmap) {
        onDispose {
            if (!source.bitmap.isRecycled) source.bitmap.recycle()
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val previewContent: @Composable BoxScope.() -> Unit = {
            AppWallpaperImage(
                imageBitmap = imageBitmap,
                scale = imageScale,
                offsetX = imageOffsetX,
                offsetY = imageOffsetY,
                blurRadiusPx = if (settings.appBackgroundWallpaperBlurEnabled) wallpaperBlurRadius else 0f,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(wallpaperBackdrop)
            )

            AppGlassSettingsProvider(
                settings = AppGlassSettings(
                    enabled = settings.appBackgroundMiuiBlurTestEnabled,
                    backdrop = wallpaperBackdrop,
                    overlayBackdrop = wallpaperBackdrop,
                    darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                )
            ) {
                CompositionLocalProvider(LocalAppBackgroundAverageLuminance provides source.averageLuminance) {
                    AppBackgroundStyleTheme(
                        enabled = true,
                        miuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
                        cardAlphaPercent = settings.appBackgroundCardAlphaPercent
                    ) {
                        val previewSettings = settings.copy(
                            appBackgroundEnabled = true,
                            appBackgroundImagePath = settings.appBackgroundImagePath.ifBlank { "wallpaper-preview" },
                            appBackgroundImageScale = imageScale,
                            appBackgroundImageOffsetX = imageOffsetX,
                            appBackgroundImageOffsetY = imageOffsetY
                        )
                        CompositionLocalProvider(LocalAdaptiveLayoutInfo provides AdaptiveLayoutInfo()) {
                            WallpaperHomePreview(
                                mainViewModel = mainViewModel,
                                settings = previewSettings,
                                previewPages = previewPages,
                                selectedPageKey = previewPageKey
                            )
                            WallpaperGestureLayer(
                                source = source,
                                imageSize = IntSize(imageBitmap.width, imageBitmap.height),
                                viewportSize = viewportSize,
                                imageScale = imageScale,
                                imageOffsetX = imageOffsetX,
                                imageOffsetY = imageOffsetY,
                                previewPages = previewPages,
                                previewPageKey = previewPageKey,
                                onTransform = { scale, offsetX, offsetY ->
                                    imageScale = scale
                                    imageOffsetX = offsetX
                                    imageOffsetY = offsetY
                                },
                                onCyclePage = {
                                    val currentIndex = previewPages.indexOf(previewPageKey).coerceAtLeast(0)
                                    if (previewPages.size > 1) {
                                        previewPageKey = previewPages[(currentIndex + 1) % previewPages.size]
                                        haptics.selection()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        val resetPreview = {
            imageScale = MySettings.APP_BACKGROUND_IMAGE_SCALE_DEFAULT
            imageOffsetX = 0f
            imageOffsetY = 0f
        }
        val applyPreview = {
            onApply(
                WallpaperEditorResult(
                    scale = imageScale,
                    offsetX = imageOffsetX,
                    offsetY = imageOffsetY,
                    visibleLuminance = calculateVisibleLuminance(
                        bitmap = source.bitmap,
                        containerSize = viewportSize,
                        scale = imageScale,
                        offsetX = imageOffsetX,
                        offsetY = imageOffsetY
                    )
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (adaptiveLayoutInfo.isTabletop) {
                AdaptiveTwoPaneLayout(
                    primaryFraction = 0.5f,
                    primary = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.94f)
                                    .aspectRatio(9f / 19.5f)
                                    .onSizeChanged { viewportSize = it },
                                content = previewContent,
                            )
                        }
                    },
                    secondary = {
                        Box(modifier = Modifier.fillMaxSize()) {
                            WallpaperEditorTopBar(
                                saving = saving,
                                centered = true,
                                onDismiss = onDismiss,
                                onReset = resetPreview,
                                onApply = applyPreview,
                            )
                        }
                    },
                )
            } else {
                Box(
                    modifier = if (adaptiveLayoutInfo.useNavigationRail) {
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight(0.94f)
                            .aspectRatio(9f / 19.5f)
                            .onSizeChanged { viewportSize = it }
                    } else {
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewportSize = it }
                    },
                    content = previewContent,
                )
                WallpaperEditorTopBar(
                    saving = saving,
                    centered = false,
                    onDismiss = onDismiss,
                    onReset = resetPreview,
                    onApply = applyPreview,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.WallpaperEditorTopBar(
    saving: Boolean,
    centered: Boolean,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = Modifier
            .align(if (centered) Alignment.Center else Alignment.TopCenter)
            .fillMaxWidth()
            .padding(
                top = if (centered) 24.dp else topInset + 10.dp,
                bottom = if (centered) 24.dp else 0.dp,
                start = 16.dp,
                end = 16.dp,
            )
            .zIndex(6f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onDismiss, enabled = !saving) {
                Icon(Icons.Default.Close, contentDescription = "取消")
            }
            FilledTonalIconButton(onClick = onReset, enabled = !saving) {
                Icon(Icons.Default.RestartAlt, contentDescription = "重置")
            }
        }
        FilledTonalIconButton(onClick = onApply, enabled = !saving) {
            Icon(Icons.Default.Check, contentDescription = "应用")
        }
    }
}

@Composable
private fun BoxScope.WallpaperHomePreview(
    mainViewModel: MainViewModel,
    settings: MySettings,
    previewPages: List<String>,
    selectedPageKey: String
) {
    val quickMemos by mainViewModel.quickMemos.collectAsState()

    HomePageRoute(
        viewModel = mainViewModel,
        currentPageKey = selectedPageKey,
        pageOrder = previewPages,
        uiSize = settings.uiSize,
        courseFeatureEnabled = settings.courseFeatureEnabled,
        quickMemoCount = quickMemos.size,
        settingsOverride = settings
    )

    IntegratedFloatingBar(
        isExpanded = false,
        onExpandedChange = {},
        isSidebarOpen = false,
        navItems = previewPages,
        selectedPageKey = selectedPageKey,
        onMenuClick = {},
        onPageClick = {},
        onSearchClick = {},
        onImageClick = {},
        onEditClick = {},
        backgroundMode = true,
        miuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
        cardAlphaPercent = settings.appBackgroundCardAlphaPercent,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = IntegratedFloatingBarBottomSpacing)
            .zIndex(3f)
    )
}

@Composable
private fun BoxScope.WallpaperGestureLayer(
    source: WallpaperEditorSource,
    imageSize: IntSize,
    viewportSize: IntSize,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    previewPages: List<String>,
    previewPageKey: String,
    onTransform: (Float, Float, Float) -> Unit,
    onCyclePage: () -> Unit
) {
    val currentScale by rememberUpdatedState(imageScale)
    val currentOffsetX by rememberUpdatedState(imageOffsetX)
    val currentOffsetY by rememberUpdatedState(imageOffsetY)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
            .pointerInput(previewPages, previewPageKey) {
                detectTapGestures(onTap = { onCyclePage() })
            }
            .pointerInput(source, viewportSize) {
                if (viewportSize == IntSize.Zero) return@pointerInput
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val oldLayout = calculateAppWallpaperLayout(
                        imageSize = imageSize,
                        containerSize = viewportSize,
                        scale = currentScale,
                        offsetX = currentOffsetX,
                        offsetY = currentOffsetY
                    )
                    val nextScale = MySettings.normalizeAppBackgroundImageScale(currentScale * gestureZoom)
                    val nextLayout = calculateAppWallpaperLayout(
                        imageSize = imageSize,
                        containerSize = viewportSize,
                        scale = nextScale,
                        offsetX = 0f,
                        offsetY = 0f
                    )
                    val nextOffsetX = normalizedPan(currentOffsetX * oldLayout.maxPanX + pan.x, nextLayout.maxPanX)
                    val nextOffsetY = normalizedPan(currentOffsetY * oldLayout.maxPanY + pan.y, nextLayout.maxPanY)
                    onTransform(nextScale, nextOffsetX, nextOffsetY)
                }
            }
    )
}


private fun normalizedPan(pan: Float, maxPan: Float): Float {
    if (maxPan <= 0f) return 0f
    return (pan / maxPan).coerceIn(
        MySettings.APP_BACKGROUND_IMAGE_OFFSET_MIN,
        MySettings.APP_BACKGROUND_IMAGE_OFFSET_MAX
    )
}

private fun calculateVisibleLuminance(
    bitmap: Bitmap,
    containerSize: IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Float {
    if (containerSize == IntSize.Zero || bitmap.width <= 0 || bitmap.height <= 0) return -1f
    val layout = calculateAppWallpaperLayout(
        imageSize = IntSize(bitmap.width, bitmap.height),
        containerSize = containerSize,
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY
    )
    if (layout.destinationSize == IntSize.Zero) return -1f
    var total = 0.0
    var count = 0
    val sampleColumns = 24
    val sampleRows = 40
    repeat(sampleRows) { row ->
        val screenY = (row + 0.5f) * containerSize.height / sampleRows
        val sourceY = ((screenY - layout.destinationOffset.y) / layout.destinationSize.height * bitmap.height)
            .roundToInt().coerceIn(0, bitmap.height - 1)
        repeat(sampleColumns) { column ->
            val screenX = (column + 0.5f) * containerSize.width / sampleColumns
            val sourceX = ((screenX - layout.destinationOffset.x) / layout.destinationSize.width * bitmap.width)
                .roundToInt().coerceIn(0, bitmap.width - 1)
            val pixel = bitmap.getPixel(sourceX, sourceY)
            if (AndroidColor.alpha(pixel) >= 180) {
                total += (0.299 * AndroidColor.red(pixel) + 0.587 * AndroidColor.green(pixel) + 0.114 * AndroidColor.blue(pixel)) / 255.0
                count++
            }
        }
    }
    return if (count == 0) -1f else (total / count).toFloat().coerceIn(0f, 1f)
}
