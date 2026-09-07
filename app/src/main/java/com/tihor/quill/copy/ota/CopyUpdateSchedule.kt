package com.tihor.quill.copy.ota

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

/** One persistent schedule; reopening the app must not push the next check into the future. */
object CopyUpdateSchedule {
    const val JOB_ID = 1301
    private val interval = TimeUnit.HOURS.toMillis(6)

    fun ensureScheduled(context: Context) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val service = ComponentName(context, CopyUpdateJobService::class.java)
            val existing = scheduler.getPendingJob(JOB_ID)
            if (existing?.service == service && existing.isPeriodic && existing.isPersisted &&
                existing.intervalMillis == interval && existing.networkType == JobInfo.NETWORK_TYPE_ANY) return
            val job = JobInfo.Builder(JOB_ID, service)
                .setPeriodic(interval)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(TimeUnit.MINUTES.toMillis(30), JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                Log.d("QuillCopyUpdate", "Background schedule unavailable; foreground checks remain active")
            }
        } catch (e: RuntimeException) {
            Log.d("QuillCopyUpdate", "Background schedule unavailable", e)
        }
    }
}
