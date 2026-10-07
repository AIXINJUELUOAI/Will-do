package com.antgskds.calendarassistant.feature.capsule.domain

import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState.Active.CapsuleItem

/** Only commit after notify succeeds, so failures are retried on the next dispatch. */
internal class CapsulePublicationTracker {
    data class Publication(val item: CapsuleItem, val firstPublishedAt: Long)
    private val published = mutableMapOf<Int, Publication>()

    fun prepare(item: CapsuleItem, now: Long, force: Boolean = false): Publication? {
        val previous = published[item.notifId]
        // Aggregate pickup's start time is recomputed on each state emission, not a real change.
        val normalized = if (item.id == "AGGREGATE_PICKUP" && previous != null) item.copy(startMillis = previous.item.startMillis) else item
        if (!force && previous?.item == normalized) return null
        return Publication(normalized, previous?.firstPublishedAt ?: now)
    }

    fun commit(publication: Publication) { published[publication.item.notifId] = publication }
    fun remove(id: Int) { published.remove(id) }
    fun clear() { published.clear() }
}
