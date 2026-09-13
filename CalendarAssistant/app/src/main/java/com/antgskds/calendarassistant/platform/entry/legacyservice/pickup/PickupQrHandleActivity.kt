package com.antgskds.calendarassistant.core.service.pickup

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.feature.schedule.domain.model.isImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PickupQrHandleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val quickMemoId = intent.getLongExtra(MainActivity.EXTRA_OPEN_QUICK_MEMO_ID, -1L).takeIf { it > 0L }
        val eventId = intent.getLongExtra(MainActivity.EXTRA_OPEN_EVENT_ID, -1L).takeIf { it > 0L }
        if (quickMemoId == null && eventId == null) {
            finishWithoutAnimation()
            return
        }

        val app = applicationContext as App
        lifecycleScope.launch {
            if (quickMemoId != null) {
                handleQuickMemo(app, quickMemoId)
            } else if (eventId != null) {
                handleEvent(app, eventId)
            }
            finishWithoutAnimation()
        }
    }

    private suspend fun handleEvent(app: App, eventId: Long) {
        val event = app.scheduleCenter.events.value.firstOrNull { it.id == eventId }
        if (event == null) {
            openEvent(eventId)
            return
        }
        val imageAttachments = withContext(Dispatchers.IO) {
            app.eventAttachmentManager.getAttachments(eventId)
                .filter { it.isImage }
                .sortedWith(compareBy({ it.createdAt }, { it.id ?: Long.MAX_VALUE }))
        }
        val invalidImage = imageAttachments.any { !File(it.localPath).isFile }
        val imagePaths = imageAttachments.filter { File(it.localPath).isFile }.map { it.localPath }
        if (invalidImage) {
            openEvent(eventId, "图片无法打开")
            return
        }
        val hasMedia = event.codeQrPayload.isNotBlank() || imagePaths.isNotEmpty()
        if (!hasMedia || !app.floatingCenter.startEventMediaCard(eventId, imagePaths)) {
            openEvent(eventId)
        }
    }

    private suspend fun handleQuickMemo(app: App, memoId: Long) {
        val memo = withContext(Dispatchers.IO) { app.quickMemoCenter.getQuickMemo(memoId) }
        val imagePath = memo?.imagePath?.takeIf { it.isNotBlank() }
        if (imagePath == null) {
            openQuickMemo(memoId)
            return
        }
        if (!File(imagePath).isFile) {
            openQuickMemo(memoId, "图片无法打开")
            return
        }
        if (!app.floatingCenter.startQuickMemoMediaCard(memoId, imagePath, memo.bodyText)) {
            openQuickMemo(memoId)
        }
    }

    private fun openEvent(eventId: Long, message: String? = null) {
        message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, eventId)
        })
    }

    private fun openQuickMemo(memoId: Long, message: String? = null) {
        message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_QUICK_MEMO_ID, memoId)
        })
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }
}
