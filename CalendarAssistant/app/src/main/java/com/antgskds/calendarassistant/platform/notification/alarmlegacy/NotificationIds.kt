package com.antgskds.calendarassistant.platform.notification.alarmlegacy

/**
 * Centralized notification id allocation.
 *
 * Keep standard reminders and live capsules in separate namespaces so reminder
 * cleanup cannot accidentally remove an active capsule notification.
 */
object NotificationIds {
    const val CLIPBOARD_CODE_MONITOR = 0xC1A0D
    const val QUICK_MEMO_VOICE_CAPTURE = 0x51A11
    const val CREATED_EVENT_RESULT_GROUP = 0x51A12
    const val QUICK_MEMO_SUGGESTION_GROUP = 0x51A13
    const val RECOGNITION_FAILURE_RESULT = 0x51A14
    const val DAILY_SUMMARY_MORNING = 0x51A15
    const val DAILY_SUMMARY_EVENING = 0x51A16
    const val IMAGE_SHARE_RECOGNITION_STATUS = 0x51A17

    private const val STANDARD_REMINDER_NAMESPACE = "standard-reminder"
    private const val LIVE_CAPSULE_NAMESPACE = "live-capsule"
    private const val PICKUP_INITIAL_NAMESPACE = "pickup-initial"
    private const val WEATHER_WARNING_NAMESPACE = "weather-warning"
    private const val QUICK_MEMO_SUGGESTION_NAMESPACE = "quick-memo-suggestion"
    private const val CREATED_EVENT_RESULT_NAMESPACE = "created-event-result"
    private const val VOICE_TRANSCRIPTION_NAMESPACE = "voice-transcription"
    private const val TEXT_QUICK_MEMO_NAMESPACE = "text-quick-memo"

    private const val LEGACY_PICKUP_INITIAL_OFFSET = 1_000_000

    fun standardReminder(eventId: Long): Int = stableId(STANDARD_REMINDER_NAMESPACE, eventId.toString())

    fun standardReminder(eventId: String): Int = stableId(STANDARD_REMINDER_NAMESPACE, eventId)

    fun liveCapsule(eventId: Long): Int = stableId(LIVE_CAPSULE_NAMESPACE, eventId.toString())

    fun liveCapsule(instanceKey: String): Int = stableId(LIVE_CAPSULE_NAMESPACE, instanceKey)

    fun pickupInitial(eventId: Long): Int = stableId(PICKUP_INITIAL_NAMESPACE, eventId.toString())

    fun weatherWarning(key: String): Int = stableId(WEATHER_WARNING_NAMESPACE, key)

    fun quickMemoVoiceTranscription(memoId: Long): Int = stableId(VOICE_TRANSCRIPTION_NAMESPACE, memoId.toString())

    fun quickMemoText(memoId: Long): Int = stableId(TEXT_QUICK_MEMO_NAMESPACE, memoId.toString())

    fun quickMemoSuggestion(suggestionId: Long): Int = stableId(QUICK_MEMO_SUGGESTION_NAMESPACE, suggestionId.toString())

    fun createdEventResult(source: String, eventId: String): Int = stableId(CREATED_EVENT_RESULT_NAMESPACE, "$source:$eventId")

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
