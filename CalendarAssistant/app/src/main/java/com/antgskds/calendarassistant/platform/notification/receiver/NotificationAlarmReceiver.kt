package com.antgskds.calendarassistant.platform.notification.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.developer.DebugActionRegistry
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTrigger
import com.antgskds.calendarassistant.platform.notification.alarm.AndroidSystemAlarmGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val key = intent?.getStringExtra(AndroidSystemAlarmGateway.EXTRA_NOTIFICATION_KEY)
        if (key.isNullOrBlank()) {
            Log.w(TAG, "Notification alarm received without key")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val app = context.applicationContext as? App
                if (app == null) {
                    Log.w(TAG, "Notification alarm ignored: app context unavailable")
                    return@launch
                }
                // Debug-only 测试台（WillDoNotify）：key 以 "debug:" 开头时，照 DebugActionRegistry 这张
                // 唯一清单分发调试动作（adb 与未来开发者页共用同一张表，无需改 Manifest）；
                // 其余 key 一律走真实提醒触发路径。
                if (key.startsWith(DEBUG_PREFIX)) {
                    val actionId = key.removePrefix(DEBUG_PREFIX)
                    val action = DebugActionRegistry.find(actionId)
                    if (action != null) {
                        action.execute(app)
                    } else {
                        Log.w(DebugActionRegistry.DEBUG_TAG, "unknown debug action: $actionId")
                    }
                } else {
                    app.notificationCenter.trigger(NotificationTrigger.ByKey(NotificationKey(key)))
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to trigger notification alarm key=$key", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NotificationAlarmReceiver"
        const val DEBUG_PREFIX = "debug:"
    }
}
