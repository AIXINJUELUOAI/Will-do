package com.antgskds.calendarassistant.feature.api.schedule.model

sealed interface ScheduleCommand {
    val requestId: String?
    val syncToSystem: Boolean
    val updateNotifications: Boolean
}

data class ScheduleCreateCommand(
    val input: ScheduleInput,
    override val requestId: String? = null,
    override val syncToSystem: Boolean = true,
    override val updateNotifications: Boolean = true
) : ScheduleCommand

data class ScheduleUpdateCommand(
    val key: ScheduleInstanceKey,
    val input: ScheduleInput,
    val editMode: ScheduleEditMode = ScheduleEditMode.THIS,
    override val requestId: String? = null,
    override val syncToSystem: Boolean = true,
    override val updateNotifications: Boolean = true
) : ScheduleCommand

data class ScheduleDeleteCommand(
    val key: ScheduleInstanceKey,
    val editMode: ScheduleEditMode = ScheduleEditMode.THIS,
    override val requestId: String? = null,
    override val syncToSystem: Boolean = true,
    override val updateNotifications: Boolean = true
) : ScheduleCommand

data class ScheduleInput(
    val title: String,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    val location: String = "",
    val description: String = "",
    val timeZone: String = "",
    val tag: String = "general",
    val color: Int = 0,
    val recurrenceRule: String = "",
    val reminders: List<ScheduleReminderSpec> = emptyList(),
    val source: String = SOURCE_MANUAL,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_RECOGNITION = "recognition"
        const val SOURCE_SMS = "sms"
        const val SOURCE_IMPORT = "import"
        const val SOURCE_DEBUG = "debug"
    }
}

data class ScheduleReminderSpec(
    val minutesBefore: Int,
    val type: ScheduleReminderType = ScheduleReminderType.NOTIFICATION
)

enum class ScheduleReminderType {
    NOTIFICATION,
    EMAIL
}

enum class ScheduleEditMode {
    THIS,
    THIS_AND_FUTURE,
    ALL
}
