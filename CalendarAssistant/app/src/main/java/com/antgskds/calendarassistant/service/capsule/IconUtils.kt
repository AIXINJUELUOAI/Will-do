package com.antgskds.calendarassistant.service.capsule

import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.state.CapsuleUiState

object IconUtils {

    private fun isFoodPickup(description: String?): Boolean {
        return description?.startsWith("【取餐】") == true
    }

    fun getSmallIconForCapsule(capsule: CapsuleUiState.Active.CapsuleItem): Int {
        return when (capsule.type) {
            CapsuleService.TYPE_NETWORK_SPEED -> R.drawable.ic_stat_net

            CapsuleService.TYPE_OCR_PROGRESS -> R.drawable.ic_stat_scan
            CapsuleService.TYPE_OCR_RESULT -> R.drawable.ic_stat_success

            CapsuleService.TYPE_PICKUP, CapsuleService.TYPE_PICKUP_EXPIRED -> {
                if (isFoodPickup(capsule.description)) R.drawable.ic_stat_food else R.drawable.ic_stat_package
            }

            CapsuleService.TYPE_SCHEDULE -> {
                when (capsule.eventType) {
                    EventTags.TRAIN -> R.drawable.ic_stat_train
                    EventTags.TAXI -> R.drawable.ic_stat_car
                    EventTags.PICKUP -> {
                        if (isFoodPickup(capsule.description)) R.drawable.ic_stat_food else R.drawable.ic_stat_package
                    }
                    EventTags.GENERAL -> R.drawable.ic_stat_event
                    EventType.COURSE -> R.drawable.ic_stat_course
                    else -> R.drawable.ic_notification_small
                }
            }
            else -> R.drawable.ic_notification_small
        }
    }

    fun getSmallIconForEvent(tag: String, description: String): Int {
        return when (tag) {
            EventTags.TRAIN -> R.drawable.ic_stat_train
            EventTags.TAXI -> R.drawable.ic_stat_car
            EventTags.PICKUP -> {
                if (isFoodPickup(description)) R.drawable.ic_stat_food else R.drawable.ic_stat_package
            }
            EventTags.GENERAL -> R.drawable.ic_stat_event
            else -> R.drawable.ic_notification_small
        }
    }

    fun getNetworkSpeedIcon(): Int = R.drawable.ic_stat_net

    fun getScanningIcon(): Int = R.drawable.ic_stat_scan

    fun getSuccessIcon(): Int = R.drawable.ic_stat_success

    fun getAnalyzingIcon(): Int = R.drawable.ic_stat_sparkle
}
