@file:OptIn(ExperimentalForeignApi::class)

package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.CoreGraphics.CGRect
import platform.UIKit.UIView

private class RemoteVideoContainerView(
    private val tileId: Int
) : UIView(frame = cValue<CGRect>()) {
    override fun layoutSubviews() {
        super.layoutSubviews()
        val actual = chimeMeeting.getRemoteView(tileId) ?: return
        if (actual.superview != this) {
            subviews.forEach { subview ->
                (subview as? UIView)?.removeFromSuperview()
            }
            addSubview(actual)
            chimeMeeting.rebindRemoteView(tileId)
        }
        actual.setFrame(bounds)
    }
}

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
        joinToken = joinToken,
        cameraFacing = cameraFacing,
        startMuted = isJoiningOnMute
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
    UIKitView(
        factory = {
            chimeMeeting.localVideoContainer
        },
        modifier = modifier,
        update = {
            chimeMeeting.localRenderView.setFrame(it.bounds)
        }
    )
}

@Composable
actual fun RemoteVideoView(modifier: Modifier, tileId: Int, isOnTop: Boolean) {
    UIKitView(
        factory = {
            RemoteVideoContainerView(tileId)
        },
        modifier = modifier,
        update = { view ->
            (view as? RemoteVideoContainerView)?.setNeedsLayout()
        }
    )
}

actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
    chimeMeeting.sendRealtimeMessage(topic, data, lifetimeMs)
}

actual fun setMute(shouldMute: Boolean): Boolean {
    return chimeMeeting.setMute(shouldMute)
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
