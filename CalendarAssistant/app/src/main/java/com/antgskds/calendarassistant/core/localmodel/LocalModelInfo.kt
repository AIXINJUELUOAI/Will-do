package com.antgskds.calendarassistant.core.localmodel

import kotlinx.serialization.Serializable

@Serializable
enum class LocalModelRuntime {
    LITERT_LM
}

@Serializable
data class LocalModelInfo(
    val id: String,
    val displayName: String,
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val runtime: LocalModelRuntime = LocalModelRuntime.LITERT_LM,
    val architecture: String = "",
    val quantization: String = "",
    val contextLength: Int? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val supportsText: Boolean = true,
    val supportsMultimodal: Boolean = false
)

@Serializable
data class LocalModelRegistry(
    val models: List<LocalModelInfo> = emptyList()
)

data class LocalModelImportProgress(
    val fileName: String,
    val copiedBytes: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

data class LocalModelImportResult(
    val model: LocalModelInfo,
    val replacedExisting: Boolean
)
