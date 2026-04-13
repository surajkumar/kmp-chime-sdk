package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIView

// ─── Bridge object — Swift registers these before any meeting operations ─────

object ChimeSdkBridge {
    /** Swift must set this before calling joinMeeting */
    var joinMeetingNative: ((String, String, String, String, String, String, String, String, String, String) -> Unit)? = null
    var leaveMeetingNative: (() -> Unit)? = null
    var startLocalVideoNative: (() -> Unit)? = null
    var stopLocalVideoNative: (() -> Unit)? = null
    var sendRealtimeMessageNative: ((String, String, Long) -> Unit)? = null
    var setMuteNative: ((Boolean) -> Unit)? = null
    var switchCameraNative: (() -> Unit)? = null
    var switchAudioDeviceNative: ((String?) -> Unit)? = null

    /** Swift provides a factory that returns the pre-created UIView for local video */
    var localVideoViewFactory: (() -> UIView)? = null
    /** Swift provides a factory that returns the render view for a given remote tile ID */
    var remoteVideoViewFactory: ((Int) -> UIView)? = null

    /**
     * Kotlin stores the active delegate here after joinMeeting() is called.
     * Swift calls methods on this delegate when Chime SDK events fire.
     */
    var eventDelegate: ChimeIOSDelegate? = null
}

// ─── Delegate protocol — Swift implements this to forward Chime events ────────

interface ChimeIOSDelegate {
    fun onConnectionStatusChanged(status: String)
    fun onActiveSpeakersChanged(speakerIds: List<String>)
    fun onChatMessageReceived(senderId: String, content: String, timestamp: Long)
    fun onEmojiReceived(senderId: String, content: String, timestamp: Long)
    fun onSystemMessage(senderId: String, content: String, timestamp: Long)
    fun onLocalVideoTileAdded(tileId: Int)
    fun onLocalVideoTileRemoved()
    fun onRemoteTileAdded(tileId: Int)
    fun onRemoteTileRemoved(tileId: Int)
    fun onLocalAttendeeIdAvailable(attendeeId: String)
    fun onVideoNeedsRestart()
    fun onCameraSendAvailable(available: Boolean)
    fun onRemoteVideoAvailable(isAvailable: Boolean, sourceCount: Int)
    fun onSessionError(message: String, isRecoverable: Boolean)
    fun onAttendeesJoined(attendeeIds: List<String>)
    fun onAttendeesDropped(attendeeIds: List<String>)
    fun onAttendeesLeft(attendeeIds: List<String>)
    fun onAttendeesMuted(attendeeIds: List<String>)
    fun onAttendeesUnmuted(attendeeIds: List<String>)
    fun onSignalStrengthChanged(attendeeId: String, externalAttendeeId: String, signal: Int)
    fun onVolumeChanged(attendeeId: String, externalAttendeeId: String, volume: Int)
}

// ─── Internal bridge: maps String status → ConnectionStatus enum ──────────────

private class IOSDelegateToCallbacks(
    private val realTimeListener: RealTimeEventListener,
    private val onChatMessageReceived: (TextMessage) -> Unit,
    private val onActiveSpeakersChanged: (Set<String>) -> Unit,
    private val onEmojiReceived: (TextMessage) -> Unit,
    private val onLocalVideoTileAdded: ((Int?) -> Unit)?,
    private val onConnectionStatusChanged: (ConnectionStatus) -> Unit,
    private val onRemoteVideoAvailable: (Boolean, Int) -> Unit,
    private val onCameraSendAvailable: (Boolean) -> Unit,
    private val onSessionError: (String, Boolean) -> Unit,
    private val onVideoNeedsRestart: () -> Unit,
    private val onLocalVideoTileRemoved: (() -> Unit)?,
    private val onRemoteTileAdded: ((Int) -> Unit)?,
    private val onRemoteTileRemoved: ((Int) -> Unit)?,
    private val onSystemMessage: (TextMessage) -> Unit,
    private val onLocalAttendeeIdAvailable: (String) -> Unit
) : ChimeIOSDelegate {

    override fun onConnectionStatusChanged(status: String) {
        val cs = when (status) {
            "CONNECTING" -> ConnectionStatus.CONNECTING
            "CONNECTED" -> ConnectionStatus.CONNECTED
            "RECONNECTING" -> ConnectionStatus.RECONNECTING
            "POOR_CONNECTION" -> ConnectionStatus.POOR_CONNECTION
            "DISCONNECTED" -> ConnectionStatus.DISCONNECTED
            else -> ConnectionStatus.ERROR
        }
        onConnectionStatusChanged(cs)
    }

    override fun onActiveSpeakersChanged(speakerIds: List<String>) {
        onActiveSpeakersChanged(speakerIds.toSet())
    }

    override fun onChatMessageReceived(senderId: String, content: String, timestamp: Long) {
        onChatMessageReceived(TextMessage(senderId, content, timestamp))
    }

    override fun onEmojiReceived(senderId: String, content: String, timestamp: Long) {
        onEmojiReceived(TextMessage(senderId, content, timestamp))
    }

    override fun onSystemMessage(senderId: String, content: String, timestamp: Long) {
        onSystemMessage(TextMessage(senderId, content, timestamp))
    }

    override fun onLocalVideoTileAdded(tileId: Int) {
        onLocalVideoTileAdded?.invoke(tileId)
    }

    override fun onLocalVideoTileRemoved() {
        onLocalVideoTileRemoved?.invoke()
    }

    override fun onRemoteTileAdded(tileId: Int) {
        onRemoteTileAdded?.invoke(tileId)
    }

    override fun onRemoteTileRemoved(tileId: Int) {
        onRemoteTileRemoved?.invoke(tileId)
    }

    override fun onLocalAttendeeIdAvailable(attendeeId: String) {
        onLocalAttendeeIdAvailable.invoke(attendeeId)
    }

    override fun onVideoNeedsRestart() {
        onVideoNeedsRestart.invoke()
    }

    override fun onCameraSendAvailable(available: Boolean) {
        onCameraSendAvailable.invoke(available)
    }

    override fun onRemoteVideoAvailable(isAvailable: Boolean, sourceCount: Int) {
        onRemoteVideoAvailable.invoke(isAvailable, sourceCount)
    }

    override fun onSessionError(message: String, isRecoverable: Boolean) {
        onSessionError.invoke(message, isRecoverable)
    }

    override fun onAttendeesJoined(attendeeIds: List<String>) {
        realTimeListener.onAttendeesJoined(attendeeIds)
    }

    override fun onAttendeesDropped(attendeeIds: List<String>) {
        realTimeListener.onAttendeesDropped(attendeeIds)
    }

    override fun onAttendeesLeft(attendeeIds: List<String>) {
        realTimeListener.onAttendeesLeft(attendeeIds)
    }

    override fun onAttendeesMuted(attendeeIds: List<String>) {
        realTimeListener.onAttendeesMuted(attendeeIds)
    }

    override fun onAttendeesUnmuted(attendeeIds: List<String>) {
        realTimeListener.onAttendeesUnmuted(attendeeIds)
    }

    override fun onSignalStrengthChanged(attendeeId: String, externalAttendeeId: String, signal: Int) {
        realTimeListener.onSignalStrengthChanged(attendeeId, externalAttendeeId, signal)
    }

    override fun onVolumeChanged(attendeeId: String, externalAttendeeId: String, volume: Int) {
        realTimeListener.onVolumeChanged(attendeeId, externalAttendeeId, volume)
    }
}

// ─── Actual implementations ───────────────────────────────────────────────────

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
    onChatMessageReceived: (TextMessage) -> Unit,
    onActiveSpeakersChanged: (Set<String>) -> Unit,
    onEmojiReceived: (TextMessage) -> Unit,
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
    onSystemMessage: (TextMessage) -> Unit,
    isJoiningOnMute: Boolean,
    onLocalAttendeeIdAvailable: (String) -> Unit
) {
    ChimeSdkBridge.eventDelegate = IOSDelegateToCallbacks(
        realTimeListener = realTimeListener,
        onChatMessageReceived = onChatMessageReceived,
        onActiveSpeakersChanged = onActiveSpeakersChanged,
        onEmojiReceived = onEmojiReceived,
        onLocalVideoTileAdded = onLocalVideoTileAdded,
        onConnectionStatusChanged = onConnectionStatusChanged,
        onRemoteVideoAvailable = onRemoteVideoAvailable,
        onCameraSendAvailable = onCameraSendAvailable,
        onSessionError = onSessionError,
        onVideoNeedsRestart = onVideoNeedsRestart,
        onLocalVideoTileRemoved = onLocalVideoTileRemoved,
        onRemoteTileAdded = onRemoteTileAdded,
        onRemoteTileRemoved = onRemoteTileRemoved,
        onSystemMessage = onSystemMessage,
        onLocalAttendeeIdAvailable = onLocalAttendeeIdAvailable
    )

    ChimeSdkBridge.joinMeetingNative?.invoke(
        externalMeetingId,
        meetingId,
        audioHostURL,
        audioFallbackURL,
        turnControlURL,
        signalingURL,
        ingestionURL,
        attendeeId,
        externalUserId,
        joinToken
    )
}

actual fun leaveMeeting() {
    ChimeSdkBridge.leaveMeetingNative?.invoke()
    ChimeSdkBridge.eventDelegate = null
}

actual fun startLocalVideo() {
    ChimeSdkBridge.startLocalVideoNative?.invoke()
}

actual fun stopLocalVideo() {
    ChimeSdkBridge.stopLocalVideoNative?.invoke()
}

@Composable
actual fun LocalVideoView(modifier: Modifier, cameraFacing: CameraFacing, isOnTop: Boolean) {
    val factory = ChimeSdkBridge.localVideoViewFactory
    if (factory != null) {
        UIKitView(factory = factory, modifier = modifier)
    }
}

@Composable
actual fun RemoteVideoView(modifier: Modifier, tileId: Int, isOnTop: Boolean) {
    val factory = ChimeSdkBridge.remoteVideoViewFactory
    if (factory != null) {
        UIKitView(factory = { factory(tileId) }, modifier = modifier)
    }
}

actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
    ChimeSdkBridge.sendRealtimeMessageNative?.invoke(topic, data, lifetimeMs)
}

actual fun setMute(shouldMute: Boolean): Boolean {
    ChimeSdkBridge.setMuteNative?.invoke(shouldMute)
    return shouldMute
}

actual fun switchCamera() {
    ChimeSdkBridge.switchCameraNative?.invoke()
}

actual fun switchAudioDevice(deviceId: String?) {
    ChimeSdkBridge.switchAudioDeviceNative?.invoke(deviceId)
}
