package com.tihor.quill.crash

import android.content.Context
import java.io.File

/**
 * Opt-in, local-first crash reporting (M11). Default OFF (privacy posture): no SDK, no auto-upload.
 * When enabled, the uncaught-exception handler writes a scrubbed stack trace to private storage and
 * then delegates to the platform handler; the user reviews and shares it manually
 * ([com.tihor.quill.ui.screens.CrashReportScreen]). The report carries only the error + stack + app
 * and device metadata — never transcript text, audio, clipboard, or API keys.
 */
object CrashReporter {

    @Volatile private var enabled = false
    private var previous: Thread.UncaughtExceptionHandler? = null
    private lateinit var appContext: Context

    /**
     * Install the handler once (from the host Activity), passing the persisted opt-in flag. Kept free
     * of the settings layer so the reporter is self-contained; the caller reads the flag and supplies it.
     */
    fun install(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        this.enabled = enabled
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (enabled) runCatching { write(throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Keep the cached flag in sync with the toggle, so we don't read DataStore at crash time. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** The most recent stored report, or null if none. */
    fun latest(): String? {
        if (!::appContext.isInitialized) return null
        val f = reportFile()
        return if (f.exists()) f.readText() else null
    }

    fun clear() {
        if (!::appContext.isInitialized) return
        runCatching { reportFile().delete() }
    }

    private fun write(throwable: Throwable) {
        val dir = File(appContext.filesDir, DIR).apply { mkdirs() }
        File(dir, FILE).writeText(format(throwable, appVersion(), device()))
    }

    /** Pure formatter (unit-testable): error + stack + app/device meta only. */
    fun format(throwable: Throwable, appVersion: String, device: String): String = buildString {
        appendLine("Quill crash report")
        appendLine("app: $appVersion")
        appendLine("device: $device")
        appendLine("error: ${throwable.javaClass.name}")
        appendLine()
        append(throwable.stackTraceToString())
    }

    private fun reportFile() = File(File(appContext.filesDir, DIR), FILE)

    private fun appVersion() = runCatching {
        val pi = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        "${pi.versionName} (${pi.longVersionCode})"
    }.getOrDefault("unknown")

    private fun device() =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · API ${android.os.Build.VERSION.SDK_INT}"

    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"
}
