package com.antgskds.calendarassistant.feature.linkanalysis.application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antgskds.calendarassistant.App

class LinkAnalysisWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("memoId",-1)
        val token = inputData.getString("token") ?: return Result.failure()
        if (id<=0) return Result.failure()
        (applicationContext as App).linkAnalysisCoordinator.run(id,token)
        return Result.success()
    }
}
