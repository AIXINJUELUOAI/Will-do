package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics

/**
 * 统一的设置/导航行：图标 + 标题 + 右侧箭头，整行可点。
 *
 * 从 SettingsSidebar 的私有 SidebarActionItem 提取为共享公开组件，供设置页、开发者页、
 * 配置编辑页等复用，保证全 App 的"跳转行"样式一致（声明一次、到处用同一个）。
 *
 * 行为与原 SidebarActionItem 完全一致；唯一差异：原 subtitle 参数从未被渲染（死参），此处去除。
 */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    showBadge: Boolean = false
) {
    val haptics = rememberAppHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null) {
                haptics.click()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (showBadge) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                )
            }
        }
        if (showChevron) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
