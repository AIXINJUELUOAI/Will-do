package com.antgskds.calendarassistant.feature.cloudsync.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2AssetLinkEntity
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2BindingEntity
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2RevisionEntity
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2MetaEntity
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2PeerEntity
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2AssetRole
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2AttachmentRef
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2EntityType
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2EventPayload
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2QuickMemoPayload
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2Record
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2RecordStatus
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2VersionPolicy
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2VersionRelation
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.schedule.data.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import com.antgskds.calendarassistant.feature.schedule.data.store.StoreDispatcher
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.SOURCE_SIMPLE_CALENDAR
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventAttachment
import java.io.File
import java.util.UUID

class SyncV2LocalRepository(
    context: Context,
    private val codec: SyncV2Codec,
) {
    private val appContext = context.applicationContext
    private val database = EventsDatabase.getInstance(appContext)
    private val syncDao = database.syncV2Dao()
    private val eventsDao = database.eventsDao()
    private val attachmentsDao = database.eventAttachmentsDao()
    private val quickMemoDao = database.quickMemoDao()
    private val attachmentManager = EventAttachmentManager(appContext)
    private val scheduleStore = StoreDispatcher.getInstance(appContext)

    suspend fun scanLocalChanges(deviceId: String, encryptionKey: ByteArray) {
        database.withTransaction {
            val events = eventsDao.getAllEventsForSync()
            val memos = quickMemoDao.getAllQuickMemos()
            Log.d(
                TAG,
                "local scan start events=${events.size} quickMemos=${memos.size} " +
                    "bindings=${syncDao.getBindings().size} revisions=${syncDao.getAllRevisions().size}",
            )
            ensureBindings(SyncV2EntityType.EVENT, events.mapNotNull { it.id })
            ensureBindings(SyncV2EntityType.QUICK_MEMO, memos.mapNotNull { it.id })

            val eventBindings = syncDao.getBindings()
                .filter { it.entityType == SyncV2EntityType.EVENT.name }
                .associateBy { it.localId }
            val parentUuids = eventBindings.filterKeys { it != null }.mapKeys { checkNotNull(it.key) }.mapValues { it.value.syncUuid }

            events.forEach { event ->
                val localId = event.id ?: return@forEach
                val binding = eventBindings[localId] ?: return@forEach
                val refs = buildEventAttachmentRefs(binding.recordKey, localId, encryptionKey)
                pruneAssetLinks(binding.recordKey, refs)
                val payload = event.toPortable(parentUuids[event.parentId])
                recordLocalPayload(binding, codec.encodePayload(payload), refs, deviceId)
            }

            val memoBindings = syncDao.getBindings()
                .filter { it.entityType == SyncV2EntityType.QUICK_MEMO.name }
                .associateBy { it.localId }
            memos.forEach { memo ->
                val localId = memo.id ?: return@forEach
                val binding = memoBindings[localId] ?: return@forEach
                val refs = buildQuickMemoAttachmentRefs(binding.recordKey, memo, encryptionKey)
                pruneAssetLinks(binding.recordKey, refs)
                recordLocalPayload(binding, codec.encodePayload(memo.toPortable()), refs, deviceId)
            }

            val liveIds = mapOf(
                SyncV2EntityType.EVENT.name to events.mapNotNull { it.id }.toSet(),
                SyncV2EntityType.QUICK_MEMO.name to memos.mapNotNull { it.id }.toSet(),
            )
            syncDao.getBindings().forEach { binding ->
                val localId = binding.localId ?: return@forEach
                if (localId in liveIds[binding.entityType].orEmpty()) return@forEach
                val selected = syncDao.getRevision(binding.selectedRevisionId)
                if (selected?.status == SyncV2RecordStatus.DELETED.name) return@forEach
                recordLocalPayload(binding, "", emptyList(), deviceId, SyncV2RecordStatus.DELETED)
            }
            val revisions = syncDao.getAllRevisions()
            Log.d(
                TAG,
                "local scan complete revisions=${revisions.size} " +
                    "eventRevisions=${revisions.count { it.entityType == SyncV2EntityType.EVENT.name }} " +
                    "quickMemoRevisions=${revisions.count { it.entityType == SyncV2EntityType.QUICK_MEMO.name }}",
            )
        }
    }

    suspend fun mergeRemoteRecords(records: List<SyncV2Record>, localDeviceId: String): Int {
        var conflicts = 0
        database.withTransaction {
            Log.i(
                TAG,
                "merge start incoming=${records.size} " +
                    "events=${records.count { it.entityType == SyncV2EntityType.EVENT.name }} " +
                    "quickMemos=${records.count { it.entityType == SyncV2EntityType.QUICK_MEMO.name }} " +
                    "deleted=${records.count { it.status == SyncV2RecordStatus.DELETED.name }}",
            )
            val allowInitialDeduplication = syncDao.getMeta(META_GENERATION) == null
            records.groupBy { recordKey(it.entityType, it.syncUuid) }.forEach { (recordKey, incomingRecords) ->
                if (allowInitialDeduplication && syncDao.getBinding(recordKey) == null) {
                    SyncV2VersionPolicy.deterministicWinner(incomingRecords)
                        ?.takeIf { it.status == SyncV2RecordStatus.ACTIVE.name }
                        ?.let { adoptMatchingInitialBinding(recordKey, it, localDeviceId) }
                }
                var heads = syncDao.getRevisions(recordKey).map(::toRecord).toMutableList()
                incomingRecords.forEach { incoming ->
                    if (heads.any { it.revisionId == incoming.revisionId }) return@forEach
                    val relations = heads.associateWith { local ->
                        SyncV2VersionPolicy.compare(local.versionVector, incoming.versionVector)
                    }
                    if (relations.values.any { it == SyncV2VersionRelation.LOCAL_NEWER || it == SyncV2VersionRelation.EQUAL }) {
                        return@forEach
                    }
                    val dominatedIds = relations.filterValues { it == SyncV2VersionRelation.REMOTE_NEWER }.keys.map { it.revisionId }
                    if (dominatedIds.isNotEmpty()) {
                        syncDao.deleteRevisions(dominatedIds)
                        heads.removeAll { it.revisionId in dominatedIds }
                    }
                    syncDao.putRevision(toEntity(incoming))
                    heads.add(incoming)
                }
                val selected = SyncV2VersionPolicy.deterministicWinner(heads) ?: return@forEach
                val binding = syncDao.getBinding(recordKey) ?: SyncV2BindingEntity(
                    recordKey = recordKey,
                    entityType = selected.entityType,
                    syncUuid = selected.syncUuid,
                    localId = null,
                )
                if (syncDao.getBinding(recordKey) == null) syncDao.putBinding(binding)
            }
            syncDao.getBindings()
                .mapNotNull { binding ->
                    val selected = SyncV2VersionPolicy.deterministicWinner(
                        syncDao.getRevisions(binding.recordKey).map(::toRecord)
                    ) ?: return@mapNotNull null
                    binding to selected
                }
                .sortedBy { (_, record) -> applyPriority(record) }
                .forEach { (binding, selected) ->
                    val reason = selectionApplyReason(binding, selected) ?: return@forEach
                    Log.i(
                        TAG,
                        "apply selected entity=${selected.entityType} status=${selected.status} " +
                            "sync=${selected.syncUuid.shortLogId()} reason=$reason localId=${binding.localId}",
                    )
                    applySelected(binding, selected)
                    val appliedBinding = syncDao.getBinding(binding.recordKey)
                    Log.i(
                        TAG,
                        "apply complete entity=${selected.entityType} sync=${selected.syncUuid.shortLogId()} " +
                            "localId=${appliedBinding?.localId} selected=${appliedBinding?.selectedRevisionId?.shortLogId()}",
                    )
                }
            conflicts = syncDao.getAllRevisions()
                .groupBy { it.recordKey }
                .values
                .sumOf { revisions -> (revisions.size - 1).coerceAtLeast(0) }
            val revisions = syncDao.getAllRevisions()
            Log.i(
                TAG,
                "merge complete localQuickMemos=${quickMemoDao.getAllQuickMemos().size} " +
                    "quickMemoRevisions=${revisions.count { it.entityType == SyncV2EntityType.QUICK_MEMO.name }} " +
                    "bindings=${syncDao.getBindings().size} conflicts=$conflicts",
            )
            }
        return conflicts
    }

    private suspend fun selectionApplyReason(
        binding: SyncV2BindingEntity,
        selected: SyncV2Record,
    ): String? {
        if (binding.selectedRevisionId != selected.revisionId) return "revision_changed"
        if (selected.status == SyncV2RecordStatus.DELETED.name) {
            return if (binding.localId != null) "deleted_binding_still_attached" else null
        }
        val localId = binding.localId ?: return "active_binding_missing_local_id"
        return when (selected.entityType) {
            SyncV2EntityType.EVENT.name -> if (eventsDao.getEventOrTaskWithId(localId) == null) {
                "event_row_missing"
            } else {
                null
            }
            SyncV2EntityType.QUICK_MEMO.name -> if (quickMemoDao.getQuickMemo(localId) == null) {
                "quick_memo_row_missing"
            } else {
                null
            }
            else -> null
        }
    }

    private fun adoptMatchingInitialBinding(
        targetRecordKey: String,
        incoming: SyncV2Record,
        localDeviceId: String,
    ) {
        val incomingFingerprint = initialDeduplicationFingerprint(incoming) ?: return
        val candidate = syncDao.getBindings()
            .asSequence()
            .filter { it.entityType == incoming.entityType && it.localId != null && it.recordKey != targetRecordKey }
            .mapNotNull { binding ->
                val revision = syncDao.getRevision(binding.selectedRevisionId)?.let(::toRecord) ?: return@mapNotNull null
                if (revision.modifiedByDeviceId != localDeviceId || revision.status != SyncV2RecordStatus.ACTIVE.name) {
                    return@mapNotNull null
                }
                if (initialDeduplicationFingerprint(revision) != incomingFingerprint) return@mapNotNull null
                binding to revision
            }
            .filterNot { (binding, _) -> hasDependentEvent(binding.syncUuid) }
            .sortedBy { (binding, _) -> binding.localId }
            .firstOrNull()
            ?: return
        val binding = candidate.first
        val links = syncDao.getAssetLinks(binding.recordKey)
        syncDao.deleteRevisions(binding.recordKey)
        syncDao.deleteBinding(binding.recordKey)
        syncDao.deleteAssetLinks(binding.recordKey)
        syncDao.putBinding(
            binding.copy(
                recordKey = targetRecordKey,
                syncUuid = incoming.syncUuid,
                selectedRevisionId = "",
                observedPayloadHash = "",
            )
        )
        links.forEach { link -> syncDao.putAssetLink(link.copy(recordKey = targetRecordKey)) }
    }

    private fun hasDependentEvent(parentSyncUuid: String): Boolean = syncDao.getAllRevisions().any { entity ->
        if (entity.entityType != SyncV2EntityType.EVENT.name || entity.status != SyncV2RecordStatus.ACTIVE.name) {
            return@any false
        }
        runCatching {
            codec.decodePayload(entity.payloadJson, SyncV2EventPayload::class.java).parentSyncUuid == parentSyncUuid
        }.getOrDefault(false)
    }

    private fun initialDeduplicationFingerprint(record: SyncV2Record): String? = runCatching {
        when (record.entityType) {
            SyncV2EntityType.EVENT.name -> {
                val payload = codec.decodePayload(record.payloadJson, SyncV2EventPayload::class.java)
                listOf(
                    payload.title.trim(),
                    payload.startTS.toString(),
                    payload.endTS.toString(),
                    payload.rrule.trim(),
                    payload.parentSyncUuid?.let { "child" }.orEmpty(),
                ).joinToString("\u001f")
            }
            SyncV2EntityType.QUICK_MEMO.name -> {
                val payload = codec.decodePayload(record.payloadJson, SyncV2QuickMemoPayload::class.java)
                listOf(
                    payload.type,
                    payload.bodyText.trim(),
                    payload.createdAt.toString(),
                ).joinToString("\u001f")
            }
            else -> return null
        }
    }.getOrNull()

    private fun applyPriority(record: SyncV2Record): Int {
        if (record.status == SyncV2RecordStatus.DELETED.name) return 3
        if (record.entityType == SyncV2EntityType.QUICK_MEMO.name) return 2
        val parent = runCatching {
            codec.decodePayload(record.payloadJson, SyncV2EventPayload::class.java).parentSyncUuid
        }.getOrNull()
        return if (parent == null) 0 else 1
    }

    fun exportRecords(): List<SyncV2Record> = syncDao.getAllRevisions().map(::toRecord)

    fun getAssetLinks(): List<SyncV2AssetLinkEntity> = syncDao.getAssetLinks()

    fun getPendingAssetLinks(): List<SyncV2AssetLinkEntity> = syncDao.getAssetLinks().filter { it.downloadPending }

    fun getPeer(deviceId: String): SyncV2PeerEntity? = syncDao.getPeer(deviceId)

    fun putPeer(peer: SyncV2PeerEntity) = syncDao.putPeer(peer)

    fun getMeta(key: String): String? = syncDao.getMeta(key)

    fun putMeta(key: String, value: String) = syncDao.putMeta(SyncV2MetaEntity(key, value))

    fun prepareVault(vaultId: String) {
        if (syncDao.getMeta(META_VAULT_ID) != vaultId) {
            resetRemoteTracking()
            putMeta(META_VAULT_ID, vaultId)
        }
        if (syncDao.getMeta(META_ASSET_KEY_VAULT_ID) != vaultId) {
            syncDao.clearAssetLinks()
            putMeta(META_ASSET_KEY_VAULT_ID, vaultId)
        }
    }

    fun resetRemoteTracking() {
        syncDao.clearPeers()
        syncDao.clearMeta(listOf(META_PUBLISHED_HASH, META_GENERATION))
    }

    suspend fun attachDownloadedAsset(link: SyncV2AssetLinkEntity, localFile: File) {
        database.withTransaction {
            val updated = link.copy(
                localPath = localFile.absolutePath,
                fileLastModified = localFile.lastModified(),
                downloadPending = false,
            )
            val binding = syncDao.getBinding(link.recordKey)
            when (binding?.entityType) {
                SyncV2EntityType.EVENT.name -> {
                    val eventId = binding.localId ?: return@withTransaction
                    val existing = link.localAttachmentId?.let { id -> attachmentsDao.getAttachmentsByIds(listOf(id)).firstOrNull() }
                    val attachment = EventAttachment(
                        id = existing?.id,
                        eventId = eventId,
                        eventKey = "",
                        localPath = localFile.absolutePath,
                        displayName = link.displayName,
                        mimeType = link.mimeType,
                        sizeBytes = link.plainSize,
                        source = link.source,
                        createdAt = link.createdAt,
                    )
                    val attachmentId = attachmentsDao.insertOrUpdate(attachment)
                    syncDao.putAssetLink(updated.copy(localAttachmentId = attachmentId))
                }
                SyncV2EntityType.QUICK_MEMO.name -> {
                    val memoId = binding.localId ?: return@withTransaction
                    val memo = quickMemoDao.getQuickMemo(memoId) ?: return@withTransaction
                    val next = when (link.role) {
                        SyncV2AssetRole.QUICK_MEMO_AUDIO.name -> memo.copy(audioPath = localFile.absolutePath)
                        SyncV2AssetRole.QUICK_MEMO_IMAGE.name -> memo.copy(imagePath = localFile.absolutePath)
                        else -> memo
                    }
                    quickMemoDao.updateQuickMemo(next)
                    syncDao.putAssetLink(updated)
                    Log.i(
                        TAG,
                        "quick memo asset attached memoId=$memoId role=${link.role} key=${link.assetKey.take(8)}",
                    )
                }
            }
        }
    }

    private fun ensureBindings(type: SyncV2EntityType, localIds: List<Long>) {
        localIds.forEach { localId ->
            if (syncDao.getBindingByLocalId(type.name, localId) != null) return@forEach
            val syncUuid = UUID.randomUUID().toString()
            syncDao.putBinding(
                SyncV2BindingEntity(
                    recordKey = recordKey(type.name, syncUuid),
                    entityType = type.name,
                    syncUuid = syncUuid,
                    localId = localId,
                )
            )
        }
    }

    private fun recordLocalPayload(
        binding: SyncV2BindingEntity,
        payloadJson: String,
        attachments: List<SyncV2AttachmentRef>,
        deviceId: String,
        status: SyncV2RecordStatus = SyncV2RecordStatus.ACTIVE,
    ) {
        val payloadHash = codec.payloadHash(payloadJson, attachments)
        if (binding.observedPayloadHash == payloadHash && binding.selectedRevisionId.isNotBlank()) return
        val heads = syncDao.getRevisions(binding.recordKey).map(::toRecord)
        val revision = SyncV2Record(
            entityType = binding.entityType,
            syncUuid = binding.syncUuid,
            revisionId = UUID.randomUUID().toString(),
            parentRevisionIds = heads.map { it.revisionId }.sorted(),
            versionVector = SyncV2VersionPolicy.next(heads.map { it.versionVector }, deviceId),
            modifiedAt = System.currentTimeMillis(),
            modifiedByDeviceId = deviceId,
            status = status.name,
            payloadJson = payloadJson,
            attachments = attachments,
        )
        if (heads.isNotEmpty()) syncDao.deleteRevisions(heads.map { it.revisionId })
        syncDao.putRevision(toEntity(revision))
        syncDao.putBinding(
            binding.copy(
                localId = if (status == SyncV2RecordStatus.DELETED) null else binding.localId,
                selectedRevisionId = revision.revisionId,
                observedPayloadHash = payloadHash,
            )
        )
        if (status == SyncV2RecordStatus.DELETED) {
            syncDao.deleteAssetLinks(binding.recordKey)
        }
        Log.i(
            TAG,
            "local revision created entity=${binding.entityType} status=${status.name} " +
                "sync=${binding.syncUuid.shortLogId()} localId=${binding.localId} attachments=${attachments.size}",
        )
    }

    private suspend fun applySelected(binding: SyncV2BindingEntity, selected: SyncV2Record) {
        val payloadHash = codec.payloadHash(selected.payloadJson, selected.attachments)
        if (selected.status == SyncV2RecordStatus.DELETED.name) {
            binding.localId?.let { localId ->
                when (binding.entityType) {
                    SyncV2EntityType.EVENT.name -> {
                        attachmentsDao.deleteAttachmentsForEvent(localId)
                        scheduleStore.deleteRemoteEvent(localId)
                    }
                    SyncV2EntityType.QUICK_MEMO.name -> quickMemoDao.deleteQuickMemoById(localId)
                }
            }
            syncDao.deleteAssetLinks(binding.recordKey)
            syncDao.putBinding(binding.copy(localId = null, selectedRevisionId = selected.revisionId, observedPayloadHash = payloadHash))
            return
        }

        val localId = when (selected.entityType) {
            SyncV2EntityType.EVENT.name -> applyEvent(binding, selected)
            SyncV2EntityType.QUICK_MEMO.name -> applyQuickMemo(binding, selected)
            else -> binding.localId
        }
        if (selected.entityType == SyncV2EntityType.EVENT.name && localId != null) {
            reconcileEventAttachments(binding.recordKey, localId, selected.attachments)
        }
        syncDao.putBinding(binding.copy(localId = localId, selectedRevisionId = selected.revisionId, observedPayloadHash = payloadHash))
        replaceAssetLinks(binding.recordKey, selected.attachments)
    }

    private fun applyEvent(binding: SyncV2BindingEntity, selected: SyncV2Record): Long {
        val payload = codec.decodePayload(selected.payloadJson, SyncV2EventPayload::class.java)
        val existing = binding.localId?.let(eventsDao::getEventOrTaskWithId)
        val parentId = payload.parentSyncUuid?.let { parentUuid ->
            syncDao.getBinding(recordKey(SyncV2EntityType.EVENT.name, parentUuid))?.localId
        } ?: 0L
        val event = Event(
            id = existing?.id,
            startTS = payload.startTS,
            endTS = payload.endTS,
            title = payload.title,
            location = payload.location,
            description = payload.description,
            reminder1Minutes = payload.reminder1Minutes,
            reminder2Minutes = payload.reminder2Minutes,
            reminder3Minutes = payload.reminder3Minutes,
            reminder1Type = payload.reminder1Type,
            reminder2Type = payload.reminder2Type,
            reminder3Type = payload.reminder3Type,
            rrule = payload.rrule,
            exdates = payload.exdates,
            attendees = payload.attendees,
            importId = existing?.importId.orEmpty(),
            timeZone = payload.timeZone,
            flags = payload.flags,
            eventType = payload.eventType,
            parentId = parentId,
            lastUpdated = payload.lastUpdated,
            source = existing?.source ?: SOURCE_SIMPLE_CALENDAR,
            availability = payload.availability,
            color = payload.color,
            type = payload.type,
            state = payload.state,
            tag = payload.tag,
            archivedAt = payload.archivedAt,
            codeQrPayload = payload.codeQrPayload,
        )
        return scheduleStore.applyRemoteEvent(event)
    }

    private suspend fun applyQuickMemo(binding: SyncV2BindingEntity, selected: SyncV2Record): Long {
        val payload = codec.decodePayload(selected.payloadJson, SyncV2QuickMemoPayload::class.java)
        val existing = binding.localId?.let { id -> quickMemoDao.getQuickMemo(id) }
        val oldLinks = syncDao.getAssetLinks(binding.recordKey).associateBy { it.role }
        fun reusablePath(role: SyncV2AssetRole): String? {
            val ref = selected.attachments.firstOrNull { it.role == role.name } ?: return null
            return oldLinks[role.name]?.takeIf { it.matches(ref) }?.localPath
        }
        val memo = QuickMemoEntity(
            id = existing?.id,
            type = payload.type,
            bodyText = payload.bodyText,
            audioPath = reusablePath(SyncV2AssetRole.QUICK_MEMO_AUDIO),
            imagePath = reusablePath(SyncV2AssetRole.QUICK_MEMO_IMAGE),
            audioDurationMs = payload.audioDurationMs,
            transcriptionStatus = payload.transcriptionStatus,
            analysisStatus = payload.analysisStatus,
            createdAt = payload.createdAt,
            updatedAt = payload.updatedAt,
            sortRank = payload.sortRank,
            todoState = payload.todoState,
            todoPendingUntil = payload.todoPendingUntil,
            todoCompletedAt = payload.todoCompletedAt,
        )
        val localId = quickMemoDao.insertQuickMemo(memo)
        Log.i(
            TAG,
            "quick memo persisted sync=${selected.syncUuid.shortLogId()} previousLocalId=${existing?.id} " +
                "resultLocalId=$localId type=${payload.type} attachments=${selected.attachments.size}",
        )
        return localId
    }

    private fun replaceAssetLinks(recordKey: String, refs: List<SyncV2AttachmentRef>) {
        val existingLinks = syncDao.getAssetLinks(recordKey)
        val existingByUuid = existingLinks.associateBy { it.attachmentUuid }
        syncDao.deleteAssetLinks(recordKey)
        refs.forEach { ref ->
            val old = existingByUuid[ref.attachmentUuid]
                ?: existingLinks.firstOrNull { it.role == ref.role && it.assetKey == ref.assetKey }
            val reusable = old?.takeIf { it.matches(ref) }
            syncDao.putAssetLink(
                SyncV2AssetLinkEntity(
                    attachmentUuid = ref.attachmentUuid,
                    recordKey = recordKey,
                    role = ref.role,
                    localAttachmentId = reusable?.localAttachmentId,
                    localPath = reusable?.localPath.orEmpty(),
                    assetKey = ref.assetKey,
                    displayName = ref.displayName,
                    mimeType = ref.mimeType,
                    plainSize = ref.plainSize,
                    plainSha256 = ref.plainSha256,
                    source = ref.source,
                    createdAt = ref.createdAt,
                    fileLastModified = reusable?.fileLastModified ?: 0L,
                    downloadPending = reusable == null,
                )
            )
        }
    }

    private fun reconcileEventAttachments(
        recordKey: String,
        eventId: Long,
        refs: List<SyncV2AttachmentRef>,
    ) {
        val refsByUuid = refs.associateBy { it.attachmentUuid }
        val linksByLocalId = syncDao.getAssetLinks(recordKey)
            .mapNotNull { link -> link.localAttachmentId?.let { it to link } }
            .toMap()
        attachmentsDao.getAttachmentsForEvent(eventId).forEach { attachment ->
            val id = attachment.id ?: return@forEach
            val link = linksByLocalId[id]
            val ref = link?.let { refsByUuid[it.attachmentUuid] }
                ?: link?.let { current -> refs.firstOrNull { it.role == current.role && it.assetKey == current.assetKey } }
            if (link == null || ref == null || !link.matches(ref)) {
                attachmentsDao.deleteAttachment(id)
            }
        }
    }

    private fun pruneAssetLinks(recordKey: String, refs: List<SyncV2AttachmentRef>) {
        val retained = refs.mapTo(hashSetOf()) { it.attachmentUuid }
        syncDao.getAssetLinks(recordKey)
            .filter { it.attachmentUuid !in retained }
            .forEach { syncDao.deleteAssetLink(it.attachmentUuid) }
    }

    private fun buildEventAttachmentRefs(recordKey: String, eventId: Long, key: ByteArray): List<SyncV2AttachmentRef> {
        return attachmentManager.getAttachments(eventId).mapNotNull { attachment ->
            val localId = attachment.id ?: return@mapNotNull null
            val existing = syncDao.getAssetLinkByLocalAttachmentId(localId)
            val link = buildLocalAssetLink(
                existing = existing,
                recordKey = recordKey,
                role = SyncV2AssetRole.EVENT_ATTACHMENT.name,
                localAttachmentId = localId,
                localPath = attachment.localPath,
                displayName = attachment.displayName,
                mimeType = attachment.mimeType,
                source = attachment.source,
                createdAt = attachment.createdAt,
                key = key,
            ) ?: return@mapNotNull existing?.toRef()
            syncDao.putAssetLink(link)
            link.toRef()
        }
    }

    private fun buildQuickMemoAttachmentRefs(recordKey: String, memo: QuickMemoEntity, key: ByteArray): List<SyncV2AttachmentRef> {
        return listOfNotNull(
            memo.audioPath?.takeIf { it.isNotBlank() }?.let { path ->
                buildMemoAssetLink(recordKey, SyncV2AssetRole.QUICK_MEMO_AUDIO, path, key)
            },
            memo.imagePath?.takeIf { it.isNotBlank() }?.let { path ->
                buildMemoAssetLink(recordKey, SyncV2AssetRole.QUICK_MEMO_IMAGE, path, key)
            },
        ).map { link -> syncDao.putAssetLink(link); link.toRef() }
    }

    private fun buildMemoAssetLink(
        recordKey: String,
        role: SyncV2AssetRole,
        path: String,
        key: ByteArray,
    ): SyncV2AssetLinkEntity? {
        val current = syncDao.getAssetLinkByRole(recordKey, role.name)?.takeIf { it.localPath == path }
        val file = File(path)
        return buildLocalAssetLink(
            existing = current,
            recordKey = recordKey,
            role = role.name,
            localAttachmentId = null,
            localPath = path,
            displayName = file.name,
            mimeType = EventAttachmentManager.inferMimeType(file.name),
            source = "quick_memo",
            createdAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
            key = key,
        ) ?: current
    }

    private fun buildLocalAssetLink(
        existing: SyncV2AssetLinkEntity?,
        recordKey: String,
        role: String,
        localAttachmentId: Long?,
        localPath: String,
        displayName: String,
        mimeType: String,
        source: String,
        createdAt: Long,
        key: ByteArray,
    ): SyncV2AssetLinkEntity? {
        val file = File(localPath)
        if (!file.isFile) return null
        if (existing != null && existing.localPath == file.absolutePath && existing.plainSize == file.length() &&
            existing.fileLastModified == file.lastModified() && existing.assetKey.isNotBlank()
        ) return existing.copy(downloadPending = false)
        val bytes = file.readBytes()
        return SyncV2AssetLinkEntity(
            attachmentUuid = existing?.attachmentUuid ?: UUID.randomUUID().toString(),
            recordKey = recordKey,
            role = role,
            localAttachmentId = localAttachmentId,
            localPath = file.absolutePath,
            assetKey = codec.assetKey(key, bytes),
            displayName = displayName.ifBlank { file.name },
            mimeType = mimeType,
            plainSize = bytes.size.toLong(),
            plainSha256 = codec.sha256(bytes),
            source = source,
            createdAt = createdAt,
            fileLastModified = file.lastModified(),
            downloadPending = false,
        )
    }

    private fun Event.toPortable(parentSyncUuid: String?) = SyncV2EventPayload(
        parentSyncUuid = parentSyncUuid,
        startTS = startTS,
        endTS = endTS,
        title = title,
        location = location,
        description = description,
        reminder1Minutes = reminder1Minutes,
        reminder2Minutes = reminder2Minutes,
        reminder3Minutes = reminder3Minutes,
        reminder1Type = reminder1Type,
        reminder2Type = reminder2Type,
        reminder3Type = reminder3Type,
        rrule = rrule,
        exdates = exdates,
        attendees = attendees,
        timeZone = timeZone,
        flags = flags,
        eventType = eventType,
        lastUpdated = lastUpdated,
        availability = availability,
        color = color,
        type = type,
        state = state,
        tag = tag,
        archivedAt = archivedAt,
        codeQrPayload = codeQrPayload,
    )

    private fun QuickMemoEntity.toPortable() = SyncV2QuickMemoPayload(
        type = type,
        bodyText = bodyText,
        audioDurationMs = audioDurationMs,
        transcriptionStatus = transcriptionStatus,
        analysisStatus = analysisStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortRank = sortRank,
        todoState = todoState,
        todoPendingUntil = todoPendingUntil,
        todoCompletedAt = todoCompletedAt,
    )

    private fun SyncV2AssetLinkEntity.toRef() = SyncV2AttachmentRef(
        attachmentUuid = attachmentUuid,
        assetKey = assetKey,
        role = role,
        displayName = displayName,
        mimeType = mimeType,
        plainSize = plainSize,
        plainSha256 = plainSha256,
        source = source,
        createdAt = createdAt,
    )

    private fun SyncV2AssetLinkEntity.matches(ref: SyncV2AttachmentRef): Boolean =
        assetKey == ref.assetKey &&
            plainSha256 == ref.plainSha256 &&
            plainSize == ref.plainSize &&
            localPath.isNotBlank() &&
            File(localPath).isFile

    private fun toRecord(entity: SyncV2RevisionEntity) = SyncV2Record(
        entityType = entity.entityType,
        syncUuid = entity.syncUuid,
        revisionId = entity.revisionId,
        parentRevisionIds = codec.decodeStringList(entity.parentRevisionIdsJson),
        versionVector = codec.decodeVector(entity.versionVectorJson),
        modifiedAt = entity.modifiedAt,
        modifiedByDeviceId = entity.modifiedByDeviceId,
        status = entity.status,
        payloadJson = entity.payloadJson,
        attachments = codec.decodeAttachments(entity.attachmentsJson),
    )

    private fun toEntity(record: SyncV2Record) = SyncV2RevisionEntity(
        revisionId = record.revisionId,
        recordKey = recordKey(record.entityType, record.syncUuid),
        entityType = record.entityType,
        syncUuid = record.syncUuid,
        parentRevisionIdsJson = codec.encodeStringList(record.parentRevisionIds),
        versionVectorJson = codec.encodeVector(record.versionVector),
        modifiedAt = record.modifiedAt,
        modifiedByDeviceId = record.modifiedByDeviceId,
        status = record.status,
        payloadJson = record.payloadJson,
        attachmentsJson = codec.encodeAttachments(record.attachments),
    )

    companion object {
        private const val TAG = "WebDavSyncV2"
        const val META_PUBLISHED_HASH = "published_state_hash"
        const val META_GENERATION = "published_generation"
        const val META_VAULT_ID = "vault_id"
        const val META_ASSET_KEY_VAULT_ID = "asset_key_vault_id"
        fun recordKey(entityType: String, syncUuid: String): String = "$entityType:$syncUuid"
    }

    private fun String.shortLogId(): String = take(8)
}
