package com.tihor.quill.copy

import android.app.job.JobScheduler
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/** Interface text comes exclusively from this installed APK. Updates require an app release. */
class CopyRepository(context: Context) {
    init {
        // Upgrade migration: retire only our former copy-update job. Never schedule a replacement.
        runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(1301) }
    }
    private val bakedIn = context.assets.open("copy/strings.default.json").bufferedReader().use {
        Json { ignoreUnknownKeys = true }.decodeFromString(CopyCatalog.serializer(), it.readText()).strings
    }
    val resolved: StateFlow<Map<String, String>> = MutableStateFlow(bakedIn)

    fun get(key: String, vararg args: Any?): String {
        val template = bakedIn[key] ?: key
        return if (args.isEmpty()) template else template.format(*args)
    }
    fun isComplete(): Boolean = CopyKeys.ALL.all { bakedIn.containsKey(it) }
    fun bakedInValue(key: String): String? = bakedIn[key]
}
