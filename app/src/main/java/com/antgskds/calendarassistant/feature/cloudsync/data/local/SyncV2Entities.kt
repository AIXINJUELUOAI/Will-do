package com.antgskds.calendarassistant.feature.cloudsync.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_v2_bindings",
    indices = [
        Index(value = ["entity_type", "local_id"], unique = true),
        Index(value = ["entity_type", "sync_uuid"], unique = true),
    ]
)
data class SyncV2BindingEntity(
    @PrimaryKey @ColumnInfo(name = "record_key") val recordKey: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "sync_uuid") val syncUuid: String,
    @ColumnInfo(name = "local_id") val localId: Long?,
    @ColumnInfo(name = "selected_revision_id") val selectedRevisionId: String = "",
    @ColumnInfo(name = "observed_payload_hash") val observedPayloadHash: String = "",
)

@Entity(
    tableName = "sync_v2_revisions",
    indices = [Index(value = ["record_key"]), Index(value = ["sync_uuid"])]
)
data class SyncV2RevisionEntity(
    @PrimaryKey @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "record_key") val recordKey: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "sync_uuid") val syncUuid: String,
    @ColumnInfo(name = "parent_revision_ids_json") val parentRevisionIdsJson: String,
    @ColumnInfo(name = "version_vector_json") val versionVectorJson: String,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "modified_by_device_id") val modifiedByDeviceId: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "attachments_json") val attachmentsJson: String,
)

@Entity(
    tableName = "sync_v2_asset_links",
    indices = [Index(value = ["record_key"]), Index(value = ["local_attachment_id"])]
)
data class SyncV2AssetLinkEntity(
    @PrimaryKey @ColumnInfo(name = "attachment_uuid") val attachmentUuid: String,
    @ColumnInfo(name = "record_key") val recordKey: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "local_attachment_id") val localAttachmentId: Long?,
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "asset_key") val assetKey: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "plain_size") val plainSize: Long,
    @ColumnInfo(name = "plain_sha256") val plainSha256: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "file_last_modified") val fileLastModified: Long,
    @ColumnInfo(name = "download_pending") val downloadPending: Boolean,
)

@Entity(tableName = "sync_v2_peers")
data class SyncV2PeerEntity(
    @PrimaryKey @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "etag") val etag: String,
    @ColumnInfo(name = "generation") val generation: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
)

@Entity(tableName = "sync_v2_meta")
data class SyncV2MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
