package com.antgskds.calendarassistant.feature.quickmemo.application

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.App

class QuickMemoTranscriptionResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val memoId = intent.getLongExtra(QuickMemoTranscriptionService.EXTRA_MEMO_ID, -1L)
        if (memoId <= 0L) return
        val app = context.applicationContext as? App ?: return
        when (intent.action) {
            QuickMemoTranscriptionService.ACTION_TRANSCRIPTION_SUCCEEDED -> {
                app.quickMemoCenter.onExternalTranscriptionSucceeded(
                    id = memoId,
                    text = intent.getStringExtra(QuickMemoTranscriptionService.EXTRA_TEXT).orEmpty(),
                    autoPin = intent.getBooleanExtra(QuickMemoTranscriptionService.EXTRA_AUTO_PIN, false)
                )
            }
            QuickMemoTranscriptionService.ACTION_TRANSCRIPTION_FAILED -> {
                app.quickMemoCenter.onExternalTranscriptionFailed(memoId)
            }
        }
    }
}
