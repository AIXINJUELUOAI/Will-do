package com.antgskds.calendarassistant.feature.cloudsync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncV2Dao {
    @Query("SELECT * FROM sync_v2_bindings")
    fun getBindings(): List<SyncV2BindingEntity>

    @Query("SELECT * FROM sync_v2_bindings WHERE record_key = :recordKey LIMIT 1")
    fun getBinding(recordKey: String): SyncV2BindingEntity?

    @Query("SELECT * FROM sync_v2_bindings WHERE entity_type = :entityType AND local_id = :localId LIMIT 1")
    fun getBindingByLocalId(entityType: String, localId: Long): SyncV2BindingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putBinding(binding: SyncV2BindingEntity)

    @Query("DELETE FROM sync_v2_bindings WHERE record_key = :recordKey")
    fun deleteBinding(recordKey: String)

    @Query("SELECT * FROM sync_v2_revisions")
    fun getAllRevisions(): List<SyncV2RevisionEntity>

    @Query("SELECT * FROM sync_v2_revisions WHERE record_key = :recordKey")
    fun getRevisions(recordKey: String): List<SyncV2RevisionEntity>

    @Query("SELECT * FROM sync_v2_revisions WHERE revision_id = :revisionId LIMIT 1")
    fun getRevision(revisionId: String): SyncV2RevisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putRevision(revision: SyncV2RevisionEntity)

    @Query("DELETE FROM sync_v2_revisions WHERE revision_id IN (:revisionIds)")
    fun deleteRevisions(revisionIds: List<String>)

    @Query("DELETE FROM sync_v2_revisions WHERE record_key = :recordKey")
    fun deleteRevisions(recordKey: String)

    @Query("SELECT * FROM sync_v2_asset_links")
    fun getAssetLinks(): List<SyncV2AssetLinkEntity>

    @Query("SELECT * FROM sync_v2_asset_links WHERE record_key = :recordKey")
    fun getAssetLinks(recordKey: String): List<SyncV2AssetLinkEntity>

    @Query("SELECT * FROM sync_v2_asset_links WHERE local_attachment_id = :localAttachmentId LIMIT 1")
    fun getAssetLinkByLocalAttachmentId(localAttachmentId: Long): SyncV2AssetLinkEntity?

    @Query("SELECT * FROM sync_v2_asset_links WHERE record_key = :recordKey AND role = :role LIMIT 1")
    fun getAssetLinkByRole(recordKey: String, role: String): SyncV2AssetLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putAssetLink(link: SyncV2AssetLinkEntity)

    @Query("DELETE FROM sync_v2_asset_links WHERE attachment_uuid = :attachmentUuid")
    fun deleteAssetLink(attachmentUuid: String)

    @Query("DELETE FROM sync_v2_asset_links WHERE record_key = :recordKey")
    fun deleteAssetLinks(recordKey: String)

    @Query("DELETE FROM sync_v2_asset_links")
    fun clearAssetLinks()

    @Query("SELECT * FROM sync_v2_peers WHERE device_id = :deviceId LIMIT 1")
    fun getPeer(deviceId: String): SyncV2PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putPeer(peer: SyncV2PeerEntity)

    @Query("SELECT value FROM sync_v2_meta WHERE `key` = :key LIMIT 1")
    fun getMeta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putMeta(meta: SyncV2MetaEntity)

    @Query("DELETE FROM sync_v2_peers")
    fun clearPeers()

    @Query("DELETE FROM sync_v2_meta WHERE `key` IN (:keys)")
    fun clearMeta(keys: List<String>)
}
