package com.antgskds.calendarassistant.platform.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/** 主线程有界读取，转成不可变快照后立即释放节点，不持有 AccessibilityEvent。 */
object PaymentWindowReader {
    data class Identity(val packageName: String, val windowId: Int)

    /** 高频事件只读取根的身份，不遍历整树；包括非支付应用，以便离开时取消候选。 */
    @Suppress("DEPRECATION")
    fun readIdentity(root: AccessibilityNodeInfo?): Identity? {
        root ?: return null
        return try { Identity(root.packageName?.toString().orEmpty(), root.windowId) }
        finally { root.recycle() }
    }

    /** 只在根身份缺失时使用活动应用窗口佐证；明确的不同根窗口不能被兜底覆盖。 */
    @Suppress("DEPRECATION")
    fun foreground(service: android.accessibilityservice.AccessibilityService,
        expectedPackage: String? = null, expectedWindow: Int? = null, includeTexts: Boolean = true): Snapshot? {
        val root = service.rootInActiveWindow
        if (root != null && !root.packageName.isNullOrBlank()) {
            val pkg = root.packageName.toString()
            if (includeTexts && AutomaticAccountingPolicy.supports(pkg)) return read(root)
            return try { Snapshot(pkg, emptyList(), false, root.windowId) } finally { root.recycle() }
        }
        root?.recycle()
        val windows = service.windows
        try {
            val active = windows.singleOrNull { it.isActive && it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
                ?: return null
            val windowRoot = active.root
            if (windowRoot != null && !windowRoot.packageName.isNullOrBlank()) {
                val pkg = windowRoot.packageName.toString()
                if (includeTexts && AutomaticAccountingPolicy.supports(pkg)) return read(windowRoot)
                return try { Snapshot(pkg, emptyList(), false, active.id) } finally { windowRoot.recycle() }
            }
            windowRoot?.recycle()
            return if (active.id == expectedWindow && expectedPackage != null)
                Snapshot(expectedPackage, emptyList(), false, active.id) else null
        } finally { windows.forEach { it.recycle() } }
    }

    fun hasImageContent(bitmap: android.graphics.Bitmap): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
        val side = ConfigCatalog.AUTO_ACCOUNTING_IMAGE_SAMPLE_SIDE
        val sample = android.graphics.Bitmap.createScaledBitmap(bitmap, side, side, true)
        try {
            val border = side / ConfigCatalog.AUTO_ACCOUNTING_IMAGE_BORDER_DIVISOR
            val pixels = IntArray(side * (side - border * 2))
            sample.getPixels(pixels, 0, side, 0, border, side, side - border * 2)
            return com.antgskds.calendarassistant.feature.accounting.domain.AccountingScreenshotPolicy.hasContent(pixels)
        } finally { if (sample !== bitmap) sample.recycle() }
    }

    data class Snapshot(val packageName: String, val texts: List<String>, val editable: Boolean, val windowId: Int = -1) {
        val fingerprint get() = AutomaticAccountingPolicy.fingerprint("$packageName|${texts.joinToString("\n")}")
        val eligible get() = AutomaticAccountingPolicy.matchesScreen(packageName, texts, editable)
    }

    @Suppress("DEPRECATION")
    fun read(root: AccessibilityNodeInfo?): Snapshot? {
        root ?: return null
        val packageName = root.packageName?.toString().orEmpty()
        val windowId = root.windowId
        if (!AutomaticAccountingPolicy.supports(packageName)) { root.recycle(); return null }
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val texts = LinkedHashSet<String>()
        var editable = false
        var visited = 0
        var chars = 0
        try {
            while (queue.isNotEmpty() && visited < ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES && chars < ConfigCatalog.AUTO_ACCOUNTING_MAX_TEXT) {
                val node = queue.removeFirst()
                try {
                    visited++
                    if (!node.isVisibleToUser) continue
                    editable = editable || node.isEditable
                    for (value in listOf(node.text, node.contentDescription)) {
                        val text = value?.toString()?.trim().orEmpty().take(ConfigCatalog.AUTO_ACCOUNTING_MAX_TEXT - chars)
                        if (text.isNotBlank() && texts.add(text)) chars += text.length
                    }
                    for (i in 0 until minOf(node.childCount, ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES - visited - queue.size)) {
                        node.getChild(i)?.let(queue::addLast)
                    }
                } finally { node.recycle() }
            }
        } finally { while (queue.isNotEmpty()) queue.removeFirst().recycle() }
        return Snapshot(packageName, texts.toList(), editable, windowId)
    }
}
