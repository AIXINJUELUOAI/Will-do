package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppBackupOptions(
    val includeEvents: Boolean = true,
    val includeSettings: Boolean = false,
    val includeAttachments: Boolean = false,
    val includePrompts: Boolean = false,
    val includeQuickMemos: Boolean = false
)

@Serializable
data class AppBackupManifest(
    val version: Int = 2,
    val createdAt: Long = 0L,
    val options: AppBackupOptions = AppBackupOptions(),
    val app: String = "WillDo"
)

@Serializable
data class AppBackupData(
    val version: Int = 2,
    val createdAt: Long = 0L,
    val options: AppBackupOptions = AppBackupOptions(),
    val eventsJson: String? = null,
    val settings: MySettings? = null,
    val promptsJson: String? = null,
    val appBackgroundImageFileName: String? = null,
    val attachments: List<AppBackupAttachmentDto> = emptyList(),
    val quickMemos: List<AppBackupQuickMemoDto> = emptyList(),
    val quickMemoSuggestions: List<AppBackupQuickMemoSuggestionDto> = emptyList()
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

@Serializable
data class AppBackupQuickMemoDto(
    val backupKey: String = "",
    val type: String = "TEXT",
    val bodyText: String = "",
    val audioFileName: String? = null,
    val imageFileName: String? = null,
    val audioDurationMs: Long = 0L,
    val transcriptionStatus: String = "NONE",
    val analysisStatus: String = "NONE",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sortRank: Long = 0L,
    val todoState: String = "NONE",
    val todoPendingUntil: Long? = null,
    val todoCompletedAt: Long? = null
)

@Serializable
data class AppBackupQuickMemoSuggestionDto(
    val backupMemoKey: String = "",
    val type: String = "SCHEDULE",
    val status: String = "PENDING",
    val candidateJson: String = "",
    val eventId: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class AppBackupImportResult(
    val eventsResult: ImportResult? = null,
    val settingsImported: Boolean = false,
    val promptsImported: Boolean = false,
    val attachmentsImported: Int = 0,
    val quickMemosImported: Int = 0
)
