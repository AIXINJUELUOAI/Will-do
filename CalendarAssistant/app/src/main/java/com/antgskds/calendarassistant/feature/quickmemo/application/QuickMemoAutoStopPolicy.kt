package com.antgskds.calendarassistant.feature.quickmemo.application

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

object QuickMemoAutoStopPolicy {
    fun durationMillis(settings: MySettings): Long? {
        if (!settings.quickMemoAutoStopEnabled) return null
        return MySettings.normalizeQuickMemoAutoStopSeconds(settings.quickMemoAutoStopSeconds) * 1_000L
    }
}
