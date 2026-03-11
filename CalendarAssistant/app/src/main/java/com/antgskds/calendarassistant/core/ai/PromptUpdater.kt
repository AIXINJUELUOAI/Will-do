package com.antgskds.calendarassistant.core.ai

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.BuildConfig
import com.antgskds.calendarassistant.data.model.RemotePrompts
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class PromptUpdateCandidate(
    val remotePrompts: RemotePrompts,
    val localVersion: Int
)

sealed interface PromptCheckResult {
    data class UpdateAvailable(val candidate: PromptUpdateCandidate) : PromptCheckResult
    data class NoUpdate(
        val localVersion: Int,
        val remoteVersion: Int,
        val ignoredVersion: Int,
        val blockedByIgnoredVersion: Boolean
    ) : PromptCheckResult
    data class Error(val message: String) : PromptCheckResult
}

object PromptUpdater {

    private const val TAG = "PromptUpdater"
    private const val REQUEST_TIMEOUT_MS = 15_000L

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }

    suspend fun check(context: Context, ignoreIgnoredVersion: Boolean = false): PromptCheckResult {
        val url = BuildConfig.PROMPT_UPDATE_URL.trim().removeSurrounding("\"")
        if (url.isBlank()) {
            Log.d(TAG, "跳过检查：PROMPT_UPDATE_URL 为空")
            return PromptCheckResult.Error("未配置云端 prompt 地址")
        }

        val appContext = context.applicationContext

        return try {
            val localVersion = AiPrompts.getLocalVersion(appContext)
            val ignoredVersion = AiPrompts.getIgnoredVersion(appContext)
            Log.d(TAG, "开始检查云端 prompt：localVersion=$localVersion, ignoredVersion=$ignoredVersion, url=$url")

            val responseText = client.get(url).bodyAsText()
            val remotePrompts = json.decodeFromString<RemotePrompts>(responseText)
            if (!remotePrompts.isValid()) {
                Log.w(TAG, "云端 prompt 无效，version=${remotePrompts.version}")
                PromptCheckResult.Error("云端 prompt 数据无效")
            } else {
                val hasNewerVersion = remotePrompts.version > localVersion
                val blockedByIgnoredVersion = hasNewerVersion && remotePrompts.version <= ignoredVersion

                if (hasNewerVersion && (ignoreIgnoredVersion || !blockedByIgnoredVersion)) {
                    Log.d(TAG, "发现可用更新：remoteVersion=${remotePrompts.version}")
                    PromptCheckResult.UpdateAvailable(
                        PromptUpdateCandidate(
                            remotePrompts = remotePrompts,
                            localVersion = localVersion
                        )
                    )
                } else {
                    Log.d(
                        TAG,
                        "无需弹窗：remoteVersion=${remotePrompts.version}, localVersion=$localVersion, ignoredVersion=$ignoredVersion"
                    )
                    PromptCheckResult.NoUpdate(
                        localVersion = localVersion,
                        remoteVersion = remotePrompts.version,
                        ignoredVersion = ignoredVersion,
                        blockedByIgnoredVersion = blockedByIgnoredVersion
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查云端 prompt 失败: ${e.javaClass.simpleName}: ${e.message}")
            PromptCheckResult.Error("检查失败，请稍后重试")
        }
    }
}
