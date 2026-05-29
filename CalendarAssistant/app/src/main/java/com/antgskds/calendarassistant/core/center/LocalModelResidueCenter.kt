package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.BuildConfig
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LocalModelResiduePrompt(
    val sizeBytes: Long,
    val fileCount: Int,
    val fingerprint: String
)

class LocalModelResidueCenter(
    private val appContext: Context,
    private val settingsQueryApi: SettingsQueryApi,
    private val settingsOperationApi: SettingsOperationApi,
    private val appScope: CoroutineScope
) {
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelsDir = File(appContext.filesDir, MODELS_DIR_NAME)

    private val _pendingPrompt = MutableStateFlow<LocalModelResiduePrompt?>(null)
    val pendingPrompt: StateFlow<LocalModelResiduePrompt?> = _pendingPrompt.asStateFlow()

    fun checkForResidue() {
        if (BuildConfig.LOCAL_MODEL_EDITION) return
        appScope.launch {
            val residue = scanResidue() ?: return@launch
            if (prefs.getString(KEY_DISMISSED_FINGERPRINT, "") == residue.fingerprint) return@launch
            _pendingPrompt.value = residue
        }
    }

    fun dismissPendingPrompt() {
        val prompt = _pendingPrompt.value ?: return
        prefs.edit().putString(KEY_DISMISSED_FINGERPRINT, prompt.fingerprint).apply()
        _pendingPrompt.value = null
    }

    fun clearResidue() {
        val prompt = _pendingPrompt.value
        _pendingPrompt.value = null
        appScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                runCatching {
                    if (modelsDir.exists()) modelsDir.deleteRecursively() else true
                }.onFailure { Log.w(TAG, "Clear local model residue failed", it) }
                    .getOrDefault(false)
            }
            if (deleted) {
                prefs.edit().remove(KEY_DISMISSED_FINGERPRINT).apply()
                val settings = settingsQueryApi.settings.value
                settingsOperationApi.updateSettings(
                    settings.copy(
                        isLocalSemanticEnabled = false,
                        selectedLocalModelId = ""
                    )
                )
            } else if (prompt != null) {
                _pendingPrompt.value = prompt
            }
        }
    }

    private suspend fun scanResidue(): LocalModelResiduePrompt? = withContext(Dispatchers.IO) {
        if (!modelsDir.exists() || !modelsDir.isDirectory) return@withContext null
        var sizeBytes = 0L
        var fileCount = 0
        var latestModified = modelsDir.lastModified()
        modelsDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                fileCount++
                sizeBytes += file.length()
                latestModified = maxOf(latestModified, file.lastModified())
            }
        }
        if (fileCount <= 0 || sizeBytes <= 0L) return@withContext null
        LocalModelResiduePrompt(
            sizeBytes = sizeBytes,
            fileCount = fileCount,
            fingerprint = "$fileCount:$sizeBytes:$latestModified"
        )
    }

    private companion object {
        private const val TAG = "LocalModelResidue"
        private const val MODELS_DIR_NAME = "local_models"
        private const val PREFS_NAME = "local_model_residue_prompt"
        private const val KEY_DISMISSED_FINGERPRINT = "dismissed_fingerprint"
    }
}
