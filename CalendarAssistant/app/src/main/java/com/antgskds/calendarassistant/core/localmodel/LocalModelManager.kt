package com.antgskds.calendarassistant.core.localmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.antgskds.calendarassistant.aiengine.AiEngineLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class LocalModelManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, MODELS_DIR_NAME)
    private val registryFile = File(modelsDir, REGISTRY_FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val _models = MutableStateFlow(loadRegistry().models)
    val models: StateFlow<List<LocalModelInfo>> = _models.asStateFlow()

    suspend fun importModel(
        uri: Uri,
        onProgress: (LocalModelImportProgress) -> Unit = {}
    ): LocalModelImportResult = withContext(Dispatchers.IO) {
        ensureModelsDir()
        val displayName = queryDisplayName(uri).ifBlank { "model-${System.currentTimeMillis()}.litertlm" }
        require(displayName.endsWith(".litertlm", ignoreCase = true)) { "请选择 .litertlm 模型文件" }

        val totalBytes = querySize(uri)
        if (totalBytes > 0L) {
            val usable = modelsDir.usableSpace
            require(usable > totalBytes + MIN_FREE_SPACE_AFTER_IMPORT) { "存储空间不足，无法导入模型" }
        }

        val targetFile = uniqueModelFile(sanitizeFileName(displayName))
        var copiedBytes = 0L
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    copiedBytes += read
                    onProgress(LocalModelImportProgress(displayName, copiedBytes, totalBytes))
                }
            }
        } ?: throw IllegalArgumentException("模型文件读取失败")

        val info = buildLiteRtModelInfo(targetFile, copiedBytes)
        val existing = _models.value.firstOrNull { it.path == info.path || it.fileName == info.fileName }
        val nextModels = _models.value.filterNot { it.id == existing?.id || it.path == info.path || it.fileName == info.fileName } + info
        saveModels(nextModels)
        LocalModelImportResult(info, replacedExisting = existing != null)
    }

    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _models.value
        val target = current.firstOrNull { it.id == modelId } ?: return@withContext false
        runCatching { File(target.path).delete() }
        saveModels(current.filterNot { it.id == modelId })
        true
    }

    fun getModel(modelId: String): LocalModelInfo? {
        return _models.value.firstOrNull { it.id == modelId && File(it.path).exists() }
    }

    fun hasModel(modelId: String): Boolean = getModel(modelId) != null

    fun setLocalModelLoggingEnabled(enabled: Boolean) {
        AiEngineLog.setEnabled(appContext, enabled)
        if (enabled) {
            LocalModelLogScheduler.scheduleNext(appContext)
        } else {
            LocalModelLogScheduler.cancel(appContext)
        }
    }

    private fun buildLiteRtModelInfo(file: File, copiedBytes: Long): LocalModelInfo {
        val supportsMultimodal = inferLiteRtMultimodal(file.name)
        return LocalModelInfo(
            id = stableLiteRtModelId(file),
            displayName = file.nameWithoutExtension,
            fileName = file.name,
            path = file.absolutePath,
            sizeBytes = if (file.length() > 0L) file.length() else copiedBytes,
            runtime = LocalModelRuntime.LITERT_LM,
            architecture = "LiteRT-LM",
            quantization = inferLiteRtQuantization(file.name),
            contextLength = inferLiteRtContextLength(file.name),
            importedAt = System.currentTimeMillis(),
            supportsText = true,
            supportsMultimodal = supportsMultimodal
        )
    }

    private fun sanitizeFileName(fileName: String): String {
        val cleaned = fileName.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.')
        return cleaned.ifBlank { "model-${UUID.randomUUID()}.litertlm" }
    }

    private fun uniqueModelFile(fileName: String): File {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        val suffix = if (extension.isNotBlank()) ".$extension" else ""
        val base = if (suffix.isNotBlank()) fileName.removeSuffix(suffix) else fileName
        var candidate = File(modelsDir, fileName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(modelsDir, "${base}_$index$suffix")
            index++
        }
        return candidate
    }

    private fun stableLiteRtModelId(file: File): String {
        val seed = "litert:${file.name}:${file.length()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun inferLiteRtMultimodal(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return listOf("vision", "image", "multimodal", "mm", "gemma-4", "gemma4").any { it in lower }
    }

    private fun inferLiteRtQuantization(fileName: String): String {
        val lower = fileName.lowercase()
        return Regex("q[0-9][a-z0-9_]*").find(lower)?.value.orEmpty()
    }

    private fun inferLiteRtContextLength(fileName: String): Int? {
        val lower = fileName.lowercase()
        Regex("ekv(\\d+)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("ctx(\\d+)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return null
    }

    private fun queryDisplayName(uri: Uri): String {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
        }.orEmpty()
    }

    private fun querySize(uri: Uri): Long {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
        } ?: -1L
    }

    private fun loadRegistry(): LocalModelRegistry {
        ensureModelsDir()
        if (!registryFile.exists()) return LocalModelRegistry()
        return runCatching {
            val models = json.decodeFromString<LocalModelRegistry>(registryFile.readText()).models
                .filter { it.runtime == LocalModelRuntime.LITERT_LM && it.fileName.endsWith(".litertlm", ignoreCase = true) }
            LocalModelRegistry(models)
        }.getOrDefault(LocalModelRegistry())
    }

    private fun saveModels(models: List<LocalModelInfo>) {
        ensureModelsDir()
        val existingModels = models.filter {
            it.runtime == LocalModelRuntime.LITERT_LM &&
                it.fileName.endsWith(".litertlm", ignoreCase = true) &&
                File(it.path).exists()
        }
        registryFile.writeText(json.encodeToString(LocalModelRegistry(existingModels)))
        _models.update { existingModels }
    }

    private fun ensureModelsDir() {
        if (modelsDir.exists() && !modelsDir.isDirectory) modelsDir.delete()
        if (!modelsDir.exists()) modelsDir.mkdirs()
    }

    companion object {
        private const val MODELS_DIR_NAME = "local_models"
        private const val REGISTRY_FILE_NAME = "registry.json"
        private const val MIN_FREE_SPACE_AFTER_IMPORT = 512L * 1024L * 1024L
    }
}
