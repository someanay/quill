package com.tihor.quill.stt

import com.tihor.quill.errors.DictationIssue
import org.junit.Assert.*
import org.junit.Test

class ProviderFailureTest {
    @Test fun httpStatusRecovery() {
        mapOf(401 to DictationIssue.BAD_KEY, 402 to DictationIssue.QUOTA,
            403 to DictationIssue.ACCESS_DENIED, 404 to DictationIssue.ENDPOINT,
            408 to DictationIssue.TIMEOUT, 413 to DictationIssue.AUDIO_TOO_LARGE,
            415 to DictationIssue.AUDIO_FORMAT, 422 to DictationIssue.REQUEST_REJECTED,
            429 to DictationIssue.RATE_LIMIT, 500 to DictationIssue.SERVER,
            502 to DictationIssue.SERVER, 503 to DictationIssue.PROVIDER_BUSY,
            504 to DictationIssue.TIMEOUT).forEach { (status, issue) ->
            assertEquals("HTTP $status", issue, DictationIssue.from(TranscriptionError.fromHttp(status, "")))
        }
    }

    @Test fun knownProviderCodesOverrideGenericStatus() {
        assertEquals(DictationIssue.BAD_KEY, DictationIssue.from(TranscriptionError.fromHttp(400, """{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}""")))
        assertEquals(DictationIssue.MODEL, DictationIssue.from(TranscriptionError.fromHttp(404, """{"error":{"code":"model_not_found"}}""")))
        assertEquals(DictationIssue.AUDIO_FORMAT, DictationIssue.from(TranscriptionError.fromHttp(400, """{"error":{"code":"invalid_audio"}}""")))
        assertEquals(DictationIssue.QUOTA, DictationIssue.from(TranscriptionError.fromHttp(429, """{"error":{"code":"insufficient_quota"}}""")))
        // Gemini RESOURCE_EXHAUSTED can mean a temporary rate limit, too.
        assertEquals(DictationIssue.RATE_LIMIT, DictationIssue.from(TranscriptionError.fromHttp(429, """{"error":{"status":"RESOURCE_EXHAUSTED"}}""")))
    }

    @Test fun arbitraryProviderTextIsNeverRetainedOrDisplayed() {
        val privateText = "secret transcript sk-private-key"
        val error = TranscriptionError.fromHttp(500, """{"error":{"message":"$privateText"}}""")
        assertFalse(error.toString().contains(privateText))
        assertFalse(TranscriptionError.Provider(500, privateText).toString().contains(privateText))
        assertEquals(DictationIssue.SERVER, DictationIssue.from(error))
    }

    @Test fun unrelatedResponseTextCannotImpersonateErrorCodes() {
        assertEquals(DictationIssue.SERVER, DictationIssue.from(TranscriptionError.fromHttp(500, """{"text":"API_KEY_INVALID model_not_found"}""")))
        assertEquals(DictationIssue.REQUEST_REJECTED, DictationIssue.from(TranscriptionError.fromHttp(400, "<html>billing credit</html>")))
    }
}
