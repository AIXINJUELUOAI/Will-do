package com.antgskds.calendarassistant.ui.connector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.feature.schedule.domain.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.ui.contract.PickupQrFloatingCardUiAction
import com.antgskds.calendarassistant.ui.contract.PickupQrFloatingCardUiState
import com.antgskds.calendarassistant.platform.floating.ui.render.PickupQrFloatingCardContent

@Composable
fun PickupQrFloatingCardRoute(
    event: Event,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember(event.title, event.description, event.tag, event.codeQrPayload) {
        buildPickupQrFloatingCardUiState(event)
    }
    PickupQrFloatingCardContent(
        state = state,
        onAction = { action ->
            when (action) {
                PickupQrFloatingCardUiAction.Dismiss -> onClose()
                PickupQrFloatingCardUiAction.Complete -> onComplete()
            }
        },
        modifier = modifier
    )
}

internal fun buildPickupQrFloatingCardUiState(event: Event): PickupQrFloatingCardUiState {
    val payload = RuleMatchingEngine.resolvePayload(event)
    val ruleId = payload?.ruleId ?: when (event.tag) {
        EventTags.FOOD -> RuleMatchingEngine.RULE_FOOD
        EventTags.TICKET -> RuleMatchingEngine.RULE_TICKET
        EventTags.SENDER -> RuleMatchingEngine.RULE_SENDER
        else -> RuleMatchingEngine.RULE_PICKUP
    }
    val fields = RuleMatchingEngine.splitFields(payload?.payload.orEmpty(), 3)
    val code = fields.getOrNull(0).orEmpty()
    val location = fields.getOrNull(2).orEmpty().ifBlank { fields.getOrNull(1).orEmpty() }
    val typeLabel = when (ruleId) {
        RuleMatchingEngine.RULE_FOOD -> "取餐二维码"
        RuleMatchingEngine.RULE_TICKET -> "取票二维码"
        RuleMatchingEngine.RULE_SENDER -> "寄件二维码"
        else -> "取件二维码"
    }
    val completeLabel = when (ruleId) {
        RuleMatchingEngine.RULE_FOOD -> "已取餐"
        RuleMatchingEngine.RULE_TICKET -> "已取票"
        RuleMatchingEngine.RULE_SENDER -> "已寄件"
        else -> "已取"
    }

    return PickupQrFloatingCardUiState(
        qrPayload = event.codeQrPayload.trim(),
        typeLabel = typeLabel,
        title = event.title.ifBlank { typeLabel },
        code = code,
        location = location,
        completeLabel = completeLabel
    )
}
