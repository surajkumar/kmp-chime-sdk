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
    onActiveSpeakersChanged: (Set<String>) -> Unit,
    cameraFacing: CameraFacing = CameraFacing.FRONT,
    onLocalVideoTileAdded: ((Int?) -> Unit)? = null,
    onConnectionStatusChanged: (ConnectionStatus) -> Unit = {},
    onRemoteVideoAvailable: (isAvailable: Boolean, sourceCount: Int) -> Unit = { _, _ -> },
    onCameraSendAvailable: (available: Boolean) -> Unit = {},
    onSessionError: (message: String, isRecoverable: Boolean) -> Unit = { _, _ -> },
    onVideoNeedsRestart: () -> Unit = {},
    onLocalVideoTileRemoved: (() -> Unit)? = null,
    preferredAudioInputDeviceType: String? = null,
    onRemoteTileAdded: ((Int) -> Unit)? = null,
    onRemoteTileRemoved: ((Int) -> Unit)? = null,
    isJoiningOnMute: Boolean = false,
    onLocalAttendeeIdAvailable: (String) -> Unit = {}
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

/** Subscribe to incoming data messages on [topic]. Call after [joinMeeting]. */
expect fun subscribeToTopic(topic: String, listener: (TextMessage) -> Unit)

/** Unsubscribe from data messages on [topic]. */
expect fun unsubscribeFromTopic(topic: String)
