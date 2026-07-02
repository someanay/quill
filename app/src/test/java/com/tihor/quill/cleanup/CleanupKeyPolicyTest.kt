package com.tihor.quill.cleanup

import com.tihor.quill.settings.SttProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Decision 5: the M10 cleanup pass stays **Groq-only**. When the active STT provider isn't Groq,
 * cleanup uses the stored Groq key if present; with none it is skipped silently (fail-soft — the raw
 * transcript is inserted, dictation is never blocked). A `null` from [CleanupKeyPolicy.resolve]
 * means "skip cleanup", which callers feed into the existing fail-soft path.
 */
class CleanupKeyPolicyTest {

    @Test
    fun groqProvider_usesTheActiveKey() {
        assertEquals("gsk_active", CleanupKeyPolicy.resolve(SttProvider.GROQ, activeKey = "gsk_active", groqKey = null))
    }

    @Test
    fun otherProvider_usesStoredGroqKey() {
        SttProvider.entries.filter { it != SttProvider.GROQ }.forEach { provider ->
            assertEquals(
                "provider $provider must fall back to the stored Groq key",
                "gsk_stored",
                CleanupKeyPolicy.resolve(provider, activeKey = "other-key", groqKey = "gsk_stored"),
            )
        }
    }

    @Test
    fun otherProvider_withoutGroqKey_skipsCleanup() {
        assertNull(CleanupKeyPolicy.resolve(SttProvider.GEMINI, activeKey = "AIza-key", groqKey = null))
        assertNull(CleanupKeyPolicy.resolve(SttProvider.OPENAI, activeKey = "sk-key", groqKey = ""))
        assertNull(CleanupKeyPolicy.resolve(SttProvider.CUSTOM, activeKey = "k", groqKey = "  "))
    }

    @Test
    fun otherProvider_neverSendsTheActiveProvidersKeyToGroq() {
        // Key isolation (decision 3): a Gemini key must never be sent to Groq's endpoint.
        val resolved = CleanupKeyPolicy.resolve(SttProvider.GEMINI, activeKey = "AIza-key", groqKey = null)
        assertNull(resolved)
    }

    @Test
    fun blankActiveKeyOnGroq_skipsCleanup() {
        assertNull(CleanupKeyPolicy.resolve(SttProvider.GROQ, activeKey = "", groqKey = "unused"))
        assertNull(CleanupKeyPolicy.resolve(SttProvider.GROQ, activeKey = null, groqKey = "unused"))
    }
}
