package com.tihor.quill.models

import com.tihor.quill.settings.SttProvider
import org.junit.Assert.*
import org.junit.Test

class FreeModelPolicyTest {
    @Test fun automaticCleanupNeverChoosesPaidOnlyOrUnknownProviders() {
        assertNull(FreeModelPolicy.cleanup(SttProvider.OPENAI))
        assertNull(FreeModelPolicy.cleanup(SttProvider.CUSTOM))
        assertEquals("openrouter/free", FreeModelPolicy.cleanup(SttProvider.OPENROUTER))
        assertNotNull(FreeModelPolicy.cleanup(SttProvider.GEMINI))
        assertNotNull(FreeModelPolicy.cleanup(SttProvider.GROQ))
    }
    @Test fun freeCandidatesBeatNewerPaidCandidates() {
        val models = listOf(ModelOption("new/transcribe", created = 500), ModelOption("old/asr", created = 1, isFree = true))
        assertEquals("old/asr", FreeModelPolicy.preferred(models, "new/transcribe", ModelUse.DICTATION))
    }
    @Test fun allPricingComponentsMustBeZeroAndCapabilityMustMatch() {
        val raw = """{"data":[
          {"id":"free/asr","architecture":{"output_modalities":["transcription"]},"pricing":{"prompt":"0","completion":"0"}},
          {"id":"paid/asr:free","architecture":{"output_modalities":["transcription"]},"pricing":{"prompt":"0","completion":"0.1"}},
          {"id":"unknown/asr","architecture":{"output_modalities":["transcription"]}},
          {"id":"free/chat","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0"}}
        ]}"""
        val models = ModelCatalog.parse(SttProvider.OPENROUTER, ModelUse.DICTATION, raw).models
        assertEquals(3, models.size)
        assertEquals(listOf("free/asr"), models.filter { it.isFree }.map { it.id })
        assertEquals(listOf("paid/asr:free"), models.filter { it.isPaid }.map { it.id })
    }
    @Test fun cachedPricingSurvivesReopening() {
        val dir = java.nio.file.Files.createTempDirectory("quill-pricing").toFile()
        try {
            val models = listOf(ModelOption("free/asr", isFree = true), ModelOption("paid/asr", isPaid = true))
            ModelCatalogCache(dir, "router").write(models)
            assertEquals(models, ModelCatalogCache(dir, "router").read())
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
}
