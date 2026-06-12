package com.antgskds.calendarassistant.core.quickmemo.asr

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class QuickMemoAsrModelStatus(
    val modelReady: Boolean,
    val tokensReady: Boolean,
    val modelDirectory: String
) {
    val ready: Boolean get() = modelReady && tokensReady
}

object QuickMemoAsrModelStore {
    const val MODEL_FILE = "model.int8.onnx"
    const val FALLBACK_MODEL_FILE = "model.onnx"
    const val TOKENS_FILE = "tokens.txt"
    const val MODEL_DIR = "quick_memos/asr/sherpa-onnx-paraformer-zh-small-2024-03-09"
    const val ASSET_MODEL_DIR = "quick_memos/asr/sherpa-onnx-paraformer-zh-small-2024-03-09"

    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR)

    fun status(context: Context): QuickMemoAsrModelStatus {
        val dir = modelDir(context)
        return QuickMemoAsrModelStatus(
            modelReady = modelFile(context) != null,
            tokensReady = File(dir, TOKENS_FILE).isUsableFile(),
            modelDirectory = dir.absolutePath
        )
    }

    fun modelFile(context: Context): File? {
        val dir = modelDir(context)
        return File(dir, MODEL_FILE).takeIf { it.isUsableFile() }
            ?: File(dir, FALLBACK_MODEL_FILE).takeIf { it.isUsableFile() }
    }

    fun tokensFile(context: Context): File? {
        return File(modelDir(context), TOKENS_FILE).takeIf { it.isUsableFile() }
    }

    fun importModelFile(context: Context, uri: Uri): Result<String> = runCatching {
        val displayName = queryDisplayName(context, uri).lowercase()
        val targetName = when {
            displayName == TOKENS_FILE || displayName.endsWith(".txt") -> TOKENS_FILE
            displayName == FALLBACK_MODEL_FILE -> FALLBACK_MODEL_FILE
            displayName == MODEL_FILE || displayName.endsWith(".onnx") -> MODEL_FILE
            else -> error("请选择 .onnx 模型文件或 tokens.txt")
        }
        val target = File(modelDir(context).apply { mkdirs() }, targetName)
        val tmp = File(target.parentFile, "$targetName.import")
        tmp.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取文件")
        if (!tmp.isUsableFile()) error("导入文件为空")
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        targetName
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

    private fun File.isUsableFile(): Boolean = isFile && length() > 0L
}
