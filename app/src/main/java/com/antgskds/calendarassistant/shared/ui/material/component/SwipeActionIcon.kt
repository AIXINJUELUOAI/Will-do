package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics

/** 日程与账单共用的圆角背景操作按钮，尺寸仍由页面按 UI 大小传入。 */
@Composable
fun SwipeActionIcon(
    icon: ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    hapticEnabled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.15f))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = contentDescription) {
                haptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint)
    }
}
