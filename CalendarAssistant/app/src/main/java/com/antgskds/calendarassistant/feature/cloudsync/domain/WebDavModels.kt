package com.antgskds.calendarassistant.feature.cloudsync.domain

data class WebDavConnectionConfig(
    val baseUrl: String,
    val username: String,
    val remotePath: String,
)

data class WebDavCredentials(
    val username: String,
    val password: String,
)

data class WebDavConnectionInput(
    val baseUrl: String,
    val username: String,
    val remotePath: String,
    val password: String,
)

data class WebDavCapabilities(
    val moveSupported: Boolean,
    val davHeader: String,
)

data class WebDavConnectionTestResult(
    val success: Boolean,
    val message: String,
    val capabilities: WebDavCapabilities? = null,
)

interface WebDavRemoteStore {
    suspend fun testConnection(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
    ): WebDavConnectionTestResult
}
