package com.tihor.quill.copy

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import com.tihor.quill.overlay.OverlayService
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BundledCopyTest {
    @Test fun upgradeIgnoresOldCopyCache_andCancelsOnlyRetiredJob() {
        val context = RuntimeEnvironment.getApplication()
        val jobs = context.getSystemService(JobScheduler::class.java)
        val component = ComponentName(context, OverlayService::class.java)
        jobs.schedule(JobInfo.Builder(1301, component).setMinimumLatency(1000).build())
        jobs.schedule(JobInfo.Builder(99, component).setMinimumLatency(1000).build())
        val cache = File(context.filesDir, "copy").apply { mkdirs() }
        File(cache, "strings.signed.json").writeText("""{"revision":999999,"strings":{"onboarding.welcome.title":"OLD REMOTE VALUE"}}""")
        val repo = CopyRepository(context)
        assertEquals(repo.bakedInValue(CopyKeys.WELCOME_TITLE), repo.get(CopyKeys.WELCOME_TITLE))
        assertFalse(repo.resolved.value.containsValue("OLD REMOTE VALUE"))
        assertNull(jobs.getPendingJob(1301))
        assertNotNull(jobs.getPendingJob(99))
    }

    @Test fun copyRuntimeHasNoNetworkOrRemoteOverrideEntryPoints() {
        val root = File("src/main/java/com/tihor/quill/copy")
        assertTrue(root.isDirectory)
        val source = root.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("https://", "http://", "okhttp", "retrofit", "applyCachedOverride", "fun applyOverride", "checkForUpdate").forEach {
            assertFalse("Remote copy capability remains: $it", source.contains(it))
        }
        assertFalse(File("src/main/AndroidManifest.xml").readText().contains("CopyUpdateJobService"))
    }
}
