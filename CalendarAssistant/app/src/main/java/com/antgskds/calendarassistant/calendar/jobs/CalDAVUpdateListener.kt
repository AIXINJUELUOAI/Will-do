package com.antgskds.calendarassistant.calendar.jobs

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.antgskds.calendarassistant.core.center.CalendarCenter
import java.util.concurrent.Executors

class CalDAVUpdateListener : JobService() {

    private val handler = Handler(Looper.getMainLooper())
    private var runningParams: JobParameters? = null
    // 用于在后台线程执行同步操作，避免主线程 Room 崩溃
    private val executor = Executors.newSingleThreadExecutor()
    private val worker = Runnable {
        scheduleJob(this)
        jobFinished(runningParams, false)
    }

    fun scheduleJob(context: Context) {
        val componentName = ComponentName(context, CalDAVUpdateListener::class.java)
        val uri = CalendarContract.Events.CONTENT_URI
        JobInfo.Builder(CALDAV_EVENT_CONTENT_JOB, componentName).apply {
            addTriggerContentUri(JobInfo.TriggerContentUri(uri, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).schedule(build())
        }
    }

    fun isScheduled(context: Context): Boolean {
        val jobScheduler = context.getSystemService(JobScheduler::class.java)
        return jobScheduler.allPendingJobs.any { it.id == CALDAV_EVENT_CONTENT_JOB }
    }

    fun cancelJob(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java)
        js.cancel(CALDAV_EVENT_CONTENT_JOB)
    }

    override fun onStartJob(params: JobParameters): Boolean {
        runningParams = params
        if (params.triggeredContentAuthorities != null && params.triggeredContentUris != null) {
            // ✅ 关键修复：在后台线程执行同步，避免主线程 Room IllegalStateException
            executor.execute {
                try {
                    CalendarCenter.getInstance(applicationContext).onSystemCalendarChanged()
                    // ✅ 同步完成后刷新内存中的事件列表，让 UI 能看到导入的事件
                    (applicationContext as? com.antgskds.calendarassistant.App)?.scheduleCenter?.refreshEvents()
                } catch (e: Exception) {
                    android.util.Log.e("CalDAVUpdate", "onSystemCalendarChanged failed", e)
                } finally {
                    // 同步完成后再 reschedule + jobFinished
                    handler.post(worker)
                }
            }
        } else {
            handler.post(worker)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        handler.removeCallbacks(worker)
        return false
    }

    companion object {
        const val CALDAV_EVENT_CONTENT_JOB = 1
    }
}
