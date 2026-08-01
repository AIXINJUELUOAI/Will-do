package com.antgskds.calendarassistant.feature.cloudsync.application

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.feature.cloudsync.data.SyncV2AssetCoordinator
import com.antgskds.calendarassistant.feature.cloudsync.data.SyncV2Codec
import com.antgskds.calendarassistant.feature.cloudsync.data.SyncV2DeviceIdentityStore
import com.antgskds.calendarassistant.feature.cloudsync.data.SyncV2LocalRepository
import com.antgskds.calendarassistant.feature.cloudsync.data.WebDavCredentialStore
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2PeerEntity
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2DeviceState
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2RunPhase
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2RuntimeStatus
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2VaultConfig
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionConfig
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavCredentials
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavRemoteStore
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WebDavSyncV2Coordinator(
    context: Context,
    private val remoteStore: WebDavRemoteStore,
    private val credentialStore: WebDavCredentialStore,
    private val settingsQueryApi: SettingsQueryApi,
    private val onDataChanged: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val codec = SyncV2Codec()
    private val localRepository = SyncV2LocalRepository(appContext, codec)
    private val assetCoordinator = SyncV2AssetCoordinator(appContext, remoteStore, localRepository, codec)
    private val identityStore = SyncV2DeviceIdentityStore(appContext)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncV2RuntimeStatus())
    val status: StateFlow<SyncV2RuntimeStatus> = _status.asStateFlow()

    suspend fun syncNow(force: Boolean = false): Result<Unit> = mutex.withLock {
        runCatching {
            val settings = settingsQueryApi.settings.value
            if (!force && !settings.webDavSyncEnabled) return@runCatching
            if (settings.webDavBaseUrl.isBlank()) error("请先配置 WebDAV 地址")
            if (!force && settings.webDavWifiOnly && !isOnWifi()) error("当前不是 Wi-Fi 网络")
            val webDavPassword = credentialStore.readPassword() ?: error("请先保存 WebDAV 密码")
            val syncPassphrase = credentialStore.readSyncPassphrase() ?: error("请先保存同步密码")
            val config = WebDavConnectionConfig(
                baseUrl = settings.webDavBaseUrl,
                username = settings.webDavUsername,
                remotePath = resolvedRemotePath(settings.webDavRemotePathOverride),
            )
            val credentials = WebDavCredentials(settings.webDavUsername, webDavPassword)
            val deviceId = identityStore.getOrCreate()
            Log.i(TAG, "sync start device=${deviceId.shortLogId()} force=$force remote=${config.remotePath}")

            update(SyncV2RunPhase.CONNECTING, "正在连接远端")
            val vault = ensureVault(config, credentials, syncPassphrase)
            val key = codec.deriveKey(
                syncPassphrase.toCharArray(),
                Base64.getDecoder().decode(vault.saltBase64),
                vault.iterations,
            )
            require(codec.keyVerifier(key) == vault.keyVerifierBase64) { "同步密码不正确" }
            localRepository.prepareVault(vault.vaultId)

            localRepository.scanLocalChanges(deviceId, key)
            update(SyncV2RunPhase.UPLOADING_ASSETS, "正在上传本机附件")
            assetCoordinator.uploadMissingAssets(config, credentials, key)

            update(SyncV2RunPhase.DOWNLOADING, "正在读取其他设备")
            val downloaded = downloadChangedPeerRecords(
                config = config,
                credentials = credentials,
                key = key,
                ownDeviceId = deviceId,
                forceRefresh = force,
            )
            Log.i(
                TAG,
                "download complete records=${downloaded.records.size} peers=${downloaded.peers.size} " +
                    "failedPeers=${downloaded.failedPeerCount}",
            )
            update(SyncV2RunPhase.MERGING, "正在合并数据")
            val conflicts = localRepository.mergeRemoteRecords(downloaded.records, deviceId)
            downloaded.peers.forEach(localRepository::putPeer)
            onDataChanged()

            update(SyncV2RunPhase.DOWNLOADING_ASSETS, "正在下载附件", conflicts = conflicts)
            val pendingAssets = assetCoordinator.downloadPendingAssets(config, credentials, key) { count ->
                _status.value = _status.value.copy(pendingAssetCount = count)
            }
            onDataChanged()

            localRepository.scanLocalChanges(deviceId, key)
            update(SyncV2RunPhase.UPLOADING_ASSETS, "正在上传附件", conflicts = conflicts)
            assetCoordinator.uploadMissingAssets(config, credentials, key)
            publishStateIfChanged(config, credentials, key, deviceId, vault.vaultId)
            val now = System.currentTimeMillis()
            _status.value = SyncV2RuntimeStatus(
                phase = SyncV2RunPhase.SUCCESS,
                message = when {
                    downloaded.failedPeerCount > 0 -> "同步完成，${downloaded.failedPeerCount} 个设备状态暂时无法读取"
                    conflicts > 0 -> "同步完成，存在 $conflicts 个冲突"
                    else -> "同步完成"
                },
                lastSuccessAt = now,
                pendingAssetCount = pendingAssets,
                conflictCount = conflicts,
            )
            Log.i(TAG, "sync success device=${deviceId.shortLogId()} conflicts=$conflicts pendingAssets=$pendingAssets")
        }.onFailure { error ->
            if (error is CancellationException) {
                Log.d(TAG, "sync cancelled in ${_status.value.phase}")
                throw error
            }
            Log.e(TAG, "WebDAV V2 sync failed in ${_status.value.phase}", error)
            _status.value = _status.value.copy(
                phase = SyncV2RunPhase.FAILED,
                message = error.message?.takeIf { it.isNotBlank() } ?: "多设备同步失败",
            )
        }
    }

    fun resetRemoteTracking() = localRepository.resetRemoteTracking()

    private suspend fun ensureVault(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        passphrase: String,
    ): SyncV2VaultConfig {
        remoteStore.readFile(config, credentials, VAULT_FILE)?.let(codec::decodeVault)?.let { return it }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val key = codec.deriveKey(passphrase.toCharArray(), salt)
        val proposed = SyncV2VaultConfig(
            vaultId = UUID.randomUUID().toString(),
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            iterations = SyncV2Codec.DEFAULT_ITERATIONS,
            keyVerifierBase64 = codec.keyVerifier(key),
            createdAt = System.currentTimeMillis(),
        )
        val created = remoteStore.createFileIfAbsent(
            config,
            credentials,
            VAULT_FILE,
            codec.encodeVault(proposed),
            "application/json",
        )
        return if (created) proposed else {
            remoteStore.readFile(config, credentials, VAULT_FILE)?.let(codec::decodeVault)
                ?: error("远端加密配置初始化失败")
        }
    }

    private suspend fun downloadChangedPeerRecords(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        key: ByteArray,
        ownDeviceId: String,
        forceRefresh: Boolean,
    ): DownloadedPeerStates {
        val records = mutableListOf<com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2Record>()
        val peers = mutableListOf<SyncV2PeerEntity>()
        var failedPeerCount = 0
        val devices = remoteStore.listDirectory(config, credentials, DEVICES_DIRECTORY)
            .filter { it.isDirectory && it.name.isNotBlank() && it.name != ownDeviceId }
        devices.forEach { device ->
            try {
                val stateResource = remoteStore.listDirectory(config, credentials, "$DEVICES_DIRECTORY/${device.name}")
                    .firstOrNull { !it.isDirectory && it.name == STATE_FILE }
                    ?: run {
                        Log.w(TAG, "peer state missing device=${device.name.shortLogId()}")
                        return@forEach
                    }
                val peer = localRepository.getPeer(device.name)
                if (!forceRefresh && stateResource.etag.isNotBlank() && peer?.etag == stateResource.etag) {
                    Log.d(
                        TAG,
                        "peer unchanged device=${device.name.shortLogId()} generation=${peer.generation} " +
                            "etag=${stateResource.etag.take(24)}",
                    )
                    return@forEach
                }
                if (forceRefresh && stateResource.etag.isNotBlank() && peer?.etag == stateResource.etag) {
                    Log.i(TAG, "peer forced refresh device=${device.name.shortLogId()} generation=${peer.generation}")
                }
                val encoded = remoteStore.readFile(config, credentials, "$DEVICES_DIRECTORY/${device.name}/$STATE_FILE")
                    ?: return@forEach
                val state = codec.decodeState(encoded, key)
                require(state.deviceId == device.name) { "远端设备目录与状态不匹配" }
                Log.i(
                    TAG,
                    "peer downloaded device=${device.name.shortLogId()} generation=${state.generation} " +
                        "records=${state.records.size} " +
                        "events=${state.records.count { it.entityType == com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2EntityType.EVENT.name }} " +
                        "quickMemos=${state.records.count { it.entityType == com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2EntityType.QUICK_MEMO.name }}",
                )
                records += state.records
                peers += SyncV2PeerEntity(
                    deviceId = device.name,
                    etag = stateResource.etag,
                    generation = state.generation,
                    lastSeenAt = System.currentTimeMillis(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failedPeerCount += 1
                Log.w(TAG, "Skipping unreadable peer state for ${device.name}", error)
            }
        }
        return DownloadedPeerStates(records, peers, failedPeerCount)
    }

    private suspend fun publishStateIfChanged(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        key: ByteArray,
        deviceId: String,
        vaultId: String,
    ) {
        val records = localRepository.exportRecords()
        val hash = codec.stateHash(records)
        val vaultScopedHash = "$vaultId:$STATE_SCHEMA_ID:$hash"
        if (localRepository.getMeta(SyncV2LocalRepository.META_PUBLISHED_HASH) == vaultScopedHash) {
            Log.d(
                TAG,
                "publish skipped unchanged records=${records.size} " +
                    "quickMemos=${records.count { it.entityType == com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2EntityType.QUICK_MEMO.name }}",
            )
            return
        }
        update(SyncV2RunPhase.PUBLISHING, "正在发布本机状态", conflicts = _status.value.conflictCount)
        val generation = (localRepository.getMeta(SyncV2LocalRepository.META_GENERATION)?.toLongOrNull() ?: 0L) + 1L
        val state = SyncV2DeviceState(
            deviceId = deviceId,
            generation = generation,
            generatedAt = System.currentTimeMillis(),
            records = records,
        )
        remoteStore.createFileIfAbsent(
            config,
            credentials,
            "$DEVICES_DIRECTORY/$deviceId/device.json",
            "{\"deviceId\":\"$deviceId\",\"name\":\"${escapeJson(Build.MANUFACTURER + " " + Build.MODEL)}\"}".toByteArray(),
            "application/json",
        )
        remoteStore.writeFileAtomically(
            config,
            credentials,
            "$DEVICES_DIRECTORY/$deviceId/$STATE_FILE",
            codec.encodeState(state, key),
        )
        Log.i(
            TAG,
            "state published generation=$generation records=${records.size} " +
                "events=${records.count { it.entityType == com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2EntityType.EVENT.name }} " +
                "quickMemos=${records.count { it.entityType == com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2EntityType.QUICK_MEMO.name }}",
        )
        localRepository.putMeta(SyncV2LocalRepository.META_GENERATION, generation.toString())
        localRepository.putMeta(SyncV2LocalRepository.META_PUBLISHED_HASH, vaultScopedHash)
    }

    private fun update(phase: SyncV2RunPhase, message: String, conflicts: Int = _status.value.conflictCount) {
        _status.value = _status.value.copy(phase = phase, message = message, conflictCount = conflicts)
    }

    private fun isOnWifi(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun resolvedRemotePath(overrideRoot: String): String {
        val root = overrideRoot.trim().trim('/').ifBlank { DEFAULT_ROOT }
        require(root.split('/').none { it.isBlank() || it == "." || it == ".." }) { "开发者远端目录覆盖无效" }
        return "/$root/sync/v2"
    }

    private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun String.shortLogId(): String = take(8)

    companion object {
        private const val TAG = "WebDavSyncV2"
        const val DEFAULT_ROOT = "WillDo"
        private const val VAULT_FILE = "vault.json"
        private const val DEVICES_DIRECTORY = "devices"
        private const val STATE_FILE = "state.json.gz.enc"
        private const val STATE_SCHEMA_ID = "stable-gson-models-1"
    }

    private data class DownloadedPeerStates(
        val records: List<com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2Record>,
        val peers: List<SyncV2PeerEntity>,
        val failedPeerCount: Int,
    )
}
