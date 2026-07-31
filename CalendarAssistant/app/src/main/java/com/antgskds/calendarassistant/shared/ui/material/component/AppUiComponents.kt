package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.ui.edition.EditionAlertDialogSurface
import com.antgskds.calendarassistant.shared.ui.edition.EditionCardSurface
import com.antgskds.calendarassistant.shared.ui.edition.EditionModalBottomSheet
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.antgskds.calendarassistant.shared.ui.material.dialog.DisableDialogWindowDimEffect

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassSettings = LocalAppGlassSettings.current
    val resolvedModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    if (glassSettings.active) {
        AppGlassSurface(
            modifier = resolvedModifier,
            shape = shape,
            fallbackColor = containerColor
        ) {
            androidx.compose.material3.Surface(color = Color.Transparent, contentColor = contentColor) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(contentPadding),
                    content = content
                )
            }
        }
        return
    }

    EditionCardSurface(
        modifier = resolvedModifier,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        materialElevation = null,
        useEditionDefaultColors = containerColor == MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun AppOverlayCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassSettings = LocalAppGlassSettings.current
    val resolvedModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    if (glassSettings.active && glassSettings.overlayBackdrop != null) {
        AppOverlayGlassSurface(
            modifier = resolvedModifier,
            shape = shape,
            fallbackColor = containerColor
        ) {
            androidx.compose.material3.Surface(color = Color.Transparent, contentColor = contentColor) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(contentPadding),
                    content = content
                )
            }
        }
        return
    }

    EditionCardSurface(
        modifier = resolvedModifier,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        materialElevation = null,
        useEditionDefaultColors = containerColor == MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun AppOverlayCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassSettings = LocalAppGlassSettings.current
    val resolvedModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    if (glassSettings.active && glassSettings.overlayBackdrop != null) {
        AppOverlayGlassSurface(
            modifier = resolvedModifier,
            shape = shape,
            fallbackColor = colors.containerColor
        ) {
            androidx.compose.material3.Surface(
                color = Color.Transparent,
                contentColor = colors.contentColor
            ) {
                androidx.compose.foundation.layout.Column(content = content)
            }
        }
        return
    }

    EditionCardSurface(
        modifier = resolvedModifier,
        shape = shape,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        shadowElevation = 0.dp,
        materialElevation = elevation,
        useEditionDefaultColors = colors.containerColor == MaterialTheme.colorScheme.surfaceContainerLow,
        content = content
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassSettings = LocalAppGlassSettings.current
    val resolvedModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    if (glassSettings.active) {
        AppGlassSurface(
            modifier = resolvedModifier,
            shape = shape,
            fallbackColor = colors.containerColor
        ) {
            androidx.compose.material3.Surface(
                color = Color.Transparent,
                contentColor = colors.contentColor
            ) {
                androidx.compose.foundation.layout.Column(content = content)
            }
        }
        return
    }

    EditionCardSurface(
        modifier = resolvedModifier,
        shape = shape,
        containerColor = colors.containerColor,
        contentColor = colors.contentColor,
        shadowElevation = 0.dp,
        materialElevation = elevation,
        useEditionDefaultColors = colors.containerColor == MaterialTheme.colorScheme.surfaceContainerLow,
        content = content
    )
}

@Composable
fun AppSettingsCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = dismissText?.let { label ->
            {
                TextButton(onClick = onDismiss ?: onDismissRequest) {
                    Text(label)
                }
            }
        }
    )
}

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation: Dp = 0.dp,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val glassSettings = LocalAppGlassSettings.current
    val glassActive = glassSettings.active && glassSettings.overlayBackdrop != null
    val childBackdrop = rememberLayerBackdrop()
    val dialogModifier = if (glassActive) {
        modifier
            .layerBackdrop(childBackdrop)
            .appMiuiOverlayBlurMaterial(shape)
    } else {
        modifier
    }

    CompositionLocalProvider(
        LocalAppGlassSettings provides if (glassActive) {
            glassSettings.copy(overlayBackdrop = childBackdrop)
        } else {
            glassSettings
        }
    ) {
        EditionAlertDialogSurface(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                if (glassActive) DisableDialogWindowDimEffect()
                confirmButton()
            },
            modifier = dialogModifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = if (glassActive) Color.Transparent else containerColor,
            tonalElevation = if (glassActive) 0.dp else tonalElevation,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor
        )
    }
}

@Composable
fun AppPickerDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Box(modifier = Modifier.fillMaxWidth()) { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassSettings = LocalAppGlassSettings.current
    val glassActive = glassSettings.active && glassSettings.overlayBackdrop != null
    if (!glassActive) {
        EditionModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            content = content,
        )
        return
    }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val sheetContent: @Composable ColumnScope.() -> Unit = if (glassActive) {
        {
            DisableDialogWindowDimEffect()
            AppOverlayGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                androidx.compose.material3.Surface(
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BottomSheetDefaults.DragHandle()
                        }
                        Column(modifier = modifier) {
                            content()
                        }
                    }
                }
            }
        }
    } else {
        content
    }

    val sheetModifier = if (glassActive) Modifier else modifier
    val containerColor = if (glassActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow
    val scrimColor = if (glassActive) Color.Transparent else BottomSheetDefaults.ScrimColor
    val dragHandle: (@Composable () -> Unit)? = if (glassActive) {
        null
    } else {
        { BottomSheetDefaults.DragHandle() }
    }

    if (sheetState != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = sheetModifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            tonalElevation = if (glassActive) 0.dp else 1.dp,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = if (glassActive) {
                { WindowInsets(0, 0, 0, 0) }
            } else {
                { BottomSheetDefaults.windowInsets }
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = sheetContent
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = sheetModifier,
            shape = shape,
            containerColor = containerColor,
            tonalElevation = if (glassActive) 0.dp else 1.dp,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = if (glassActive) {
                { WindowInsets(0, 0, 0, 0) }
            } else {
                { BottomSheetDefaults.windowInsets }
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = sheetContent
        )
    }
}
