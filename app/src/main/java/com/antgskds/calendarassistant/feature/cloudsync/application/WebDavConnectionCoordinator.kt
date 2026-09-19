package com.antgskds.calendarassistant.feature.cloudsync.application

import com.antgskds.calendarassistant.feature.cloudsync.data.WebDavCredentialStore
import com.antgskds.calendarassistant.feature.cloudsync.data.SyncV2Codec
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionConfig
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionInput
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionTestResult
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavCredentials
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavRemoteStore
import com.antgskds.calendarassistant.shared.operation.SettingsOperationApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi

class WebDavConnectionCoordinator(
    private val remoteStore: WebDavRemoteStore,
    private val credentialStore: WebDavCredentialStore,
    private val settingsQueryApi: SettingsQueryApi,
    private val settingsOperationApi: SettingsOperationApi,
) {
    private val syncCodec = SyncV2Codec()

    fun hasStoredPassword(): Boolean = credentialStore.hasPassword()
    fun hasStoredSyncPassphrase(): Boolean = credentialStore.hasSyncPassphrase()
    fun readStoredPassword(): String = credentialStore.readPassword().orEmpty()
    fun readStoredSyncPassphrase(): String = credentialStore.readSyncPassphrase().orEmpty()

    suspend fun testAndSave(input: WebDavConnectionInput): WebDavConnectionTestResult {
        val password = input.password.ifBlank { credentialStore.readPassword().orEmpty() }
        if (password.isBlank()) {
            return WebDavConnectionTestResult(false, "请填写 WebDAV 密码")
        }
        val syncPassphrase = input.syncPassphrase.ifBlank { credentialStore.readSyncPassphrase().orEmpty() }
        if (syncPassphrase.isBlank()) {
            return WebDavConnectionTestResult(false, "请填写同步密码")
        }

        val current = settingsQueryApi.settings.value

        val config = WebDavConnectionConfig(
            baseUrl = input.baseUrl.trim(),
            username = input.username.trim(),
            remotePath = resolveRemotePath(current.webDavRemotePathOverride),
        )
        val result = remoteStore.testConnection(
            config = config,
            credentials = WebDavCredentials(config.username, password),
        )
        if (!result.success) return result

        val vault = remoteStore.readFile(config, WebDavCredentials(config.username, password), V2_VAULT_FILE)
            ?.let { encoded ->
                runCatching { syncCodec.decodeVault(encoded) }.getOrElse { error ->
                    return WebDavConnectionTestResult(false, error.message ?: "远端加密配置无效")
                }
            }
        if (vault != null) {
            val matches = runCatching {
                val key = syncCodec.deriveKey(
                    syncPassphrase.toCharArray(),
                    java.util.Base64.getDecoder().decode(vault.saltBase64),
                    vault.iterations,
                )
                syncCodec.keyVerifier(key) == vault.keyVerifierBase64
            }.getOrDefault(false)
            if (!matches) return WebDavConnectionTestResult(false, "同步密码与远端数据不匹配")
        }

        if (input.password.isNotBlank()) credentialStore.savePassword(input.password)
        if (input.syncPassphrase.isNotBlank()) credentialStore.saveSyncPassphrase(input.syncPassphrase)
        settingsOperationApi.updateSettings(
            current.copy(
                webDavBaseUrl = config.baseUrl,
                webDavUsername = config.username,
            )
        )
        return result
    }

    companion object {
        const val DEFAULT_REMOTE_ROOT = "WillDo"
        private const val V2_VAULT_FILE = "vault.json"

        fun resolveRemotePath(overrideRoot: String): String {
            val root = overrideRoot.trim().trim('/').ifBlank { DEFAULT_REMOTE_ROOT }
            require(root.split('/').none { it.isBlank() || it == "." || it == ".." }) {
                "开发者远端目录覆盖无效"
            }
            return "/$root/sync/v2"
        }
    }
}
