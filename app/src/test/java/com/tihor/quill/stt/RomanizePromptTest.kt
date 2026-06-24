package com.tihor.quill.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Hinglish romanization prompt is seeded only for the English/romanize default. */
class RomanizePromptTest {

    @Test
    fun english_getsTheHinglishPrompt() {
        assertEquals(RomanizePrompt.HINGLISH, RomanizePrompt.promptFor("en"))
        assertEquals(RomanizePrompt.HINGLISH, RomanizePrompt.promptFor("EN"))
    }

    @Test
    fun nonEnglishOrAutoDetect_getNoPrompt() {
        assertNull(RomanizePrompt.promptFor("hi"))
        assertNull(RomanizePrompt.promptFor("fr"))
        assertNull(RomanizePrompt.promptFor(null)) // auto-detect maps to null upstream
    }
}
