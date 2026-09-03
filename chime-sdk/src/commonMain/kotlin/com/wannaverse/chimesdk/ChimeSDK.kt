package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class ChimeSDK {
    companion object {
        /**
         * Creates a meeting session and initializes the SDK. Call [joinMeeting] to connect to the session.
         *
         * @param externalMeetingId Your app-defined meeting identifier.
         * @param meetingId Chime meeting ID returned by CreateMeeting.
         * @param audioHostURL Media server host for audio (UDP/SRTP).
         * @param audioFallbackURL WebSocket fallback when UDP is blocked.
         * @param turnControlURL TURN credential endpoint.
         * @param signalingURL WebSocket signaling endpoint.
         * @param ingestionURL Client event ingestion endpoint.
         * @param attendeeId Chime attendee ID returned by CreateAttendee.
         * @param externalUserId Your app-defined user identifier.
         * @param joinToken Attendee join token returned by CreateAttendee.
         */
        fun createSession(
            externalMeetingId: String,
            meetingId: String,
            audioHostURL: String,
            audioFallbackURL: String,
            turnControlURL: String,
            signalingURL: String,
            ingestionURL: String,
            attendeeId: String,
            externalUserId: String,
            joinToken: String
        ): ChimeSDK
    }

    /**
     * Returns the currently available audio input devices such as microphones.
     *
     * The returned list reflects the devices detected by the current platform at
     * the time of the call and may change as hardware is connected or removed.
     */
    fun getAvailableInputDevices(): List<AudioDevice>

    /**
     * Returns the currently available audio output devices such as speakers and headsets.
     *
     * The returned list reflects the devices detected by the current platform at
     * the time of the call and may change as hardware is connected or removed.
     */
    fun getAvailableOutputDevices(): List<AudioDevice>

    /**
     * Joins a Chime meeting and starts audio/video with default parameters. Call [createSession] first.
     *
     * @param realTimeListener Callbacks for attendee presence, mute, and volume events.
     * @param onActiveSpeakersChanged Invoked with the set of currently active speaker attendee IDs
     * @param onConnectionStatusChanged Invoked when the session connection status changes.
     * @param onRemoteVideoAvailable Invoked when remote video availability or source count changes.
     * @param onCameraSendAvailable Invoked when the ability to send local camera video changes.
     * @param onSessionError Invoked on session errors; [isRecoverable] indicates whether the SDK will retry.
     * @param selectedAudioInputDevice [AudioDevice.label] of the audio input device to use, or null to use the platform default.
     * @param isJoiningOnMute Whether to join with the microphone muted. Defaults to false.
     * @param onLocalTileAdded Invoked with the local video tile ID once the local tile is bound, or null if unavailable.
     */
    fun joinMeeting(
        realTimeListener: RealTimeEventListener,
        onActiveSpeakersChanged: (Set<String>) -> Unit,
        onConnectionStatusChanged: (ConnectionStatus) -> Unit,
        onRemoteVideoAvailable: (Boolean, Int) -> Unit,
        onCameraSendAvailable: (Boolean) -> Unit,
        onSessionError: (String, Boolean) -> Unit,
        selectedAudioInputDevice: String?,
        isJoiningOnMute: Boolean,
        onLocalTileAdded: (Int) -> Unit,
        onLocalTileRemoved: () -> Unit,
        onRemoteTileAdded: (Int) -> Unit,
        onRemoteTileRemoved: () -> Unit
    )

    /**
     * Returns the currently active audio device for this session, or null if no device is active.
     *
     * This function will return null if [joinMeeting] has not been called.
     */
    fun getActiveAudioDevice(): AudioDevice?

    /**
     * Ends the active meeting session and releases all resources.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     */
    fun leaveMeeting()

    /**
     * Starts capturing and sending local camera video.
     *
     * @param cameraFacing The camera to use for local video capture.
     */
    fun startLocalVideo(cameraFacing: CameraFacing)

    /**
     * Stops capturing and sending local camera video.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     */
    fun stopLocalVideo()

    /**
     * Composable that renders the local camera preview.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     */
    @Composable
    fun LocalVideoView(cameraFacing: CameraFacing, modifier: Modifier = Modifier)

    /**
     * Composable that renders a remote participant's video tile.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     */
    @Composable
    fun RemoteVideoView(tileId: Int, modifier: Modifier = Modifier)

    /**
     * Broadcasts a real-time data message on [topic].
     *
     * This function will have no effect if [joinMeeting] has not been called.
     *
     * @param topic Destination topic string.
     * @param data UTF-8 payload, max 2 KB.
     * @param lifetimeMs How long the message is replayed to late joiners (ms). 0 means no replay.
     */
    fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long = 0)

    /**
     * Mutes or unmutes the local microphone.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     *
     * @return true if the operation succeeded.
     */
    fun setMute(shouldMute: Boolean): Boolean

    /**
     * Toggles between front and back cameras.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     */
    fun switchCamera()

    /**
     * Returns true if the current device has a torch (flashlight) available.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     */
    fun torchAvailable(): Boolean

    /**
     * Returns true if the torch (flashlight) is currently enabled.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     */
    fun torchEnabled(): Boolean

    /**
     * Enables or disables the torch (flashlight) on the current camera.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     *
     * @param enabled true to enable the torch, false to disable it.
     */
    fun setTorchEnabled(enabled: Boolean)

    /**
     * Routes audio output to the given device.
     *
     * This function will have no effect if [joinMeeting] has not been called.
     *
     * @param device target device, or null to use the platform default.
     */
    fun switchAudioDevice(device: AudioDevice?)

    /**
     * Subscribes to incoming data messages on [topic]. Call after [joinMeeting].
     *
     * This function will have no effect if [joinMeeting] has not been called.
     *
     * @param topic Topic to subscribe to.
     * @param listener Invoked on the main thread for each received [TextMessage].
     */
    fun subscribeToTopic(topic: String, listener: (ChimeMessage) -> Unit)

    /**
     * Unsubscribes from data messages on [topic].
     *
     * This function will have no effect if [joinMeeting] has not been called.
     *
     * @param topic Topic previously passed to [subscribeToTopic].
     */
    fun unsubscribeFromTopic(topic: String)

    @Composable
    fun ScreenShareButton()
}
