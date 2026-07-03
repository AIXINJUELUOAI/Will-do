package com.antgskds.calendarassistant.core.service.image

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.util.ImageImportUtils
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.NormalNotificationContent
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.RecognitionNormalDisplay
import java.io.File

/** Background image-share recognition so the task is not cancelled with the transient share Activity. */
class ImageShareRecognitionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val imagePath = inputData.getString(KEY_IMAGE_PATH).orEmpty()
        val sourceId = inputData.getString(KEY_SOURCE_ID).orEmpty().ifBlank { imagePath }
        if (imagePath.isBlank()) {
            showResult("识别失败", "图片路径缺失")
            return Result.success()
        }

        val app = applicationContext as App
        app.contentIngestCenter
        app.notificationCenter.showRecognitionStatusNotification(
            notificationId = NotificationIds.IMAGE_SHARE_RECOGNITION_STATUS,
            content = RecognitionNormalDisplay.analyzing(),
            isProgress = true,
            autoLaunch = false
        )

        var bitmap: Bitmap? = null
        return try {
            val imageFile = File(imagePath)
            bitmap = ImageImportUtils.decodeSampledBitmapFromFile(imageFile)
            val decodedBitmap = bitmap ?: error("图片解码失败")
            when (val result = app.recognitionCenter.analyzeImage(
                bitmap = decodedBitmap,
                settings = app.settingsQueryApi.settings.value,
                context = applicationContext,
                sourceType = "image_share",
                sourceId = sourceId,
                sourceImagePath = imagePath,
                ingestRequested = true,
                traceId = EventIdentity.newTraceId("image_share")
            )) {
                is AnalysisResult.Success -> {
                    val count = result.data.size
                    showResult(
                        title = "识别完成",
                        content = if (count > 0) "已识别 $count 个日程，正在保存" else "未识别到有效日程",
                        durationMs = 5000L
                    )
                }
                is AnalysisResult.Empty -> showResult("识别完成", result.message, durationMs = 8000L)
                is AnalysisResult.Failure -> showResult("识别失败", result.failure.fullMessage(), durationMs = 8000L)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Image share recognition failed", e)
            showResult("识别失败", e.message ?: "图片识别失败", durationMs = 8000L)
            Result.success()
        } finally {
            bitmap?.recycleIfNeeded()
        }
    }

    private fun showResult(title: String, content: String, durationMs: Long = 8000L) {
        val app = applicationContext as App
        app.notificationCenter.showRecognitionStatusNotification(
            notificationId = NotificationIds.IMAGE_SHARE_RECOGNITION_STATUS,
            content = NormalNotificationContent(title = title, contentText = content),
            isProgress = false,
            autoLaunch = false,
            durationMs = durationMs
        )
    }

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }

    companion object {
        private const val TAG = "ImageShareRecognitionWorker"
        private const val KEY_IMAGE_PATH = "image_path"
        private const val KEY_SOURCE_ID = "source_id"

        fun enqueue(context: Context, imagePath: String, sourceId: String) {
            val request = OneTimeWorkRequestBuilder<ImageShareRecognitionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_IMAGE_PATH to imagePath,
                        KEY_SOURCE_ID to sourceId
                    )
                )
                .addTag("image_share_recognition")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
