package com.tihor.quill.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

/** Why a recording stopped. */
enum class StopReason { MANUAL, SILENCE, CAP, ERROR }

/**
 * Captures 16 kHz mono 16-bit PCM via [AudioRecord] and writes a WAV (spec §2.2). Raw PCM is needed
 * for the live level meter and the silence auto-stop, and gives a deterministic format for STT
 * independent of the device encoder. Input routes through the [AudioRoutingManager] seam (handset in
 * v1). Encoding to FLAC before upload is a separate, deferred step ([AudioEncoder]).
 *
 * Callbacks fire on the recording thread — marshal to the main thread in the caller if touching UI.
 */
class AudioRecorder(
    private val context: Context,
    private val routing: AudioRoutingManager = HandsetAudioRoutingManager(context),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Volatile
    private var recording = false
    private var thread: Thread? = null

    val isRecording: Boolean get() = recording

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Start capturing into [output]. [onAmplitude] receives per-buffer RMS (0..~32767) for the level
     * meter; [onWarning] fires once near the session cap; [onFinished] fires when recording ends.
     */
    @SuppressLint("MissingPermission") // guarded by hasMicPermission()
    fun start(
        output: File,
        capMs: Long = RecordingLimits.DEFAULT_CAP_MS,
        onAmplitude: (Int) -> Unit = {},
        onWarning: () -> Unit = {},
        onFinished: (File, StopReason) -> Unit = { _, _ -> },
    ) {
        if (recording) return
        if (!hasMicPermission()) {
            Log.w(TAG, "RECORD_AUDIO not granted.")
            onFinished(output, StopReason.ERROR)
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid min buffer size: $minBuf")
            onFinished(output, StopReason.ERROR)
            return
        }
        val bufferSize = maxOf(minBuf, SAMPLE_RATE / 5 * BYTES_PER_SAMPLE) // ~200 ms

        recording = true
        thread = Thread {
            runRecordingLoop(output, bufferSize, capMs, onAmplitude, onWarning, onFinished)
        }.also { it.start() }
    }

    /** Ask the loop to stop; [onFinished] fires from the loop. Non-blocking (safe on the main thread). */
    fun requestStop() {
        recording = false
    }

    /** Stop and wait for the loop to finish (use for teardown, not the UI thread). */
    fun stop() {
        recording = false
        thread?.join(1500)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun runRecordingLoop(
        output: File,
        bufferSize: Int,
        capMs: Long,
        onAmplitude: (Int) -> Unit,
        onWarning: () -> Unit,
        onFinished: (File, StopReason) -> Unit,
    ) {
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize.")
            record.release()
            recording = false
            onFinished(output, StopReason.ERROR)
            return
        }
        routing.selectedInput()?.let { record.preferredDevice = it }

        val silence = SilenceDetector()
        val buffer = ShortArray(bufferSize / BYTES_PER_SAMPLE)
        var dataBytes = 0
        var warned = false
        var reason = StopReason.MANUAL

        val raf = RandomAccessFile(output, "rw")
        raf.setLength(0)
        // Placeholder header, patched with the real data size on stop.
        raf.write(WavHeader.build(SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE, 0))

        val started = clock()
        silence.start(started)
        record.startRecording()
        try {
            while (recording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val rms = rms(buffer, read)
                onAmplitude(rms.toInt())

                raf.write(shortsToLeBytes(buffer, read))
                dataBytes += read * BYTES_PER_SAMPLE

                val now = clock()
                val elapsed = now - started
                if (!warned && RecordingLimits.shouldWarn(elapsed, capMs)) {
                    warned = true
                    onWarning()
                }
                if (RecordingLimits.reachedCap(elapsed, capMs)) {
                    reason = StopReason.CAP
                    break
                }
                if (silence.onFrame(rms, now)) {
                    reason = StopReason.SILENCE
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Recording loop error", t)
            reason = StopReason.ERROR
        } finally {
            recording = false
            runCatching { record.stop() }
            record.release()
            // Patch RIFF/data sizes.
            runCatching {
                raf.seek(0)
                raf.write(WavHeader.build(SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE, dataBytes))
            }
            runCatching { raf.close() }
            Log.i(TAG, "Recording stopped ($reason): ${output.name}, ${dataBytes} PCM bytes")
            onFinished(output, reason)
        }
    }

    private fun rms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val v = buffer[i].toDouble()
            sum += v * v
        }
        return if (length == 0) 0.0 else sqrt(sum / length)
    }

    private fun shortsToLeBytes(buffer: ShortArray, length: Int): ByteArray {
        val out = ByteArray(length * BYTES_PER_SAMPLE)
        for (i in 0 until length) {
            val s = buffer[i].toInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    companion object {
        private const val TAG = "Quill.Audio"
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
