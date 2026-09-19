package com.antgskds.calendarassistant.platform.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import org.json.JSONArray
import org.json.JSONObject

/** 在事件回调线程读取；返回 JSON 后不再持有系统节点。 */
object AccessibilityDiagnosticSnapshot {
    fun field(value: CharSequence?): String = value?.toString().orEmpty()
        .take(ConfigCatalog.PAYMENT_DIAGNOSTIC_FIELD_CHARS)

    @Suppress("DEPRECATION")
    fun tree(root: AccessibilityNodeInfo?): JSONObject {
        if (root == null) return JSONObject().put("available", false).put("reason", "null_root")
        val queue = java.util.ArrayDeque<Triple<AccessibilityNodeInfo, Int, Boolean>>()
        queue.add(Triple(root, -1, true))
        val nodes = JSONArray()
        val texts = linkedSetOf<String>()
        var editable = false
        var truncated = false
        var textNodes = 0
        val result = JSONObject().put("available", true)
        try {
            val pkg = root.packageName?.toString().orEmpty()
            result.put("package", pkg).put("windowId", root.windowId)
            while (queue.isNotEmpty() && nodes.length() < ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_NODES) {
                val (node, parent, ancestorsVisible) = queue.removeFirst()
                try {
                    val id = nodes.length()
                    val visiblePath = ancestorsVisible && node.isVisibleToUser
                    val password = node.isPassword
                    val rawText = if (password) "[PASSWORD]" else node.text?.toString().orEmpty()
                    val rawDesc = if (password) "[PASSWORD]" else node.contentDescription?.toString().orEmpty()
                    if (rawText.length > ConfigCatalog.PAYMENT_DIAGNOSTIC_FIELD_CHARS || rawDesc.length > ConfigCatalog.PAYMENT_DIAGNOSTIC_FIELD_CHARS) truncated = true
                    val text = field(rawText)
                    val desc = field(rawDesc)
                    if (!password && (text.isNotBlank() || desc.isNotBlank())) textNodes++
                    if (visiblePath) {
                        editable = editable || node.isEditable
                        if (!password) {
                            if (text.isNotBlank()) texts += text
                            if (desc.isNotBlank()) texts += desc
                        }
                    }
                    val bounds = Rect().also(node::getBoundsInScreen)
                    nodes.put(JSONObject().put("node", id).put("parent", parent)
                        .put("class", field(node.className)).put("id", field(node.viewIdResourceName))
                        .put("visible", node.isVisibleToUser).put("visiblePath", visiblePath)
                        .put("editable", node.isEditable).put("password", password)
                        .put("bounds", bounds.toShortString()).put("text", text).put("desc", desc)
                        .put("children", node.childCount))
                    val count = minOf(node.childCount, ConfigCatalog.PAYMENT_DIAGNOSTIC_MAX_NODES - nodes.length() - queue.size)
                    if (count < node.childCount) truncated = true
                    for (i in 0 until count) node.getChild(i)?.let { queue.add(Triple(it, id, visiblePath)) }
                } finally { node.recycle() }
            }
            if (queue.isNotEmpty()) truncated = true
            val check = AutomaticAccountingPolicy.inspectScreen(pkg, texts.toList(), editable)
            result.put("rule", JSONObject().put("eligible", check.eligible).put("supported", check.supported)
                .put("editable", check.editable).put("excluded", check.excludedMarker ?: JSONObject.NULL)
                .put("success", check.success).put("amount", check.amount)
                .put("scope", "this_sample_only_not_full_production_decision"))
        } catch (e: Exception) {
            truncated = true
            result.put("error", e.javaClass.simpleName)
        } finally {
            while (queue.isNotEmpty()) queue.removeFirst().first.recycle()
        }
        return result.put("nodes", nodes).put("nodeCount", nodes.length())
            .put("textNodes", textNodes).put("truncated", truncated)
    }
}
