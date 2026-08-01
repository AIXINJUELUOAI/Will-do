package com.antgskds.calendarassistant.feature.cloudsync.domain

import com.antgskds.calendarassistant.feature.schedule.domain.model.Attendee

const val SYNC_V2_PROTOCOL_VERSION = 2

enum class SyncV2EntityType {
    EVENT,
    QUICK_MEMO,
}

enum class SyncV2RecordStatus {
    ACTIVE,
    DELETED,
}

enum class SyncV2AssetRole {
    EVENT_ATTACHMENT,
    QUICK_MEMO_AUDIO,
    QUICK_MEMO_IMAGE,
}

data class SyncV2AttachmentRef(
    val attachmentUuid: String,
    val assetKey: String,
    val role: String,
    val displayName: String,
    val mimeType: String,
    val plainSize: Long,
    val plainSha256: String,
    val source: String = "",
    val createdAt: Long = 0L,
)

data class SyncV2Record(
    val entityType: String,
    val syncUuid: String,
    val revisionId: String,
    val parentRevisionIds: List<String>,
    val versionVector: Map<String, Long>,
    val modifiedAt: Long,
    val modifiedByDeviceId: String,
    val status: String,
    val payloadJson: String,
    val attachments: List<SyncV2AttachmentRef> = emptyList(),
)

data class SyncV2DeviceState(
    val protocolVersion: Int = SYNC_V2_PROTOCOL_VERSION,
    val deviceId: String,
    val generation: Long,
    val generatedAt: Long,
    val records: List<SyncV2Record>,
)

data class SyncV2VaultConfig(
    val protocolVersion: Int = SYNC_V2_PROTOCOL_VERSION,
    val vaultId: String,
    val saltBase64: String,
    val iterations: Int,
    val keyVerifierBase64: String,
    val createdAt: Long,
)

data class SyncV2EventPayload(
    val parentSyncUuid: String?,
    val startTS: Long,
    val endTS: Long,
    val title: String,
    val location: String,
    val description: String,
    val reminder1Minutes: Int,
    val reminder2Minutes: Int,
    val reminder3Minutes: Int,
    val reminder1Type: Int,
    val reminder2Type: Int,
    val reminder3Type: Int,
    val rrule: String,
    val exdates: List<String>,
    val attendees: List<Attendee>,
    val timeZone: String,
    val flags: Int,
    val eventType: Long,
    val lastUpdated: Long,
    val availability: Int,
    val color: Int,
    val type: Int,
    val state: Int,
    val tag: String,
    val archivedAt: Long?,
    val codeQrPayload: String,
)

data class SyncV2QuickMemoPayload(
    val type: String,
    val bodyText: String,
    val audioDurationMs: Long,
    val transcriptionStatus: String,
    val analysisStatus: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sortRank: Long,
    val todoState: String,
    val todoPendingUntil: Long?,
    val todoCompletedAt: Long?,
)

enum class SyncV2RunPhase {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    MERGING,
    DOWNLOADING_ASSETS,
    UPLOADING_ASSETS,
    PUBLISHING,
    SUCCESS,
    FAILED,
}

data class SyncV2RuntimeStatus(
    val phase: SyncV2RunPhase = SyncV2RunPhase.IDLE,
    val message: String = "尚未同步",
    val lastSuccessAt: Long = 0L,
    val pendingAssetCount: Int = 0,
    val conflictCount: Int = 0,
)
