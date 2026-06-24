package com.tihor.quill.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Audio-source seam (spec §3.2). v1 captures from the handset mic only, but this interface ships now
 * so Bluetooth capture (P1) drops in later with no rework — the P1 impl must use
 * `setCommunicationDevice()` + `AudioDeviceCallback` (not the deprecated `startBluetoothSco()`).
 */
interface AudioRoutingManager {
    fun availableInputs(): List<AudioDeviceInfo>

    /** The device to capture from, or null for the platform default (handset). */
    fun selectedInput(): AudioDeviceInfo?

    fun selectInput(device: AudioDeviceInfo): Boolean
    fun clearSelection()
}

/** v1 implementation: always the handset default (no selection). */
class HandsetAudioRoutingManager(private val context: Context) : AudioRoutingManager {
    private val audioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun availableInputs(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()

    override fun selectedInput(): AudioDeviceInfo? = null // handset default
    override fun selectInput(device: AudioDeviceInfo): Boolean = false // P1
    override fun clearSelection() = Unit
}
