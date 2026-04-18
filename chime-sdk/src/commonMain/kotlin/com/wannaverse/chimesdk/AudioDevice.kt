package com.wannaverse.chimesdk

/**
 * Represents an audio output device available on the current platform.
 *
 * @property type Platform-specific device type constant.
 * @property label Human-readable device name.
 * @property id Opaque device identifier. Pass to [switchAudioDevice] to activate this device.
 */
data class AudioDevice(val type: Int, val label: String, val id: String?)
