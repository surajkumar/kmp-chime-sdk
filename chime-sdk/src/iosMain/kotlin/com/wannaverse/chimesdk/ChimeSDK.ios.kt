@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

actual fun joinMeeting(
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
    cameraFacing: CameraFacing,
    onLocalVideoTileAdded: ((Int?) -> Unit)?,
    onConnectionStatusChanged: (ConnectionStatus) -> Unit,
    onRemoteVideoAvailable: (isAvailable: Boolean, sourceCount: Int) -> Unit,
    onCameraSendAvailable: (available: Boolean) -> Unit,
    onSessionError: (message: String, isRecoverable: Boolean) -> Unit,
    onVideoNeedsRestart: () -> Unit,
    onLocalVideoTileRemoved: (() -> Unit)?,
    preferredAudioInputDeviceType: String?,
    onRemoteTileAdded: ((Int) -> Unit)?,
    onRemoteTileRemoved: ((Int) -> Unit)?,
    isJoiningOnMute: Boolean,
    onLocalAttendeeIdAvailable: (String) -> Unit
) {
    chimeMeeting.realTimeListener = realTimeListener
    chimeMeeting.onActiveSpeakersChanged = onActiveSpeakersChanged
    chimeMeeting.onLocalVideoTileAdded = onLocalVideoTileAdded
    chimeMeeting.onConnectionStatusChanged = onConnectionStatusChanged
    chimeMeeting.onRemoteVideoAvailable = onRemoteVideoAvailable
    chimeMeeting.onCameraSendAvailable = onCameraSendAvailable
    chimeMeeting.onSessionError = onSessionError
    chimeMeeting.onVideoNeedsRestart = onVideoNeedsRestart
    chimeMeeting.onLocalVideoTileRemoved = onLocalVideoTileRemoved
    chimeMeeting.onRemoteTileAdded = onRemoteTileAdded
    chimeMeeting.onRemoteTileRemoved = onRemoteTileRemoved
    chimeMeeting.onLocalAttendeeIdAvailable = onLocalAttendeeIdAvailable

    chimeMeeting.join(
        externalMeetingId = externalMeetingId,
        meetingId = meetingId,
        audioHostURL = audioHostURL,
        audioFallbackURL = audioFallbackURL,
        turnControlURL = turnControlURL,
        signalingURL = signalingURL,
        ingestionURL = ingestionURL,
        attendeeId = attendeeId,
        externalUserId = externalUserId,
        joinToken = joinToken
    )
}

actual fun leaveMeeting() {
    chimeMeeting.leave()
}

actual fun startLocalVideo() {
    chimeMeeting.startLocalVideo()
}

actual fun stopLocalVideo() {
    chimeMeeting.stopLocalVideo()
}

@Composable
actual fun LocalVideoView(modifier: Modifier, cameraFacing: CameraFacing, isOnTop: Boolean) {
    UIKitView(factory = { chimeMeeting.localRenderView }, modifier = modifier)
}

@Composable
actual fun RemoteVideoView(modifier: Modifier, tileId: Int, isOnTop: Boolean) {
    UIKitView(factory = { chimeMeeting.getRemoteView(tileId) ?: UIView() }, modifier = modifier)
}

actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
    chimeMeeting.sendRealtimeMessage(topic, data, lifetimeMs)
}

actual fun setMute(shouldMute: Boolean): Boolean {
    chimeMeeting.setMute(shouldMute)
    return shouldMute
}

actual fun switchCamera() {
    chimeMeeting.switchCamera()
}

actual fun switchAudioDevice(deviceId: String?) {
    chimeMeeting.switchAudioDevice(deviceId)
}

actual fun subscribeToTopic(topic: String, listener: (TextMessage) -> Unit) {
    chimeMeeting.topicListeners[topic] = listener
    chimeMeeting.subscribeTopic(topic)
}

actual fun unsubscribeFromTopic(topic: String) {
    chimeMeeting.topicListeners.remove(topic)
    chimeMeeting.unsubscribeTopic(topic)
}
