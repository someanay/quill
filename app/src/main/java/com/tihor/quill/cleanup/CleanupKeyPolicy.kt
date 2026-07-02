package com.tihor.quill.cleanup

import com.tihor.quill.settings.SttProvider

/**
 * Decision 5 (2026-07): the M10 cleanup pass stays **Groq-only** (fixed Groq chat model). With a
 * non-Groq STT provider active, cleanup uses the **stored Groq key** if there is one; without one it
 * is skipped silently — the raw transcript is inserted, dictation is never blocked (fail-soft).
 * Returning `null` means "skip cleanup". Never resolves to the active provider's key: a key entered
 * for provider A is never sent to provider B (decision 3).
 */
object CleanupKeyPolicy {
    fun resolve(provider: SttProvider, activeKey: String?, groqKey: String?): String? {
        val candidate = if (provider == SttProvider.GROQ) activeKey else groqKey
        return candidate?.takeUnless { it.isBlank() }
    }
}
