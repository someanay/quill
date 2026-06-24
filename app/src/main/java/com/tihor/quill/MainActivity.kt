package com.tihor.quill

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tihor.quill.copy.QuillRoot
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.ui.OnboardingActivity
import com.tihor.quill.ui.app.QuillApp
import kotlinx.coroutines.flow.first
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

        // First-run gate: a quick DataStore read decides whether onboarding has been completed.
        val onboarded = runBlocking { SettingsRepository(this@MainActivity).settings.first().onboardingComplete }
        if (!onboarded) startActivity(Intent(this, OnboardingActivity::class.java))

        setContent { QuillRoot { QuillApp() } }
    }
}
