package com.antgskds.calendarassistant.feature.cloudsync.domain

enum class SyncV2VersionRelation {
    EQUAL,
    LOCAL_NEWER,
    REMOTE_NEWER,
    CONCURRENT,
}

object SyncV2VersionPolicy {
    fun compare(local: Map<String, Long>, remote: Map<String, Long>): SyncV2VersionRelation {
        val devices = local.keys + remote.keys
        var localGreater = false
        var remoteGreater = false
        devices.forEach { deviceId ->
            val localValue = local[deviceId] ?: 0L
            val remoteValue = remote[deviceId] ?: 0L
            if (localValue > remoteValue) localGreater = true
            if (remoteValue > localValue) remoteGreater = true
        }
        return when {
            !localGreater && !remoteGreater -> SyncV2VersionRelation.EQUAL
            localGreater && !remoteGreater -> SyncV2VersionRelation.LOCAL_NEWER
            remoteGreater && !localGreater -> SyncV2VersionRelation.REMOTE_NEWER
            else -> SyncV2VersionRelation.CONCURRENT
        }
    }

    fun next(observed: Collection<Map<String, Long>>, deviceId: String): Map<String, Long> {
        val merged = linkedMapOf<String, Long>()
        observed.forEach { vector ->
            vector.forEach { (sourceDeviceId, counter) ->
                merged[sourceDeviceId] = maxOf(merged[sourceDeviceId] ?: 0L, counter)
            }
        }
        merged[deviceId] = (merged[deviceId] ?: 0L) + 1L
        return merged.toSortedMap()
    }

    fun deterministicWinner(records: List<SyncV2Record>): SyncV2Record? = records.maxWithOrNull(
        compareBy<SyncV2Record> { it.modifiedAt }
            .thenBy { it.modifiedByDeviceId }
            .thenBy { it.revisionId }
    )
}
