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

        // First-run gate + crash opt-in: one quick DataStore read.
        val settings = runBlocking { SettingsRepository(this@MainActivity).settings.first() }
        // Opt-in, local-first crash reporting (M11): no-ops unless the user turned it on.
        CrashReporter.install(this, settings.crashReportingEnabled)
        if (!settings.onboardingComplete) startActivity(Intent(this, OnboardingActivity::class.java))

        // M13a: layer-3 OTA copy fetch — always runs (part of how the app works, not a preference).
        // Off the critical path: never blocks UI; an accepted bundle is cached and applied next launch.
        // Failure/offline/bad bundle is silent (cached-or-baked-in stands).
        lifecycleScope.launch {
            runCatching { Copy.repository(applicationContext).newUpdater(applicationContext).checkForUpdate() }
        }

        setContent { QuillRoot { QuillApp() } }
    }

    override fun onStart() {
        super.onStart()
        // Process-kill recovery (M11): if the bubble was killed while the user wanted it on, restart
        // it now — legal because the app is foregrounded. Respects a deliberate force-stop (the OS
        // wouldn't foreground us without a manual relaunch).
        lifecycleScope.launch { ForegroundRecovery.onForeground(this@MainActivity) }
    }
}
