package com.tihor.quill.secrets

import com.tihor.quill.settings.SttProvider
import org.junit.Assert.*
import org.junit.Test

class KeySetupDraftsTest {
    @Test fun draftsSurviveRecreationStaySeparateAndCanBeDiscarded() {
        val store = object : SecretStore {
            val data = mutableMapOf<String, String>()
            override fun put(alias: String, secret: String) { data[alias] = secret }
            override fun get(alias: String) = data[alias]
            override fun clear(alias: String) { data.remove(alias) }
            override fun contains(alias: String) = alias in data
        }
        val drafts = KeySetupDrafts(store)
        drafts.write(SttProvider.GEMINI, "unfinished-gemini")
        drafts.write(SttProvider.GROQ, "unfinished-groq")
        assertEquals("unfinished-gemini", KeySetupDrafts(store).read(SttProvider.GEMINI))
        assertFalse(SttKeyStore(store).has(SttProvider.GEMINI))
        drafts.clear(SttProvider.GEMINI)
        assertEquals("", KeySetupDrafts(store).read(SttProvider.GEMINI))
        assertEquals("unfinished-groq", drafts.read(SttProvider.GROQ))
        drafts.write(SttProvider.GROQ, "")
        assertTrue(store.data.isEmpty())
    }
}
