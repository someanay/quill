package com.tihor.quill.copy.ota

import android.app.job.JobParameters
import android.app.job.JobService
import com.tihor.quill.copy.Copy
import kotlinx.coroutines.*

/** Android owns the timing; no foreground service or exact alarm is needed for copy. */
class CopyUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    val repo = Copy.repository(applicationContext)
                    repo.applyCachedOverride(applicationContext)
                    repo.newUpdater(applicationContext).checkForUpdate().also {
                        if (it is CopyUpdater.Outcome.Accepted) repo.applyCachedOverride(applicationContext)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CopyUpdater.Outcome.Failed("background check unavailable")
            }
            ensureActive()
            jobFinished(params, outcome is CopyUpdater.Outcome.Failed)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
