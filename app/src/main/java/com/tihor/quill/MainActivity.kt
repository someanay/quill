package com.tihor.quill

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.tihor.quill.copy.Copy
import com.tihor.quill.copy.QuillRoot
import com.tihor.quill.crash.CrashReporter
import com.tihor.quill.reliability.ForegroundRecovery
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.ui.OnboardingActivity
import com.tihor.quill.ui.app.QuillApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Single-activity host (board B–F shell). Wraps the app in [QuillRoot] (Copy Catalog + Ink&Signal
 * theme) and hands off to [QuillApp], which owns the bottom-nav back stack. First run routes to
 * onboarding (board A) before the control center shows.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.tihor.quill.copy.ota.CopyUpdateSchedule.ensureScheduled(applicationContext)

        // First-run gate + crash opt-in: one quick DataStore read.
        val settings = runBlocking { SettingsRepository(this@MainActivity).settings.first() }
        // Opt-in, local-first crash reporting (M11): no-ops unless the user turned it on.
        CrashReporter.install(this, settings.crashReportingEnabled)
        if (!settings.onboardingComplete) startActivity(Intent(this, OnboardingActivity::class.java))

        setContent { QuillRoot { QuillApp() } }
    }

    override fun onStart() {
        super.onStart()
        // Re-apply the cached OTA copy bundle on every foreground (not just cold start), before the UI
        // re-engages, so a bundle fetched on a previous open shows up on this resume. Cheap + idempotent
        // (small file read + Ed25519 verify; falls back to baked-in). QuillRoot observes resolved copy,
        // so this recomposes live. Synchronous to avoid a flicker mid-read.
        Copy.repository(applicationContext).applyCachedOverride(applicationContext)
        // M13a: layer-3 OTA copy fetch — runs on EVERY foreground (not just cold start). Off the
        // critical path: never blocks UI. If the fetch ACCEPTS a newer bundle, apply it immediately so
        // the new copy shows live in THIS session (QuillRoot observes resolved copy and recomposes) —
        // no second open or cold restart needed. Failure/offline/bad bundle is silent (cached/baked-in
        // stands). The apply is the same cheap idempotent re-verify used on foreground above.
        lifecycleScope.launch {
            val repo = Copy.repository(applicationContext)
            val outcome = runCatching {
                repo.newUpdater(applicationContext).checkForUpdate()
            }.getOrNull()
            if (outcome is com.tihor.quill.copy.ota.CopyUpdater.Outcome.Accepted) {
                repo.applyCachedOverride(applicationContext)
            }
        }
        // Process-kill recovery (M11): if the bubble was killed while the user wanted it on, restart
        // it now — legal because the app is foregrounded. Respects a deliberate force-stop (the OS
        // wouldn't foreground us without a manual relaunch).
        lifecycleScope.launch { ForegroundRecovery.onForeground(this@MainActivity) }
    }
}
