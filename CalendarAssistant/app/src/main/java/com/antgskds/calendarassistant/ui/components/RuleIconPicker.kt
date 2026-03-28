package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.rule.PresetIcons
import com.antgskds.calendarassistant.core.rule.RuleIconSource
import com.antgskds.calendarassistant.core.rule.RuleRegistry

/**
 * 通知图标选择器弹窗。
 * @param currentResName 当前已选的 drawable 资源名，null 表示使用默认
 * @param onDismiss 关闭弹窗（未选择）
 * @param onSelect 选择图标回调，传 null 表示"使用默认"
 */
@Composable
fun RuleIconPickerDialog(
    currentResName: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    items(PresetIcons.CAPSULE_ICON_PRESETS) { preset ->
                        val isSelected = preset.resName == currentResName
                        val iconPainter = remember(preset.resName) {
                            val resId = context.resources.getIdentifier(
                                preset.resName, "drawable", context.packageName
                            )
                            if (resId != 0) {
                                val bitmap = context.getDrawable(resId)?.toBitmap()
                                bitmap?.asImageBitmap()?.let { BitmapPainter(it) }
                            } else null
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(preset.resName) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (iconPainter != null) {
                                    Icon(
                                        painter = iconPainter,
                                        contentDescription = preset.label,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        dismissButton = {
            TextButton(onClick = { onSelect(null) }) {
                Icon(
                    Icons.Rounded.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text("使用默认", modifier = Modifier.padding(start = 4.dp))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

/**
 * 解析规则的胶囊图标资源名。
 * 优先返回用户自定义选择，其次返回默认 drawable 资源名。
 */
fun resolveRuleIconResName(ruleId: String): String {
    return RuleRegistry.getCustomCapsuleIconName(ruleId)
        ?: RuleRegistry.getDefaultCapsuleIconName(ruleId)
}

/**
 * 在规则列表中使用的图标预览。
 * @param iconResName 直接传入图标资源名，优先于 ruleId 查询
 */
@Composable
fun RuleIconPreview(
    ruleId: String,
    iconResName: String? = null,
    modifier: Modifier = Modifier.size(32.dp)
) {
    val context = LocalContext.current
    val resName = iconResName ?: resolveRuleIconResName(ruleId)
    val resId = remember(resName) {
        context.resources.getIdentifier(resName, "drawable", context.packageName)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            Icon(
                painter = painterResource(id = resId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                "?",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
