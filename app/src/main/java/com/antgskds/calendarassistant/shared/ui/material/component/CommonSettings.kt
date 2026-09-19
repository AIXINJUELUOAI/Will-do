package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// 【关键修复】引用新位置的 NotificationScheduler
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationScheduler

// --- 1. Expandable Group Component ---

@Composable
fun ExpandableSettingsGroup(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    showDivider: Boolean = true,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onExpandedChange(!expanded) }
                .padding(vertical = 18.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "arrow")
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = Color.Gray
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
                content()
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
