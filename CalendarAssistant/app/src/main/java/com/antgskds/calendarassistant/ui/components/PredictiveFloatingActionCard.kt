package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.ui.motion.PredictiveBottomDialogHost

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
    modifier: Modifier = Modifier,
    actionsBelowContent: Boolean = false,
    actionContent: (@Composable RowScope.() -> Unit)? = null
) {
    PredictiveBottomDialogHost(
        visible = visible,
        onDismiss = onDismiss,
        dismissEnabled = !isLoading || allowDismissWhileLoading,
        dismissOnClickOutside = dismissOnClickOutside,
        predictiveBackEnabled = predictiveBackEnabled,
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
