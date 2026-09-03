package com.wannaverse.chimesdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.amazonaws.services.chime.sdk.meetings.analytics.DefaultEventAnalyticsController
import com.amazonaws.services.chime.sdk.meetings.analytics.DefaultMeetingStatsCollector
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerpolicy.DefaultActiveSpeakerPolicy
import com.amazonaws.services.chime.sdk.meetings.audiovideo.contentshare.ContentShareSource
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.DefaultCameraCaptureSource
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.DefaultScreenCaptureSource
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.DefaultSurfaceTextureCaptureSourceFactory
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.gl.DefaultEglCoreFactory
import com.amazonaws.services.chime.sdk.meetings.device.MediaDevice
import com.amazonaws.services.chime.sdk.meetings.device.MediaDeviceType
import com.amazonaws.services.chime.sdk.meetings.ingestion.DefaultAppStateMonitor
import com.amazonaws.services.chime.sdk.meetings.session.DefaultMeetingSession
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionConfiguration
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionCredentials
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionURLs
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ChimeSDK(
    private val meetingSession: DefaultMeetingSession,
    private val eventAnalyticsController: DefaultEventAnalyticsController,
    private val eglCoreFactory: DefaultEglCoreFactory
) {
    actual companion object {
        internal lateinit var activity: ComponentActivity

        context(activity: ComponentActivity)
        fun initialize() {
            this.activity = activity
        }

        private val logger = ConsoleLogger(LogLevel.INFO)

        actual fun createSession(
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
        ): ChimeSDK {
            val meetingSessionConfiguration = MeetingSessionConfiguration(
                meetingId = meetingId,
                externalMeetingId = externalMeetingId,
                credentials = MeetingSessionCredentials(
                    attendeeId = attendeeId,
                    externalUserId = externalUserId,
                    joinToken = joinToken
                ),
                urls = MeetingSessionURLs(
                    _audioFallbackURL = audioFallbackURL,
                    _audioHostURL = audioHostURL,
                    _ingestionURL = ingestionURL,
                    _signalingURL = signalingURL,
                    _turnControlURL = turnControlURL,
                    urlRewriter = { it }
                )
            )

            val eventAnalyticsController = DefaultEventAnalyticsController(
                logger = logger,
                meetingSessionConfiguration = meetingSessionConfiguration,
                meetingStatsCollector = DefaultMeetingStatsCollector(logger),
                appStateMonitor = DefaultAppStateMonitor(logger)
            )

            val eglCoreFactory = DefaultEglCoreFactory()

            val meetingSession =
                DefaultMeetingSession(
                    meetingSessionConfiguration,
                    logger,
                    activity.applicationContext,
                    eglCoreFactory
                )

            return ChimeSDK(meetingSession, eventAnalyticsController, eglCoreFactory)
        }
    }

    private lateinit var realTimeObserver: RealTimeObserverImpl
    private lateinit var deviceObserver: DeviceObserverImpl
    private lateinit var videoTileObserver: VideoTileObserverImpl
    private lateinit var audioVideoObserver: AudioVideoObserverImpl
    private lateinit var activeSpeakerObserver: ActiveSpeakerObserverImpl
    private lateinit var dataMessageObserver: DataMessageObserverImpl

    actual fun getAvailableInputDevices(): List<AudioDevice> =
        meetingSession.audioVideo
            .listAudioDevices()
            .mapNotNull { device ->
                val type = when (device.type) {
                    MediaDeviceType.AUDIO_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                    MediaDeviceType.AUDIO_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceType.AUDIO_USB_HEADSET -> AudioDeviceType.EARPIECE
                    MediaDeviceType.AUDIO_HANDSET -> AudioDeviceType.BUILT_IN_MIC
                    else -> return@mapNotNull null
                }

                AudioDevice(
                    type = type,
                    label = device.label
                )
            }

    actual fun getAvailableOutputDevices(): List<AudioDevice> =
        meetingSession.audioVideo
            .listAudioDevices()
            .mapNotNull { device ->
                val type = when (device.type) {
                    MediaDeviceType.AUDIO_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                    MediaDeviceType.AUDIO_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceType.AUDIO_USB_HEADSET -> AudioDeviceType.EARPIECE
                    MediaDeviceType.AUDIO_BUILTIN_SPEAKER -> AudioDeviceType.SPEAKER
                    else -> return@mapNotNull null
                }

                AudioDevice(
                    type = type,
                    label = device.label
                )
            }

    actual fun joinMeeting(
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
    ) {
        realTimeObserver = RealTimeObserverImpl(realTimeListener)
        meetingSession.audioVideo.addRealtimeObserver(realTimeObserver)

        deviceObserver = DeviceObserverImpl(
            meetingSession = meetingSession,
            realTimeEventListener = realTimeListener
        )
        meetingSession.audioVideo.addDeviceChangeObserver(deviceObserver)

        meetingSession.audioVideo.listAudioDevices()
            .firstOrNull { it.label == selectedAudioInputDevice }
            ?.let(deviceObserver::selectAudioDevice)

        videoTileObserver = VideoTileObserverImpl(
            meetingSession = meetingSession,
            onLocalTileAdded = onLocalTileAdded,
            onLocalTileRemoved = onLocalTileRemoved,
            onRemoteTileAdded = onRemoteTileAdded,
            onRemoteTileRemoved = onRemoteTileRemoved
        )
        meetingSession.audioVideo.addVideoTileObserver(videoTileObserver)

        audioVideoObserver = AudioVideoObserverImpl(
            meetingSession = meetingSession,
            onConnectionStatusChanged = onConnectionStatusChanged,
            onRemoteVideoAvailable = onRemoteVideoAvailable,
            onCameraSendAvailable = onCameraSendAvailable,
            onSessionError = onSessionError,
            isJoiningOnMute = isJoiningOnMute
        )
        meetingSession.audioVideo.addAudioVideoObserver(audioVideoObserver)

        activeSpeakerObserver = ActiveSpeakerObserverImpl(onActiveSpeakersChanged)
        meetingSession.audioVideo.addActiveSpeakerObserver(
            observer = activeSpeakerObserver,
            policy = DefaultActiveSpeakerPolicy()
        )

        dataMessageObserver = DataMessageObserverImpl(meetingSession)

        meetingSession.audioVideo.start()
        meetingSession.audioVideo.startRemoteVideo()
    }

    private var cameraCaptureSource: DefaultCameraCaptureSource? = null

    private fun stopCameraCaptureSource() {
        cameraCaptureSource?.torchEnabled = false
        cameraCaptureSource?.stop()
        cameraCaptureSource = null

        meetingSession.audioVideo.stopLocalVideo()
    }

    private var screenCaptureSource: DefaultScreenCaptureSource? = null
    private var screenCaptureServiceIntent: Intent? = null

    private fun stopScreenCaptureSource() {
        screenCaptureSource?.stop()
        activity.stopService(screenCaptureServiceIntent)
        screenCaptureSource = null
        screenCaptureServiceIntent = null

        meetingSession.audioVideo.stopContentShare()
    }

    actual fun getActiveAudioDevice(): AudioDevice? = meetingSession.audioVideo
        .getActiveAudioDevice()
        ?.let { device ->
            val type = when (device.type) {
                MediaDeviceType.AUDIO_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                MediaDeviceType.AUDIO_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                MediaDeviceType.AUDIO_USB_HEADSET -> AudioDeviceType.EARPIECE
                MediaDeviceType.AUDIO_HANDSET -> AudioDeviceType.BUILT_IN_MIC
                MediaDeviceType.AUDIO_BUILTIN_SPEAKER -> AudioDeviceType.SPEAKER
                else -> return null
            }

            return AudioDevice(
                type = type,
                label = device.label
            )
        }

    actual fun leaveMeeting() {
        stopCameraCaptureSource()

        meetingSession.audioVideo.removeRealtimeObserver(realTimeObserver)
        meetingSession.audioVideo.removeDeviceChangeObserver(deviceObserver)
        meetingSession.audioVideo.removeVideoTileObserver(videoTileObserver)
        meetingSession.audioVideo.removeAudioVideoObserver(audioVideoObserver)
        meetingSession.audioVideo.removeActiveSpeakerObserver(activeSpeakerObserver)
        dataMessageObserver.clearListeners()

        meetingSession.audioVideo.stopRemoteVideo()
        meetingSession.audioVideo.stop()
    }

    actual fun startLocalVideo(cameraFacing: CameraFacing) {
        val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camera = MediaDevice.listVideoDevices(cameraManager).first {
            it.type == if (cameraFacing == CameraFacing.FRONT) MediaDeviceType.VIDEO_FRONT_CAMERA else MediaDeviceType.VIDEO_BACK_CAMERA
        }

        val factory = DefaultSurfaceTextureCaptureSourceFactory(logger, eglCoreFactory)
        cameraCaptureSource = DefaultCameraCaptureSource(
            context = activity,
            logger = logger,
            surfaceTextureCaptureSourceFactory = factory,
            eventAnalyticsController = eventAnalyticsController
        ).apply {
            device = camera
            start()

            meetingSession.audioVideo.startLocalVideo(this)
        }
    }

    actual fun stopLocalVideo() = stopCameraCaptureSource()

    @Composable
    actual fun LocalVideoView(cameraFacing: CameraFacing, modifier: Modifier) {
        val mirror = remember(cameraFacing) { cameraFacing == CameraFacing.FRONT }

        AndroidView(
            factory = {
                videoTileObserver.localRenderView.apply { this.mirror = mirror }
            },
            modifier = modifier,
            update = {
                it.mirror = mirror
            }
        )
    }

    @Composable
    actual fun RemoteVideoView(tileId: Int, modifier: Modifier) = AndroidView(
        factory = {
            videoTileObserver.getRemoteRenderView(tileId)
                ?: throw IllegalStateException("Remote view for tile $tileId not found")
        },
        modifier = modifier,
        update = {}
    )

    actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) =
        meetingSession.audioVideo.realtimeSendDataMessage(topic, data, lifetimeMs.toInt())

    actual fun setMute(shouldMute: Boolean): Boolean =
        if (shouldMute) meetingSession.audioVideo.realtimeLocalMute() else meetingSession.audioVideo.realtimeLocalUnmute()

    actual fun switchCamera() {
        cameraCaptureSource?.switchCamera()
    }

    actual fun torchAvailable(): Boolean = true

    actual fun torchEnabled(): Boolean = cameraCaptureSource?.torchEnabled ?: false

    actual fun setTorchEnabled(enabled: Boolean) {
        cameraCaptureSource?.torchEnabled = enabled
    }

    actual fun switchAudioDevice(device: AudioDevice?) {
        meetingSession.audioVideo.listAudioDevices()
            .firstOrNull { it.label == device?.label }
            ?.let(meetingSession.audioVideo::chooseAudioDevice)
    }

    actual fun subscribeToTopic(topic: String, listener: (ChimeMessage) -> Unit) =
        dataMessageObserver.addListener(topic, listener)

    actual fun unsubscribeFromTopic(topic: String) = dataMessageObserver.removeListener(topic)

    @Composable
    actual fun ScreenShareButton() {
        val screenCaptureLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

                with(activity) {
                    screenCaptureServiceIntent = Intent(this, ScreenCaptureService::class.java)
                    startService(screenCaptureServiceIntent)

                    screenCaptureSource = DefaultScreenCaptureSource(
                        this,
                        logger,
                        DefaultSurfaceTextureCaptureSourceFactory(logger, eglCoreFactory),
                        it.resultCode,
                        it.data!!
                    )
                    screenCaptureSource!!.start()

                    val contentShareSource = ContentShareSource().apply {
                        videoSource = screenCaptureSource
                    }

                    println("starting share")
                    meetingSession.audioVideo.startContentShare(contentShareSource)
                }
            }

        val mediaProjectionManager = remember {
            activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        }

        val buttonSize = 50.dp
        var isRecording by rememberSaveable { mutableStateOf(false) }

        // Outer ring dynamic properties
        val borderWidth = buttonSize * 0.06f
        val outerPadding = buttonSize * 0.08f

        // Inner icon state animations
        val innerCornerRadius by animateDpAsState(
            targetValue = if (isRecording) 8.dp else (buttonSize / 2),
            animationSpec = tween(durationMillis = 300),
            label = "SquareToCircleRadius"
        )

        val innerSizeScale by animateFloatAsState(
            targetValue = if (isRecording) 0.42f else 0.82f,
            animationSpec = tween(durationMillis = 300),
            label = "InnerSizeScale"
        )

        Box(
            modifier = Modifier
                .size(buttonSize)
                .border(
                    width = borderWidth,
                    color = Color.White,
                    shape = CircleShape
                )
                .padding(outerPadding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent()) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(buttonSize * innerSizeScale)
                    .clip(RoundedCornerShape(innerCornerRadius))
                    .background(Color.Blue)
            )
        }
    }
}
