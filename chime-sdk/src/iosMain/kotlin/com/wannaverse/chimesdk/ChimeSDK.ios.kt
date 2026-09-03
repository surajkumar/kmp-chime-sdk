@file:OptIn(ExperimentalForeignApi::class)

package com.wannaverse.chimesdk

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.zIndex
import cocoapods.AmazonChimeSDK.ConsoleLogger
import cocoapods.AmazonChimeSDK.DefaultActiveSpeakerPolicy
import cocoapods.AmazonChimeSDK.DefaultCameraCaptureSource
import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import cocoapods.AmazonChimeSDK.LogLevelINFO
import cocoapods.AmazonChimeSDK.MediaDevice
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBluetooth
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBuiltInSpeaker
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioHandset
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioWiredHeadset
import cocoapods.AmazonChimeSDK.MediaDeviceTypeVideoBackCamera
import cocoapods.AmazonChimeSDK.MediaDeviceTypeVideoFrontCamera
import cocoapods.AmazonChimeSDK.MeetingSessionConfiguration
import cocoapods.AmazonChimeSDK.MeetingSessionCredentials
import cocoapods.AmazonChimeSDK.MeetingSessionURLs
import cocoapods.AmazonChimeSDK.URLRewriterUtils
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothHFP
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVideoChat
import platform.AVFAudio.setActive
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults
import platform.ReplayKit.RPSystemBroadcastPickerView
import platform.UIKit.NSLayoutAttributeCenterX
import platform.UIKit.NSLayoutAttributeCenterY
import platform.UIKit.NSLayoutAttributeHeight
import platform.UIKit.NSLayoutAttributeNotAnAttribute
import platform.UIKit.NSLayoutAttributeWidth
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSLayoutRelationEqual
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject
import platform.darwin.nil

// Due to a kotlin limitation we cannot create fields in companion objects of classes which extend from objc
private object CompanionObject {
    val logger = ConsoleLogger(name = "ChimeSDK", level = LogLevelINFO)

    lateinit var bundleIdentifier: String
}

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ChimeSDK(
    private val meetingSession: DefaultMeetingSession
) : NSObject() {
    actual companion object {
        fun initialize(bundleIdentifier: String) {
            CompanionObject.bundleIdentifier = bundleIdentifier
        }

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
            val credentials = MeetingSessionCredentials(
                attendeeId = attendeeId,
                externalUserId = externalUserId,
                joinToken = joinToken
            )

            val urls = MeetingSessionURLs(
                audioFallbackUrl = audioFallbackURL,
                audioHostUrl = audioHostURL,
                turnControlUrl = turnControlURL,
                signalingUrl = signalingURL,
                urlRewriter = { it },
                ingestionUrl = ingestionURL
            )

            val configuration = MeetingSessionConfiguration(
                meetingId = meetingId,
                externalMeetingId = externalMeetingId,
                credentials = credentials,
                urls = urls,
                urlRewriter = URLRewriterUtils.defaultUrlRewriter()
            )

            val meetingSession =
                DefaultMeetingSession(configuration = configuration, logger = CompanionObject.logger)

            return ChimeSDK(meetingSession)
        }
    }

    private lateinit var realtimeObserver: RealTimeObserverImpl
    private lateinit var deviceObserver: DeviceObserverImpl
    private lateinit var videoTileObserver: VideoTileObserverImpl
    private lateinit var audioVideoObserver: AudioVideoObserverImpl
    private lateinit var activeSpeakerObserver: ActiveSpeakerObserverImpl
    private lateinit var dataMessageObserver: DataMessageObserverImpl

    private var cameraCaptureSource: DefaultCameraCaptureSource? = null

    private fun stopCameraCaptureSource() {
        cameraCaptureSource?.setTorchEnabled(false)
        cameraCaptureSource?.stop()
        cameraCaptureSource = null
    }

    actual fun getAvailableInputDevices(): List<AudioDevice> =
        meetingSession.audioVideo()
            .listAudioDevices()
            .mapNotNull { device ->
                if (device !is MediaDevice) return@mapNotNull null
                val type = when (device.type()) {
                    MediaDeviceTypeAudioBluetooth -> AudioDeviceType.BLUETOOTH
                    MediaDeviceTypeAudioWiredHeadset -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceTypeAudioHandset -> AudioDeviceType.BUILT_IN_MIC
                    else -> return@mapNotNull null
                }

                AudioDevice(
                    type = type,
                    label = device.label()
                )
            }

    actual fun getAvailableOutputDevices(): List<AudioDevice> =
        meetingSession.audioVideo()
            .listAudioDevices()
            .mapNotNull { device ->
                if (device !is MediaDevice) return@mapNotNull null
                val type = when (device.type()) {
                    MediaDeviceTypeAudioBluetooth -> AudioDeviceType.BLUETOOTH
                    MediaDeviceTypeAudioWiredHeadset -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceTypeAudioBuiltInSpeaker -> AudioDeviceType.SPEAKER
                    else -> return@mapNotNull null
                }

                AudioDevice(
                    type = type,
                    label = device.label()
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
        realtimeObserver = RealTimeObserverImpl(realTimeListener)
        meetingSession.audioVideo().addRealtimeObserverWithObserver(realtimeObserver)

        deviceObserver = DeviceObserverImpl(meetingSession, realTimeListener)
        meetingSession.audioVideo().addDeviceChangeObserverWithObserver(deviceObserver)

        meetingSession.audioVideo().listAudioDevices()
            .filterIsInstance<MediaDevice>()
            .firstOrNull { it.label() == selectedAudioInputDevice }
            ?.let(deviceObserver::selectAudioDevice)

        videoTileObserver = VideoTileObserverImpl(
            meetingSession = meetingSession,
            onLocalTileAdded = onLocalTileAdded,
            onLocalTileRemoved = onLocalTileRemoved,
            onRemoteTileAdded = onRemoteTileAdded,
            onRemoteTileRemoved = onRemoteTileRemoved
        )
        meetingSession.audioVideo().addVideoTileObserverWithObserver(observer = videoTileObserver)

        audioVideoObserver = AudioVideoObserverImpl(
            meetingSession = meetingSession,
            onConnectionStatusChanged = onConnectionStatusChanged,
            onRemoteVideoAvailable = onRemoteVideoAvailable,
            onCameraSendAvailable = onCameraSendAvailable,
            onSessionError = onSessionError,
            isJoiningOnMute = isJoiningOnMute
        )
        meetingSession.audioVideo().addAudioVideoObserverWithObserver(audioVideoObserver)

        activeSpeakerObserver = ActiveSpeakerObserverImpl(onActiveSpeakersChanged)
        meetingSession.audioVideo().addActiveSpeakerObserverWithPolicy(
            policy = DefaultActiveSpeakerPolicy(),
            observer = activeSpeakerObserver
        )

        dataMessageObserver = DataMessageObserverImpl(meetingSession)

        configureAudioSession()

        AVAudioSession.sharedInstance().requestRecordPermission { _ ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    try {
                        meetingSession.audioVideo().startAndReturnError(error = null)
                        meetingSession.audioVideo().startRemoteVideo()
                    } catch (e: Throwable) {
                        onSessionError.invoke("Failed to start audio: ${e.message}", false)
                    }
                }
            }
        }
    }

    actual fun getActiveAudioDevice(): AudioDevice? = meetingSession.audioVideo()
        .getActiveAudioDevice()
        ?.let { device ->
            val type = when (device.type()) {
                MediaDeviceTypeAudioBluetooth -> AudioDeviceType.BLUETOOTH
                MediaDeviceTypeAudioWiredHeadset -> AudioDeviceType.WIRED_HEADSET
                MediaDeviceTypeAudioHandset -> AudioDeviceType.BUILT_IN_MIC
                MediaDeviceTypeAudioBuiltInSpeaker -> AudioDeviceType.SPEAKER
                else -> return null
            }

            return AudioDevice(
                type = type,
                label = device.label()
            )
        }

    actual fun leaveMeeting() {
        stopCameraCaptureSource()

        meetingSession.audioVideo().removeRealtimeObserverWithObserver(realtimeObserver)
        meetingSession.audioVideo().removeDeviceChangeObserverWithObserver(deviceObserver)
        meetingSession.audioVideo().removeVideoTileObserverWithObserver(videoTileObserver)
        meetingSession.audioVideo().removeAudioVideoObserverWithObserver(audioVideoObserver)
        meetingSession.audioVideo().removeActiveSpeakerObserverWithObserver(activeSpeakerObserver)
        dataMessageObserver.clearListeners()

        meetingSession.audioVideo().stopLocalVideo()
        meetingSession.audioVideo().stopRemoteVideo()
        meetingSession.audioVideo().stop()
    }

    actual fun startLocalVideo(cameraFacing: CameraFacing) {
        meetingSession.audioVideo().startLocalVideoAndReturnError(error = null)
        val camera = MediaDevice.listVideoDevices()
            .filterIsInstance<MediaDevice>()
            .first {
                it.type() == if (cameraFacing == CameraFacing.FRONT) MediaDeviceTypeVideoFrontCamera else MediaDeviceTypeVideoBackCamera
            }

        cameraCaptureSource = DefaultCameraCaptureSource(CompanionObject.logger).apply {
            setDevice(camera)
            start()

            meetingSession.audioVideo().startLocalVideoWithSource(this)
        }
    }

    actual fun stopLocalVideo() {
        meetingSession.audioVideo().stopLocalVideo()
        stopCameraCaptureSource()
    }

    @Composable
    actual fun LocalVideoView(cameraFacing: CameraFacing, modifier: Modifier) {
        val mirror = remember(cameraFacing) { cameraFacing == CameraFacing.FRONT }

        UIKitView(
            factory = {
                videoTileObserver.localRenderView.apply {
                    contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                    layer.masksToBounds = true
                    setMirror(mirror)
                }
            },
            modifier = modifier,
            update = {
                it.setMirror(mirror)
            }
        )
    }

    @Composable
    actual fun RemoteVideoView(tileId: Int, modifier: Modifier) = UIKitView(
        factory = {
            (videoTileObserver.getRemoteView(tileId)
                ?: throw IllegalArgumentException("Remote view for tile $tileId not found")
                    ).apply {
                    contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                    layer.masksToBounds = true
                }
        },
        modifier = modifier,
        update = {}
    )

    actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
        meetingSession.audioVideo().realtimeSendDataMessageWithTopic(
            topic = topic,
            data = data,
            lifetimeMs = lifetimeMs.toInt(),
            error = null
        )
    }

    actual fun setMute(shouldMute: Boolean): Boolean = meetingSession.audioVideo().run {
        return if (shouldMute) realtimeLocalMute() else realtimeLocalUnmute()
    }

    actual fun switchCamera() {
        cameraCaptureSource?.switchCamera()
    }

    actual fun torchAvailable(): Boolean = cameraCaptureSource?.torchAvailable ?: false

    actual fun torchEnabled(): Boolean = cameraCaptureSource?.torchEnabled() ?: false

    actual fun setTorchEnabled(enabled: Boolean) {
        cameraCaptureSource?.setTorchEnabled(enabled)
    }

    actual fun switchAudioDevice(device: AudioDevice?) {
        val targetChimeDevice = meetingSession
            .audioVideo()
            .listAudioDevices()
            .filterIsInstance<MediaDevice>()
            .firstOrNull { it.label() == device?.label } ?: return
        meetingSession.audioVideo().chooseAudioDeviceWithMediaDevice(targetChimeDevice)
    }

    actual fun subscribeToTopic(topic: String, listener: (ChimeMessage) -> Unit) =
        dataMessageObserver.addListener(topic, listener)

    actual fun unsubscribeFromTopic(topic: String) = dataMessageObserver.removeListener(topic)

    private fun configureAudioSession() {
        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(
            category = AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeVideoChat,
            options = AVAudioSessionCategoryOptionAllowBluetoothHFP or AVAudioSessionCategoryOptionAllowBluetoothA2DP,
            error = null
        )
        audioSession.setActive(true, null)
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    actual fun ScreenShareButton() {
        SideEffect {
            val meetingSessionConfig = meetingSession.configuration()
            val meetingId = meetingSessionConfig.meetingId()
            val meetingCredentials = meetingSessionConfig.credentials()
            val meetingUrls = meetingSessionConfig.urls()

            val userDefaultsMeetingIdKey = "meetingId"
            val userDefaultsCredentialsKey = "meetingCredentials"
            val userDefaultsUrlsKey = "meetingUrls"

            val credentialsJson = buildJsonObject {
                put("attendeeId", meetingCredentials.attendeeId())
                put("externalUserId", meetingCredentials.externalUserId())
                put("joinToken", meetingCredentials.joinToken())
            }.toString()
            val urlsJson = buildJsonObject {
                put("audioFallbackUrl", meetingUrls.audioFallbackUrl())
                put("audioHostUrl", meetingUrls.audioHostUrl())
                put("turnControlUrl", meetingUrls.turnControlUrl())
                put("signalingUrl", meetingUrls.signalingUrl())
                put("ingestionUrl", meetingUrls.ingestionUrl())
            }.toString()

            NSUserDefaults(suiteName = "group.${CompanionObject.bundleIdentifier}").apply {
                setObject(meetingId as NSString, forKey = userDefaultsMeetingIdKey)
                setObject(credentialsJson as NSString, forKey = userDefaultsCredentialsKey)
                setObject(urlsJson as NSString, forKey = userDefaultsUrlsKey)
            }
        }

        val pickerViewDiameter = remember { 35.0 }
        val broadcastPicker = remember {
            RPSystemBroadcastPickerView(
                frame = CGRectMake(
                    x = 0.0,
                    y = 0.0,
                    width = pickerViewDiameter,
                    height = pickerViewDiameter
                )
            ).apply {
                setPreferredExtension("${CompanionObject.bundleIdentifier}.ScreenCaptureService")
                setShowsMicrophoneButton(false)
            }
        }

        UIKitView(
            factory = {
                UIView().apply {
                    addSubview(broadcastPicker)
                    bringSubviewToFront(broadcastPicker)
                }
            },
            modifier = Modifier.size(pickerViewDiameter.dp).zIndex(99f),
            properties = UIKitInteropProperties(
                placedAsOverlay = true
            ),
            update = {
                it.setNeedsLayout()
                val centerX = NSLayoutConstraint.constraintWithItem(
                    view1 = broadcastPicker,
                    attribute = NSLayoutAttributeCenterX,
                    relatedBy = NSLayoutRelationEqual,
                    toItem = it,
                    _attribute = NSLayoutAttributeCenterX,
                    multiplier = 1.0,
                    constant = 0.0,
                )
                val centerY = NSLayoutConstraint.constraintWithItem(
                    view1 = broadcastPicker,
                    attribute = NSLayoutAttributeCenterY,
                    relatedBy = NSLayoutRelationEqual,
                    toItem = it,
                    _attribute = NSLayoutAttributeCenterY,
                    multiplier = 1.0,
                    constant = 0.0,
                )
                val width = NSLayoutConstraint.constraintWithItem(
                    view1 = broadcastPicker,
                    attribute = NSLayoutAttributeWidth,
                    relatedBy = NSLayoutRelationEqual,
                    toItem = nil,
                    _attribute = NSLayoutAttributeNotAnAttribute,
                    multiplier = 1.0,
                    constant = pickerViewDiameter,
                )
                val height = NSLayoutConstraint.constraintWithItem(
                    view1 = broadcastPicker,
                    attribute = NSLayoutAttributeHeight,
                    relatedBy = NSLayoutRelationEqual,
                    toItem = nil,
                    _attribute = NSLayoutAttributeNotAnAttribute,
                    multiplier = 1.0,
                    constant = pickerViewDiameter,
                )
                it.addConstraints(listOf(centerX, centerY, width, height))
            }
        )
    }
}