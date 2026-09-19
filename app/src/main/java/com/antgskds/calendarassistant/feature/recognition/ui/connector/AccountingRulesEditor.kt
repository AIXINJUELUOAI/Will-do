package com.antgskds.calendarassistant.feature.recognition.ui.connector

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.accounting.data.AccountingMessageRulePrefs
import com.antgskds.calendarassistant.feature.accounting.domain.*
import com.antgskds.calendarassistant.shared.ui.edition.EditionButton
import com.antgskds.calendarassistant.shared.ui.edition.EditionSwitch
import com.antgskds.calendarassistant.shared.ui.edition.EditionTextField
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import java.util.UUID

/** 复用开发者正则规则页，不增加新的设置页面。保存后下一条消息立即使用新规则。 */
@Composable
fun AccountingRulesEditor() {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(AccountingMessageRulePrefs.load(context)) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf("修改后点击保存；测试只显示结果，不入库。") }
    var kind by remember { mutableStateOf(AccountingMessageKind.NOTIFICATION) }
    var sender by remember { mutableStateOf("com.eg.android.AlipayGphone") }
    var title by remember { mutableStateOf("交易提醒") }
    var body by remember { mutableStateOf("你有一笔10.73元的支出，点击领0.5元话费券。") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        .padding(bottom = LocalAppPageBottomPadding.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("通知与短信记账规则", style = MaterialTheme.typography.titleMedium)
        Text("按顺序取首条有效匹配。来源正则完整匹配包名或短信发件人；正文匹配“标题＋换行＋正文”。必填组 amount；可选 merchant、time、transactionId。未命中静默跳过，不调用 AI。",
            style = MaterialTheme.typography.bodySmall)
        Row {
            EditionButton(onClick = {
                runCatching { AccountingMessageRulePrefs.save(context, rules) }
                    .onSuccess { result = "已保存，后续消息使用新规则" }
                    .onFailure { result = it.message ?: "保存失败" }
            }) { Text("保存规则") }
            TextButton(onClick = { rules = AccountingMessageRules.defaults(); result = "已载入默认规则，保存后生效" }) { Text("恢复默认") }
            TextButton(onClick = {
                val rule = AccountingMessageRule(UUID.randomUUID().toString(), "新规则", enabled = false)
                rules = rules + rule; selectedId = rule.id
            }) { Text("新增") }
        }
        Text(result, color = MaterialTheme.colorScheme.primary)
        Row {
            AccountingMessageKind.entries.forEach { value ->
                TextButton(onClick = { kind = value }) { Text((if (kind == value) "✓ " else "") + if (value == AccountingMessageKind.SMS) "短信" else "通知") }
            }
        }
        AccountingRuleField("包名／短信发件人", sender) { sender = it }
        AccountingRuleField("通知标题（短信可留空）", title) { title = it }
        AccountingRuleField("测试正文", body, multiline = true) { body = it }
        EditionButton(onClick = {
            val invalid = rules.firstOrNull { it.enabled && AccountingMessageRules.validate(it) != null }
            if (invalid != null) result = "${invalid.name}：${AccountingMessageRules.validate(invalid)}"
            else {
                val draft = AccountingMessageRules.parse(AccountingMessage(kind, sender, body, System.currentTimeMillis(), title), rules)
                result = draft?.let { "${it.direction} ${it.amount} 元 · ${it.merchant}\n${it.occurredAt} · ${it.channel} · ${it.paymentStatus}" }
                    ?: "未匹配有效账单"
            }
        }) { Text("测试当前规则") }
        rules.forEachIndexed { index, rule ->
            key(rule.id) {
                fun update(next: AccountingMessageRule) { rules = rules.toMutableList().also { it[index] = next } }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { selectedId = if (selectedId == rule.id) null else rule.id }, modifier = Modifier.weight(1f)) { Text(rule.name) }
                            EditionSwitch(checked = rule.enabled, onCheckedChange = { update(rule.copy(enabled = it)) })
                        }
                        if (selectedId == rule.id) {
                            AccountingRuleField("名称", rule.name) { update(rule.copy(name = it)) }
                            Row {
                                AccountingMessageKind.entries.forEach { value ->
                                    TextButton(onClick = { update(rule.copy(kind = value)) }) { Text((if (rule.kind == value) "✓ " else "") + if (value == AccountingMessageKind.SMS) "短信" else "通知") }
                                }
                            }
                            AccountingRuleField("来源正则", rule.sourcePattern) { update(rule.copy(sourcePattern = it)) }
                            AccountingRuleField("正文正则（amount 命名组）", rule.pattern, multiline = true) { update(rule.copy(pattern = it)) }
                            AccountingRuleField("收支方向 EXPENSE / INCOME / TRANSFER", rule.direction) { update(rule.copy(direction = it)) }
                            AccountingRuleField("渠道", rule.channel) { update(rule.copy(channel = it)) }
                            AccountingRuleField("分类", rule.category) { update(rule.copy(category = it)) }
                            AccountingRuleField("默认名称（没有 merchant 组时）", rule.merchant) { update(rule.copy(merchant = it)) }
                            AccountingRuleField("time 组日期格式", rule.timeFormat) { update(rule.copy(timeFormat = it)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("退款收入")
                                EditionSwitch(checked = rule.refund, onCheckedChange = { update(rule.copy(refund = it, direction = if (it) "INCOME" else rule.direction)) })
                                TextButton(onClick = { rules = rules.filterIndexed { i, _ -> i != index } }) { Text("删除") }
                                if (index > 0) TextButton(onClick = {
                                    rules = rules.toMutableList().also { it[index] = it[index - 1]; it[index - 1] = rule }
                                }) { Text("上移") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountingRuleField(label: String, value: String, multiline: Boolean = false, change: (String) -> Unit) {
    EditionTextField(value = value, onValueChange = change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline, minLines = if (multiline) 2 else 1)
}
