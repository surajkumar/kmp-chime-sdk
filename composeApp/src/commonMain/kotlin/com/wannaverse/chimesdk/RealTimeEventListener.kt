package com.wannaverse.chimesdk

interface RealTimeEventListener {
    fun onAttendeesJoined(attendeeIds: List<String>)
    fun onAttendeesDropped(attendeeIds: List<String>)
    fun onAttendeesLeft(attendeeIds: List<String>)
    fun onAttendeesMuted(attendeeIds: List<String>)
    fun onAttendeesUnmuted(attendeeIds: List<String>)
    fun onSignalStrengthChanged(attendeeId: String, externalAttendeeId: String, signal: Int)
    fun onVolumeChanged(attendeeId: String, externalAttendeeId: String, volume: Int)
    fun onAudioDevicesUpdated(
        audioDevices: List<AudioDevice>,
        selectedDevice: AudioDevice?
    )
}
