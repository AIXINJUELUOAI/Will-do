package com.antgskds.calendarassistant.service.notification

/**
 * Centralized notification id allocation.
 *
 * Keep standard reminders and live capsules in separate namespaces so reminder
 * cleanup cannot accidentally remove an active capsule notification.
 */
object NotificationIds {
    private const val STANDARD_REMINDER_NAMESPACE = "standard-reminder"
    private const val LIVE_CAPSULE_NAMESPACE = "live-capsule"
    private const val PICKUP_INITIAL_NAMESPACE = "pickup-initial"

    private const val LEGACY_PICKUP_INITIAL_OFFSET = 1_000_000

    fun standardReminder(eventId: Long): Int = stableId(STANDARD_REMINDER_NAMESPACE, eventId.toString())

    fun standardReminder(eventId: String): Int = stableId(STANDARD_REMINDER_NAMESPACE, eventId)

    fun liveCapsule(eventId: Long): Int = stableId(LIVE_CAPSULE_NAMESPACE, eventId.toString())

    fun liveCapsule(instanceKey: String): Int = stableId(LIVE_CAPSULE_NAMESPACE, instanceKey)

    fun pickupInitial(eventId: Long): Int = stableId(PICKUP_INITIAL_NAMESPACE, eventId.toString())

    fun legacyEventIds(eventId: Long): Set<Int> {
        val base = eventId.hashCode()
        return setOf(
            base,
            eventId.toInt(),
            eventId.toString().hashCode(),
            base + LEGACY_PICKUP_INITIAL_OFFSET
        )
    }

    fun legacyKeyIds(key: String): Set<Int> {
        val numericId = key.toLongOrNull()
        return buildSet {
            add(key.hashCode())
            if (numericId != null) {
                add(numericId.hashCode())
                add(numericId.toInt())
                add(numericId.hashCode() + LEGACY_PICKUP_INITIAL_OFFSET)
            }
        }
    }

    private fun stableId(namespace: String, key: String): Int {
        return "$namespace:$key".hashCode() and Int.MAX_VALUE
    }
}
