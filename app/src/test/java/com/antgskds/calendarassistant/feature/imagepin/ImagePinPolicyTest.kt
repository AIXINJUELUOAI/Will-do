package com.antgskds.calendarassistant.feature.imagepin

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import org.junit.Assert.*
import org.junit.Test

class ImagePinPolicyTest {
    private val enabled = MySettings(imagePinEnabled = true, isLiveCapsuleEnabled = true)

    @Test fun screenshotPinNeedsFeatureLiveNotificationsAndViewingPermissions() {
        assertEquals("请先在实验室开启图片挂起",
            ImagePinPolicy.captureBlockReason(enabled.copy(imagePinEnabled = false), true, true, true))
        assertEquals("请先开启实况通知",
            ImagePinPolicy.captureBlockReason(enabled.copy(isLiveCapsuleEnabled = false), true, true, true))
        assertEquals("请先允许悬浮窗权限，以便查看挂起图片",
            ImagePinPolicy.captureBlockReason(enabled, false, true, true))
        assertEquals("请先允许通知权限",
            ImagePinPolicy.captureBlockReason(enabled, true, false, true))
        assertEquals("请先开启实况通知渠道",
            ImagePinPolicy.captureBlockReason(enabled, true, true, false))
    }

    @Test fun screenshotPinDoesNotNeedAnAiModelAndRechecksRevokedAccess() {
        assertNull(ImagePinPolicy.captureBlockReason(enabled, true, true, true))
        assertNotNull(ImagePinPolicy.captureBlockReason(enabled, true, false, true))
        assertNull(ImagePinPolicy.captureBlockReason(enabled, true, true, true))
    }
}
