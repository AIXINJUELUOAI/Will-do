package com.antgskds.calendarassistant.service.capsule

import android.content.Context
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager

object IconUtils {

    /**
     * 获取胶囊小图标。
     * 优先从 RuleRegistry 缓存获取自定义图标，无缓存时使用硬编码回退。
     */
    fun getSmallIconForCapsule(context: Context, capsule: CapsuleUiState.Active.CapsuleItem): Int {
        // 聚合取件码胶囊始终使用包裹图标
        if (capsule.id == CapsuleStateManager.AGGREGATE_PICKUP_ID) {
            return R.drawable.ic_stat_package
        }

        // 特殊类型直接返回
        when (capsule.type) {
            CapsuleStateManager.TYPE_NETWORK_SPEED -> return R.drawable.ic_stat_net
            CapsuleStateManager.TYPE_OCR_PROGRESS -> return R.drawable.ic_stat_scan
            CapsuleStateManager.TYPE_OCR_RESULT -> return R.drawable.ic_stat_success
        }

        // 优先从 RuleRegistry 获取用户自定义图标
        val payload = RuleMatchingEngine.resolvePayload(capsule.description, capsule.eventType)
        val ruleId = payload?.ruleId ?: capsule.eventType
        val customIcon = com.antgskds.calendarassistant.core.rule.RuleRegistry.getCustomCapsuleIconResId(ruleId)
        if (customIcon != null) return customIcon

        // 其次从 RuleRegistry 获取默认图标
        val defaultIcon = com.antgskds.calendarassistant.core.rule.RuleRegistry.getIconResId(ruleId)
        if (defaultIcon != null) return defaultIcon

        // 硬编码回退
        return when (ruleId) {
            RuleMatchingEngine.RULE_PICKUP -> {
                if (capsule.description?.startsWith("【取餐】") == true) R.drawable.ic_stat_food
                else R.drawable.ic_stat_package
            }
            RuleMatchingEngine.RULE_FOOD -> R.drawable.ic_stat_food
            RuleMatchingEngine.RULE_TRAIN -> R.drawable.ic_stat_train
            RuleMatchingEngine.RULE_TAXI -> R.drawable.ic_stat_car
            RuleMatchingEngine.RULE_FLIGHT -> R.drawable.ic_stat_flight
            RuleMatchingEngine.RULE_TICKET -> R.drawable.ic_stat_ticket
            RuleMatchingEngine.RULE_SENDER -> R.drawable.ic_stat_sender
            EventType.COURSE -> R.drawable.ic_stat_course
            EventType.EVENT, RuleMatchingEngine.RULE_GENERAL -> R.drawable.ic_stat_event
            else -> R.drawable.ic_notification_small
        }
    }

    fun getSmallIconForEvent(context: Context, tag: String, description: String): Int {
        val payload = RuleMatchingEngine.resolvePayload(description, tag)
        val ruleId = payload?.ruleId ?: tag
        return com.antgskds.calendarassistant.core.rule.RuleRegistry.getIconResIdWithFallback(ruleId, context)
    }

    fun getNetworkSpeedIcon(): Int = R.drawable.ic_stat_net

    fun getScanningIcon(): Int = R.drawable.ic_stat_scan

    fun getSuccessIcon(): Int = R.drawable.ic_stat_success
}
