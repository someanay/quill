package com.tihor.quill.errors

import com.tihor.quill.stt.TranscriptionError
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException

class DictationIssueTest {
    @Test fun missingPermissions_haveDistinctRecoveryAndNeverClaimSavedAudio() {
        assertEquals(DictationIssue.MIC_PERMISSION, DictationIssue.preflight(false, false, true, true, false))
        assertEquals(DictationIssue.MIC_PRIVACY, DictationIssue.preflight(true, true, true, true, false))
        assertEquals(DictationIssue.OVERLAY_PERMISSION, DictationIssue.preflight(true, false, false, true, false))
        assertEquals(DictationIssue.ACCESSIBILITY, DictationIssue.preflight(true, false, true, false, false))
        assertNull(DictationIssue.preflight(true, false, true, false, true)) // explicit clipboard mode
        assertEquals(Recovery.APP_PERMISSIONS, DictationIssue.MIC_PERMISSION.recovery(false))
        assertFalse(DictationIssue.MIC_PERMISSION.canRetryAudio)
    }

    @Test fun retryRequiresUsableAudio_andSetupErrorsOpenSettings() {
        assertEquals(Recovery.RECORD_AGAIN, DictationIssue.TIMEOUT.recovery(false))
        assertEquals(Recovery.RETRY, DictationIssue.TIMEOUT.recovery(true))
        assertEquals(Recovery.PROVIDER_SETTINGS, DictationIssue.BAD_KEY.recovery(true))
        assertEquals(Recovery.RECORD_AGAIN, DictationIssue.AUDIO_MISSING.recovery(true))
        assertEquals(Recovery.DISMISS, DictationIssue.COPIED_NO_FIELD.recovery(false))
    }

    @Test fun transportFailuresAreSpecific() {
        mapOf(
            UnknownHostException() to DictationIssue.DNS,
            SocketTimeoutException() to DictationIssue.TIMEOUT,
            ConnectException() to DictationIssue.CONNECTION,
            SSLHandshakeException("private detail") to DictationIssue.SECURE_CONNECTION,
        ).forEach { (cause, expected) -> assertEquals(expected, DictationIssue.from(TranscriptionError.Network(cause))) }
    }

    @Test fun providerAndRecordingFailuresAreNotCollapsed() {
        assertEquals(DictationIssue.BAD_KEY, DictationIssue.from(TranscriptionError.BadKey()))
        assertEquals(DictationIssue.RATE_LIMIT, DictationIssue.from(TranscriptionError.RateLimited()))
        assertEquals(DictationIssue.QUOTA, DictationIssue.from(TranscriptionError.QuotaExhausted()))
        assertEquals(DictationIssue.RESPONSE_INVALID, DictationIssue.from(TranscriptionError.Parse(Exception())))
        assertEquals(DictationIssue.NO_SPEECH, DictationIssue.from(TranscriptionError.EmptyResponse()))
        assertEquals(DictationIssue.BLOCKED, DictationIssue.from(TranscriptionError.BlockedResponse()))
        assertEquals(DictationIssue.INCOMPLETE, DictationIssue.from(TranscriptionError.IncompleteResponse()))
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotAnError() { DictationIssue.from(CancellationException()) }
}
