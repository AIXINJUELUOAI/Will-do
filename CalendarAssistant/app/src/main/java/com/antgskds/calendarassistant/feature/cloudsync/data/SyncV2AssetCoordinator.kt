package com.antgskds.calendarassistant.feature.cloudsync.data

import android.content.Context
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionConfig
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavCredentials
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavRemoteStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException

class SyncV2AssetCoordinator(
    context: Context,
    private val remoteStore: WebDavRemoteStore,
    private val localRepository: SyncV2LocalRepository,
    private val codec: SyncV2Codec,
) {
    private val localAssetDir = File(context.applicationContext.filesDir, "webdav_sync_assets_v2").apply { mkdirs() }

    suspend fun uploadMissingAssets(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        key: ByteArray,
    ) {
        val remoteNames = remoteStore.listDirectory(config, credentials, ASSET_DIRECTORY)
            .filterNot { it.isDirectory }
            .map { it.name }
            .toHashSet()
        val candidates = localRepository.getAssetLinks()
            .filter { !it.downloadPending && it.assetKey.isNotBlank() }
            .distinctBy { it.assetKey }
        var uploaded = 0
        var alreadyRemote = 0
        var missingLocal = 0
        candidates.forEach { link ->
            val finalName = "${link.assetKey}.bin.enc"
            if (finalName in remoteNames) {
                alreadyRemote += 1
                return@forEach
            }
            val file = File(link.localPath)
            if (!file.isFile) {
                missingLocal += 1
                Log.w(TAG, "asset upload skipped missingLocal role=${link.role} key=${link.assetKey.take(8)}")
                return@forEach
            }
            val plain = file.readBytes()
            require(codec.assetKey(key, plain) == link.assetKey) { "本地附件内容已变化，请重新同步" }
            remoteStore.writeFileIfAbsentAtomically(
                config = config,
                credentials = credentials,
                relativePath = "$ASSET_DIRECTORY/$finalName",
                content = codec.encodeAsset(plain, key),
            )
            remoteNames += finalName
            uploaded += 1
            Log.i(TAG, "asset uploaded role=${link.role} key=${link.assetKey.take(8)} bytes=${plain.size}")
        }
        Log.d(
            TAG,
            "asset upload complete candidates=${candidates.size} uploaded=$uploaded " +
                "alreadyRemote=$alreadyRemote missingLocal=$missingLocal",
        )
    }

    suspend fun downloadPendingAssets(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        key: ByteArray,
        onPendingCountChanged: (Int) -> Unit,
    ): Int {
        val pending = localRepository.getPendingAssetLinks().distinctBy { it.attachmentUuid }
        var remaining = pending.size
        Log.d(TAG, "asset download start pending=${pending.size}")
        onPendingCountChanged(remaining)
        pending.forEach { link ->
            try {
                val encrypted = remoteStore.readFile(
                    config,
                    credentials,
                    "$ASSET_DIRECTORY/${link.assetKey}.bin.enc",
                ) ?: run {
                    Log.w(TAG, "asset remote missing role=${link.role} key=${link.assetKey.take(8)}")
                    return@forEach
                }
                val plain = codec.decodeAsset(encrypted, key)
                require(codec.assetKey(key, plain) == link.assetKey) { "远端附件内容标识不匹配" }
                require(codec.sha256(plain) == link.plainSha256) { "远端附件校验失败" }
                val target = File(localAssetDir, "${link.assetKey}_${safeName(link.displayName)}")
                val temporary = File(localAssetDir, ".${target.name}.${UUID.randomUUID()}.tmp")
                try {
                    temporary.outputStream().use { it.write(plain) }
                    if (target.exists()) target.delete()
                    check(temporary.renameTo(target)) { "保存同步附件失败" }
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
                localRepository.attachDownloadedAsset(link, target)
                remaining -= 1
                onPendingCountChanged(remaining)
                Log.i(TAG, "asset downloaded role=${link.role} key=${link.assetKey.take(8)} bytes=${plain.size}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Keep this asset pending; another asset must not block body synchronization.
                Log.w(TAG, "asset download failed role=${link.role} key=${link.assetKey.take(8)}", error)
            }
        }
        Log.d(TAG, "asset download complete pending=${pending.size} remaining=$remaining")
        return remaining
    }

    private fun safeName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
        .takeLast(80)
        .ifBlank { "asset.bin" }

    companion object {
        private const val TAG = "WebDavSyncV2"
        const val ASSET_DIRECTORY = "assets"
    }
}
