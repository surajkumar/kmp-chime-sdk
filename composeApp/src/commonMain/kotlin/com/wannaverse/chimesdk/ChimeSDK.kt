package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun joinMeeting(
    externalMeetingId: String,
    meetingId: String,
    audioHostURL: String,
    audioFallbackURL: String,
    turnControlURL: String,
    signalingURL: String,
    ingestionURL: String,
    attendeeId: String,
    externalUserId: String,
    joinToken: String,
    realTimeListener: RealTimeEventListener,
    onChatMessageReceived: (TextMessage) -> Unit,
    onActiveSpeakersChanged: (Set<String>) -> Unit,
    onEmojiReceived: (TextMessage) -> Unit,
    cameraFacing: CameraFacing = CameraFacing.FRONT,
    onLocalVideoTileAdded: ((Int?) -> Unit)? = null,
    onConnectionStatusChanged: (ConnectionStatus) -> Unit = {},
    onRemoteVideoAvailable: (isAvailable: Boolean, sourceCount: Int) -> Unit = { _, _ -> },
    onCameraSendAvailable: (available: Boolean) -> Unit = {},
    onSessionError: (message: String, isRecoverable: Boolean) -> Unit = { _, _ -> },
    onVideoNeedsRestart: () -> Unit = {},
    onLocalVideoTileRemoved: (() -> Unit)?,
    preferredAudioInputDeviceType: String? = null,
    onRemoteTileAdded: ((Int) -> Unit)? = null,
    onRemoteTileRemoved: ((Int) -> Unit)? = null,
    onSystemMessage: (TextMessage) -> Unit,
    isJoiningOnMute: Boolean,
    onLocalAttendeeIdAvailable: (String) -> Unit
)

expect fun leaveMeeting()

expect fun startLocalVideo()

expect fun stopLocalVideo()

@Composable
expect fun LocalVideoView(modifier: Modifier, cameraFacing: CameraFacing, isOnTop: Boolean)

@Composable
expect fun RemoteVideoView(modifier: Modifier, tileId: Int, isOnTop: Boolean)

expect fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long = 0)

expect fun setMute(shouldMute: Boolean): Boolean

expect fun switchCamera()

expect fun switchAudioDevice(deviceId: String?)
