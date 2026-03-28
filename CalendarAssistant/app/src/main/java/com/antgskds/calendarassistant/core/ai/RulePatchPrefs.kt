package com.antgskds.calendarassistant.core.ai

import android.content.Context

object RulePatchPrefs {
    private const val PREFS_NAME = "rule_patch_prefs"
    private const val KEY_RULE_PATCH_ENABLED = "rule_patch_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RULE_PATCH_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RULE_PATCH_ENABLED, enabled)
            .apply()
    }
}
