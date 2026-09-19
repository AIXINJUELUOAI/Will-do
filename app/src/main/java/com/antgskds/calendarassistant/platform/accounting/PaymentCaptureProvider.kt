package com.antgskds.calendarassistant.platform.accounting

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** 只接收支付应用自身 UID 的 Hook 数据。开关关闭时不解析、不写库；不开放查询账本。 */
class PaymentCaptureProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = requireNotNull(context)
        val callerPackages = context.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        val source = arg?.takeIf { AutomaticAccountingPolicy.supports(it) && it in callerPackages }
            ?: throw SecurityException("Unsupported payment caller")
        val app = context.applicationContext as App
        val settings = app.settingsQueryApi.settings.value
        val enabled = AutomaticAccountingPolicy.enabled(settings)
        if (method == "status") return Bundle().apply { putBoolean("enabled", enabled) }
        if (method != "capture" || !enabled) return Bundle().apply { putString("status", "disabled") }
        val payload = extras?.getString("payload").orEmpty()
        if (payload.isBlank() || payload.length > ConfigCatalog.AUTO_ACCOUNTING_MAX_PAYLOAD) {
            return Bundle().apply { putString("status", "rejected") }
        }
        // 仅本地解析与事务，不执行网络。同步返回实际处理结果，避免进程退出丢失已应答消息。
        return try {
            val receivedAt = System.currentTimeMillis()
            val trace = "payment:" + AutomaticAccountingPolicy.fingerprint("$source|$payload")
            val result = runBlocking(Dispatchers.IO) {
                withTimeout(ConfigCatalog.AUTO_ACCOUNTING_PROVIDER_TIMEOUT_MS.toLong()) {
                    app.recognitionApi.analyzeAutomaticPaymentMessage(source, payload, receivedAt, settings, trace)
                }
            }
            val status = when (result) {
                is AnalysisResult.Success -> "processed"
                is AnalysisResult.Empty -> "unmatched"
                is AnalysisResult.Failure -> "failed"
            }
            AppLogger.i("WillDoAccounting", "hook source=$source status=$status")
            Bundle().apply { putString("status", status) }
        } catch (e: Exception) {
            AppLogger.w("WillDoAccounting", "hook source=$source error=${e.javaClass.simpleName}")
            Bundle().apply { putString("status", "failed") }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
