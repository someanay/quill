package com.tihor.quill.audio

import android.Manifest
import android.media.AudioManager
import com.tihor.quill.errors.DictationIssue
import com.tihor.quill.copy.Copy
import com.tihor.quill.errors.Recovery
import com.tihor.quill.overlay.CaptionView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingPermissionTest {
    @Test fun deniedMicrophoneDoesNotCreateRecordingOrStartCapture() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val output = File(app.cacheDir, "denied-mic.wav").apply { delete() }
        val recorder = AudioRecorder(app)
        var reason: StopReason? = null
        recorder.start(output, onFinished = { _, stopped -> reason = stopped })
        assertEquals(StopReason.ERROR, reason)
        assertEquals(DictationIssue.MIC_PERMISSION, recorder.lastIssue)
        assertFalse(recorder.isRecording)
        assertFalse(output.exists())
    }

    @Test fun mutedMicrophoneIsDistinctFromMissingAppPermission() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        app.getSystemService(AudioManager::class.java).isMicrophoneMute = true
        assertEquals(DictationIssue.MIC_PRIVACY, AudioRecorder(app).microphoneIssue())
    }

    @Test fun captionNamesPermissionAndRecoveryWithoutClaimingAudioWasSaved() {
        val app = RuntimeEnvironment.getApplication()
        val copy = Copy.repository(app)
        val issue = DictationIssue.MIC_PERMISSION
        val caption = CaptionView(app)
        caption.configureError(copy.get(issue.titleKey), copy.get(Recovery.APP_PERMISSIONS.labelKey), copy.get(issue.messageKey))
        assertTrue(caption.contentDescription.toString().contains("Microphone"))
        assertTrue(caption.contentDescription.toString().contains("No audio was recorded"))
        assertFalse(caption.contentDescription.toString().contains("Tap to retry"))
        assertFalse(caption.contentDescription.toString().contains("Couldn't transcribe"))
    }
}
