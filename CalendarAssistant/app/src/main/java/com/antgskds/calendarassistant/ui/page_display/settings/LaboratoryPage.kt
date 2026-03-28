package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antgskds.calendarassistant.core.ai.RulePatchPrefs
import com.antgskds.calendarassistant.core.rule.RuleActionDefaults
import com.antgskds.calendarassistant.core.rule.RuleActionSeeder
import com.antgskds.calendarassistant.core.rule.RuleIconSource
import com.antgskds.calendarassistant.core.rule.RuleRegistry
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.entity.EventRuleEntity
import com.antgskds.calendarassistant.ui.components.RuleIconPickerDialog
import com.antgskds.calendarassistant.ui.components.RuleIconPreview
import com.antgskds.calendarassistant.ui.components.resolveRuleIconResName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LaboratoryPage(uiSize: Int = 2) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getInstance(context.applicationContext) }
    val ruleDao = remember { database.eventRuleDao() }
    val stateDao = remember { database.eventStateDao() }
    val scrollState = rememberScrollState()

    var ruleEditEnabled by remember { mutableStateOf(RulePatchPrefs.isEnabled(context)) }
    var rules by remember { mutableStateOf<List<EventRuleEntity>>(emptyList()) }
    var showRuleEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<EventRuleEntity?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }
    var editingRuleState by remember { mutableStateOf<EventRuleEntity?>(null) }
    var currentIconResName by remember { mutableStateOf<String?>(null) }

    fun loadRules() {
        scope.launch(Dispatchers.IO) {
            val list = ruleDao.getAll()
            withContext(Dispatchers.Main) {
                rules = list
            }
        }
    }

    LaunchedEffect(Unit) {
        loadRules()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "实验功能",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "规则编辑器",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "管理识别规则（开关开启后参与识别）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ruleEditEnabled,
                        onCheckedChange = { enabled ->
                            ruleEditEnabled = enabled
                            RulePatchPrefs.setEnabled(context, enabled)
                        }
                    )
                }
                AnimatedVisibility(
                    visible = ruleEditEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                editingRule = null
                                editingRuleState = null
                                currentIconResName = null
                                showRuleEditor = true
                            }) {
                                Text("新建")
                            }
                        }

                        if (rules.isEmpty()) {
                            Text(
                                text = "暂无规则",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            rules.forEach { rule ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RuleIconPreview(
                                        ruleId = rule.ruleId,
                                        iconResName = RuleIconSource.parse(rule.iconSourceJson).capsuleIcon.ifBlank { null }
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(
                                            text = rule.name.ifBlank { rule.ruleId },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = rule.ruleId,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Switch(
                                            checked = rule.isEnabled,
                                            onCheckedChange = { enabled ->
                                                scope.launch(Dispatchers.IO) {
                                                    ruleDao.update(rule.copy(isEnabled = enabled))
                                                    val list = ruleDao.getAll()
                                                    withContext(Dispatchers.Main) { rules = list }
                                                }
                                            }
                                        )
                                        TextButton(onClick = {
                                            editingRule = rule
                                            editingRuleState = rule
                                            currentIconResName = rule.let {
                                                RuleIconSource.parse(it.iconSourceJson).capsuleIcon.ifBlank { null }
                                            }
                                            showRuleEditor = true
                                        }) {
                                            Text("编辑")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!ruleEditEnabled) {
                    Text(
                        text = "开启后可编辑规则并参与识别",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showRuleEditor) {
        RuleEditorDialog(
            existingRule = editingRule,
            editingRuleState = editingRuleState,
            currentIconResName = currentIconResName,
            onRuleStateChange = { rule, iconResName ->
                editingRuleState = rule
                currentIconResName = iconResName
            },
            stateDao = stateDao,
            onDismiss = { showRuleEditor = false },
            onIconPick = { showIconPicker = true },
            onSave = { rule, pendingTemplate, terminalTemplate ->
                scope.launch(Dispatchers.IO) {
                    ruleDao.insert(rule)
                    RuleActionSeeder.ensureRuleDefaults(context, rule.ruleId)
                    RuleActionSeeder.updateDisplayTemplates(
                        context = context,
                        ruleId = rule.ruleId,
                        pendingTemplate = pendingTemplate,
                        terminalTemplate = terminalTemplate
                    )
                    RuleRegistry.refresh(context)
                    val list = ruleDao.getAll()
                    withContext(Dispatchers.Main) {
                        rules = list
                        showRuleEditor = false
                    }
                }
            },
            onDelete = { ruleId ->
                scope.launch(Dispatchers.IO) {
                    ruleDao.delete(ruleId)
                    stateDao.deleteByRuleId(ruleId)
                    RuleRegistry.refresh(context)
                    val list = ruleDao.getAll()
                    withContext(Dispatchers.Main) {
                        rules = list
                        showRuleEditor = false
                    }
                }
            }
        )
    }

    if (showIconPicker) {
        RuleIconPickerDialog(
            currentResName = currentIconResName,
            onDismiss = { showIconPicker = false },
            onSelect = { resName ->
                scope.launch(Dispatchers.IO) {
                    val baseRule = editingRuleState ?: return@launch
                    val newSource = RuleIconSource(capsuleIcon = resName ?: "")
                    val updated = baseRule.copy(iconSourceJson = RuleIconSource.serialize(newSource))
                    ruleDao.insert(updated)
                    RuleRegistry.refresh(context)
                    val list = ruleDao.getAll()
                    withContext(Dispatchers.Main) {
                        rules = list
                        editingRuleState = updated
                        currentIconResName = resName?.ifBlank { null }
                        showIconPicker = false
                    }
                }
            }
        )
    }
}

@Composable
private fun RuleEditorDialog(
    existingRule: EventRuleEntity?,
    editingRuleState: EventRuleEntity?,
    currentIconResName: String?,
    onRuleStateChange: (EventRuleEntity?, String?) -> Unit,
    stateDao: com.antgskds.calendarassistant.data.db.dao.EventStateDao,
    onDismiss: () -> Unit,
    onSave: (EventRuleEntity, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onIconPick: () -> Unit
) {
    var ruleId by remember { mutableStateOf(existingRule?.ruleId.orEmpty()) }
    var ruleName by remember { mutableStateOf(existingRule?.name.orEmpty()) }
    var aiPrompt by remember { mutableStateOf(existingRule?.aiPrompt.orEmpty()) }
    var aiTitlePrompt by remember { mutableStateOf(existingRule?.aiTitlePrompt.orEmpty()) }
    var errorText by remember { mutableStateOf("") }
    var pendingTemplate by remember { mutableStateOf("") }
    var terminalTemplate by remember { mutableStateOf("") }
    var templateDirty by remember { mutableStateOf(false) }

    val normalizedRuleId = ruleId.trim().lowercase()
    val isEditing = existingRule != null

    LaunchedEffect(normalizedRuleId, isEditing) {
        if (normalizedRuleId.isBlank()) return@LaunchedEffect
        if (isEditing) {
            val defaults = RuleActionDefaults.defaultsFor(normalizedRuleId)
            val pendingStateId = RuleActionDefaults.stateId(normalizedRuleId, defaults.pending.suffix)
            val terminalStateId = RuleActionDefaults.stateId(normalizedRuleId, defaults.terminal.suffix)
            val states = withContext(Dispatchers.IO) { stateDao.getByRuleId(normalizedRuleId) }
            val stateMap = states.associateBy { it.stateId }
            pendingTemplate = stateMap[pendingStateId]?.displayTemplate ?: defaults.pending.displayTemplate
            terminalTemplate = stateMap[terminalStateId]?.displayTemplate ?: defaults.terminal.displayTemplate
            templateDirty = false
        } else if (!templateDirty) {
            val defaults = RuleActionDefaults.defaultsFor(normalizedRuleId)
            pendingTemplate = defaults.pending.displayTemplate
            terminalTemplate = defaults.terminal.displayTemplate
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 670.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        if (isEditing) "编辑规则" else "新建规则",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 内容
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = ruleId,
                        onValueChange = { ruleId = it },
                        label = { Text("ruleId") },
                        enabled = !isEditing,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 通知图标选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIconPick() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("通知图标")
                            val resolvedName = currentIconResName ?: resolveRuleIconResName(normalizedRuleId)
                            Text(
                                text = resolvedName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RuleIconPreview(
                            ruleId = normalizedRuleId,
                            iconResName = currentIconResName,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    OutlinedTextField(
                        value = aiTitlePrompt,
                        onValueChange = { aiTitlePrompt = it },
                        label = { Text("标题模板") },
                        placeholder = { Text("示例：航班号 路线") },
                        minLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        label = { Text("规则提示词") },
                        placeholder = { Text("示例：取件码|品牌|位置") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pendingTemplate,
                        onValueChange = {
                            pendingTemplate = it
                            templateDirty = true
                        },
                        label = { Text("待办模板") },
                        placeholder = { Text("示例：取件码 品牌") },
                        minLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = terminalTemplate,
                        onValueChange = {
                            terminalTemplate = it
                            templateDirty = true
                        },
                        label = { Text("完成模板") },
                        placeholder = { Text("示例：标题 已完成") },
                        minLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorText.isNotBlank()) {
                        Text(errorText, color = MaterialTheme.colorScheme.error)
                    }
                }

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (existingRule != null) {
                        TextButton(onClick = { onDelete(existingRule.ruleId) }) {
                            Text("删除")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (normalizedRuleId.isBlank()) {
                            errorText = "ruleId 不能为空"
                            return@Button
                        }
                        if (!normalizedRuleId.matches(Regex("[a-z0-9_]+"))) {
                            errorText = "ruleId 只能包含小写字母/数字/下划线"
                            return@Button
                        }
                        val name = ruleName.trim().ifBlank { normalizedRuleId }
                        val baseRule = editingRuleState
                        val defaultInitialStateId = RuleActionDefaults.stateId(
                            normalizedRuleId,
                            RuleActionDefaults.STATE_PENDING
                        )
                        val resolvedInitialStateId = baseRule?.initialStateId?.ifBlank { defaultInitialStateId }
                            ?: defaultInitialStateId
                        val currentIconJson = if (currentIconResName.isNullOrBlank()) "{}"
                            else RuleIconSource.serialize(RuleIconSource(capsuleIcon = currentIconResName))
                        val rule = EventRuleEntity(
                            ruleId = normalizedRuleId,
                            name = name,
                            isEnabled = baseRule?.isEnabled ?: true,
                            appliesToSchedule = baseRule?.appliesToSchedule ?: true,
                            aiTag = baseRule?.aiTag?.ifBlank { normalizedRuleId } ?: normalizedRuleId,
                            aiPrompt = aiPrompt.trim(),
                            aiTitlePrompt = aiTitlePrompt.trim(),
                            extractionConfigJson = baseRule?.extractionConfigJson ?: "{}",
                            iconSourceJson = currentIconJson,
                            initialStateId = resolvedInitialStateId
                        )
                        onSave(rule, pendingTemplate, terminalTemplate)
                    }) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
