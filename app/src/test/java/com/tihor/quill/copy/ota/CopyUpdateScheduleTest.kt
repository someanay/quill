package com.tihor.quill.copy.ota

import android.app.job.JobScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyUpdateScheduleTest {
    @Test fun periodicCheckNeedsNetworkAndSurvivesReboot() {
        val context = RuntimeEnvironment.getApplication()
        CopyUpdateSchedule.ensureScheduled(context)
        val job = context.getSystemService(JobScheduler::class.java)
            .getPendingJob(CopyUpdateSchedule.JOB_ID)!!
        assertTrue(job.isPeriodic)
        assertTrue(job.isPersisted)
        assertEquals(TimeUnit.HOURS.toMillis(6), job.intervalMillis)
        assertEquals(android.app.job.JobInfo.NETWORK_TYPE_ANY, job.networkType)
        assertFalse(job.isRequireCharging)
        assertEquals(CopyUpdateJobService::class.java.name, job.service.className)
    }

    @Test fun reopeningKeepsExistingSchedule() {
        val context = RuntimeEnvironment.getApplication()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        CopyUpdateSchedule.ensureScheduled(context)
        val first = scheduler.getPendingJob(CopyUpdateSchedule.JOB_ID)
        CopyUpdateSchedule.ensureScheduled(context)
        assertSame(first, scheduler.getPendingJob(CopyUpdateSchedule.JOB_ID))
        assertEquals(1, scheduler.allPendingJobs.count { it.id == CopyUpdateSchedule.JOB_ID })
    }
}
