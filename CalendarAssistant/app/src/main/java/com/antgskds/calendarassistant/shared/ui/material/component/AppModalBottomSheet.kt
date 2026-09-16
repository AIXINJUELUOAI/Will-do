package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.antgskds.calendarassistant.shared.ui.material.dialog.DisableDialogWindowDimEffect
import kotlinx.coroutines.launch

enum class AppSheetActionRole { Primary, Secondary, Destructive }

/** 顺序就是视觉顺序：双按钮从左到右，三按钮先上方，再下方从左到右。 */
data class AppSheetAction(
    val text: String,
    val onClick: () -> Unit,
    val role: AppSheetActionRole = AppSheetActionRole.Primary,
    val enabled: Boolean = true,
)

/**
 * 固定标题与操作区，中间正文滚动，整体使用连续材质。
 * 默认管理正文滚动；业务使用 LazyColumn 等滚动容器时传 scrollState = null。
 * 业务不再添加标题、底部操作或系统栏留白。actions 为空时不创建操作区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: List<AppSheetAction> = emptyList(),
    sheetState: SheetState? = null,
    scrollState: ScrollState? = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    require(actions.size <= 3) { "AppModalBottomSheet supports at most three actions" }
    val glassSettings = LocalAppGlassSettings.current
    val glassActive = glassSettings.overlayActive
    val resolvedState = sheetState ?: rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val imeInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val maxHeight = (windowHeight - imeInset - topInset - 24.dp).coerceAtLeast(0.dp)
    val lightSystemBars = if (glassActive) !glassSettings.darkTheme
        else MaterialTheme.colorScheme.surfaceContainerLow.luminance() > 0.5f

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = resolvedState,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = if (glassActive) Color.Transparent else BottomSheetDefaults.ScrimColor,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
        contentColor = MaterialTheme.colorScheme.onSurface,
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = lightSystemBars,
            isAppearanceLightNavigationBars = lightSystemBars,
        ),
    ) {
        if (glassActive) DisableDialogWindowDimEffect()
        SheetNavigationBarEffect()
        // 必须是原生内容 Column 的直接子节点，背景单独对齐外壳的坐标。
        SheetMaterialSurface(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
            shape = shape,
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                CompositionLocalProvider(LocalAppPageBottomPadding provides 0.dp) {
                    Column(
                        Modifier.fillMaxWidth().windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        )
                    ) {
                        Box(
                            Modifier.fillMaxWidth().clickable(onClickLabel = "收起面板") {
                                scope.launch {
                                    resolvedState.hide()
                                    if (!resolvedState.isVisible) onDismissRequest()
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { BottomSheetDefaults.DragHandle() }
                        Column(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(title, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
                            if (!subtitle.isNullOrBlank()) {
                                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                                .then(if (scrollState != null) Modifier.verticalScroll(scrollState) else Modifier)
                                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                            content = content,
                        )
                        AppSheetActions(actions)
                    }
                }
            }
        }
    }
}

/**
 * Material3 1.4 的外壳 Surface → 内容 Column → 本组件。
 * 原生 Column 为保持内容比例会额外缩放 Y，背景不能跟着留下底部缺口。
 * 仅将背景映射回 Surface 的局部范围，正文继续完全使用原生变换。
 * 升级 Material3 时需要复核这两层父布局关系；这里不读取私有手势进度或复制动画常量。
 */
@Composable
private fun SheetMaterialSurface(
    modifier: Modifier,
    shape: Shape,
    fallbackColor: Color,
    content: @Composable () -> Unit,
) {
    val settings = LocalAppGlassSettings.current
    val backdrop = rememberAppWindowBackdrop(parent = settings.overlayBackdrop)
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    // 此节点本身不能裁切：背景需要延伸到缩放后的正文范围以外。
    // 最终裁切由原生 Surface 负责，仍保留 Sheet 的顶部圆角。
    Box(
        modifier.appWindowBackdrop(backdrop).onGloballyPositioned { coordinates ->
            containerBounds = sheetContainerBounds(coordinates)
        }
    ) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer {
                    val bounds = containerBounds
                    if (bounds != null && size.width > 0f && size.height > 0f) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = bounds.left
                        translationY = bounds.top
                        scaleX = bounds.width / size.width
                        scaleY = bounds.height / size.height
                    }
                }
                .clip(shape)
                .background(fallbackColor)
                .appMiuiOverlayBlurMaterial(shape)
        )
        // 背景采样父场景，正文中的下一层弹窗采样本 Sheet；避免采到自己。
        AppGlassSettingsProvider(if (settings.overlayActive) settings.copy(overlayBackdrop = backdrop) else settings) {
            content()
        }
    }
}

private fun sheetContainerBounds(content: LayoutCoordinates): Rect? {
    val nativeColumn = content.parentLayoutCoordinates ?: return null
    val nativeSurface = nativeColumn.parentLayoutCoordinates ?: return null
    if (!content.isAttached || !nativeSurface.isAttached ||
        content.size.width == 0 || content.size.height == 0) return null

    // localBoundingBoxOf 默认会裁切到正文尺寸；使用两个点保留超出正文的背景范围。
    val topLeft = content.localPositionOf(nativeSurface, Offset.Zero)
    val bottomRight = content.localPositionOf(
        nativeSurface,
        Offset(nativeSurface.size.width.toFloat(), nativeSurface.size.height.toFloat()),
    )
    if (!topLeft.x.isFinite() || !topLeft.y.isFinite() ||
        !bottomRight.x.isFinite() || !bottomRight.y.isFinite() ||
        bottomRight.x <= topLeft.x || bottomRight.y <= topLeft.y) return null
    return Rect(topLeft, bottomRight)
}

/** 无操作不占位；单按钮撑满，双按钮等宽，三按钮上方一个、下方两个。 */
@Composable
private fun AppSheetActions(actions: List<AppSheetAction>) {
    if (actions.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (actions.size == 1 || actions.size == 3) {
            SheetActionButton(actions.first(), Modifier.fillMaxWidth())
        }
        if (actions.size >= 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.takeLast(2).forEach { SheetActionButton(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SheetActionButton(action: AppSheetAction, modifier: Modifier) {
    // 常规高度 52 dp；大字体可自然增高，避免多字操作被固定高度裁掉。
    val buttonModifier = modifier.heightIn(min = 52.dp)
    val shape = RoundedCornerShape(16.dp)
    if (action.role == AppSheetActionRole.Secondary) {
        OutlinedButton(onClick = action.onClick, enabled = action.enabled, modifier = buttonModifier, shape = shape) {
            Text(action.text, textAlign = TextAlign.Center)
        }
    } else {
        Button(
            onClick = action.onClick,
            enabled = action.enabled,
            modifier = buttonModifier,
            shape = shape,
            colors = if (action.role == AppSheetActionRole.Destructive) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) else ButtonDefaults.buttonColors(),
        ) { Text(action.text, textAlign = TextAlign.Center) }
    }
}

/** 去掉独立 Sheet 窗口的导航栏对比度遮罩，安全区仍由同一材质覆盖。 */
@Suppress("DEPRECATION")
@Composable
private fun SheetNavigationBarEffect() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        val color = window.navigationBarColor
        val divider = window.navigationBarDividerColor
        val contrast = window.isNavigationBarContrastEnforced
        onDispose {
            window.navigationBarColor = color
            window.navigationBarDividerColor = divider
            window.isNavigationBarContrastEnforced = contrast
        }
    }
    SideEffect {
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
    }
}
