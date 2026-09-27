package com.antgskds.calendarassistant.feature.imagepin

import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleDisplayModel
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveNotificationTemplate
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveTemplateKind
import org.junit.Assert.*
import org.junit.Test

class ImagePinPresentationTest {
    @Test fun indefinitePinShowsViewHintAndEndActionWithoutInventingATimeRange() {
        val display = CapsuleDisplayModel.imagePin(
            id = 1L, count = 1,
            endAction = CapsuleActionSpec("结束挂起", "clear", "image_pin_id", 1L)
        )
        val content = XiaomiLiveNotificationTemplate.create(
            display, useShortTitle = true, hasActions = true, forceTextIcon = false,
            summaryStatus = "图片挂起", startMillis = 1_000L, endMillis = Long.MAX_VALUE
        )
        assertEquals("图片挂起", content.summaryTitle)
        assertEquals("图片挂起，点击查看", content.content)
        assertEquals(XiaomiLiveTemplateKind.TEXT_ICON_ACTION, content.templateKind)
        assertNull(content.hintTitle)
    }

    @Test fun multipleImagesShowCountOnCompactIslandAndKeepOneViewAndEndTarget() {
        for (count in listOf(2, 20)) {
            val action = CapsuleActionSpec("结束挂起", "clear", "image_pin_id", 42L)
            val display = CapsuleDisplayModel.imagePin(42L, count, action)
            val content = XiaomiLiveNotificationTemplate.create(
                display, useShortTitle = true, hasActions = true, forceTextIcon = false,
                summaryStatus = "图片挂起", startMillis = 1_000L, endMillis = Long.MAX_VALUE
            )
            assertEquals("共 $count 张", display.shortText)
            assertEquals(display.shortText, content.summaryTitle)
            assertEquals("图片挂起", content.title)
            assertTrue(content.content.contains("左右滑动查看"))
            assertEquals(42L, display.tapImagePinId)
            assertEquals(listOf(action), display.effectiveActions)
            assertNull(content.hintTitle)
        }
    }

    @Test fun legacySingleImageSurvivesMigrationAndAppendRoundTrip() {
        val old = restoreImagePinIds(null, 100L)
        assertEquals(listOf(100L), old)
        val appended = old + listOf(101L, 102L)
        assertEquals(appended, restoreImagePinIds(appended.joinToString(","), 102L))
        assertTrue(restoreImagePinIds(null, 0L).isEmpty())
        assertTrue(restoreImagePinIds("", 100L).isEmpty())
        assertEquals(listOf(102L, 100L), restoreImagePinIds("102,invalid,-1,0,100,102", 999L))
    }
}
