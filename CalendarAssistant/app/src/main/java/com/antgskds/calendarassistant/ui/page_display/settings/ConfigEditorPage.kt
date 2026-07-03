package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.management.catalog.ConfigControl
import com.antgskds.calendarassistant.shared.management.catalog.ConfigDomain
import com.antgskds.calendarassistant.shared.management.catalog.ConfigItem

/**
 * 配置编辑页 —— 完全由 [ConfigCatalog] 驱动。
 *
 * 落地页：把 catalog 里出现的域各列一行（SettingsNavRow），点一类进域子页。
 * 域子页：遍历该域的配置项，按 control 类型渲染控件；改值写回 settingsOperationApi（唯一事实源 MySettings）。
 * 加一条配置 = 往 ConfigCatalog 登记一条，这里**自动出现**，无需改本页。
 */
@Composable
fun ConfigEditorPage(uiSize: Int = 2) {
    val context = LocalContext.current
    val app = context.applicationContext as? App
    if (app == null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "应用上下文不可用", color = MaterialTheme.colorScheme.error)
        }
        return
    }
    val settings by app.settingsQueryApi.settings.collectAsState()
    var selectedDomain by remember { mutableStateOf<ConfigDomain?>(null) }

    val domain = selectedDomain
    if (domain != null) {
        BackHandler { selectedDomain = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (domain == null) {
            Text(
                text = "配置编辑",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "按分类管理可调配置；点一类进入该类的配置项。值始终保存在设置里，这里只是编辑入口。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ConfigCatalog.visibleDomains()
                        .filter { it != ConfigDomain.VOICE }
                        .forEachIndexed { index, d ->
                            if (index > 0) ConfigRowDivider()
                            ActionSettingItem(
                            title = d.label,
                            subtitle = configDomainSubtitle(d),
                            value = "",
                            icon = Icons.Default.ChevronRight,
                            enabled = true,
                            onClick = { selectedDomain = d },
                            cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            ),
                            cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                        }
                }
            }
        } else {
            Text(
                text = domain.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            ConfigCatalog.itemsInDomain(domain).forEach { item ->
                ConfigItemControl(
                    item = item,
                    currentSettings = settings,
                    onPick = { newSettings -> app.settingsOperationApi.updateSettings(newSettings) }
                )
            }
        }
    }
}

private fun configDomainSubtitle(domain: ConfigDomain): String = when (domain) {
    ConfigDomain.APPEARANCE -> "背景与主题外观配置"
    ConfigDomain.RECOGNITION -> "AI/正则识别模式与识别策略"
    ConfigDomain.WEATHER -> "天气风险阈值与显示配置"
    ConfigDomain.NOTIFICATION -> "通知展示、提醒时长与发布策略"
    ConfigDomain.VOICE -> "语音输入与悬浮窗长按配置"
}

@Composable
private fun ConfigRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun ConfigItemControl(
    item: ConfigItem,
    currentSettings: MySettings,
    onPick: (MySettings) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (val control = item.control) {
                is ConfigControl.IntOptions -> {
                    val current = item.get(currentSettings)
                    val idx = control.options.indexOfFirst { it.value == current }.coerceAtLeast(0)
                    Text(
                        text = control.options.getOrNull(idx)?.label ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = idx.toFloat(),
                        onValueChange = { v ->
                            val newIdx = v.roundToInt().coerceIn(0, control.options.size - 1)
                            if (newIdx != idx) onPick(item.set(currentSettings, control.options[newIdx].value))
                        },
                        valueRange = 0f..(control.options.size - 1).coerceAtLeast(1).toFloat(),
                        steps = (control.options.size - 2).coerceAtLeast(0)
                    )
                }
                is ConfigControl.IntInput -> {
                    val current = item.get(currentSettings)
                    Text(
                        text = "$current${control.unitLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = current.toFloat(),
                        onValueChange = { v ->
                            val stepped = control.min + ((v - control.min) / control.step).roundToInt() * control.step
                            if (stepped.coerceIn(control.min, control.max) != current) onPick(item.set(currentSettings, stepped.coerceIn(control.min, control.max)))
                        },
                        valueRange = control.min.toFloat()..control.max.toFloat(),
                        steps = 0
                    )
                }
                is ConfigControl.Toggle -> {
                    val current = item.get(currentSettings)
                    Switch(
                        checked = current != 0,
                        onCheckedChange = { checked -> onPick(item.set(currentSettings, if (checked) 1 else 0)) }
                    )
                }
            }
        }
    }
}
