package com.wannaverse.chimesdk

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.amazonaws.services.chime.sdk.meetings.analytics.DefaultEventAnalyticsController
import com.amazonaws.services.chime.sdk.meetings.analytics.DefaultMeetingStatsCollector
import com.amazonaws.services.chime.sdk.meetings.audiovideo.AudioVideoObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerpolicy.DefaultActiveSpeakerPolicy
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.CameraCaptureSource
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.DefaultCameraCaptureSource
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.DefaultSurfaceTextureCaptureSourceFactory
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.gl.DefaultEglCoreFactory
import com.amazonaws.services.chime.sdk.meetings.device.MediaDevice
import com.amazonaws.services.chime.sdk.meetings.device.MediaDeviceType
import com.amazonaws.services.chime.sdk.meetings.ingestion.DefaultAppStateMonitor
import com.amazonaws.services.chime.sdk.meetings.session.DefaultMeetingSession
import com.amazonaws.services.chime.sdk.meetings.session.MeetingFeatures
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionConfiguration
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionCredentials
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionURLs
import com.wannaverse.chimesdk.composables.VideoTileView

private val chimeLogger = ChimeLogger()
var meetingSession: DefaultMeetingSession? = null

private var deviceObserver: DeviceObserver? = null
private var realTimeObserver: RealTimeObserver? = null
private var audioVideoObserver: AudioVideoObserver? = null
private var chatObserver: ChatObserver? = null
private var eventAnalyticsController: DefaultEventAnalyticsController? = null
private var eglCoreFactory: DefaultEglCoreFactory? = null
private var cameraCaptureSource: CameraCaptureSource? = null
private var cachedVideoDevices: List<MediaDevice>? = null
private var currentCameraFacing = CameraFacing.FRONT
private var cameraOn = false

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
    currentCameraFacing = cameraFacing

    val configuration = MeetingSessionConfiguration(
        credentials = MeetingSessionCredentials(
            attendeeId = attendeeId,
            externalUserId = externalUserId,
            joinToken = joinToken
        ),
        externalMeetingId = externalMeetingId,
        features = MeetingFeatures(),
        meetingId = meetingId,
        urls = MeetingSessionURLs(
            _audioFallbackURL = audioFallbackURL,
            _audioHostURL = audioHostURL,
            _ingestionURL = ingestionURL,
            _signalingURL = signalingURL,
            _turnControlURL = turnControlURL,
            urlRewriter = { url -> url }
        )
    )

    eventAnalyticsController = DefaultEventAnalyticsController(
        logger = chimeLogger,
        meetingSessionConfiguration = configuration,
        meetingStatsCollector = DefaultMeetingStatsCollector(chimeLogger),
        appStateMonitor = DefaultAppStateMonitor(chimeLogger, appContext.applicationContext as? Application)
    )

    eglCoreFactory = DefaultEglCoreFactory()
    meetingSession = DefaultMeetingSession(configuration, chimeLogger, appContext, eglCoreFactory!!)

    // Observers
    realTimeObserver = RealTimeObserver().also { it.setListener(realTimeListener) }
    deviceObserver = DeviceObserver(meetingSession!!, realTimeListener)

    onLocalAttendeeIdAvailable(meetingSession!!.configuration.credentials.attendeeId)

    // Audio device selection
    val audioDevices = meetingSession!!.audioVideo.listAudioDevices()
    if (audioDevices.isNotEmpty()) {
        val preferred = if (preferredAudioInputDeviceType != null) {
            audioDevices.firstOrNull { device ->
                when {
                    preferredAudioInputDeviceType.contains("Bluetooth", ignoreCase = true) ->
                        device.type == MediaDeviceType.AUDIO_BLUETOOTH
                    preferredAudioInputDeviceType.contains("Speaker", ignoreCase = true) ->
                        device.type == MediaDeviceType.AUDIO_BUILTIN_SPEAKER
                    preferredAudioInputDeviceType.contains("Microphone", ignoreCase = true) ||
                        preferredAudioInputDeviceType.contains("Earpiece", ignoreCase = true) ->
                        device.type == MediaDeviceType.AUDIO_HANDSET
                    else -> false
                }
            } ?: audioDevices[0]
        } else {
            audioDevices[0]
        }
        deviceObserver!!.selectAudioDevice(preferred)
    }

    // Video tile callbacks
    VideoTileManager.onLocalTileAdded = { onLocalVideoTileAdded?.invoke(VideoTileManager.localTileId) }
    VideoTileManager.onLocalTileRemoved = { onLocalVideoTileRemoved?.invoke() }
    VideoTileManager.onRemoteTileAdded = { tileId -> onRemoteTileAdded?.invoke(tileId) }
    VideoTileManager.onRemoteTileRemoved = { tileId -> onRemoteTileRemoved?.invoke(tileId) }

    meetingSession!!.audioVideo.addVideoTileObserver(VideoTileManager)
    meetingSession!!.audioVideo.addDeviceChangeObserver(deviceObserver!!)
    meetingSession!!.audioVideo.addRealtimeObserver(realTimeObserver!!)

    audioVideoObserver = AudioVideoObserverImpl(
        onConnectionStatusChanged = onConnectionStatusChanged,
        onRemoteVideoAvailable = onRemoteVideoAvailable,
        onCameraSendAvailable = onCameraSendAvailable,
        onSessionError = onSessionError,
        onVideoNeedsRestart = onVideoNeedsRestart,
        isJoiningOnMute = isJoiningOnMute
    )
    meetingSession!!.audioVideo.addAudioVideoObserver(audioVideoObserver!!)

    MeetingActiveSpeakerObserver.onActiveSpeakersChanged = onActiveSpeakersChanged
    meetingSession!!.audioVideo.addActiveSpeakerObserver(
        observer = MeetingActiveSpeakerObserver,
        policy = DefaultActiveSpeakerPolicy()
    )

    chatObserver = ChatObserver(
        onChatMessageReceived = onChatMessageReceived,
        onEmojiReceived = onEmojiReceived,
        onSystemMessage = onSystemMessage
    )
    meetingSession!!.audioVideo.addRealtimeDataMessageObserver("chat", chatObserver!!)
    meetingSession!!.audioVideo.addRealtimeDataMessageObserver("emoji", chatObserver!!)
    meetingSession!!.audioVideo.addRealtimeDataMessageObserver("system", chatObserver!!)

    meetingSession!!.audioVideo.start()
    meetingSession!!.audioVideo.startRemoteVideo()
}

actual fun leaveMeeting() {
    try {
        if (cameraOn || cameraCaptureSource != null) {
            cameraCaptureSource?.stop()
            meetingSession?.audioVideo?.stopLocalVideo()
            cameraOn = false
            cameraCaptureSource = null
        }

        meetingSession?.audioVideo?.stopRemoteVideo()
        meetingSession?.audioVideo?.realtimeLocalMute()

        deviceObserver?.let { meetingSession?.audioVideo?.removeDeviceChangeObserver(it) }
        realTimeObserver?.let { meetingSession?.audioVideo?.removeRealtimeObserver(it) }
        meetingSession?.audioVideo?.removeVideoTileObserver(VideoTileManager)
        audioVideoObserver?.let { meetingSession?.audioVideo?.removeAudioVideoObserver(it) }
        meetingSession?.audioVideo?.removeActiveSpeakerObserver(MeetingActiveSpeakerObserver)

        chatObserver?.let {
            meetingSession?.audioVideo?.removeRealtimeDataMessageObserverFromTopic("chat")
            meetingSession?.audioVideo?.removeRealtimeDataMessageObserverFromTopic("emoji")
            meetingSession?.audioVideo?.removeRealtimeDataMessageObserverFromTopic("system")
        }

        meetingSession?.audioVideo?.stop()
        deviceObserver?.clearCurrentDevice()
        VideoTileManager.clearAll()
    } catch (e: Exception) {
        e.printStackTrace()
        VideoTileManager.clearAll()
    } finally {
        eglCoreFactory = null
        meetingSession = null
        deviceObserver = null
        realTimeObserver = null
        chatObserver = null
        audioVideoObserver = null
        eventAnalyticsController = null
        cameraCaptureSource = null
        cachedVideoDevices = null
        cameraOn = false
    }
}

actual fun startLocalVideo() {
    val session = meetingSession ?: throw IllegalStateException("No active meeting session")

    val videoDevices = cachedVideoDevices ?: run {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        MediaDevice.listVideoDevices(cm).also { cachedVideoDevices = it }
    }

    val desiredType = if (currentCameraFacing == CameraFacing.FRONT)
        MediaDeviceType.VIDEO_FRONT_CAMERA else MediaDeviceType.VIDEO_BACK_CAMERA

    val device = videoDevices.firstOrNull { it.type == desiredType }
        ?: throw IllegalStateException("No camera found for $desiredType")

    if (cameraCaptureSource == null) {
        val factory = DefaultSurfaceTextureCaptureSourceFactory(
            chimeLogger,
            eglCoreFactory ?: throw IllegalStateException("EGL factory not initialized")
        )
        cameraCaptureSource = DefaultCameraCaptureSource(
            appContext, chimeLogger, factory,
            eventAnalyticsController = eventAnalyticsController
        )
    }

    cameraCaptureSource!!.device = device
    cameraCaptureSource!!.start()
    session.audioVideo.startLocalVideo(cameraCaptureSource!!)
    cameraOn = true
}

actual fun stopLocalVideo() {
    try {
        meetingSession?.audioVideo?.stopLocalVideo()
        cameraCaptureSource?.stop()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        cameraOn = false
    }
}

@Composable
actual fun LocalVideoView(modifier: Modifier, cameraFacing: CameraFacing, isOnTop: Boolean) {
    VideoTileView(
        tileId = VideoTileManager.localTileId,
        modifier = modifier,
        cameraFacing = cameraFacing,
        isOnTop = isOnTop
    )
}

@Composable
actual fun RemoteVideoView(modifier: Modifier, tileId: Int, isOnTop: Boolean) {
    VideoTileView(
        tileId = tileId,
        modifier = modifier,
        isOnTop = isOnTop
    )
}

actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
    meetingSession?.audioVideo?.realtimeSendDataMessage(topic, data, lifetimeMs.toInt())
}

actual fun setMute(shouldMute: Boolean): Boolean {
    val success = if (shouldMute) {
        meetingSession?.audioVideo?.realtimeLocalMute() ?: false
    } else {
        meetingSession?.audioVideo?.realtimeLocalUnmute() ?: false
    }
    return success
}

actual fun switchCamera() {
    val source = cameraCaptureSource ?: return

    val previous = currentCameraFacing
    currentCameraFacing = if (currentCameraFacing == CameraFacing.FRONT) CameraFacing.BACK else CameraFacing.FRONT

    val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val devices = MediaDevice.listVideoDevices(cm)
    cachedVideoDevices = devices

    val desiredType = if (currentCameraFacing == CameraFacing.FRONT)
        MediaDeviceType.VIDEO_FRONT_CAMERA else MediaDeviceType.VIDEO_BACK_CAMERA

    val newDevice = devices.firstOrNull { it.type == desiredType }
    if (newDevice == null) {
        currentCameraFacing = previous
        return
    }
    source.device = newDevice
}

actual fun switchAudioDevice(deviceId: String?) {
    if (deviceId.isNullOrBlank()) return
    val devices = meetingSession?.audioVideo?.listAudioDevices() ?: return

    val target = devices.firstOrNull { device ->
        if (device.id == deviceId) {
            true
        } else if (device.id.isNullOrBlank()) {
            val typeConst = when (device.type) {
                MediaDeviceType.AUDIO_BUILTIN_SPEAKER -> AudioDeviceType.SPEAKER
                MediaDeviceType.AUDIO_HANDSET -> AudioDeviceType.EARPIECE
                MediaDeviceType.AUDIO_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                MediaDeviceType.AUDIO_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                else -> AudioDeviceType.UNKNOWN
            }
            val typeName = typeConst.name
            val suffix = when (typeConst) {
                AudioDeviceType.SPEAKER -> "PHONE_SPEAKER"
                AudioDeviceType.EARPIECE -> "HANDSET_EARPIECE"
                AudioDeviceType.BLUETOOTH -> deviceId.substringAfter("${typeName}_", "")
                else -> device.label.replace("[^A-Za-z0-9]".toRegex(), "_").uppercase()
            }
            "${typeName}_$suffix" == deviceId
        } else {
            false
        }
    }

    target?.let { meetingSession?.audioVideo?.chooseAudioDevice(it) }
}
