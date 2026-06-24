package com.tihor.quill.audio

import java.io.File

/**
 * Pre-upload encode step (spec §2.2). The architecture treats any compression as an explicit step we
 * control, not opaque recorder output — so this seam exists from v1.
 *
 * **v1 status:** ships as a WAV pass-through. Groq/OpenAI `audio/transcriptions` accept WAV directly,
 * so the end-to-end path works without native code. The spec's lossless FLAC encode (libFLAC via
 * NDK/JNI — ~half the WAV size) is a deferred **upload-size optimization** that slots in behind this
 * interface with no caller change. (Deferred because the NDK isn't provisioned and a native CMake
 * build on the current AGP 9.2.1 toolchain is high-risk; revisit before release — todo.md M4.)
 */
interface AudioEncoder {
    /** Return the file to upload for [wav] (same WAV today; a `.flac` once FLAC lands). */
    fun prepareForUpload(wav: File): File
}

class WavPassthroughEncoder : AudioEncoder {
    override fun prepareForUpload(wav: File): File = wav
}
