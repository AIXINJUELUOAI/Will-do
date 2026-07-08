package com.antgskds.calendarassistant.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics

@Composable
fun FloatingActionCard(
    visible: Boolean,
    title: String,
    content: String = "",
    confirmText: String,
    dismissText: String = "取消",
    dismissIsDestructive: Boolean = false,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    allowDismissWhileLoading: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionsBelowContent: Boolean = false,
    actionContent: (@Composable RowScope.() -> Unit)? = null
) {
    if (!visible) return

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

@Composable
fun FloatingActionCardSurface(
    title: String,
    content: String = "",
    confirmText: String,
    dismissText: String = "取消",
    dismissIsDestructive: Boolean = false,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    allowDismissWhileLoading: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionsBelowContent: Boolean = false,
    actionContent: (@Composable RowScope.() -> Unit)? = null
) {
    val haptics = rememberAppHaptics()
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = containerColor,
        shadowElevation = 8.dp
    ) {
        if (actionContent != null && actionsBelowContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                ActionCardText(
                    title = title,
                    content = content,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actionContent
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionCardText(
                    title = title,
                    content = content,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                if (actionContent != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actionContent
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                haptics.click()
                                onDismiss()
                            },
                            enabled = !isLoading || allowDismissWhileLoading,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = dismissText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (dismissIsDestructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Button(
                            onClick = {
                                if (isDestructive) haptics.warning() else haptics.confirm()
                                onConfirm()
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            AnimatedContent(targetState = isLoading, label = "loading_anim") { loading ->
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = confirmText,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCardText(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (content.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
