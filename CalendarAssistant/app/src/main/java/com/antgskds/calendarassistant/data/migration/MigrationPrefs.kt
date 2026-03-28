package com.antgskds.calendarassistant.data.migration

import android.content.Context

class MigrationPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEventsMigrated(): Boolean {
        val version = prefs.getInt(KEY_EVENTS_VERSION, 0)
        val completedAt = prefs.getLong(KEY_EVENTS_COMPLETED_AT, 0L)
        return version == EVENTS_MIGRATION_VERSION && completedAt > 0L
    }

    fun markEventsMigrated(timestamp: Long) {
        prefs.edit()
            .putInt(KEY_EVENTS_VERSION, EVENTS_MIGRATION_VERSION)
            .putLong(KEY_EVENTS_COMPLETED_AT, timestamp)
            .apply()
    }

    fun getEventsMigratedAt(): Long {
        return prefs.getLong(KEY_EVENTS_COMPLETED_AT, 0L)
    }

    /**
     * 检查归档事件是否已迁移
     */
    fun isArchivesMigrated(): Boolean {
        val version = prefs.getInt(KEY_ARCHIVES_VERSION, 0)
        val completedAt = prefs.getLong(KEY_ARCHIVES_COMPLETED_AT, 0L)
        return version == ARCHIVES_MIGRATION_VERSION && completedAt > 0L
    }

    /**
     * 标记归档事件迁移完成
     */
    fun markArchivesMigrated(timestamp: Long) {
        prefs.edit()
            .putInt(KEY_ARCHIVES_VERSION, ARCHIVES_MIGRATION_VERSION)
            .putLong(KEY_ARCHIVES_COMPLETED_AT, timestamp)
            .apply()
    }

    fun getArchivesMigratedAt(): Long {
        return prefs.getLong(KEY_ARCHIVES_COMPLETED_AT, 0L)
    }

    fun isRecurringMigrated(): Boolean {
        val version = prefs.getInt(KEY_RECURRING_VERSION, 0)
        val completedAt = prefs.getLong(KEY_RECURRING_COMPLETED_AT, 0L)
        return version == RECURRING_MIGRATION_VERSION && completedAt > 0L
    }

    fun markRecurringMigrated(timestamp: Long) {
        prefs.edit()
            .putInt(KEY_RECURRING_VERSION, RECURRING_MIGRATION_VERSION)
            .putLong(KEY_RECURRING_COMPLETED_AT, timestamp)
            .apply()
    }

    fun getRecurringMigratedAt(): Long {
        return prefs.getLong(KEY_RECURRING_COMPLETED_AT, 0L)
    }

    companion object {
        private const val PREFS_NAME = "migration_state"
        private const val KEY_EVENTS_VERSION = "events_migration_version"
        private const val KEY_EVENTS_COMPLETED_AT = "events_migration_completed_at"
        private const val EVENTS_MIGRATION_VERSION = 1

        private const val KEY_ARCHIVES_VERSION = "archives_migration_version"
        private const val KEY_ARCHIVES_COMPLETED_AT = "archives_migration_completed_at"
        private const val ARCHIVES_MIGRATION_VERSION = 1

        private const val KEY_RECURRING_VERSION = "recurring_migration_version"
        private const val KEY_RECURRING_COMPLETED_AT = "recurring_migration_completed_at"
        private const val RECURRING_MIGRATION_VERSION = 1
    }
}
