package com.antgskds.calendarassistant.core.update

import android.util.Log
import com.antgskds.calendarassistant.BuildConfig
import com.antgskds.calendarassistant.data.model.RemoteAppUpdateInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

sealed interface AppUpdateCheckResult {
    data class Success(
        val info: RemoteAppUpdateInfo,
        val hasUpdate: Boolean,
        val localVersionCode: Int
    ) : AppUpdateCheckResult

    data class Error(val message: String) : AppUpdateCheckResult
}

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
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

    suspend fun check(): AppUpdateCheckResult {
        val url = BuildConfig.APP_UPDATE_URL.trim().removeSurrounding("\"")
        if (url.isBlank()) {
            Log.d(TAG, "跳过检查：APP_UPDATE_URL 为空")
            return AppUpdateCheckResult.Error("未配置软件更新地址")
        }

        return try {
            Log.d(TAG, "开始检查软件更新：localVersionCode=${BuildConfig.VERSION_CODE}, url=$url")
            val responseText = client.get(url).bodyAsText()
            val info = json.decodeFromString<RemoteAppUpdateInfo>(responseText)

            if (!info.isValid()) {
                Log.w(TAG, "云端软件更新数据无效，versioncode=${info.versioncode}")
                AppUpdateCheckResult.Error("云端软件更新数据无效")
            } else {
                AppUpdateCheckResult.Success(
                    info = info,
                    hasUpdate = info.versioncode > BuildConfig.VERSION_CODE,
                    localVersionCode = BuildConfig.VERSION_CODE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查软件更新失败: ${e.javaClass.simpleName}: ${e.message}")
            AppUpdateCheckResult.Error("检查失败，请稍后重试")
        }
    }
}
