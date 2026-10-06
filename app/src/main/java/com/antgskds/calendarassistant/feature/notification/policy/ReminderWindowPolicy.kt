package com.antgskds.calendarassistant.feature.notification.policy

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.time.Instant
import java.time.ZoneId

object ReminderWindowPolicy {
    fun endExclusive(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .plusDays(ConfigCatalog.REMINDER_WINDOW_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()

    fun contains(triggerAt: Long, nowMillis: Long, endExclusive: Long): Boolean =
        triggerAt > nowMillis && triggerAt < endExclusive

    fun allowsRegistration(triggerAt: Long, nowMillis: Long, endExclusive: Long, foreground: Boolean, restoring: Boolean): Boolean =
        (foreground || restoring) && contains(triggerAt, nowMillis, endExclusive)

    fun needsNormalReminder(settings: MySettings): Boolean =
        !settings.isLiveCapsuleEnabled || settings.braceletModeEnabled

    fun occurrences(firstAt: Long, nowMillis: Long, endExclusive: Long, nextAfter: (Long) -> Long?): List<Long> {
        val result = mutableListOf<Long>()
        var current: Long? = if (firstAt > nowMillis) firstAt else nextAfter(nowMillis)?.takeIf { it > nowMillis }
        while (current != null && current < endExclusive) {
            val at = current
            if (at > nowMillis) result.add(at)
            current = nextAfter(at)?.takeIf { it > at }
        }
        return result
    }
}
