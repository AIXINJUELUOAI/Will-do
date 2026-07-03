package com.antgskds.calendarassistant.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.sms.SmsPickupSource
import com.antgskds.calendarassistant.data.source.SettingsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 短信广播接收器
 *
 * 监听 SMS_RECEIVED，将短信候选提交到统一协调器。
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        Log.d(TAG, "====== [探针] 收到 SMS_RECEIVED 广播 ======")

        val settings = SettingsDataSource(context).loadSettings()
        Log.d(TAG, "[探针] Settings 同步读取完成, isSmsMonitoringEnabled=${settings.isSmsMonitoringEnabled}")
        if (!settings.isSmsMonitoringEnabled) {
            Log.d(TAG, "[探针] 短信监控未开启，跳过")
            return
        }

        val messages = getSmsMessages(intent)
        Log.d(TAG, "[探针] 解析到 ${messages.size} 条短信 PDU")

        val app = context.applicationContext as App
        val ingestCoordinator = app.smsPickupIngestCoordinator
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                for (msg in messages) {
                    val sender = msg.originatingAddress ?: continue
                    val body = msg.messageBody ?: continue
                    Log.d(TAG, "[探针] 收到短信 from=$sender, body=${body.take(80)}...")
                    ingestCoordinator.submit(
                        source = SmsPickupSource.SMS_RECEIVER,
                        sender = sender,
                        body = body,
                        smsId = msg.timestampMillis
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "[探针] 短信处理异常", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun getSmsMessages(intent: Intent): Array<SmsMessage> {
        val bundle = intent.extras ?: return emptyArray()
        val pdus = bundle.get("pdus") as? Array<*> ?: return emptyArray()
        val format = bundle.getString("format") ?: ""

        return pdus.mapNotNull { pdu ->
            try {
                SmsMessage.createFromPdu(pdu as ByteArray, format)
            } catch (_: Exception) {
                null
            }
        }.toTypedArray()
    }
}
