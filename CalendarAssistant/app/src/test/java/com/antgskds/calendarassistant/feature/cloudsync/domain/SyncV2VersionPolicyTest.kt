package com.antgskds.calendarassistant.feature.cloudsync.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncV2VersionPolicyTest {
    @Test
    fun compareRecognizesEqualDominatingAndConcurrentVectors() {
        assertEquals(
            SyncV2VersionRelation.EQUAL,
            SyncV2VersionPolicy.compare(mapOf("A" to 2L), mapOf("A" to 2L)),
        )
        assertEquals(
            SyncV2VersionRelation.LOCAL_NEWER,
            SyncV2VersionPolicy.compare(mapOf("A" to 3L, "B" to 1L), mapOf("A" to 2L, "B" to 1L)),
        )
        assertEquals(
            SyncV2VersionRelation.REMOTE_NEWER,
            SyncV2VersionPolicy.compare(mapOf("A" to 1L), mapOf("A" to 1L, "B" to 1L)),
        )
        assertEquals(
            SyncV2VersionRelation.CONCURRENT,
            SyncV2VersionPolicy.compare(mapOf("A" to 2L), mapOf("B" to 2L)),
        )
    }

    @Test
    fun nextMergesAllObservedHeadsAndAdvancesLocalDevice() {
        assertEquals(
            mapOf("A" to 3L, "B" to 3L, "C" to 1L),
            SyncV2VersionPolicy.next(
                observed = listOf(
                    mapOf("A" to 3L, "B" to 1L),
                    mapOf("A" to 1L, "B" to 3L),
                ),
                deviceId = "C",
            ),
        )
    }

    @Test
    fun deterministicWinnerDoesNotDependOnInputOrder() {
        val older = record("older", modifiedAt = 10L, deviceId = "B")
        val newer = record("newer", modifiedAt = 20L, deviceId = "A")

        assertEquals(newer, SyncV2VersionPolicy.deterministicWinner(listOf(older, newer)))
        assertEquals(newer, SyncV2VersionPolicy.deterministicWinner(listOf(newer, older)))
    }

    private fun record(revisionId: String, modifiedAt: Long, deviceId: String) = SyncV2Record(
        entityType = SyncV2EntityType.EVENT.name,
        syncUuid = "event-1",
        revisionId = revisionId,
        parentRevisionIds = emptyList(),
        versionVector = mapOf(deviceId to 1L),
        modifiedAt = modifiedAt,
        modifiedByDeviceId = deviceId,
        status = SyncV2RecordStatus.ACTIVE.name,
        payloadJson = "{}",
    )
}
