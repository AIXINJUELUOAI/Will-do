package com.antgskds.calendarassistant.feature.quickmemo.data.asr

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.io.File

data class QuickMemoAsrModelStatus(
    val ready: Boolean,
    val modelName: String,
    val missingFiles: List<String>,
)

object QuickMemoAsrModelStore {
    // Legacy models remain usable until a complete Qwen replacement has been validated.
    const val MODEL_FILE = "model.int8.onnx"
    const val FALLBACK_MODEL_FILE = "model.onnx"
    const val TOKENS_FILE = "tokens.txt"
    const val MODEL_DIR = "quick_memos/asr/sherpa-onnx-paraformer-zh-small-2024-03-09"
    const val ASSET_MODEL_DIR = MODEL_DIR

    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR)
    fun qwenDir(context: Context): File = File(context.filesDir, QwenAsrModelFiles.DIRECTORY)

    fun status(context: Context): QuickMemoAsrModelStatus {
        val missing = QwenAsrModelFiles.missing(qwenDir(context))
        if (missing.isEmpty()) return QuickMemoAsrModelStatus(true, "Qwen3-ASR 0.6B", emptyList())
        val legacyReady = modelFile(context) != null && tokensFile(context) != null
        return QuickMemoAsrModelStatus(legacyReady, if (legacyReady) "Paraformer" else "", missing)
    }

    fun modelFile(context: Context): File? = listOf(MODEL_FILE, FALLBACK_MODEL_FILE)
        .map { File(modelDir(context), it) }.firstOrNull { it.isFile && it.length() >= ConfigCatalog.ASR_MODEL_MIN_BYTES }

    fun tokensFile(context: Context): File? = File(modelDir(context), TOKENS_FILE).takeIf {
        it.isFile && it.length() >= 128 && it.bufferedReader().useLines { lines -> lines.take(512).count { line -> line.isNotBlank() } >= 20 }
    }

    @Synchronized
    fun importModelFile(context: Context, uri: Uri): Result<String> = runCatching {
        val displayName = queryDisplayName(context, uri)
        if (displayName.endsWith(".zip", ignoreCase = true)) {
            importQwenBundle(context, uri)
            val cleaned = runCatching { QwenAsrModelFiles.cleanupLegacyModel(context.filesDir) }.getOrDefault(false)
            return@runCatching if (cleaned) "Qwen3-ASR 0.6B 完整模型" else "Qwen3-ASR 0.6B 完整模型（旧模型清理未完成，将自动重试）"
        }
        val qwenName = QwenAsrModelFiles.targetName(displayName)
        val legacyName = displayName.lowercase().removeSuffix(".txt").takeIf { it == MODEL_FILE || it == FALLBACK_MODEL_FILE }
            ?: displayName.lowercase().takeIf { it == TOKENS_FILE }
        val targetName = qwenName ?: legacyName ?: error("请选择 Qwen 模型 ZIP 包、配套模型文件或原 Paraformer 文件")
        val target = File(if (qwenName != null) qwenDir(context) else modelDir(context), targetName)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.import")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> QwenAsrModelFiles.copyLimited(input, temporary) }
                ?: error("无法读取文件")
            if (qwenName != null) QwenAsrModelFiles.validate(qwenName, temporary)
            else require(if (targetName == TOKENS_FILE) temporary.length() >= 128 else temporary.length() >= ConfigCatalog.ASR_MODEL_MIN_BYTES) {
                "模型文件不完整"
            }
            check(temporary.renameTo(target)) { "无法替换模型文件，原模型已保留" }
        } finally {
            temporary.delete()
        }
        if (qwenName != null && QwenAsrModelFiles.missing(qwenDir(context)).isEmpty()) {
            val cleaned = runCatching { QwenAsrModelFiles.cleanupLegacyModel(context.filesDir) }.getOrDefault(false)
            if (!cleaned) return@runCatching "$targetName（旧模型清理未完成，将自动重试）"
        }
        targetName
    }

    private fun importQwenBundle(context: Context, uri: Uri) {
        val target = qwenDir(context)
        val staging = File(target.parentFile, "${target.name}.import")
        val previous = File(target.parentFile, "${target.name}.previous")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)?.use { QwenAsrModelFiles.extractBundle(it, staging) }
                ?: error("无法读取模型包")
            previous.deleteRecursively()
            if (target.exists()) check(target.renameTo(previous)) { "无法备份已有模型" }
            if (!staging.renameTo(target)) {
                check(!previous.exists() || previous.renameTo(target)) { "模型替换失败，旧文件保留在备份目录" }
                error("模型替换失败，原模型已恢复")
            }
            previous.deleteRecursively()
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }
}
