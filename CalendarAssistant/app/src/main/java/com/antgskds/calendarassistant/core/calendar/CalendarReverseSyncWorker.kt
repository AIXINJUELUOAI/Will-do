package com.antgskds.calendarassistant.core.calendar

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.antgskds.calendarassistant.data.repository.AppRepository
import java.util.concurrent.TimeUnit

class CalendarReverseSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Reverse sync worker started")
            val repository = AppRepository.getInstance(applicationContext)
            val result = repository.syncFromCalendar()
            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                Log.d(TAG, "Reverse sync worker success: count=$count")
                Result.success()
            } else {
                Log.w(TAG, "Reverse sync failed: ${result.exceptionOrNull()?.message}")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reverse sync worker exception", e)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "CalendarReverseSyncWorker"
        private const val UNIQUE_WORK_NAME = "calendar_reverse_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CalendarReverseSyncWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
