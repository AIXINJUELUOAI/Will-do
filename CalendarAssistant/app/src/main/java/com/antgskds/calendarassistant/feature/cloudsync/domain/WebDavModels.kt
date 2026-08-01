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
    val password: String,
    val syncPassphrase: String,
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

data class WebDavWriteResult(
    val moveSupported: Boolean,
    val etag: String = "",
)

data class WebDavResource(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val etag: String = "",
    val size: Long = 0L,
    val lastModified: String = "",
)

interface WebDavRemoteStore {
    suspend fun testConnection(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
    ): WebDavConnectionTestResult

    suspend fun writeFileAtomically(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
        content: ByteArray,
        contentType: String = "application/octet-stream",
    ): WebDavWriteResult

    suspend fun readFile(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
    ): ByteArray?

    suspend fun listDirectory(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativeDirectory: String,
    ): List<WebDavResource>

    suspend fun deleteFile(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
    ): Boolean

    suspend fun createFileIfAbsent(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
        content: ByteArray,
        contentType: String = "application/octet-stream",
    ): Boolean

    suspend fun writeFileIfAbsentAtomically(
        config: WebDavConnectionConfig,
        credentials: WebDavCredentials,
        relativePath: String,
        content: ByteArray,
        contentType: String = "application/octet-stream",
    ): Boolean
}
