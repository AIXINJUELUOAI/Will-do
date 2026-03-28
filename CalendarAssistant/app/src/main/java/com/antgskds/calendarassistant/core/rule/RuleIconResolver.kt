package com.antgskds.calendarassistant.core.rule

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object RuleIconResolver {
    private const val TAG = "RuleIconResolver"
    private val iconMap = ConcurrentHashMap<String, Int>()

    suspend fun refresh(context: Context) {
        withContext(Dispatchers.IO) {
            val rules = AppDatabase.getInstance(context.applicationContext).eventRuleDao().getAll()
            val updated = mutableMapOf<String, Int>()
            rules.forEach { rule ->
                val fallbackResName = buildFallbackResName(rule.ruleId)
                val resolved = resolveResId(context, fallbackResName)
                if (resolved != null && resolved != 0) {
                    updated[rule.ruleId] = resolved
                }
            }
            iconMap.clear()
            iconMap.putAll(updated)
        }
    }

    fun resolve(ruleId: String?): Int? {
        val key = ruleId?.trim().orEmpty()
        if (key.isBlank()) return null
        return iconMap[key]
    }

    fun buildFallbackResName(ruleId: String?): String {
        val normalized = ruleId?.trim().orEmpty()
        if (normalized.isBlank()) return ""
        return "iic_stat_$normalized"
    }

    private fun resolveResId(context: Context, resName: String): Int? {
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
        if (resId == 0) {
            Log.w(TAG, "Icon resource not found: $resName")
            return null
        }
        return resId
    }
}
