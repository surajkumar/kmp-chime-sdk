@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.*
import kotlinx.cinterop.cValue
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreGraphics.CGRect
import platform.Foundation.*
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.objc.class_addProtocol
import platform.objc.object_getClass
import platform.objc.objc_getProtocol

internal val chimeMeeting = ChimeMeetingImpl()

internal class LocalVideoContainerView : UIView(frame = cValue<CGRect>()) {
    override fun layoutSubviews() {
        super.layoutSubviews()
        chimeMeeting.localRenderView.setFrame(bounds)
    }
}

internal class ChimeMeetingImpl : NSObject(),
    AudioVideoObserverProtocol,
    RealtimeObserverProtocol,
    VideoTileObserverProtocol,
    DeviceChangeObserverProtocol,
    ActiveSpeakerObserverProtocol,
    DataMessageObserverProtocol {

    private var meetingSession: DefaultMeetingSession? = null
    private var isFrontCamera = true
    private var isJoiningOnMute = false
    private val subscribedTopics: MutableSet<String> = mutableSetOf()
    private var isRemoteVideoStarted = false

    private var localTileId: Long? = null
    private val remoteTiles: MutableMap<Long, DefaultVideoRenderView> = mutableMapOf()
    private val attendeeTileMap: MutableMap<String, Long> = mutableMapOf()
    private val remoteVideoSources: MutableMap<String, RemoteVideoSource> = mutableMapOf()

    val localRenderView: DefaultVideoRenderView = DefaultVideoRenderView().also { it.setMirror(true) }
    val localVideoContainer: LocalVideoContainerView = LocalVideoContainerView().also { it.addSubview(localRenderView) }
    var onConnectionStatusChanged: ((ConnectionStatus) -> Unit)? = null
    var onActiveSpeakersChanged: ((Set<String>) -> Unit)? = null
    var onLocalVideoTileAdded: ((Int?) -> Unit)? = null
    var onRemoteVideoAvailable: ((Boolean, Int) -> Unit)? = null
    var onCameraSendAvailable: ((Boolean) -> Unit)? = null
    var onSessionError: ((String, Boolean) -> Unit)? = null
    var onVideoNeedsRestart: (() -> Unit)? = null
    var onLocalVideoTileRemoved: (() -> Unit)? = null
    var onRemoteTileAdded: ((Int) -> Unit)? = null
    var onRemoteTileRemoved: ((Int) -> Unit)? = null
    var onLocalAttendeeIdAvailable: ((String) -> Unit)? = null
    var realTimeListener: RealTimeEventListener? = null
    val topicListeners: MutableMap<String, (TextMessage) -> Unit> = mutableMapOf()

    override fun observerId(): String = "KotlinChimeMeeting"

    private data class ObserverProtocolDescriptor(
        val label: String,
        val candidates: List<String>
    )

    private val observerProtocols = listOf(
        ObserverProtocolDescriptor(
            label = "audio",
            candidates = listOf("AudioVideoObserver", "_TtP14AmazonChimeSDK18AudioVideoObserver_")
        ),
        ObserverProtocolDescriptor(
            label = "realtime",
            candidates = listOf("RealtimeObserver", "_TtP14AmazonChimeSDK16RealtimeObserver_")
        ),
        ObserverProtocolDescriptor(
            label = "tile",
            candidates = listOf("VideoTileObserver", "_TtP14AmazonChimeSDK17VideoTileObserver_")
        ),
        ObserverProtocolDescriptor(
            label = "device",
            candidates = listOf("DeviceChangeObserver", "_TtP14AmazonChimeSDK20DeviceChangeObserver_")
        ),
        ObserverProtocolDescriptor(
            label = "speaker",
            candidates = listOf("ActiveSpeakerObserver", "_TtP14AmazonChimeSDK21ActiveSpeakerObserver_")
        ),
        ObserverProtocolDescriptor(
            label = "data",
            candidates = listOf("DataMessageObserver", "_TtP14AmazonChimeSDK19DataMessageObserver_")
        )
    )

    init {
        @Suppress("UNUSED_VARIABLE") val _1: AudioVideoObserverProtocol = this
        @Suppress("UNUSED_VARIABLE") val _2: RealtimeObserverProtocol = this
        @Suppress("UNUSED_VARIABLE") val _3: VideoTileObserverProtocol = this
        @Suppress("UNUSED_VARIABLE") val _4: DeviceChangeObserverProtocol = this
        @Suppress("UNUSED_VARIABLE") val _5: ActiveSpeakerObserverProtocol = this
        @Suppress("UNUSED_VARIABLE") val _6: DataMessageObserverProtocol = this

        forceRegisterObserverProtocols()
    }

    private fun resolveProtocolName(descriptor: ObserverProtocolDescriptor): String? {
        for (name in descriptor.candidates) {
            val protocol = objc_getProtocol(name)
            if (protocol != null) {
                return name
            }
        }
        return null
    }

    private fun forceRegisterObserverProtocols() {
        val cls = object_getClass(this) ?: return
        for (descriptor in observerProtocols) {
            val protocolName = resolveProtocolName(descriptor) ?: continue
            val protocol = objc_getProtocol(protocolName)
            if (protocol != null) {
                class_addProtocol(cls, protocol)
            }
        }
    }

    fun getRemoteView(tileId: Int): UIView? = remoteTiles[tileId.toLong()]

    fun rebindLocalView() {
        val tileId = localTileId ?: return
        localRenderView.setFrame(localVideoContainer.bounds)
        meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
    }

    fun rebindRemoteView(tileId: Int) {
        val renderView = remoteTiles[tileId.toLong()] ?: return
        meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = renderView, tileId = tileId.toLong())
    }

    private fun startRemoteVideoIfNeeded() {
        val session = meetingSession ?: return
        if (isRemoteVideoStarted) return
        session.audioVideo().startRemoteVideo()
        isRemoteVideoStarted = true
    }

    private fun registerSubscribedTopics() {
        val session = meetingSession ?: return
        subscribedTopics.forEach { topic ->
            session.audioVideo().addRealtimeDataMessageObserverWithTopic(topic = topic, observer = this)
        }
    }

    fun join(
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
        cameraFacing: CameraFacing = CameraFacing.FRONT,
        startMuted: Boolean = false
    ) {
        isFrontCamera = (cameraFacing == CameraFacing.FRONT)
        isJoiningOnMute = startMuted
        val logger = ConsoleLogger(name = "ChimeMeetingImpl", level = LogLevelINFO)

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
            urlRewriter = { it }
        )

        val session = DefaultMeetingSession(configuration = configuration, logger = logger)
        meetingSession = session

        session.audioVideo().addVideoTileObserverWithObserver(observer = this)
        session.audioVideo().addAudioVideoObserverWithObserver(observer = this)
        session.audioVideo().addRealtimeObserverWithObserver(observer = this)
        session.audioVideo().addDeviceChangeObserverWithObserver(observer = this)
        session.audioVideo().addActiveSpeakerObserverWithPolicy(
            policy = DefaultActiveSpeakerPolicy(),
            observer = this
        )

        onLocalAttendeeIdAvailable?.invoke(attendeeId)

        configureAudioSession()

        session.audioVideo().listAudioDevices().filterIsInstance<MediaDevice>().firstOrNull()?.let {
            session.audioVideo().chooseAudioDeviceWithMediaDevice(mediaDevice = it)
        }

        AVAudioSession.sharedInstance().requestRecordPermission { _: Boolean ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _: Boolean ->
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    startAudioAndVideo()
                }
            }
        }
    }

    private fun configureAudioSession() {
        val s = AVAudioSession.sharedInstance()
        s.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeVideoChat,
            options = AVAudioSessionCategoryOptionAllowBluetoothHFP or
                    AVAudioSessionCategoryOptionAllowBluetoothA2DP,
            error = null
        )
        s.setActive(true, error = null)
    }

    private fun startAudioAndVideo() {
        val session = meetingSession ?: return

        try {
            session.audioVideo().startAndReturnError(error = null)
            startRemoteVideoIfNeeded()
            registerSubscribedTopics()
        } catch (e: Throwable) {
            onSessionError?.invoke("Failed to start audio: ${e.message}", false)
            return
        }

        if (isJoiningOnMute) {
            session.audioVideo().realtimeLocalMute()
        }

    }

    fun leave() {
        val session = meetingSession ?: return
        session.audioVideo().stopLocalVideo()
        session.audioVideo().stopRemoteVideo()
        session.audioVideo().stop()
        session.audioVideo().removeVideoTileObserverWithObserver(observer = this)
        session.audioVideo().removeAudioVideoObserverWithObserver(observer = this)
        session.audioVideo().removeRealtimeObserverWithObserver(observer = this)
        session.audioVideo().removeDeviceChangeObserverWithObserver(observer = this)
        session.audioVideo().removeActiveSpeakerObserverWithObserver(observer = this)
        subscribedTopics.forEach {
            session.audioVideo().removeRealtimeDataMessageObserverFromTopicWithTopic(topic = it)
        }
        subscribedTopics.clear()
        meetingSession = null
        localTileId = null
        remoteTiles.clear()
        attendeeTileMap.clear()
        remoteVideoSources.clear()
        isFrontCamera = true
        isJoiningOnMute = false
        isLocalVideoStarted = false
        didStartLocalVideo = false
        isRemoteVideoStarted = false
        clearCallbacks()
    }

    private fun clearCallbacks() {
        onConnectionStatusChanged = null
        onActiveSpeakersChanged = null
        onLocalVideoTileAdded = null
        onRemoteVideoAvailable = null
        onCameraSendAvailable = null
        onSessionError = null
        onVideoNeedsRestart = null
        onLocalVideoTileRemoved = null
        onRemoteTileAdded = null
        onRemoteTileRemoved = null
        onLocalAttendeeIdAvailable = null
        realTimeListener = null
        topicListeners.clear()
    }

    private var isLocalVideoStarted = false

    fun startLocalVideo() {
        if (isLocalVideoStarted) return
        meetingSession?.audioVideo()?.startLocalVideoAndReturnError(error = null)
        isLocalVideoStarted = true
    }

    fun stopLocalVideo() {
        meetingSession?.audioVideo()?.stopLocalVideo()
    }

    fun switchCamera() {
        val session = meetingSession ?: return
        isFrontCamera = !isFrontCamera
        session.audioVideo().switchCamera()
    }

    fun setMute(shouldMute: Boolean): Boolean {
        val av = meetingSession?.audioVideo() ?: return false
        return if (shouldMute) av.realtimeLocalMute() else av.realtimeLocalUnmute()
    }

    fun switchAudioDevice(deviceId: String?) {
        val id = deviceId ?: return
        meetingSession?.audioVideo()
            ?.listAudioDevices()
            ?.filterIsInstance<MediaDevice>()
            ?.firstOrNull { it.label() == id }
            ?.let { meetingSession?.audioVideo()?.chooseAudioDeviceWithMediaDevice(mediaDevice = it) }
    }

    fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
        try {
            meetingSession?.audioVideo()?.realtimeSendDataMessageWithTopic(
                topic = topic,
                data = data,
                lifetimeMs = lifetimeMs.toInt(),
                error = null
            )
        } catch (e: Throwable) {
            println("ChimeMeetingImpl: sendRealtimeMessage failed: ${e.message}")
        }
    }

    fun subscribeTopic(topic: String) {
        subscribedTopics.add(topic)
        val session = meetingSession ?: return
        session.audioVideo().addRealtimeDataMessageObserverWithTopic(topic = topic, observer = this)
    }

    fun unsubscribeTopic(topic: String) {
        val session = meetingSession ?: return
        subscribedTopics.remove(topic)
        session.audioVideo().removeRealtimeDataMessageObserverFromTopicWithTopic(topic = topic)
    }

    override fun audioSessionDidStartConnectingWithReconnecting(reconnecting: Boolean) {
        onConnectionStatusChanged?.invoke(
            if (reconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING
        )
    }

    override fun audioSessionDidStartWithReconnecting(reconnecting: Boolean) {
        onConnectionStatusChanged?.invoke(ConnectionStatus.CONNECTED)

        startRemoteVideoIfNeeded()

        registerSubscribedTopics()
    }

    override fun audioSessionDidDrop() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.RECONNECTING)
        onSessionError?.invoke("Audio dropped, reconnecting...", true)
    }

    override fun audioSessionDidStopWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {
        onConnectionStatusChanged?.invoke(ConnectionStatus.DISCONNECTED)
        onSessionError?.invoke("Session ended: ${sessionStatus.statusCode()}", false)
    }

    override fun audioSessionDidCancelReconnect() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.ERROR)
        onSessionError?.invoke("Failed to reconnect", false)
    }

    override fun connectionDidBecomePoor() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.POOR_CONNECTION)
    }

    override fun connectionDidRecover() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.CONNECTED)
    }

    override fun videoSessionDidStartConnecting() {}

    override fun videoSessionDidStartWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {
        if (sessionStatus.statusCode() == MeetingSessionStatusCodeVideoAtCapacityViewOnly) {
            onSessionError?.invoke("Video at capacity. View only.", false)
        }
    }

    override fun videoSessionDidStopWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {}

    override fun remoteVideoSourcesDidBecomeAvailableWithSources(sources: List<*>) {
        val newSources = sources.filterIsInstance<RemoteVideoSource>()

        newSources.forEach { remoteVideoSources[it.attendeeId()] = it }

        onRemoteVideoAvailable?.invoke(true, newSources.size)
    }

    override fun remoteVideoSourcesDidBecomeUnavailableWithSources(sources: List<*>) {
        val unavailable = sources.filterIsInstance<RemoteVideoSource>()
        unavailable.forEach { remoteVideoSources.remove(it.attendeeId()) }
        onRemoteVideoAvailable?.invoke(false, unavailable.size)
    }

    private var didStartLocalVideo = false

    override fun cameraSendAvailabilityDidChangeWithAvailable(available: Boolean) {
        onCameraSendAvailable?.invoke(available)

        val session = meetingSession ?: return

        if (available && !didStartLocalVideo) {
            session.audioVideo().startLocalVideoAndReturnError(null)
            didStartLocalVideo = true
            isLocalVideoStarted = true
        }
    }

    override fun attendeesDidJoinWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesJoined(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun attendeesDidLeaveWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesLeft(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun attendeesDidDropWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesDropped(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun attendeesDidMuteWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesMuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun attendeesDidUnmuteWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesUnmuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun signalStrengthDidChangeWithSignalUpdates(signalUpdates: List<*>) {
        signalUpdates.filterIsInstance<SignalUpdate>().forEach { update ->
            realTimeListener?.onSignalStrengthChanged(
                update.attendeeInfo().attendeeId(),
                update.attendeeInfo().externalUserId(),
                update.signalStrength().toInt()
            )
        }
    }

    override fun volumeDidChangeWithVolumeUpdates(volumeUpdates: List<*>) {
        volumeUpdates.filterIsInstance<VolumeUpdate>().forEach { update ->
            realTimeListener?.onVolumeChanged(
                update.attendeeInfo().attendeeId(),
                update.attendeeInfo().externalUserId(),
                update.volumeLevel().toInt()
            )
        }
    }

    override fun videoTileDidAddWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        if (tileState.isLocalTile()) {
            localTileId = tileId
            meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
            onLocalVideoTileAdded?.invoke(tileId.toInt())
        } else {
            val attendeeId = tileState.attendeeId()
            val oldTileId = attendeeTileMap[attendeeId]
            if (oldTileId != null && oldTileId != tileId) {
                meetingSession?.audioVideo()?.unbindVideoViewWithTileId(tileId = oldTileId)
                remoteTiles.remove(oldTileId)
                attendeeTileMap.remove(attendeeId)
                onRemoteTileRemoved?.invoke(oldTileId.toInt())
            }
            val renderView = remoteTiles.getOrPut(tileId) {
                DefaultVideoRenderView().also { it.setMirror(false) }
            }
            attendeeTileMap[attendeeId] = tileId
            renderView.setFrame(renderView.superview?.bounds ?: renderView.bounds)
            onRemoteTileAdded?.invoke(tileId.toInt())
        }
    }

    override fun videoTileDidRemoveWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        meetingSession?.audioVideo()?.unbindVideoViewWithTileId(tileId = tileId)

        if (tileId == localTileId) {
            localTileId = null
            onLocalVideoTileRemoved?.invoke()
        } else if (remoteTiles.containsKey(tileId)) {
            remoteTiles.remove(tileId)
            if (attendeeTileMap[tileState.attendeeId()] == tileId) {
                attendeeTileMap.remove(tileState.attendeeId())
            }
            onRemoteTileRemoved?.invoke(tileId.toInt())
        }
    }

    override fun videoTileDidPauseWithTileState(tileState: VideoTileState) {}

    override fun videoTileDidResumeWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()
        if (tileState.isLocalTile()) {
            localTileId = tileId
            meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
            onLocalVideoTileAdded?.invoke(tileId.toInt())
        } else {
            val renderView = remoteTiles.getOrPut(tileId) {
                DefaultVideoRenderView().also { it.setMirror(false) }
            }
            attendeeTileMap[tileState.attendeeId()] = tileId
            renderView.setFrame(renderView.superview?.bounds ?: renderView.bounds)
            onRemoteTileAdded?.invoke(tileId.toInt())
        }
    }

    override fun videoTileSizeDidChangeWithTileState(tileState: VideoTileState) {}

    override fun audioDeviceDidChangeWithFreshAudioDeviceList(freshAudioDeviceList: List<*>) {}

    override fun activeSpeakerDidDetectWithAttendeeInfo(attendeeInfo: List<*>) {
        val ids = attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.externalUserId() }.toSet()
        onActiveSpeakersChanged?.invoke(ids)
    }

    override fun activeSpeakerScoreDidChangeWithScores(scores: Map<Any?, *>) {}

    override fun dataMessageDidReceivedWithDataMessage(dataMessage: DataMessage) {
        val topic = dataMessage.topic()
        val senderId = dataMessage.senderAttendeeId()
        val content = dataMessage.text() ?: ""
        val timestamp = dataMessage.timestampMs()
        topicListeners[topic]?.invoke(
            TextMessage(topic = topic, senderId = senderId, content = content, timestamp = timestamp)
        )
    }
}
