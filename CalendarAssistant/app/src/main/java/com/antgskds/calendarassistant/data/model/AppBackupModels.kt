package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppBackupOptions(
    val includeEvents: Boolean = true,
    val includeSettings: Boolean = false,
    val includeAttachments: Boolean = false,
    val includePrompts: Boolean = false
)

@Serializable
data class AppBackupManifest(
    val version: Int = 1,
    val createdAt: Long = 0L,
    val options: AppBackupOptions = AppBackupOptions(),
    val app: String = "WillDo"
)

@Serializable
data class AppBackupData(
    val version: Int = 1,
    val createdAt: Long = 0L,
    val options: AppBackupOptions = AppBackupOptions(),
    val eventsJson: String? = null,
    val settings: MySettings? = null,
    val promptsJson: String? = null,
    val attachments: List<AppBackupAttachmentDto> = emptyList()
)

@Serializable
data class AppBackupAttachmentDto(
    val backupEventKey: String = "",
    val fileName: String = "",
    val displayName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val source: String = "manual"
)

data class AppBackupImportResult(
    val eventsResult: ImportResult? = null,
    val settingsImported: Boolean = false,
    val promptsImported: Boolean = false,
    val attachmentsImported: Int = 0
)
