package com.tihor.quill

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.tihor.quill.copy.QuillRoot
import com.tihor.quill.crash.CrashReporter
import com.tihor.quill.reliability.ForegroundRecovery
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.ui.OnboardingActivity
import com.tihor.quill.ui.app.QuillApp
import com.tihor.quill.ui.app.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Single-activity host (board B–F shell). Wraps the app in [QuillRoot] (Copy Catalog + Ink&Signal
 * theme) and hands off to [QuillApp], which owns the bottom-nav back stack. First run routes to
 * onboarding (board A) before the control center shows.
 */
class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_ERROR_SETTINGS = "error_settings" }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // First-run gate + crash opt-in: one quick DataStore read.
        val settings = runBlocking { SettingsRepository(this@MainActivity).settings.first() }
        // Opt-in, local-first crash reporting (M11): no-ops unless the user turned it on.
        CrashReporter.install(this, settings.crashReportingEnabled)
        if (!settings.onboardingComplete) startActivity(Intent(this, OnboardingActivity::class.java))

        val errorSettings = when (intent.getStringExtra(EXTRA_ERROR_SETTINGS)) {
            "keys" -> Screen.ApiKeys
            "speech" -> Screen.SpeechModel
            else -> Screen.Home
        }
        setContent { QuillRoot { QuillApp(initialScreen = errorSettings) } }
    }

    override fun onStart() {
        super.onStart()
        // Process-kill recovery (M11): if the bubble was killed while the user wanted it on, restart
        // it now — legal because the app is foregrounded. Respects a deliberate force-stop (the OS
        // wouldn't foreground us without a manual relaunch).
        lifecycleScope.launch { ForegroundRecovery.onForeground(this@MainActivity) }
    }
}
