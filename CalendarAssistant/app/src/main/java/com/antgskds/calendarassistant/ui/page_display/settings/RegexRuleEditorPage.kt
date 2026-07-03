package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.rule.RegexScheduleRecognizer
import com.antgskds.calendarassistant.core.rule.RegexScheduleRule
import com.antgskds.calendarassistant.core.rule.RegexScheduleRulePrefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RegexRuleEditorPage(uiSize: Int = 2) {
    val context = LocalContext.current
    val app = context.applicationContext as? App
    if (app == null) {
        Text(text = "应用上下文不可用", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        return
    }
    val settings by app.settingsQueryApi.settings.collectAsState()
    var rules by remember { mutableStateOf(RegexScheduleRulePrefs.loadRules(context)) }
    var testInput by remember { mutableStateOf("明天 9点 项目会") }
    var testMessage by remember { mutableStateOf("输入一句话后点击测试，结果不会写入日程。") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }

    fun save(nextRules: List<RegexScheduleRule>) {
        rules = nextRules
        RegexScheduleRulePrefs.saveRules(context, nextRules)
    }

    fun runTest() {
        runCatching {
            RegexScheduleRecognizer.analyze(
                text = testInput,
                rules = rules,
                defaultDurationMinutes = settings.defaultEventDurationMinutes,
            ).firstOrNull()
        }.onSuccess { result ->
            testMessage = if (result == null) {
                "未匹配到明确日程"
            } else {
                val draft = result.draft
                val time = Instant.ofEpochSecond(draft.startTS).atZone(ZoneId.systemDefault()).format(timeFormatter)
                "命中：${result.rule.name}\n${draft.title}\n$time"
            }
        }.onFailure { error ->
            testMessage = "测试失败：${error.message ?: error::class.java.simpleName}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "正则规则",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "这些规则只用于文本和语音转写后的日程识别。规则使用 Kotlin Regex，默认通过 date/time/title/location 命名分组抽取字段。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("测试文本") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    minLines = 2
                )
                Text(
                    text = testMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        val defaults = RegexScheduleRulePrefs.reset(context)
                        rules = defaults
                        testMessage = "已恢复默认规则"
                    }) {
                        Text("恢复默认")
                    }
                    Button(onClick = { runTest() }) {
                        Text("测试")
                    }
                }
            }
        }

        rules.forEachIndexed { index, rule ->
            RegexRuleCard(
                rule = rule,
                onRuleChange = { updated ->
                    save(rules.toMutableList().also { it[index] = updated })
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp + uiSize.dp))
    }
}

@Composable
private fun RegexRuleCard(
    rule: RegexScheduleRule,
    onRuleChange: (RegexScheduleRule) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text(rule.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onRuleChange(rule.copy(enabled = it)) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            OutlinedTextField(
                value = rule.name,
                onValueChange = { onRuleChange(rule.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                singleLine = true
            )
            OutlinedTextField(
                value = rule.pattern,
                onValueChange = { onRuleChange(rule.copy(pattern = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("正则") },
                minLines = 3
            )
            OutlinedTextField(
                value = rule.titleTemplate,
                onValueChange = { onRuleChange(rule.copy(titleTemplate = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题模板") },
                supportingText = { Text("可用 {title}/{code} 或其他命名分组，如 {location}") },
                singleLine = true
            )
            OutlinedTextField(
                value = rule.descriptionTemplate,
                onValueChange = { onRuleChange(rule.copy(descriptionTemplate = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("描述模板") },
                supportingText = { Text("留空时使用原文；码类可用 {code}|| 生成字段") },
                singleLine = true
            )
            OutlinedTextField(
                value = rule.locationTemplate,
                onValueChange = { onRuleChange(rule.copy(locationTemplate = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("地点模板") },
                supportingText = { Text("留空时使用地点组；可用 {from} -> {to} 等命名分组") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RegexGroupField("日期组", rule.dateGroup, Modifier.weight(1f)) { onRuleChange(rule.copy(dateGroup = it)) }
                RegexGroupField("时间组", rule.timeGroup, Modifier.weight(1f)) { onRuleChange(rule.copy(timeGroup = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RegexGroupField("标题组", rule.titleGroup, Modifier.weight(1f)) { onRuleChange(rule.copy(titleGroup = it)) }
                RegexGroupField("地点组", rule.locationGroup, Modifier.weight(1f)) { onRuleChange(rule.copy(locationGroup = it)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("缺少日期/时间时使用当前时间", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "适合取件码、取餐码等即时码类规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rule.useCurrentTimeWhenMissing,
                    onCheckedChange = { onRuleChange(rule.copy(useCurrentTimeWhenMissing = it)) }
                )
            }
        }
    }
}

@Composable
private fun RegexGroupField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true
    )
}
