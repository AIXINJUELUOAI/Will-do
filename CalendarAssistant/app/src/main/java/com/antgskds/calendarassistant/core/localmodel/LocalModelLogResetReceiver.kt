package com.antgskds.calendarassistant.core.localmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.aiengine.AiEngineLog

class LocalModelLogResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RESET_LOG) return
        AiEngineLog.resetForToday(context.applicationContext)
        LocalModelLogScheduler.scheduleNext(context.applicationContext)
    }

    companion object {
        const val ACTION_RESET_LOG = "com.antgskds.calendarassistant.action.RESET_LOCAL_MODEL_LOG"
    }
}
