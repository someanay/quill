package com.tihor.quill.copy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/**
 * Resolves UI copy by key (spec §2.3). v1 is offline-first: the complete baked-in catalog
 * (`assets/copy/strings.default.json`) is the source of truth and always-available fallback. The OTA
 * override layer (M13) calls [applyOverride], which **only** touches [CopyKeys.OVERRIDABLE] keys —
 * compliance copy is locked to the baked-in binary value and can never be changed over the air.
 */
class CopyRepository(context: Context) {

    private val bakedIn: Map<String, String> = loadBakedIn(context)
    private val _resolved = MutableStateFlow(bakedIn)
    val resolved: StateFlow<Map<String, String>> = _resolved

    /** Resolve [key], formatting with [args] if given. Missing keys fall back to the key itself. */
    fun get(key: String, vararg args: Any?): String {
        val template = _resolved.value[key] ?: key
        return if (args.isEmpty()) template else template.format(*args)
    }

    /**
     * Apply a validated remote override (M13). Overrides are restricted to overridable keys; any
     * compliance key in [override] is ignored so it stays at its baked-in value.
     */
    fun applyOverride(override: Map<String, String>) {
        val merged = bakedIn.toMutableMap()
        override.forEach { (k, v) -> if (k in CopyKeys.OVERRIDABLE) merged[k] = v }
        _resolved.value = merged
    }

    /** True if the baked-in catalog contains every key the code references (CI completeness check). */
    fun isComplete(): Boolean = CopyKeys.ALL.all { bakedIn.containsKey(it) }

    fun bakedInValue(key: String): String? = bakedIn[key]

    private fun loadBakedIn(context: Context): Map<String, String> {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        return JSON.decodeFromString(CopyCatalog.serializer(), text).strings
    }

    companion object {
        private const val ASSET_PATH = "copy/strings.default.json"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
