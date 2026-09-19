package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antgskds.calendarassistant.shared.ui.material.dialog.DisableDialogWindowDimEffect
import com.antgskds.calendarassistant.shared.ui.material.dialog.DialogEdgeToEdgeEffect
import com.antgskds.calendarassistant.shared.ui.motion.PredictiveBottomDialogHost
import kotlinx.coroutines.delay

const val PredictiveFloatingActionCardExitMillis = 240L

@Composable
fun PredictiveFloatingActionCard(
    visible: Boolean,
    title: String,
    content: String = "",
    confirmText: String,
    dismissText: String = "取消",
    dismissIsDestructive: Boolean = false,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    allowDismissWhileLoading: Boolean = false,
    dismissOnClickOutside: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDismissRequest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actionsBelowContent: Boolean = false,
    actionContent: (@Composable RowScope.() -> Unit)? = null
) {
    var keepDialog by remember { mutableStateOf(visible) }
    var dialogReady by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            keepDialog = true
        } else if (keepDialog) {
            delay(PredictiveFloatingActionCardExitMillis)
            keepDialog = false
            dialogReady = false
        }
    }
    if (!keepDialog) return

    val glassSettings = LocalAppGlassSettings.current
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogEdgeToEdgeEffect(isDarkTheme = glassSettings.darkTheme)
        DisableDialogWindowDimEffect()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    // A platform Dialog may become visible after Compose has already advanced
                    // the transition. Start only after its window content has been laid out.
                    if (visible && size.width > 0 && size.height > 0) dialogReady = true
                }
        ) {
            PredictiveBottomDialogHost(
                visible = visible && dialogReady,
                onDismiss = onDismissRequest ?: onDismiss,
                dismissEnabled = !isLoading || allowDismissWhileLoading,
                dismissOnClickOutside = dismissOnClickOutside,
                predictiveBackEnabled = predictiveBackEnabled,
                scrimColor = if (glassSettings.active) Color.Transparent else Color.Black.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxSize()
            ) {
                FloatingActionCardSurface(
                    title = title,
                    content = content,
                    confirmText = confirmText,
                    dismissText = dismissText,
                    dismissIsDestructive = dismissIsDestructive,
                    isDestructive = isDestructive,
                    isLoading = isLoading,
                    allowDismissWhileLoading = allowDismissWhileLoading,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                    modifier = modifier,
                    actionsBelowContent = actionsBelowContent,
                    actionContent = actionContent
                )
            }
        }
    }
}
