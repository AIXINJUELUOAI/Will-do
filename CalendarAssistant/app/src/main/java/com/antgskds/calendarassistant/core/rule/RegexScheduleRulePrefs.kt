package com.antgskds.calendarassistant.core.rule

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object RegexScheduleRulePrefs {
    private const val PREFS_NAME = "regex_schedule_rule_prefs"
    private const val KEY_RULES_JSON = "rules_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun loadRules(context: Context): List<RegexScheduleRule> {
        val raw = prefs(context).getString(KEY_RULES_JSON, null)
        if (raw.isNullOrBlank()) return defaultRules()
        return runCatching {
            mergeWithDefaults(
                json.decodeFromString(ListSerializer(RegexScheduleRule.serializer()), raw)
                    .ifEmpty { defaultRules() }
            )
        }.getOrElse { defaultRules() }
    }

    fun saveRules(context: Context, rules: List<RegexScheduleRule>) {
        val safeRules = rules.ifEmpty { defaultRules() }
        val raw = json.encodeToString(ListSerializer(RegexScheduleRule.serializer()), safeRules)
        prefs(context).edit().putString(KEY_RULES_JSON, raw).apply()
    }

    fun reset(context: Context): List<RegexScheduleRule> {
        prefs(context).edit().remove(KEY_RULES_JSON).apply()
        return defaultRules()
    }

    fun defaultRules(): List<RegexScheduleRule> = RegexScheduleRuleDefaults.rules()

    private fun mergeWithDefaults(savedRules: List<RegexScheduleRule>): List<RegexScheduleRule> {
        val savedIds = savedRules.map { it.id }.toSet()
        val missingDefaults = defaultRules().filterNot { it.id in savedIds }
        return savedRules + missingDefaults
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
