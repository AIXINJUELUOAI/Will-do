package com.antgskds.calendarassistant.feature.quickmemo.data.asr

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
            tokensReady = tokensFile(context) != null,
            modelDirectory = dir.absolutePath
        )
    }

    fun modelFile(context: Context): File? {
        val dir = modelDir(context)
        return File(dir, MODEL_FILE).takeIf { it.isValidModelFile() }
            ?: File(dir, FALLBACK_MODEL_FILE).takeIf { it.isValidModelFile() }
    }

    fun tokensFile(context: Context): File? {
        return File(modelDir(context), TOKENS_FILE).takeIf { it.isValidTokensFile() }
    }

    fun importModelFile(context: Context, uri: Uri): Result<String> = runCatching {
        val displayName = queryDisplayName(context, uri).lowercase()
        val targetName = resolveTargetName(displayName)
        val target = File(modelDir(context).apply { mkdirs() }, targetName)
        val tmp = File(target.parentFile, "$targetName.import")
        tmp.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取文件")
        if (!tmp.isUsableFile()) error("导入文件为空")
        validateImportedFile(targetName, tmp)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        targetName
    }

    private fun resolveTargetName(displayName: String): String {
        return when (displayName) {
            TOKENS_FILE -> TOKENS_FILE
            FALLBACK_MODEL_FILE, "$FALLBACK_MODEL_FILE.txt" -> FALLBACK_MODEL_FILE
            MODEL_FILE, "$MODEL_FILE.txt" -> MODEL_FILE
            else -> error("请选择 model.int8.onnx、model.int8.onnx.txt、model.onnx、model.onnx.txt 或 tokens.txt")
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

    private fun File.isUsableFile(): Boolean = isFile && length() > 0L

    private fun validateImportedFile(targetName: String, file: File) {
        when (targetName) {
            MODEL_FILE, FALLBACK_MODEL_FILE -> {
                if (!file.isValidModelFile()) {
                    error("模型文件异常，请确认导入的是 paraformer 的 $MODEL_FILE 或 $FALLBACK_MODEL_FILE")
                }
            }
            TOKENS_FILE -> {
                if (!file.isValidTokensFile()) {
                    error("tokens.txt 内容异常，请导入语音模型目录中的原始 tokens.txt")
                }
            }
        }
    }

    private fun File.isValidModelFile(): Boolean {
        return isUsableFile() && length() >= MIN_MODEL_BYTES
    }

    private fun File.isValidTokensFile(): Boolean {
        if (!isUsableFile() || length() < MIN_TOKENS_BYTES) return false
        return runCatching {
            bufferedReader().useLines { lines ->
                lines
                    .take(TOKENS_SAMPLE_LINE_LIMIT)
                    .count { it.trim().isNotEmpty() } >= MIN_TOKENS_NON_EMPTY_LINES
            }
        }.getOrDefault(false)
    }

    private const val MIN_MODEL_BYTES = 1024L * 1024L
    private const val MIN_TOKENS_BYTES = 128L
    private const val TOKENS_SAMPLE_LINE_LIMIT = 512
    private const val MIN_TOKENS_NON_EMPTY_LINES = 20
}
