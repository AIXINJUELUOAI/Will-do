package com.antgskds.calendarassistant.feature.api.notification.data

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationAction
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationBehavior
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationDisplaySnapshot
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKind
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationPriority
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationSnapshot
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationState
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTarget
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTargetType
import com.antgskds.calendarassistant.feature.api.notification.ports.NotificationRegistryStore
import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleInstanceKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SharedPreferencesNotificationRegistryStore(context: Context) : NotificationRegistryStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override suspend fun upsert(snapshot: NotificationSnapshot) {
        synchronized(lock) {
            val entries = readEntries().toMutableMap()
            entries[snapshot.key.value] = NotificationSnapshotDto.from(snapshot)
            writeEntries(entries)
        }
    }

    override suspend fun get(key: NotificationKey): NotificationSnapshot? {
        return synchronized(lock) {
            readEntries()[key.value]?.toSnapshotOrNull()
        }
    }

    override suspend fun list(query: NotificationQuery): List<NotificationSnapshot> {
        return synchronized(lock) {
            readEntries().values
                .mapNotNull { it.toSnapshotOrNull() }
                .asSequence()
                .filter { snapshot -> query.key == null || snapshot.key == query.key }
                .filter { snapshot -> query.kind == null || snapshot.kind == query.kind }
                .filter { snapshot -> query.route == null || snapshot.route == query.route }
                .filter { snapshot -> query.state == null || snapshot.state == query.state }
                .filter { snapshot -> query.includeCancelled || snapshot.state != NotificationState.CANCELLED }
                .filter { snapshot ->
                    val dueAt = query.dueAtOrBeforeEpochMillis ?: return@filter true
                    val triggerAt = snapshot.behavior.triggerAtEpochMillis ?: return@filter true
                    triggerAt <= dueAt
                }
                .sortedBy { it.behavior.triggerAtEpochMillis ?: Long.MAX_VALUE }
                .let { sequence -> query.limit?.let(sequence::take) ?: sequence }
                .toList()
        }
    }

    override suspend fun delete(key: NotificationKey) {
        synchronized(lock) {
            val entries = readEntries().toMutableMap()
            val removed = entries.remove(key.value)
            if (removed == null) return@synchronized
            writeEntries(entries)
        }
    }

    override suspend fun deleteAll(keys: Collection<NotificationKey>): List<NotificationSnapshot> {
        val distinctKeys = keys.map { it.value }.toSet()
        if (distinctKeys.isEmpty()) return emptyList()

        return synchronized(lock) {
            val entries = readEntries().toMutableMap()
            val removed = distinctKeys.mapNotNull { key -> entries.remove(key)?.toSnapshotOrNull() }
            if (removed.isNotEmpty()) {
                writeEntries(entries)
            }
            removed
        }
    }

    private fun readEntries(): Map<String, NotificationSnapshotDto> {
        val raw = prefs.getString(KEY_SNAPSHOTS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<NotificationRegistryFileDto>(raw) }
            .onFailure { error ->
                Log.w(TAG, "Failed to read notification registry snapshots", error)
            }
            .getOrNull()
            ?.snapshots
            ?.associateBy { it.key }
            .orEmpty()
    }

    private fun writeEntries(entries: Map<String, NotificationSnapshotDto>) {
        val payload = NotificationRegistryFileDto(snapshots = entries.values.sortedBy { it.key })
        val encoded = json.encodeToString(payload)
        prefs.edit().putString(KEY_SNAPSHOTS, encoded).commit()
    }

    private companion object {
        const val TAG = "NotificationRegistry"
        const val PREFS_NAME = "notification_registry_store"
        const val KEY_SNAPSHOTS = "snapshots_json"
    }
}

@Serializable
private data class NotificationRegistryFileDto(
    val version: Int = 1,
    val snapshots: List<NotificationSnapshotDto> = emptyList()
)

@Serializable
private data class NotificationSnapshotDto(
    val key: String,
    val kind: String,
    val state: String,
    val route: String,
    val display: NotificationDisplaySnapshotDto,
    val notificationId: Int? = null,
    val smallIconResId: Int? = null,
    val scheduleInstanceKey: ScheduleInstanceKeyDto? = null,
    val offsetMinutes: Int? = null,
    val channelKey: String? = null,
    val category: String? = null,
    val behavior: NotificationBehaviorDto = NotificationBehaviorDto(),
    val tapTarget: NotificationTapTargetDto? = null,
    val actions: List<NotificationActionDto> = emptyList(),
    val updatedAtEpochMillis: Long? = null,
    val version: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toSnapshotOrNull(): NotificationSnapshot? {
        if (key.isBlank()) return null
        return NotificationSnapshot(
            key = NotificationKey(key),
            kind = enumValueOrDefault(kind, NotificationKind.GENERIC),
            state = enumValueOrDefault(state, NotificationState.FAILED),
            route = enumValueOrDefault(route, NotificationRoute.AUTO),
            display = display.toDisplay(),
            notificationId = notificationId,
            smallIconResId = smallIconResId,
            scheduleInstanceKey = scheduleInstanceKey?.toScheduleInstanceKeyOrNull(),
            offsetMinutes = offsetMinutes,
            channelKey = channelKey,
            category = category,
            behavior = behavior.toBehavior(),
            tapTarget = tapTarget?.toTapTarget(),
            actions = actions.map { it.toAction() },
            updatedAtEpochMillis = updatedAtEpochMillis,
            version = version,
            metadata = metadata
        )
    }

    companion object {
        fun from(snapshot: NotificationSnapshot): NotificationSnapshotDto {
            return NotificationSnapshotDto(
                key = snapshot.key.value,
                kind = snapshot.kind.name,
                state = snapshot.state.name,
                route = snapshot.route.name,
                display = NotificationDisplaySnapshotDto.from(snapshot.display),
                notificationId = snapshot.notificationId,
                smallIconResId = snapshot.smallIconResId,
                scheduleInstanceKey = snapshot.scheduleInstanceKey?.let(ScheduleInstanceKeyDto::from),
                offsetMinutes = snapshot.offsetMinutes,
                channelKey = snapshot.channelKey,
                category = snapshot.category,
                behavior = NotificationBehaviorDto.from(snapshot.behavior),
                tapTarget = snapshot.tapTarget?.let(NotificationTapTargetDto::from),
                actions = snapshot.actions.map(NotificationActionDto::from),
                updatedAtEpochMillis = snapshot.updatedAtEpochMillis,
                version = snapshot.version,
                metadata = snapshot.metadata
            )
        }
    }
}

@Serializable
private data class NotificationDisplaySnapshotDto(
    val shortText: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val tertiaryText: String? = null,
    val expandedText: String? = null
) {
    fun toDisplay(): NotificationDisplaySnapshot {
        return NotificationDisplaySnapshot(
            shortText = shortText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            tertiaryText = tertiaryText,
            expandedText = expandedText
        )
    }

    companion object {
        fun from(display: NotificationDisplaySnapshot): NotificationDisplaySnapshotDto {
            return NotificationDisplaySnapshotDto(
                shortText = display.shortText,
                primaryText = display.primaryText,
                secondaryText = display.secondaryText,
                tertiaryText = display.tertiaryText,
                expandedText = display.expandedText
            )
        }
    }
}

@Serializable
private data class NotificationBehaviorDto(
    val triggerAtEpochMillis: Long? = null,
    val timeoutAfterMillis: Long? = null,
    val ongoing: Boolean = false,
    val autoCancel: Boolean = true,
    val onlyAlertOnce: Boolean = true,
    val allowWhileIdle: Boolean = true,
    val replaceExisting: Boolean = true,
    val priority: String = NotificationPriority.DEFAULT.name
) {
    fun toBehavior(): NotificationBehavior {
        return NotificationBehavior(
            triggerAtEpochMillis = triggerAtEpochMillis,
            timeoutAfterMillis = timeoutAfterMillis,
            ongoing = ongoing,
            autoCancel = autoCancel,
            onlyAlertOnce = onlyAlertOnce,
            allowWhileIdle = allowWhileIdle,
            replaceExisting = replaceExisting,
            priority = enumValueOrDefault(priority, NotificationPriority.DEFAULT)
        )
    }

    companion object {
        fun from(behavior: NotificationBehavior): NotificationBehaviorDto {
            return NotificationBehaviorDto(
                triggerAtEpochMillis = behavior.triggerAtEpochMillis,
                timeoutAfterMillis = behavior.timeoutAfterMillis,
                ongoing = behavior.ongoing,
                autoCancel = behavior.autoCancel,
                onlyAlertOnce = behavior.onlyAlertOnce,
                allowWhileIdle = behavior.allowWhileIdle,
                replaceExisting = behavior.replaceExisting,
                priority = behavior.priority.name
            )
        }
    }
}

@Serializable
private data class NotificationTapTargetDto(
    val type: String,
    val payload: Map<String, String> = emptyMap()
) {
    fun toTapTarget(): NotificationTapTarget {
        return NotificationTapTarget(
            type = enumValueOrDefault(type, NotificationTapTargetType.NONE),
            payload = payload
        )
    }

    companion object {
        fun from(target: NotificationTapTarget): NotificationTapTargetDto {
            return NotificationTapTargetDto(
                type = target.type.name,
                payload = target.payload
            )
        }
    }
}

@Serializable
private data class NotificationActionDto(
    val key: String,
    val label: String,
    val payload: Map<String, String> = emptyMap()
) {
    fun toAction(): NotificationAction {
        return NotificationAction(key = key, label = label, payload = payload)
    }

    companion object {
        fun from(action: NotificationAction): NotificationActionDto {
            return NotificationActionDto(
                key = action.key,
                label = action.label,
                payload = action.payload
            )
        }
    }
}

@Serializable
private data class ScheduleInstanceKeyDto(
    val type: String,
    val eventId: Long? = null,
    val parentId: Long? = null,
    val occurrenceEpochSeconds: Long? = null,
    val source: String? = null,
    val id: String? = null
) {
    fun toScheduleInstanceKeyOrNull(): ScheduleInstanceKey? {
        return when (type) {
            TYPE_SINGLE -> eventId?.let(ScheduleInstanceKey::Single)
            TYPE_RECURRING -> {
                val parent = parentId ?: return null
                val occurrence = occurrenceEpochSeconds ?: return null
                ScheduleInstanceKey.Recurring(parent, occurrence)
            }
            TYPE_EXTERNAL -> {
                val safeSource = source?.takeIf { it.isNotBlank() } ?: return null
                val safeId = id?.takeIf { it.isNotBlank() } ?: return null
                ScheduleInstanceKey.External(safeSource, safeId)
            }
            else -> null
        }
    }

    companion object {
        private const val TYPE_SINGLE = "single"
        private const val TYPE_RECURRING = "recurring"
        private const val TYPE_EXTERNAL = "external"

        fun from(key: ScheduleInstanceKey): ScheduleInstanceKeyDto {
            return when (key) {
                is ScheduleInstanceKey.Single -> ScheduleInstanceKeyDto(
                    type = TYPE_SINGLE,
                    eventId = key.eventId
                )
                is ScheduleInstanceKey.Recurring -> ScheduleInstanceKeyDto(
                    type = TYPE_RECURRING,
                    parentId = key.parentId,
                    occurrenceEpochSeconds = key.occurrenceEpochSeconds
                )
                is ScheduleInstanceKey.External -> ScheduleInstanceKeyDto(
                    type = TYPE_EXTERNAL,
                    source = key.source,
                    id = key.id
                )
            }
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, defaultValue: T): T {
    return enumValues<T>().firstOrNull { it.name == value } ?: defaultValue
}
