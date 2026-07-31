package com.antgskds.calendarassistant.feature.cloudsync.application

import com.antgskds.calendarassistant.feature.cloudsync.data.WebDavCredentialStore
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
    fun hasStoredPassword(): Boolean = credentialStore.hasPassword()

    suspend fun testAndSave(input: WebDavConnectionInput): WebDavConnectionTestResult {
        val password = input.password.ifBlank { credentialStore.readPassword().orEmpty() }
        if (password.isBlank()) {
            return WebDavConnectionTestResult(false, "请填写 WebDAV 密码")
        }

        val config = WebDavConnectionConfig(
            baseUrl = input.baseUrl.trim(),
            username = input.username.trim(),
            remotePath = input.remotePath.trim().ifBlank { DEFAULT_REMOTE_PATH },
        )
        val result = remoteStore.testConnection(
            config = config,
            credentials = WebDavCredentials(config.username, password),
        )
        if (!result.success) return result

        if (input.password.isNotBlank()) credentialStore.savePassword(input.password)
        val current = settingsQueryApi.settings.value
        settingsOperationApi.updateSettings(
            current.copy(
                webDavBaseUrl = config.baseUrl,
                webDavUsername = config.username,
                webDavRemotePath = config.remotePath,
            )
        )
        return result
    }

    companion object {
        const val DEFAULT_REMOTE_PATH = "/WillDo"
    }
}
