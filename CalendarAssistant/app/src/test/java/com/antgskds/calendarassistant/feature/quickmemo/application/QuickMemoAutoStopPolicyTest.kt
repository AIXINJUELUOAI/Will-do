package com.antgskds.calendarassistant.feature.quickmemo.application

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickMemoAutoStopPolicyTest {
    @Test
    fun disabledSettingDoesNotCreateTimeout() {
        assertNull(
            QuickMemoAutoStopPolicy.durationMillis(
                MySettings(quickMemoAutoStopEnabled = false, quickMemoAutoStopSeconds = 5)
            )
        )
    }

    @Test
    fun enabledSettingUsesSelectedTimeout() {
        assertEquals(
            15_000L,
            QuickMemoAutoStopPolicy.durationMillis(
                MySettings(quickMemoAutoStopEnabled = true, quickMemoAutoStopSeconds = 15)
            )
        )
    }

    @Test
    fun timeoutIsClampedToSupportedRange() {
        assertEquals(
            15_000L,
            QuickMemoAutoStopPolicy.durationMillis(
                MySettings(quickMemoAutoStopEnabled = true, quickMemoAutoStopSeconds = 99)
            )
        )
        assertEquals(
            1_000L,
            QuickMemoAutoStopPolicy.durationMillis(
                MySettings(quickMemoAutoStopEnabled = true, quickMemoAutoStopSeconds = 0)
            )
        )
    }

    @Test
    fun uiScaleIsClampedToDeveloperRange() {
        assertEquals(MySettings.UI_SCALE_MIN, MySettings.normalizeUiScale(0.1f))
        assertEquals(MySettings.UI_SCALE_MAX, MySettings.normalizeUiScale(2f))
    }
}
