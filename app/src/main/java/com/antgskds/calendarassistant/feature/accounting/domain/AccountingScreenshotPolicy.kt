package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/** 对去掉系统栏的缩略采样做纯色粗检；不能据此证明金额清晰或交易已完成。 */
object AccountingScreenshotPolicy {
    fun hasContent(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        var minimum = 255
        var maximum = 0
        for (pixel in pixels) {
            val value = (((pixel shr 16) and 255) * 299 + ((pixel shr 8) and 255) * 587 + (pixel and 255) * 114) / 1000
            minimum = minOf(minimum, value)
            maximum = maxOf(maximum, value)
        }
        return maximum - minimum >= ConfigCatalog.AUTO_ACCOUNTING_IMAGE_MIN_RANGE
    }
}
