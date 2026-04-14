package com.wannaverse.chimesdk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallState(
    val isJoined: Boolean = false,
    val isLoading: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val errorMessage: String? = null,
    val isMuted: Boolean = false,
    val isCameraOn: Boolean = false,
    val activeSpeakers: Set<String> = emptySet(),
    val chatMessages: List<TextMessage> = emptyList(),
    val emojiMessages: List<TextMessage> = emptyList(),
    val systemMessages: List<TextMessage> = emptyList(),
    val audioDevices: List<AudioDevice> = emptyList(),
    val selectedAudioDevice: AudioDevice? = null,
    val localAttendeeId: String? = null,
    val hasRemoteVideo: Boolean = false,
    val remoteVideoSourceCount: Int = 0,
    val remoteTileIds: List<Int> = emptyList(),
    val startMuted: Boolean = false,
    val cameraFacing: CameraFacing = CameraFacing.FRONT
)

class AppViewModel(
    initialInfo: MeetingInformation = MeetingInformation()
) : ViewModel(), RealTimeEventListener {

    private val _callState = MutableStateFlow(CallState())
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _meetingInfo = MutableStateFlow(initialInfo)
    val meetingInfo: StateFlow<MeetingInformation> = _meetingInfo.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    fun updateMeetingInfo(info: MeetingInformation) {
        _meetingInfo.value = info
    }

    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    fun setStartMuted(muted: Boolean) {
        _callState.update { it.copy(startMuted = muted) }
    }

    fun setCameraFacing(facing: CameraFacing) {
        _callState.update { it.copy(cameraFacing = facing) }
    }

    fun joinMeeting() {
        val info = _meetingInfo.value
        if (info.meetingId.isBlank()) {
            _callState.update { it.copy(errorMessage = "Meeting ID is required") }
            return
        }
        if (info.attendeeId.isBlank()) {
            _callState.update { it.copy(errorMessage = "Attendee ID is required") }
            return
        }
        if (info.joinToken.isBlank()) {
            _callState.update { it.copy(errorMessage = "Join Token is required") }
            return
        }

        val startMuted = _callState.value.startMuted
        val facing = _callState.value.cameraFacing

        _callState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                joinMeeting(
                    externalMeetingId = info.externalMeetingId.ifBlank { info.meetingId },
                    meetingId = info.meetingId,
                    audioHostURL = info.audioHostURL,
                    audioFallbackURL = info.audioFallbackURL,
                    turnControlURL = info.turnControlURL,
                    signalingURL = info.signalingURL,
                    ingestionURL = info.ingestionURL,
                    attendeeId = info.attendeeId,
                    externalUserId = info.externalUserId.ifBlank { info.attendeeId },
                    joinToken = info.joinToken,
                    realTimeListener = this@AppViewModel,
                    onActiveSpeakersChanged = { speakers ->
                        _callState.update { it.copy(activeSpeakers = speakers) }
                    },
                    cameraFacing = facing,
                    onLocalVideoTileAdded = { _ ->
                        _callState.update { it.copy(isCameraOn = true) }
                    },
                    onConnectionStatusChanged = { status ->
                        _callState.update { it.copy(connectionStatus = status) }
                    },
                    onRemoteVideoAvailable = { isAvailable, count ->
                        _callState.update {
                            it.copy(hasRemoteVideo = isAvailable, remoteVideoSourceCount = count)
                        }
                    },
                    onCameraSendAvailable = { _ -> },
                    onSessionError = { message, isRecoverable ->
                        if (!isRecoverable) {
                            _callState.update { it.copy(errorMessage = message) }
                        }
                    },
                    onVideoNeedsRestart = { restartVideo() },
                    onLocalVideoTileRemoved = {
                        _callState.update { it.copy(isCameraOn = false) }
                    },
                    preferredAudioInputDeviceType = null,
                    onRemoteTileAdded = { tileId ->
                        _callState.update { it.copy(remoteTileIds = it.remoteTileIds + tileId) }
                    },
                    onRemoteTileRemoved = { tileId ->
                        _callState.update { it.copy(remoteTileIds = it.remoteTileIds - tileId) }
                    },
                    isJoiningOnMute = startMuted,
                    onLocalAttendeeIdAvailable = { id ->
                        _callState.update { it.copy(localAttendeeId = id) }
                    }
                )

                subscribeToTopic("chat") { msg ->
                    _callState.update { it.copy(chatMessages = it.chatMessages + msg) }
                }
                subscribeToTopic("emoji") { msg ->
                    _callState.update { it.copy(emojiMessages = it.emojiMessages + msg) }
                }
                subscribeToTopic("system") { msg ->
                    _callState.update { it.copy(systemMessages = it.systemMessages + msg) }
                }

                startLocalVideo()
                _callState.update { it.copy(isLoading = false, isJoined = true, isMuted = startMuted) }
            } catch (e: Exception) {
                _callState.update {
                    it.copy(
                        isLoading = false,
                        isJoined = false,
                        errorMessage = e.message ?: "Failed to join meeting"
                    )
                }
            }
        }
    }

    fun leaveMeeting() {
        viewModelScope.launch {
            try {
                com.wannaverse.chimesdk.leaveMeeting()
            } catch (_: Exception) {
            }
            _callState.value = CallState(
                startMuted = _callState.value.startMuted,
                cameraFacing = _callState.value.cameraFacing
            )
        }
    }

    fun toggleMute() {
        val desired = !_callState.value.isMuted
        val success = setMute(desired)
        if (success) {
            _callState.update { it.copy(isMuted = desired) }
        }
    }

    fun toggleCamera() {
        if (_callState.value.isCameraOn) {
            stopLocalVideo()
            _callState.update { it.copy(isCameraOn = false) }
        } else {
            startLocalVideo()
            _callState.update { it.copy(isCameraOn = true) }
        }
    }

    fun onSwitchCamera() {
        switchCamera()
    }

    fun sendChat() {
        val text = _chatInput.value.trim()
        if (text.isBlank()) return
        sendRealtimeMessage("chat", text)
        _chatInput.value = ""
    }

    fun selectAudioDevice(device: AudioDevice) {
        switchAudioDevice(device.id)
        val updated = _callState.value.audioDevices.map {
            it.copy(isSelected = it.id == device.id)
        }
        _callState.update { it.copy(audioDevices = updated, selectedAudioDevice = device) }
    }

    fun clearError() {
        _callState.update { it.copy(errorMessage = null) }
    }

    private fun restartVideo() {
        try {
            stopLocalVideo()
            startLocalVideo()
        } catch (_: Exception) {
        }
    }

    // RealTimeEventListener
    override fun onAttendeesJoined(attendeeIds: List<String>) {}
    override fun onAttendeesDropped(attendeeIds: List<String>) {}
    override fun onAttendeesLeft(attendeeIds: List<String>) {}
    override fun onAttendeesMuted(attendeeIds: List<String>) {}
    override fun onAttendeesUnmuted(attendeeIds: List<String>) {}
    override fun onSignalStrengthChanged(attendeeId: String, externalAttendeeId: String, signal: Int) {}
    override fun onVolumeChanged(attendeeId: String, externalAttendeeId: String, volume: Int) {}
    override fun onAudioDevicesUpdated(audioDevices: List<AudioDevice>, selectedDevice: AudioDevice?) {
        _callState.update { it.copy(audioDevices = audioDevices, selectedAudioDevice = selectedDevice) }
    }
}
